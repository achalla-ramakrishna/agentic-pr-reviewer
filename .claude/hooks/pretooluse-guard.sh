#!/usr/bin/env bash
# PreToolUse guardrail for this repo's Claude Code sessions.
#
# Catches what static permission globs alone can't: obfuscated secret
# access, destructive git history rewrites, and commands that try to ship a
# secret env var out over the network. Wired via .claude/settings.json.
#
# Contract: reads the tool-call JSON on stdin. Exit 0 = allow. Exit 2 =
# block, with the reason on stderr fed back to the agent.
set -euo pipefail

input="$(cat)"

extract() {
  local key="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$input" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null || true
  else
    printf '%s' "$input" \
      | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" \
      | head -1 \
      | sed -E 's/.*: *"(.*)"/\1/'
  fi
}

extract_nested() {
  local key="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$input" | jq -r --arg k "$key" '.tool_input[$k] // empty' 2>/dev/null || true
  else
    printf '%s' "$input" \
      | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" \
      | head -1 \
      | sed -E 's/.*: *"(.*)"/\1/'
  fi
}

tool_name="$(extract tool_name)"
command_str="$(extract_nested command)"
file_path="$(extract_nested file_path)"
if [[ -z "$file_path" ]]; then
  file_path="$(extract_nested path)"
fi

block() {
  echo "BLOCKED by PreToolUse guard: $1" >&2
  exit 2
}

secret_path_re='(^|/)\.env($|[./])|\.pem$|\.p12$|\.key$|id_rsa|(^|/)\.aws/credentials|(^|/)\.ssh/'

if [[ -n "$file_path" && "$file_path" =~ $secret_path_re ]]; then
  block "path '$file_path' looks like a secret/credential file."
fi

if [[ "$tool_name" == "Bash" && -n "$command_str" ]]; then
  if echo "$command_str" | grep -Eq '\.env([^a-zA-Z0-9_./]|$)|id_rsa|\.pem\b|\.ssh/|\.aws/credentials'; then
    block "command references a secret/credential path: $command_str"
  fi
  if echo "$command_str" | grep -Eq '\bgit[[:space:]]+push[[:space:]]+.*(--force|-f\b)|\bgit[[:space:]]+reset[[:space:]]+--hard|\brm[[:space:]]+-rf[[:space:]]+(/|~|\.\.)'; then
    block "destructive/irreversible command needs explicit human approval: $command_str"
  fi
  if echo "$command_str" | grep -Eiq '(curl|wget).*(OPENAI_API_KEY|GITHUB_TOKEN|GH_TOKEN)'; then
    block "command appears to send a secret env var over the network: $command_str"
  fi
fi

exit 0
