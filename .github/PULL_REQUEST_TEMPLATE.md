## Summary
<!-- One paragraph: what changes and why. Title must be a conventional commit, e.g. `feat(ticket): enforce status transition table (TD-23)` -->

Closes #

## What a reviewer should look at first
<!-- The riskiest file or decision. Point at it. -->

## Evidence
<!-- curl output, screenshot, test names, Swagger diff. Anything user-visible needs proof here. -->

## Checklist (self-review before requesting review)
- [ ] Read the whole diff top to bottom in the GitHub UI
- [ ] Every acceptance criterion in the linked issue has a test that fails without this change
- [ ] Unit / slice / integration tests as appropriate; coverage gate green locally (`./mvnw verify`)
- [ ] Migration (if any) named `V{n}__…`, applies from empty and on top of the previous version; seed still runs twice cleanly
- [ ] `docs/openapi.yaml` regenerated and committed; `breaking-change` label added if the diff is breaking (and justified below)
- [ ] New error paths return Problem Details; role rules tested positive and negative
- [ ] No secrets, no `System.currentTimeMillis`, no field injection, no `TODO` without an issue number
- [ ] Docs: ARCHITECTURE.md / ADR / README / CHANGELOG touched where relevant
- [ ] Formatted (`make fmt`), ArchUnit green

## Breaking change justification
<!-- Only if labelled `breaking-change`. -->
