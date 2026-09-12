// registry-auth renews one named image-pull Secret using OCI instance identity.
package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/oracle/oci-go-sdk/v65/common"
	"github.com/oracle/oci-go-sdk/v65/common/auth"
)

const registry = "iad.ocir.io"
const secretName = "codegen-registry-pull"
const serviceAccountDirectory = "/var/run/secrets/kubernetes.io/serviceaccount/"

var namespaces = []string{"kube-system", "codegen-build", "codegen-core"}

type silentSDKLogger struct{}

func (silentSDKLogger) LogLevel() int                         { return 0 }
func (silentSDKLogger) Log(int, string, ...interface{}) error { return nil }

type registryCredential struct {
	ServerURL string
	Username  string
	Secret    string
}

func noRedirect(_ *http.Request, _ []*http.Request) error {
	return errors.New("redirect refused")
}

func fetchCredential(ctx context.Context, expectedTenancy string) (registryCredential, error) {
	var empty registryCredential
	provider, err := auth.InstancePrincipalConfigurationProvider()
	if err != nil {
		return empty, errors.New("instance identity unavailable")
	}
	tenancy, err := provider.TenancyOCID()
	if err != nil || tenancy != expectedTenancy {
		return empty, errors.New("instance tenancy mismatch")
	}
	client, err := common.NewClientWithConfig(provider)
	if err != nil {
		return empty, errors.New("signed registry client unavailable")
	}
	client.Host = registry
	client.HTTPClient = &http.Client{Timeout: 30 * time.Second, CheckRedirect: noRedirect}
	endpoint, _ := url.Parse("https://" + registry + "/20180419/docker/token")
	request := &http.Request{Method: http.MethodGet, URL: endpoint, Header: http.Header{"Accept": {"application/json"}}}
	response, err := client.Call(ctx, request)
	if err != nil {
		if response != nil && response.Body != nil {
			response.Body.Close()
		}
		return empty, errors.New("registry authentication request failed")
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return empty, fmt.Errorf("registry authentication returned HTTP %d", response.StatusCode)
	}
	var result struct {
		Token string `json:"token"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 1<<20)).Decode(&result); err != nil || result.Token == "" {
		return empty, errors.New("registry authentication response invalid")
	}
	return registryCredential{ServerURL: registry, Username: "BEARER_TOKEN", Secret: result.Token}, nil
}

func secretPatch(credential registryCredential) ([]byte, error) {
	if credential.ServerURL != registry || credential.Username != "BEARER_TOKEN" || credential.Secret == "" {
		return nil, errors.New("invalid registry credential")
	}
	authValue := base64.StdEncoding.EncodeToString([]byte(credential.Username + ":" + credential.Secret))
	config, err := json.Marshal(map[string]any{"auths": map[string]any{registry: map[string]string{"auth": authValue}}})
	if err != nil {
		return nil, errors.New("registry configuration encoding failed")
	}
	return json.Marshal(map[string]any{"data": map[string]string{".dockerconfigjson": base64.StdEncoding.EncodeToString(config)}})
}

func patchSecrets(ctx context.Context, credential registryCredential) error {
	ca, err := os.ReadFile(serviceAccountDirectory + "ca.crt")
	if err != nil {
		return errors.New("Kubernetes CA unavailable")
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(ca) {
		return errors.New("Kubernetes CA invalid")
	}
	token, err := os.ReadFile(serviceAccountDirectory + "token")
	if err != nil || len(bytes.TrimSpace(token)) == 0 {
		return errors.New("Kubernetes service account identity unavailable")
	}
	client := &http.Client{Timeout: 20 * time.Second, CheckRedirect: noRedirect,
		Transport: &http.Transport{TLSClientConfig: &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12}}}
	body, err := secretPatch(credential)
	if err != nil {
		return err
	}
	for _, namespace := range namespaces {
		endpoint := "https://kubernetes.default.svc/api/v1/namespaces/" + namespace + "/secrets/" + secretName
		request, err := http.NewRequestWithContext(ctx, http.MethodPatch, endpoint, bytes.NewReader(body))
		if err != nil {
			return errors.New("Kubernetes request construction failed")
		}
		request.Header.Set("Authorization", "Bearer "+strings.TrimSpace(string(token)))
		request.Header.Set("Content-Type", "application/merge-patch+json")
		response, err := client.Do(request)
		if err != nil {
			return fmt.Errorf("registry secret refresh transport failed in %s", namespace)
		}
		// The response body contains the Secret. Never read it into logs.
		response.Body.Close()
		if response.StatusCode != http.StatusOK {
			return fmt.Errorf("registry secret refresh returned HTTP %d in %s", response.StatusCode, namespace)
		}
	}
	return nil
}

func run() error {
	mode := flag.String("mode", "refresh", "refresh, check, or credential (private bootstrap pipe only)")
	tenancy := flag.String("tenancy", "", "expected OCI tenancy OCID")
	flag.Parse()
	if !strings.HasPrefix(*tenancy, "ocid1.tenancy.oc1..") || !(*mode == "refresh" || *mode == "check" || *mode == "credential") {
		return errors.New("valid mode and expected tenancy are required")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	credential, err := fetchCredential(ctx, *tenancy)
	if err != nil {
		return err
	}
	switch *mode {
	case "credential":
		// Only the local bootstrap controller may consume this pipe, never a terminal.
		info, err := os.Stdout.Stat()
		if err != nil || info.Mode()&os.ModeCharDevice != 0 {
			return errors.New("credential output requires a private pipe")
		}
		return json.NewEncoder(os.Stdout).Encode(credential)
	case "refresh":
		if err := patchSecrets(ctx, credential); err != nil {
			return err
		}
		fmt.Println("Project registry pull credentials refreshed in three namespaces.")
	case "check":
		fmt.Println("Instance-principal registry authentication succeeded.")
	}
	return nil
}

func main() {
	// SDK diagnostics can include signed headers. Only fixed, sanitized errors escape.
	log.SetOutput(io.Discard)
	os.Unsetenv("OCI_GO_SDK_DEBUG")
	common.SetSDKLogger(silentSDKLogger{})
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
