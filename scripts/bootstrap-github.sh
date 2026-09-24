#!/usr/bin/env bash
# One-time GitHub setup for triagedesk-api. Dry-run by default; --apply executes.
#
# Creates: the PRIVATE repo from this directory (pushes main), every label in
# .github/labels.yml, milestones Sprint 0..6 with due dates from docs/PLAN.md,
# the user-level Project "TriageDesk" with fields Points / Priority / Type / Epic,
# links the repo to it, protects main, then imports the backlog as issues.
#
# Requires: gh >= 2.40 authenticated with scopes repo, workflow, project, read:project
#   gh auth refresh -s project,read:project   (one-time, opens the browser)
#
# The Sprint *iteration* field cannot be created by the CLI: add it once in the
# project UI (Settings -> Fields -> + New field -> Iteration, 1 week, start Mon 2026-09-29).
set -euo pipefail

OWNER="gavinfecko"
REPO="triagedesk-api"
PROJECT_TITLE="TriageDesk"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPLY=false
[[ "${1:-}" == "--apply" ]] && APPLY=true

run() { if $APPLY; then "$@"; else echo "  would: $*"; fi; }
say() { printf '\n== %s\n' "$*"; }

say "Preflight"
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated"; exit 1; }
SCOPES="$(gh auth status 2>&1 | grep -i 'token scopes' || true)"
echo "  $SCOPES"
HAS_PROJECT=true
if ! grep -q "project" <<<"$SCOPES"; then
  HAS_PROJECT=false
  echo "  missing 'project' scope: steps 1-3, 5, 6 run; the board (step 4) is skipped."
  echo "  later:  gh auth refresh -s project,read:project  &&  scripts/bootstrap-github.sh --apply   (idempotent)"
fi
cd "$ROOT"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "not a git repo"; exit 1; }
$APPLY || echo "  DRY RUN (pass --apply to execute)"

say "1. Repository (private)"
if gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "  exists: $OWNER/$REPO"
  git remote get-url origin >/dev/null 2>&1 || run git remote add origin "https://github.com/$OWNER/$REPO.git"
else
  run gh repo create "$OWNER/$REPO" --private --source . --remote origin --push \
    --description "Help desk ticketing API: Java 21, Spring Boot, PostgreSQL, JWT, SLA engine. Run as a solo Agile project with the whole process on GitHub." \
    --homepage "https://github.com/$OWNER/$REPO/blob/main/docs/PLAN.md"
fi
run gh repo edit "$OWNER/$REPO" --add-topic spring-boot --add-topic java --add-topic postgresql --add-topic rest-api --add-topic agile --add-topic help-desk \
  --enable-issues --enable-projects --delete-branch-on-merge --enable-squash-merge --disable-merge-commit --disable-rebase-merge

say "2. Labels from .github/labels.yml"
python3 - "$ROOT/.github/labels.yml" <<'PY' | while IFS=$'\t' read -r name color desc; do
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r"- name: (.+)\n  color: (\w+)\n  description: (.+)", text):
    print("\t".join(g.strip() for g in m.groups()))
PY
  run gh label create "$name" --repo "$OWNER/$REPO" --color "$color" --description "$desc" --force
done

say "3. Milestones (sprints)"
declare -a MS=(
  "Sprint 0|2026-09-28|Skeleton, CI, board, error model, docs"
  "Sprint 1|2026-10-05|Register, log in, open and read a ticket; roles; seed data"
  "Sprint 2|2026-10-12|Agents work tickets: search, assign, state machine, replies, audit"
  "Sprint 3|2026-10-19|SLA engine: policies, business hours, timers, breaches, email"
  "Sprint 4|2026-10-26|Dashboard, export, hardening, Docker, live deploy, v1.0.0"
  "Sprint 5|2026-11-02|Angular: client, auth, my tickets, detail, agent queue"
  "Sprint 6|2026-11-09|Angular: admin, dashboard, e2e, Pages, portfolio"
)
EXISTING="$(gh api "repos/$OWNER/$REPO/milestones?state=all&per_page=100" --jq '.[].title' 2>/dev/null || true)"
for entry in "${MS[@]}"; do
  IFS='|' read -r title due desc <<<"$entry"
  if grep -qx "$title" <<<"$EXISTING"; then echo "  exists: $title"; continue; fi
  run gh api "repos/$OWNER/$REPO/milestones" -f title="$title" -f due_on="${due}T23:59:59Z" -f description="$desc" --silent
done

say "4. Project board '$PROJECT_TITLE' (user-level, spans api + web)"
PROJECT_NUM=""
if ! $HAS_PROJECT; then echo "  skipped (no project scope)"; fi
$HAS_PROJECT && PROJECT_JSON="$(gh project list --owner "$OWNER" --format json --limit 50 2>/dev/null || echo '{"projects":[]}')"
$HAS_PROJECT && PROJECT_NUM="$(printf '%s' "$PROJECT_JSON" | python3 -c 'import sys,json; d=json.loads(sys.stdin.read() or "{}"); print(next((p["number"] for p in d.get("projects",[]) if p["title"]==sys.argv[1]), ""))' "$PROJECT_TITLE" 2>/dev/null || true)"
if [[ -n "$PROJECT_NUM" ]]; then
  echo "  exists: #$PROJECT_NUM"
elif $HAS_PROJECT; then
  if $APPLY; then
    PROJECT_NUM="$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json | python3 -c 'import sys,json; print(json.load(sys.stdin)["number"])')"
    echo "  created: #$PROJECT_NUM"
  else
    echo "  would: gh project create --owner $OWNER --title $PROJECT_TITLE"
  fi
fi
if [[ -n "$PROJECT_NUM" ]]; then
  run gh project field-create "$PROJECT_NUM" --owner "$OWNER" --name Points --data-type NUMBER
  run gh project field-create "$PROJECT_NUM" --owner "$OWNER" --name Priority --data-type SINGLE_SELECT --single-select-options "P0,P1,P2,P3"
  run gh project field-create "$PROJECT_NUM" --owner "$OWNER" --name Type --data-type SINGLE_SELECT --single-select-options "story,bug,task,spike"
  run gh project field-create "$PROJECT_NUM" --owner "$OWNER" --name Epic --data-type SINGLE_SELECT --single-select-options "E0 Foundation,E1 Identity,E2 Ticket lifecycle,E3 Collaboration,E4 SLA,E5 Notifications,E6 Reporting,E7 Delivery,E8 Web"
  run gh project link "$PROJECT_NUM" --owner "$OWNER" --repo "$OWNER/$REPO"
  echo "  manual once: add the 'Sprint' ITERATION field (1 week, start 2026-09-29) and rename Status options to Backlog / Ready / In Progress / In Review / Done"
fi

say "5. Branch protection on main"
# Required check contexts are added by scripts/require-checks.sh once the CI workflow exists (TD-3).
PROT='{"required_status_checks":null,"enforce_admins":true,"required_pull_request_reviews":{"required_approving_review_count":0},"restrictions":null,"required_linear_history":true,"allow_force_pushes":false,"allow_deletions":false,"required_conversation_resolution":true}'
if $APPLY; then
  printf '%s' "$PROT" | gh api -X PUT "repos/$OWNER/$REPO/branches/main/protection" --input - --silent && echo "  protected"
else
  echo "  would: PUT repos/$OWNER/$REPO/branches/main/protection (PR required, linear, no force-push, admins included; checks added later by scripts/require-checks.sh)"
fi

say "6. Backlog -> issues"
if $APPLY; then
  python3 "$ROOT/scripts/backlog_to_issues.py" --repo "$OWNER/$REPO" --apply ${PROJECT_NUM:+--project "$PROJECT_NUM"} --owner "$OWNER"
else
  python3 "$ROOT/scripts/backlog_to_issues.py" --repo "$OWNER/$REPO" | tail -3
fi

say "Done"
$APPLY && echo "  board: https://github.com/users/$OWNER/projects/${PROJECT_NUM:-?}   repo: https://github.com/$OWNER/$REPO" || true
