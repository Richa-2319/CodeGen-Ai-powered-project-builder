# OCI account and GenAI configuration

This guide describes reusable provider configuration. It does not create an
account, grant trial credits, transfer charges or provision resources.

## Account prerequisites

Use an OCI account authorized for the deployment. Complete signup, identity
verification and account terms directly in Oracle's interface when needed.
Configure an external CLI profile and verify tenancy, region, service access
and permissions before planning infrastructure. Never place passwords, service
keys, signing keys or operator tokens in Git.

For trial hosting, check expiration, remaining credits and continued service
access after the trial. See [capacity planning](FREE-TIER-ASSESSMENT.md).

## Compatible inference endpoint

The intelligence service accepts a base URL, completion path and model name.
Set these nonsecret values for the account's confirmed region and supported API:

```text
AI_BASE_URL=https://inference.generativeai.REGION.oci.oraclecloud.com
AI_CHAT_COMPLETIONS_PATH=/20231130/actions/v1/chat/completions
AI_MODEL=CONFIRMED_ON_DEMAND_MODEL_ID
```

Supply the OCI Generative AI service API key through `AI_API_KEY` in the
protected runtime Secret. An OCI CLI session token or IAM signing key is not
interchangeable with a provider bearer key. Restrict inference permissions to
the required compartment and model; maintain an expiry/renewal procedure.

The demonstration exercised `openai.gpt-oss-120b` in `us-chicago-1`. Verify
current model availability and on-demand access for your account; a catalog
entry does not establish inference access in every region.

Compose forwards the endpoint variables from the protected environment file.
The Kubernetes overlays import optional settings from
`codegen-core/runtime-extra-config`; keep the provider key in
`codegen-core/app-secrets`. Environment changes require a reviewed intelligence
service rollout because values are read at startup.

Use the complete completion path matching the selected API. Do not concatenate
multiple API prefixes. Regression tests cover configurable routing, bearer
authentication, model/output limits and streamed response parsing; actual
provider access and tool-call compatibility need a live check.

## Separate hosting and inference accounts

The backend can use a dedicated GenAI service key from an authorized inference
account while running on another host. Hosting location alone does not move
inference charges. Configure and verify the intended inference billing context;
native cross-tenancy IAM is a separate configuration choice.

After a small inference check, verify attribution in provider usage records
before general use. The application usage display is not an invoice or a cloud
spending cap. This configuration does not transfer previously incurred charges.

## Provider references

- [Service API keys and endpoints](https://docs.oracle.com/en-us/iaas/Content/generative-ai/api-keys.htm)
- [Chat Completions API](https://docs.oracle.com/en-us/iaas/Content/generative-ai/chat-completions-api.htm)
- [Model and endpoint regions](https://docs.oracle.com/en-us/iaas/Content/generative-ai/model-endpoint-regions.htm)
- [Key and model restrictions](https://docs.oracle.com/en-us/iaas/Content/generative-ai/add-api-permission.htm)
- [Native cross-tenancy IAM](https://docs.oracle.com/en-us/iaas/Content/Identity/policieshow/iam-cross-domain.htm)
