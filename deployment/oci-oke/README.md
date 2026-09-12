# Reviewed OKE demonstration infrastructure

Select and verify the OCI profile, tenancy, region, parent compartment and
hosting budget before using this template. `oci_profile` has no default.
Copy the generic example to a protected external target file and use a fresh
reviewed plan. See [the deployment guide](../OCI-BASIC-OKE.md).

This configuration creates a `codegen-demo` compartment beneath the explicit
parent, or reads `existing_compartment_ocid` when provided. It then creates an
isolated network, a BASIC OKE cluster, and one managed
`VM.Standard.A1.Flex` ARM worker at **2 OCPU / 12 GB RAM / 50 GB boot volume** in
the explicitly selected subscribed OCI region. It creates no application Secrets, Kubernetes
workloads, registry credentials, load balancer, DNS record,
certificate, object-storage bucket, or paid enhanced-cluster feature.

Application resources, image pins, public ingress and any separately managed
API Gateway/IAM configuration must be prepared for the chosen environment.
The template alone does not establish application readiness or free hosting.

## Required inputs

Copy `example.tfvars.json` to a private external location and replace every
placeholder with information from the explicitly selected tenancy. Use the
authorized external SecurityToken profile and set `oci_profile`, tenancy,
deployment region, approved parent compartment, availability-domain name, supported
Kubernetes version, matching ARM OKE image and administrator CIDRs explicitly.
Do not reuse identifiers or credentials from another account. The defaults in
`variables.tf` are examples from local configuration validation, not proof of
availability for your selected account. Recheck image/version compatibility and
physical A1 capacity immediately before provisioning; an option listing is not
a capacity reservation.

For another regional stack in the same project, set `existing_compartment_ocid`
to the verified project compartment and use a separate backend state path and
`TF_DATA_DIR`. A data-source postcondition checks its parent and active state.
The original state retains ownership of the compartment. The `moved` block
preserves its identity when an older state with an unindexed compartment is
opened with this version of the configuration.

`admin_access_cidrs` has **no default**. Supply the confirmed public egress
CIDR(s) of the administrator access path, each `/24` through `/32`. A
SecurityToken authorizes OCI API requests; it does not independently provide a
network path to the new Kubernetes API. A loopback or documentation CIDR is
suitable only for a clearly labeled non-applicable plan preview. Replace it
with real operator egress CIDRs and create a fresh reviewed plan before apply.

Before creation, verify the parent has no conflicting `codegen-demo`
compartment, the operator has permissions to create its compartment/network/
cluster/node pool, and the tenancy has the necessary OKE service policies.
Check regional `vcn-count` availability as well as Basic-cluster and A1 limits;
available compute quota does not imply that another VCN can be created.
This module does not grant or broaden tenancy-level IAM permissions. Also
confirm the VCN CIDR will not collide with any network that might later be
peered with it.

## Network design

| Component | CIDR default | Connectivity |
| --- | --- | --- |
| API endpoint | `10.77.0.0/28` | Public IP; TCP6443 only from required admin CIDRs and workers; worker control traffic TCP12250 |
| Retained unused subnet | `10.77.1.0/24` | Original public-worker subnet; no worker is placed here |
| Public web ingress | `10.77.2.0/24` | NLB HTTP redirect and managed HTTPS gateway; TCP80/443 |
| Managed worker | `10.77.3.0/24` | Private VNIC; outbound traffic through reserved-IP NAT |
| Flannel pods | `10.244.0.0/16` | Overlay networking; no separate pod subnet |
| Kubernetes Services | `10.96.0.0/16` | Cluster-internal virtual addresses |

All traffic rules are stateful. Workers admit intra-worker traffic and control
plane TCP traffic. ICMP fragmentation-needed rules support path MTU discovery.
Worker outbound TCP permits OKE/OCI services, image downloads, AI and Stripe;
link-local DNS/NTP and intra-worker traffic are included. The API endpoint can
reach workers and OKE over HTTPS. Each subnet explicitly uses its reviewed
security list instead of the VCN's default SSH-enabled list. Workers require
no ephemeral public IP. The original public subnet is retained because OCI
cannot change subnet access type in place and its deletion was not authorized.
The new subnet uses a dedicated route table and NAT gateway with a reserved IP,
as supported by [Oracle's NAT configuration](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/nat-create.htm).

This uses Oracle's documented [OKE network requirements](https://docs.oracle.com/en-us/iaas/Content/ContEng/Concepts/contengnetworkconfig.htm)
and [cluster configuration](https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/containerengine_cluster).
Flannel does not enforce Kubernetes NetworkPolicy objects. Oracle documents
that [Calico is unsupported with OKE Flannel](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengsettingupcalico.htm).
Server-side generated-code execution remains disabled. The application now
has a browser sandbox preview, which requires no code execution on the worker.

Set `public_ui_backend_ipv4` to the verified private worker IP for the existing
UI deployment. Null keeps new ingress subnets closed. The enabled configuration
preserves public TCP80/443, the NLB's worker listener/health ports, and managed
HTTPS access to frontend NodePort32081. Worker ingress is limited to the project
ingress subnet; API Gateway egress on32081 targets that one worker /32. The
NLB controller and API Gateway resources are managed separately. Review a fresh
plan after controller changes; do not apply an old closed-ingress plan.

### DNS and image access

Verify CoreDNS readiness and service-name resolution before deploying the
application. The recovery manifest is an optional diagnostic aid; inspect the
cluster's DNS address and image requirements before using it.

Verify registry and provider connectivity from the worker. When authorized
private mirrors are required, use verified image digests and configure scoped
pull access. The registry renewal helper uses worker identity; operator session
credentials must remain outside the cluster. Recheck access after replacing a
worker, and keep DNS and registry components under normal maintenance.

## Authentication, provider, and state

The provider uses `auth = "SecurityToken"`, the required `config_file_profile = var.oci_profile`, and
the explicit target region/tenancy, following [Oracle provider configuration](https://docs.oracle.com/en-us/iaas/Content/dev/terraform/configuring.htm).
It reads the already-configured external profile. Do not copy tokens, private
keys, `.oci/config`, or Kubernetes authentication data into this tree or tfvars.
Refresh the selected profile externally through its established login process before plan/apply;
token lifetime can be shorter than provisioning time. Avoid Terraform debug
logging because authentication-bearing data can appear in logs.

The configuration requires Terraform `>=1.12,<2` and OCI provider `~>8.27.0`,
whose resource schema includes the [managed-node shape, placement and boot-volume fields](https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/containerengine_node_pool).
Use the checked-in dependency lock file. Store state and saved plans outside
this repository in an access-restricted durable directory. No state file or
credential is included here. The backend below is local for one operator; a
shared/encrypted remote backend requires a separate setup decision.

## Validation and read-only plan

From the repository root, with `terraform` available:

```bash
terraform -chdir=deployment/oci-oke fmt -check -recursive
# Offline initialization downloads the locked public provider but does not call OCI.
TF_DATA_DIR=/tmp/codegen-oke-validate terraform -chdir=deployment/oci-oke init -backend=false
TF_DATA_DIR=/tmp/codegen-oke-validate terraform -chdir=deployment/oci-oke validate
TF_DATA_DIR=/tmp/codegen-oke-validate terraform -chdir=deployment/oci-oke test
```

The six tests use a mocked OCI provider and plan-only runs. The enabled public
UI rules include provider-computed nested fields and are additionally checked
against a fresh read-only live plan for the destination. The mocked tests check the bounded
allocation, BASIC cluster, subnet separation and absence of public API/worker
TCP ingress; they do not prove IAM, capacity, pricing or runtime readiness.

For a real read-only plan, first choose an access-restricted external directory
for state/plans and create an external `target.local.tfvars.json` from the
generic example, containing the confirmed target and administrator values.
Do not put credentials in that file. Then run:

```bash
CODEGEN_TF_PRIVATE_DIR='/absolute/private/path/codegen-oke'
# Create that directory with mode 0700 through your normal local setup process.
TF_DATA_DIR="$CODEGEN_TF_PRIVATE_DIR/provider-data" terraform -chdir=deployment/oci-oke init \
  -backend-config="path=$CODEGEN_TF_PRIVATE_DIR/codegen-demo.tfstate"
TF_DATA_DIR="$CODEGEN_TF_PRIVATE_DIR/provider-data" terraform -chdir=deployment/oci-oke plan \
  -input=false -var-file="$CODEGEN_TF_PRIVATE_DIR/target.local.tfvars.json" \
  -out="$CODEGEN_TF_PRIVATE_DIR/codegen-demo.tfplan"
```

Planning reads the target using the selected profile and writes a local plan; it does not create
OCI resources. Do not use `-refresh=false` to imply a plan checked current state.
Review that it creates exactly the dedicated resources below and contains no
existing-resource replacement or deletion. Confirm the exact fresh plan satisfies the user's authorized scope and hosting budget.
If it does not, resolve that specific cost/scope decision with the
user before applying. No apply command
is automated or embedded in this project.

## Resource and lifecycle review

There are 16 Terraform-managed resources when creating a new compartment, or
15 when reading the existing one: VCN, IGW, NAT gateway, reserved egress IP, two
route tables, three security lists, four regional subnets, cluster, and node
pool, plus the optional compartment. OCI also
creates the VCN's default network objects and the node's compute/private-VNIC/
boot-volume resources. The application PVCs are created later by the Kubernetes
CSI driver when the demo manifests are applied; they are outside this Terraform
state. Existing tenancy resources are not imported, repurposed or deleted.

The worker allocation is a fixed single node with no autoscaler, upgrade surge,
SSH key or automatic node cycling configured. Demo pods request 1.05 CPU and
2112Mi; add node/system/DNS/ingress overhead and temporary rollout headroom.
One node provides no high availability. Image/version updates and node upgrades
need a reviewed outage/capacity plan; changing the node-pool desired image does
not by itself prove running nodes have been replaced or upgraded.

`prevent_destroy` protects the compartment, cluster and node pool from accidental
Terraform deletion. The compartment also has deletion disabled. Removing those
guards or cleaning up Kubernetes PVCs/cloud volumes requires an explicit
separate decision and verified backups. Do not run `terraform destroy` as a
routine retry. After approved creation, obtain kubeconfig externally using the
exact output cluster ID/profile/region, verify ARM node readiness and `oci-bv`,
and follow the repository's `k8s/demo/README.md` for application preparation.
