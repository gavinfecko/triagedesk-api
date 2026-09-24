#!/usr/bin/env bash
# Make the four CI jobs required on main. Run once after the CI workflow (TD-3) has reported on a PR.
set -euo pipefail
OWNER="gavinfecko"; REPO="triagedesk-api"
CHECKS='{"strict":true,"contexts":["lint","build-test","security","openapi-diff"]}'
printf '%s' "$CHECKS" | gh api -X PATCH "repos/$OWNER/$REPO/branches/main/protection/required_status_checks" --input - --silent
echo "main now requires: lint, build-test, security, openapi-diff"
