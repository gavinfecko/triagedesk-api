#!/usr/bin/env bash
# Move an issue's card on the TriageDesk board: scripts/board-status.sh <issue-number> "<Backlog|Ready|In Progress|In Review|Done>"
# GitHub's built-in project workflows already move closed issues to Done; this covers the steps in between.
set -euo pipefail
ISSUE="$1"; STATUS="$2"; OWNER="gavinfecko"; NUMBER=2; REPO="triagedesk-api"
read -r PID FID OID < <(gh api graphql -f query='query($o:String!,$n:Int!){ user(login:$o){ projectV2(number:$n){ id field(name:"Status"){ ... on ProjectV2SingleSelectField{ id options{ id name } } } } } }' \
  -f o="$OWNER" -F n="$NUMBER" --jq ".data.user.projectV2 | [.id, .field.id, (.field.options[] | select(.name==\"$STATUS\") | .id)] | @tsv")
ITEM=$(gh api graphql -f query='query($o:String!,$r:String!,$i:Int!){ repository(owner:$o,name:$r){ issue(number:$i){ projectItems(first:5){ nodes{ id project{ number } } } } } }' \
  -f o="$OWNER" -f r="$REPO" -F i="$ISSUE" --jq ".data.repository.issue.projectItems.nodes[] | select(.project.number==$NUMBER) | .id")
if [ -z "$ITEM" ]; then
  ITEM=$(gh project item-add "$NUMBER" --owner "$OWNER" --url "https://github.com/$OWNER/$REPO/issues/$ISSUE" --format json --jq .id)
fi
gh api graphql -f query='mutation($p:ID!,$i:ID!,$f:ID!,$v:String!){ updateProjectV2ItemFieldValue(input:{projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$v}}){ projectV2Item{ id } } }' \
  -f p="$PID" -f i="$ITEM" -f f="$FID" -f v="$OID" --silent
echo "#$ISSUE → $STATUS"
