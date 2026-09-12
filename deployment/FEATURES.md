# Using CodeGen

Open your deployment's HTTPS URL and sign in. Existing projects remain private
unless their owner publishes them. This repository does not provision a live
endpoint automatically; see [deployment setup](README.md).

## Preview and edit

Open a project and select **Preview**. The saved app loads automatically after generation finishes. **Run Preview** or the refresh icon reloads its files. Empty projects show a generation prompt. Runtime errors appear in the existing error panel.

The preview runs React/TypeScript in an isolated browser frame. It supports relative imports, `@/` aliases, common UI packages, UUID identifiers, Tailwind and daisyUI. The UUID 11.1.0 browser bundle and license are pinned in `frontend/preview/vendor/uuid`; its checksums are verified during the build. Other preview assets are self-hosted too.

Select **Code**, open a file, edit it and click **Save** (or Cmd/Ctrl-S). Unsaved files have an indicator; navigating away warns about drafts. Saving refreshes the private preview. Viewers cannot edit. Save and generation cannot run concurrently through the editor UI.

Browser storage inside preview is temporary and resets on restart. External APIs, remote assets, arbitrary npm packages and server processes are unavailable. Custom package/config scripts are not executed. Projects needing those capabilities require a separately configured runtime. A working React preview does not establish support for every generated framework or dependency.

## Share private access

Choose **Share**, enter another existing CodeGen account's email, select **Can edit** or **Can view**, and click **Add**. The project appears in that account's dashboard. Copy the private project link to give them the URL; no email is sent automatically.

Only the owner can add/remove members or change their roles. Editors can save files and generate changes. Viewers can read the project and run its preview. Neither role can publish, take ownership, remove the owner or manage membership. Removed members lose project access.

## Publish an app

The owner selects **Publish** and then **Publish app**. This creates a public `/p/<id>` link containing a snapshot of the saved frontend files. Anyone with that link can run the app and access its frontend source. Account details, membership and chat history are not part of the snapshot. Never publish secrets embedded in frontend source.

Edits stay private until **Publish latest version** is selected. Republishing retains the same link. **Unpublish**, or deleting the project, removes future public access. Previously downloaded copies cannot be recalled. Publishing does not create a worker, container or dedicated cloud resource; the same isolated browser runtime renders the snapshot.

## Plan and usage

**Plan & usage** shows the subscribed plan, owned-project count, daily AI allowance, recorded usage and its date/time zone. Paid upgrades remain disabled by the owner's choice. Reported provider token counts are preferred; missing counts are estimated from the text. This display is not the OCI bill and estimates exclude unreported system/reasoning/tool tokens.

The provider and model are deployment settings. OCI Generative AI with
`openai.gpt-oss-120b` has been exercised in the demonstration deployment;
configure your own authorized endpoint, model and service key using
[the provider guide](OCI-TRIAL.md). Confirm current provider pricing and usage
attribution in your account; the app's usage display is not a billing report.

## Verification

See [VALIDATION.md](VALIDATION.md) for test scope and [OCI-BASIC-OKE.md](OCI-BASIC-OKE.md) for deployment operations. The live checks use synthetic accounts and temporary projects, and never publish a user's existing project automatically.
