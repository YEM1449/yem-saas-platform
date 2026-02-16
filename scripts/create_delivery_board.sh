#!/usr/bin/env bash
set -euo pipefail

# Creates/updates GitHub Projects v2 board directly via GraphQL API (no gh CLI).
# Required env:
#   GITHUB_TOKEN   - token with project scope
#   GH_OWNER       - org/user login where the project should live
# Optional env:
#   BOARD_TITLE    - defaults to "CRM-HLM — Delivery Board (Auto)"
#   REPO           - owner/repo for linking issue URLs in draft body (optional)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQ_FILE="$ROOT_DIR/docs/requirements/requirements.normalized.yml"
AUDIT_FILE="$ROOT_DIR/docs/audit/requirements-audit.md"
BOARD_TITLE="${BOARD_TITLE:-CRM-HLM — Delivery Board (Auto)}"
API_URL="https://api.github.com/graphql"

if [[ -z "${GITHUB_TOKEN:-}" || -z "${GH_OWNER:-}" ]]; then
  echo "ERROR: GITHUB_TOKEN and GH_OWNER are required." >&2
  exit 1
fi

if [[ ! -f "$REQ_FILE" || ! -f "$AUDIT_FILE" ]]; then
  echo "ERROR: expected files missing: $REQ_FILE and/or $AUDIT_FILE" >&2
  exit 1
fi

api_graphql() {
  local query="$1"
  local variables_json="${2:-{}}"

  curl -sS -X POST "$API_URL" \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$(jq -cn --arg q "$query" --argjson v "$variables_json" '{query:$q,variables:$v}')"
}

owner_query='query($login:String!){ organization(login:$login){id login} user(login:$login){id login} }'
owner_resp="$(api_graphql "$owner_query" "$(jq -cn --arg login "$GH_OWNER" '{login:$login}')")"
owner_id="$(echo "$owner_resp" | jq -r '.data.organization.id // .data.user.id // empty')"
if [[ -z "$owner_id" ]]; then
  echo "ERROR: could not resolve owner '$GH_OWNER' (org or user)." >&2
  echo "$owner_resp" >&2
  exit 1
fi

projects_query='query($login:String!){ organization(login:$login){projectsV2(first:100){nodes{id title number}}} user(login:$login){projectsV2(first:100){nodes{id title number}}} }'
projects_resp="$(api_graphql "$projects_query" "$(jq -cn --arg login "$GH_OWNER" '{login:$login}')")"
project_id="$(echo "$projects_resp" | jq -r --arg t "$BOARD_TITLE" '[.data.organization.projectsV2.nodes[]?, .data.user.projectsV2.nodes[]?] | map(select(.title==$t)) | .[0].id // empty')"

if [[ -z "$project_id" ]]; then
  create_project_mut='mutation($owner:ID!,$title:String!){ createProjectV2(input:{ownerId:$owner,title:$title}) { projectV2{id title number} } }'
  create_resp="$(api_graphql "$create_project_mut" "$(jq -cn --arg owner "$owner_id" --arg title "$BOARD_TITLE" '{owner:$owner,title:$title}')")"
  project_id="$(echo "$create_resp" | jq -r '.data.createProjectV2.projectV2.id // empty')"
  if [[ -z "$project_id" ]]; then
    echo "ERROR: failed to create project." >&2
    echo "$create_resp" >&2
    exit 1
  fi
  echo "Created board: $BOARD_TITLE"
else
  echo "Using existing board: $BOARD_TITLE"
fi

project_fields_query='query($id:ID!){ node(id:$id){ ... on ProjectV2 { id fields(first:100){nodes{... on ProjectV2FieldCommon{id name dataType} ... on ProjectV2SingleSelectField{id name options{id name}}}}}}}'

ensure_field_single_select() {
  local name="$1"
  local options_json="$2"

  local fields_resp existing
  fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
  existing="$(echo "$fields_resp" | jq -r --arg n "$name" '.data.node.fields.nodes[]? | select(.name==$n) | .id' | head -n1)"

  if [[ -n "$existing" ]]; then
    echo "$existing"
    return
  fi

  local mut='mutation($project:ID!,$name:String!,$opts:[ProjectV2SingleSelectFieldOptionInput!]!){ createProjectV2Field(input:{projectId:$project,name:$name,dataType:SINGLE_SELECT,singleSelectOptions:$opts}){ projectV2Field{... on ProjectV2SingleSelectField{id name}} } }'
  local resp
  resp="$(api_graphql "$mut" "$(jq -cn --arg project "$project_id" --arg name "$name" --argjson opts "$options_json" '{project:$project,name:$name,opts:$opts}')")"
  echo "$resp" | jq -r '.data.createProjectV2Field.projectV2Field.id'
}

ensure_field_text() {
  local name="$1"

  local fields_resp existing
  fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
  existing="$(echo "$fields_resp" | jq -r --arg n "$name" '.data.node.fields.nodes[]? | select(.name==$n) | .id' | head -n1)"

  if [[ -n "$existing" ]]; then
    echo "$existing"
    return
  fi

  local mut='mutation($project:ID!,$name:String!){ createProjectV2Field(input:{projectId:$project,name:$name,dataType:TEXT}){ projectV2Field{... on ProjectV2Field{id} ... on ProjectV2FieldCommon{id}} } }'
  local resp
  resp="$(api_graphql "$mut" "$(jq -cn --arg project "$project_id" --arg name "$name" '{project:$project,name:$name}')")"
  echo "$resp" | jq -r '.data.createProjectV2Field.projectV2Field.id'
}

status_field_id="$(ensure_field_single_select "Status" '[{"name":"Upcoming","color":"GRAY"},{"name":"In Progress","color":"BLUE"},{"name":"Done","color":"GREEN"},{"name":"Decision Needed","color":"RED"}]')"
module_field_id="$(ensure_field_single_select "Module" '[{"name":"MOD-01","color":"BLUE"},{"name":"MOD-02","color":"BLUE"},{"name":"MOD-03","color":"BLUE"},{"name":"MOD-04","color":"BLUE"},{"name":"MOD-05","color":"BLUE"},{"name":"MOD-06","color":"BLUE"},{"name":"MOD-07","color":"BLUE"},{"name":"MOD-08","color":"BLUE"},{"name":"MOD-09","color":"BLUE"},{"name":"MOD-10","color":"BLUE"},{"name":"MOD-11","color":"BLUE"},{"name":"MOD-12","color":"BLUE"},{"name":"MOD-13","color":"BLUE"}]')"
priority_field_id="$(ensure_field_single_select "Priority" '[{"name":"P0","color":"RED"},{"name":"P1","color":"YELLOW"},{"name":"P2","color":"GRAY"}]')"
reqid_field_id="$(ensure_field_text "Requirement ID")"

fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
option_id() {
  local field_name="$1"
  local option_name="$2"
  echo "$fields_resp" | jq -r --arg f "$field_name" --arg o "$option_name" '.data.node.fields.nodes[]? | select(.name==$f) | .options[]? | select(.name==$o) | .id' | head -n1
}

status_opt_upcoming="$(option_id "Status" "Upcoming")"
status_opt_in_progress="$(option_id "Status" "In Progress")"
status_opt_done="$(option_id "Status" "Done")"
status_opt_decision="$(option_id "Status" "Decision Needed")"

priority_opt_p0="$(option_id "Priority" "P0")"
priority_opt_p1="$(option_id "Priority" "P1")"
priority_opt_p2="$(option_id "Priority" "P2")"

declare -A module_opt
for i in $(seq -w 1 13); do
  key="MOD-$i"
  module_opt[$key]="$(option_id "Module" "$key")"
done

items_query='query($id:ID!){ node(id:$id){ ... on ProjectV2 { items(first:200){nodes{id content{... on DraftIssue {title}}}}}}}'
items_resp="$(api_graphql "$items_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
existing_titles="$(echo "$items_resp" | jq -r '.data.node.items.nodes[]?.content.title // empty')"

# JSON list of requirements from YAML
req_json="$(ruby -ryaml -rjson -e 'd=YAML.load_file(ARGV[0]); puts JSON.generate(d["requirements"])' "$REQ_FILE")"

# status map from audit markdown table
status_json="$(awk -F'|' '/\| REQ-[0-9][0-9][0-9] \|/{gsub(/^[ \t]+|[ \t]+$/,"",$2); gsub(/^[ \t]+|[ \t]+$/,"",$4); print $2"|"$4}' "$AUDIT_FILE" | jq -Rn '[inputs|split("|")|{(.[0]):.[1]}]|add')"

add_draft_mut='mutation($project:ID!,$title:String!,$body:String!){ addProjectV2DraftIssue(input:{projectId:$project,title:$title,body:$body}){ projectItem{id} } }'
set_select_mut='mutation($project:ID!,$item:ID!,$field:ID!,$opt:String!){ updateProjectV2ItemFieldValue(input:{projectId:$project,itemId:$item,fieldId:$field,value:{singleSelectOptionId:$opt}}){ projectV2Item{id} } }'
set_text_mut='mutation($project:ID!,$item:ID!,$field:ID!,$text:String!){ updateProjectV2ItemFieldValue(input:{projectId:$project,itemId:$item,fieldId:$field,value:{text:$text}}){ projectV2Item{id} } }'

count=0
while IFS= read -r req; do
  id="$(echo "$req" | jq -r '.id')"
  module="$(echo "$req" | jq -r '.module_id')"
  prio="$(echo "$req" | jq -r '.priority')"
  title="$(echo "$req" | jq -r '.title')"
  full_title="$id: $title"

  if grep -Fxq "$full_title" <<< "$existing_titles"; then
    continue
  fi

  raw_status="$(echo "$status_json" | jq -r --arg id "$id" '.[$id] // "MISSING"')"
  case "$raw_status" in
    DONE) board_status="Done"; status_opt="$status_opt_done" ;;
    PARTIAL) board_status="In Progress"; status_opt="$status_opt_in_progress" ;;
    "DECISION NEEDED") board_status="Decision Needed"; status_opt="$status_opt_decision" ;;
    *) board_status="Upcoming"; status_opt="$status_opt_upcoming" ;;
  esac

  case "$prio" in
    P0) prio_opt="$priority_opt_p0" ;;
    P1) prio_opt="$priority_opt_p1" ;;
    *) prio_opt="$priority_opt_p2" ;;
  esac

  mod_opt="${module_opt[$module]:-}"
  body="Auto-synced from requirements baseline.\n\n- Requirement: $id\n- Module: $module\n- Priority: $prio\n- Audit Status: $raw_status"

  add_resp="$(api_graphql "$add_draft_mut" "$(jq -cn --arg project "$project_id" --arg title "$full_title" --arg body "$body" '{project:$project,title:$title,body:$body}')")"
  item_id="$(echo "$add_resp" | jq -r '.data.addProjectV2DraftIssue.projectItem.id // empty')"
  if [[ -z "$item_id" ]]; then
    echo "WARN: could not add item for $id" >&2
    echo "$add_resp" >&2
    continue
  fi

  api_graphql "$set_select_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$status_field_id" --arg opt "$status_opt" '{project:$project,item:$item,field:$field,opt:$opt}')" >/dev/null
  [[ -n "$mod_opt" ]] && api_graphql "$set_select_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$module_field_id" --arg opt "$mod_opt" '{project:$project,item:$item,field:$field,opt:$opt}')" >/dev/null
  [[ -n "$prio_opt" ]] && api_graphql "$set_select_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$priority_field_id" --arg opt "$prio_opt" '{project:$project,item:$item,field:$field,opt:$opt}')" >/dev/null
  api_graphql "$set_text_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$reqid_field_id" --arg text "$id" '{project:$project,item:$item,field:$field,text:$text}')" >/dev/null

  count=$((count + 1))
  echo "Added: $full_title ($board_status)"
done < <(echo "$req_json" | jq -c '.[]')

echo "Board sync complete. New draft items added: $count"
