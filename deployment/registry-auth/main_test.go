package main

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"testing"
)

func TestSecretPatchScopesCredentialToRegistry(t *testing.T) {
	patch, err := secretPatch(registryCredential{registry, "BEARER_TOKEN", "synthetic-test-value"})
	if err != nil {
		t.Fatal("valid synthetic credential rejected")
	}
	var body struct {
		Data map[string]string `json:"data"`
	}
	if json.Unmarshal(patch, &body) != nil || len(body.Data) != 1 {
		t.Fatal("expected exactly one secret data field")
	}
	raw, err := base64.StdEncoding.DecodeString(body.Data[".dockerconfigjson"])
	if err != nil {
		t.Fatal("invalid Kubernetes secret encoding")
	}
	var config struct {
		Auths map[string]map[string]string `json:"auths"`
	}
	if json.Unmarshal(raw, &config) != nil || len(config.Auths) != 1 {
		t.Fatal("invalid registry scope")
	}
	decoded, err := base64.StdEncoding.DecodeString(config.Auths[registry]["auth"])
	if err != nil || string(decoded) != "BEARER_TOKEN:synthetic-test-value" {
		t.Fatal("invalid Docker auth encoding")
	}
}

func TestSecretPatchRejectsWrongRegistryOrMissingCredential(t *testing.T) {
	for _, value := range []registryCredential{{"untrusted.invalid", "BEARER_TOKEN", "synthetic"}, {registry, "other", "synthetic"}, {registry, "BEARER_TOKEN", ""}} {
		if _, err := secretPatch(value); err == nil {
			t.Fatal("invalid credential accepted")
		}
	}
}

func TestRedirectsAreRefused(t *testing.T) {
	if noRedirect(&http.Request{}, nil) == nil {
		t.Fatal("credential-bearing redirect accepted")
	}
}
