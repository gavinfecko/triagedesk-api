#!/usr/bin/env bash
# Re-apply main's protection with the four CI jobs required. Safe to run any time (idempotent).
set -euo pipefail
OWNER="gavinfecko"; REPO="triagedesk-api"
PROT='{"required_status_checks":{"strict":true,"contexts":["lint","build-test","security","openapi-diff"]},"enforce_admins":true,"required_pull_request_reviews":{"required_approving_review_count":0},"restrictions":null,"required_linear_history":true,"allow_force_pushes":false,"allow_deletions":false,"required_conversation_resolution":true}'
printf '%s' "$PROT" | gh api -X PUT "repos/$OWNER/$REPO/branches/main/protection" --input - --silent
echo "main requires a PR and: lint, build-test, security, openapi-diff"
