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

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: missing dependency: $1" >&2; exit 1; }; }
need jq
need curl
need ruby
need awk

if [[ -z "${GITHUB_TOKEN:-}" || -z "${GH_OWNER:-}" ]]; then
  echo "ERROR: GITHUB_TOKEN and GH_OWNER are required." >&2
  exit 1
fi

if [[ ! -f "$REQ_FILE" || ! -f "$AUDIT_FILE" ]]; then
  echo "ERROR: expected files missing: $REQ_FILE and/or $AUDIT_FILE" >&2
  exit 1
fi

# ---- Normalize + validate GH_OWNER ----
normalize_owner() {
  local s="$1"
  s="$(printf '%s' "$s" | tr -d '\r\n' | xargs)"
  s="${s#@}"
  s="$(printf '%s' "$s" | sed -E 's#^https?://github.com/##; s#^git@github.com:##; s#\.git$##')"
  s="${s%%/*}"
  printf '%s' "$s"
}
GH_OWNER="$(normalize_owner "$GH_OWNER")"

if [[ -z "$GH_OWNER" || ! "$GH_OWNER" =~ ^[A-Za-z0-9]([A-Za-z0-9-]{0,37}[A-Za-z0-9])?$ ]]; then
  echo "ERROR: GH_OWNER must be a GitHub org/user login (e.g. 'YEM1449'). Got: '$GH_OWNER'" >&2
  exit 1
fi

echo "Using GH_OWNER=$GH_OWNER"
echo "Board title: $(printf '%q' "$BOARD_TITLE")"

# ---- REST owner lookup -> GraphQL node_id ----
owner_rest="$(curl -sS \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/users/$GH_OWNER")"

owner_id="$(echo "$owner_rest" | jq -r '.node_id // empty')"
owner_login="$(echo "$owner_rest" | jq -r '.login // empty')"
if [[ -z "$owner_id" ]]; then
  echo "ERROR: could not resolve owner '$GH_OWNER' via REST /users/<login>." >&2
  echo "$owner_rest" >&2
  exit 1
fi
echo "Resolved owner login: $owner_login"

# ---- GraphQL helpers ----
api_graphql() {
  local query="$1"
  local variables_json="${2:-}"
  [[ -z "$variables_json" ]] && variables_json='{}'

  if [[ -z "$query" ]]; then
    echo "ERROR: api_graphql called with empty query" >&2
    return 1
  fi

  # Build payload: use --argjson to pass variables directly as JSON
  local payload safe_vars
  safe_vars="$(echo "$variables_json" | jq -c '.' 2>/dev/null)" || safe_vars='{}'
  if [[ -z "$safe_vars" ]]; then safe_vars='{}'; fi
  payload="$(jq -cn --arg q "$query" --argjson v "$safe_vars" \
    '{query:$q, variables:$v}')"

  curl -sS -X POST "$API_URL" \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/vnd.github+json" \
    -d "$payload"
}

require_graphql_success() {
  local response="$1"
  local context="$2"

  if ! echo "$response" | jq -e . >/dev/null 2>&1; then
    echo "ERROR: Non-JSON response during: $context" >&2
    echo "$response" >&2
    exit 1
  fi

  local has_errors
  has_errors="$(echo "$response" | jq 'has("errors") and (.errors | length > 0)')"
  if [[ "$has_errors" == "true" ]]; then
    echo "ERROR: GraphQL failure during: $context" >&2
    echo "$response" | jq -r '.errors[] | "- " + (.message // "unknown error")' >&2
    echo "Full response (debug):" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

# ------------------------------------------------------------
# FIX: Project listing uses owner_id (node id) instead of login
# ------------------------------------------------------------
projects_query='
query($id:ID!){
  node(id:$id){
    ... on Organization { projectsV2(first:100){ nodes{ id title number } } }
    ... on User         { projectsV2(first:100){ nodes{ id title number } } }
  }
}'
projects_vars="$(jq -cn --arg id "$owner_id" '{id:$id}')"
projects_resp="$(api_graphql "$projects_query" "$projects_vars")"
require_graphql_success "$projects_resp" "project listing"

project_id="$(echo "$projects_resp" | jq -r --arg t "$BOARD_TITLE" \
  '.data.node.projectsV2.nodes
   | map(select(.title==$t))
   | .[0].id // empty')"

if [[ -z "$project_id" ]]; then
  create_project_mut='
mutation($owner:ID!,$title:String!){
  createProjectV2(input:{ownerId:$owner,title:$title}){ projectV2{id title number} }
}'
  create_vars="$(jq -cn --arg owner "$owner_id" --arg title "$BOARD_TITLE" '{owner:$owner,title:$title}')"
  create_resp="$(api_graphql "$create_project_mut" "$create_vars")"
  require_graphql_success "$create_resp" "project creation"

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

# ---- (everything below is unchanged from your script) ----
# FIX: include `color` in options so we can merge existing + new options
project_fields_query='query($id:ID!){ node(id:$id){ ... on ProjectV2 { id fields(first:100){nodes{... on ProjectV2FieldCommon{id name dataType} ... on ProjectV2SingleSelectField{id name options{id name color}}}}}}}'

ensure_field_single_select() {
  local name="$1"
  local options_json="$2"

  local fields_resp existing
  fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
  require_graphql_success "$fields_resp" "field lookup ($name)"
  existing="$(echo "$fields_resp" | jq -r --arg n "$name" '.data.node.fields.nodes[]? | select(.name==$n) | .id' | head -n1)"

  if [[ -n "$existing" ]]; then
    # FIX: Field already exists (e.g., built-in Status). Check if all desired
    # options are present. If not, try to add missing ones via updateProjectV2Field.
    local existing_opts merged_opts existing_count merged_count
    existing_opts="$(echo "$fields_resp" | jq --arg n "$name" \
      '[.data.node.fields.nodes[]? | select(.name==$n) | .options[]? | {name, color: (.color // "GRAY")}]')"

    # Merge: keep existing options (preserve their colors), append new ones
    merged_opts="$(jq -n --argjson e "$existing_opts" --argjson d "$options_json" \
      '[$e[], ($d[] | select(.name as $n | [$e[].name] | index($n) | not))]')"

    existing_count="$(echo "$existing_opts" | jq 'length')"
    merged_count="$(echo "$merged_opts" | jq 'length')"

    if [[ "$merged_count" -gt "$existing_count" ]]; then
      echo "  Field '$name' exists but missing $((merged_count - existing_count)) option(s). Updating..." >&2
      # updateProjectV2Field input uses fieldId only (no projectId); options need description
      local update_mut='mutation($field:ID!,$opts:[ProjectV2SingleSelectFieldOptionInput!]!){ updateProjectV2Field(input:{fieldId:$field,singleSelectOptions:$opts}){ projectV2Field{... on ProjectV2SingleSelectField{id options{id name}}} } }'
      local merged_with_desc update_vars update_resp
      merged_with_desc="$(echo "$merged_opts" | jq '[.[] | . + {description: (.description // "")}]')"
      update_vars="$(jq -cn --arg field "$existing" --argjson opts "$merged_with_desc" \
        '{field:$field,opts:$opts}')"
      update_resp="$(api_graphql "$update_mut" "$update_vars")"
      if echo "$update_resp" | jq -e 'has("errors") and (.errors | length > 0)' >/dev/null 2>&1; then
        echo "  WARN: Could not update '$name' options (may be read-only)." >&2
        echo "  $(echo "$update_resp" | jq -r '.errors[].message')" >&2
        echo "  Tip: add missing options manually in the GitHub Projects UI." >&2
      else
        echo "  Updated '$name' options." >&2
      fi
    fi

    echo "$existing"
    return
  fi

  local mut='mutation($project:ID!,$name:String!,$opts:[ProjectV2SingleSelectFieldOptionInput!]!){ createProjectV2Field(input:{projectId:$project,name:$name,dataType:SINGLE_SELECT,singleSelectOptions:$opts}){ projectV2Field{... on ProjectV2SingleSelectField{id name}} } }'
  # Ensure each option has a description field (required by the API)
  local opts_with_desc vars
  opts_with_desc="$(echo "$options_json" | jq '[.[] | . + {description: (.description // "")}]')"
  vars="$(jq -cn --arg project "$project_id" --arg name "$name" --argjson opts "$opts_with_desc" '{project:$project,name:$name,opts:$opts}')"
  local resp
  resp="$(api_graphql "$mut" "$vars")"
  require_graphql_success "$resp" "field creation ($name)"
  echo "$resp" | jq -r '.data.createProjectV2Field.projectV2Field.id'
}

ensure_field_text() {
  local name="$1"

  local fields_resp existing
  fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
  require_graphql_success "$fields_resp" "field lookup ($name)"
  existing="$(echo "$fields_resp" | jq -r --arg n "$name" '.data.node.fields.nodes[]? | select(.name==$n) | .id' | head -n1)"

  if [[ -n "$existing" ]]; then
    echo "$existing"
    return
  fi

  local mut='mutation($project:ID!,$name:String!){ createProjectV2Field(input:{projectId:$project,name:$name,dataType:TEXT}){ projectV2Field{... on ProjectV2Field{id} ... on ProjectV2FieldCommon{id}} } }'
  local vars
  vars="$(jq -cn --arg project "$project_id" --arg name "$name" '{project:$project,name:$name}')"
  local resp
  resp="$(api_graphql "$mut" "$vars")"
  require_graphql_success "$resp" "field creation ($name)"
  echo "$resp" | jq -r '.data.createProjectV2Field.projectV2Field.id'
}

status_field_id="$(ensure_field_single_select "Status" '[{"name":"Upcoming","color":"GRAY"},{"name":"In Progress","color":"BLUE"},{"name":"Done","color":"GREEN"},{"name":"Decision Needed","color":"RED"}]')"
module_field_id="$(ensure_field_single_select "Module" '[{"name":"MOD-01","color":"BLUE"},{"name":"MOD-02","color":"BLUE"},{"name":"MOD-03","color":"BLUE"},{"name":"MOD-04","color":"BLUE"},{"name":"MOD-05","color":"BLUE"},{"name":"MOD-06","color":"BLUE"},{"name":"MOD-07","color":"BLUE"},{"name":"MOD-08","color":"BLUE"},{"name":"MOD-09","color":"BLUE"},{"name":"MOD-10","color":"BLUE"},{"name":"MOD-11","color":"BLUE"},{"name":"MOD-12","color":"BLUE"},{"name":"MOD-13","color":"BLUE"}]')"
priority_field_id="$(ensure_field_single_select "Priority" '[{"name":"P0","color":"RED"},{"name":"P1","color":"YELLOW"},{"name":"P2","color":"GRAY"}]')"
reqid_field_id="$(ensure_field_text "Requirement ID")"

fields_resp="$(api_graphql "$project_fields_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
require_graphql_success "$fields_resp" "field options fetch"
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

items_query='query($id:ID!){ node(id:$id){ ... on ProjectV2 { items(first:100){nodes{id content{... on DraftIssue {title}}}}}}}'
items_resp="$(api_graphql "$items_query" "$(jq -cn --arg id "$project_id" '{id:$id}')")"
require_graphql_success "$items_resp" "item listing"
existing_titles="$(echo "$items_resp" | jq -r '.data.node.items.nodes[]?.content.title // empty')"

req_json="$(ruby -ryaml -rjson -e 'd=YAML.load_file(ARGV[0]); puts JSON.generate(d["requirements"])' "$REQ_FILE")"
status_json="$(awk -F'|' '/\| REQ-[0-9][0-9][0-9] \|/{gsub(/^[ \t]+|[ \t]+$/,"",$2); gsub(/^[ \t]+|[ \t]+$/,"",$4); print $2"|"$4}' "$AUDIT_FILE" | jq -Rn '[inputs|split("|")|{(.[0]):.[1]}]|add')"

add_draft_mut='mutation($project:ID!,$title:String!,$body:String!){ addProjectV2DraftIssue(input:{projectId:$project,title:$title,body:$body}){ projectItem{id} } }'
set_select_mut='mutation($project:ID!,$item:ID!,$field:ID!,$opt:String!){ updateProjectV2ItemFieldValue(input:{projectId:$project,itemId:$item,fieldId:$field,value:{singleSelectOptionId:$opt}}){ projectV2Item{id} } }'
set_text_mut='mutation($project:ID!,$item:ID!,$field:ID!,$text:String!){ updateProjectV2ItemFieldValue(input:{projectId:$project,itemId:$item,fieldId:$field,value:{text:$text}}){ projectV2Item{id} } }'

# FIX: guard against empty option IDs and check mutation responses.
# Uses outer-scope: $set_select_mut, $project_id, $item_id (set per iteration)
set_field_select() {
  local fld="$1" opt="$2" label="$3" req_id="$4"
  if [[ -z "$opt" ]]; then
    echo "  WARN: skipping $label for $req_id (option not found)" >&2
    return
  fi
  local r
  r="$(api_graphql "$set_select_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$fld" --arg opt "$opt" '{project:$project,item:$item,field:$field,opt:$opt}')")"
  if echo "$r" | jq -e 'has("errors") and (.errors | length > 0)' >/dev/null 2>&1; then
    echo "  WARN: failed to set $label for $req_id: $(echo "$r" | jq -r '.errors[0].message')" >&2
  fi
}

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
    DONE) status_opt="$status_opt_done" ;;
    PARTIAL) status_opt="$status_opt_in_progress" ;;
    "DECISION NEEDED") status_opt="$status_opt_decision" ;;
    *) status_opt="$status_opt_upcoming" ;;
  esac

  case "$prio" in
    P0) prio_opt="$priority_opt_p0" ;;
    P1) prio_opt="$priority_opt_p1" ;;
    *) prio_opt="$priority_opt_p2" ;;
  esac

  mod_opt="${module_opt[$module]:-}"
  if [[ -z "$mod_opt" || "$mod_opt" == "null" ]]; then
    echo "ERROR: missing Module option id for '$module'" >&2
    exit 1
  fi

  # FIX: use printf so \n produces actual newlines (bash "..." keeps them literal)
  body="$(printf 'Auto-synced from requirements baseline.\n\n- Requirement: %s\n- Module: %s\n- Priority: %s\n- Audit Status: %s' "$id" "$module" "$prio" "$raw_status")"

  add_resp="$(api_graphql "$add_draft_mut" "$(jq -cn --arg project "$project_id" --arg title "$full_title" --arg body "$body" '{project:$project,title:$title,body:$body}')")"
  require_graphql_success "$add_resp" "add draft issue ($id)"
  item_id="$(echo "$add_resp" | jq -r '.data.addProjectV2DraftIssue.projectItem.id // empty')"
  [[ -n "$item_id" ]] || { echo "WARN: could not add item for $id" >&2; continue; }

  set_field_select "$status_field_id" "$status_opt" "Status" "$id"
  set_field_select "$module_field_id" "$mod_opt" "Module" "$id"
  set_field_select "$priority_field_id" "$prio_opt" "Priority" "$id"

  # Set text field (Requirement ID)
  text_resp="$(api_graphql "$set_text_mut" "$(jq -cn --arg project "$project_id" --arg item "$item_id" --arg field "$reqid_field_id" --arg text "$id" '{project:$project,item:$item,field:$field,text:$text}')")"
  if echo "$text_resp" | jq -e 'has("errors") and (.errors | length > 0)' >/dev/null 2>&1; then
    echo "  WARN: failed to set Requirement ID for $id: $(echo "$text_resp" | jq -r '.errors[0].message')" >&2
  fi

  count=$((count + 1))
  echo "Added: $full_title"
done < <(echo "$req_json" | jq -c '.[]')

echo "Board sync complete. New draft items added: $count"
