#!/usr/bin/env bash
# Creates/updates a custom Java quality profile and quality gate through the SonarQube Web API.
# Usage: SONAR_URL=https://sonar.example.com SONAR_TOKEN=xxx ./update-quality-profile.sh
# Keep this script in git: rule changes then go through code review like any other change.
set -euo pipefail
: "${SONAR_URL:?set SONAR_URL}"
: "${SONAR_TOKEN:?set SONAR_TOKEN}"
PROFILE="SkyRoute Java"
GATE="SkyRoute Gate"
api() { curl -fsS -u "${SONAR_TOKEN}:" "$@"; }

# 1. Copy the built-in "Sonar way" profile so upgrades to it don't silently change our rules.
api -X POST "${SONAR_URL}/api/qualityprofiles/copy" \
  --data-urlencode "fromKey=$(api "${SONAR_URL}/api/qualityprofiles/search?language=java&qualityProfile=Sonar%20way" | jq -r '.profiles[0].key')" \
  --data-urlencode "toName=${PROFILE}" || echo "profile exists, updating"
PROFILE_KEY=$(api "${SONAR_URL}/api/qualityprofiles/search?language=java&qualityProfile=$(jq -rn --arg p "$PROFILE" '$p|@uri')" | jq -r '.profiles[0].key')

activate() { # rule key, severity, optional params "k1=v1;k2=v2"
  api -X POST "${SONAR_URL}/api/qualityprofiles/activate_rule" \
    --data-urlencode "key=${PROFILE_KEY}" --data-urlencode "rule=$1" \
    --data-urlencode "severity=$2" ${3:+--data-urlencode "params=$3"} >/dev/null
  echo "activated $1 ($2)"
}
deactivate() {
  api -X POST "${SONAR_URL}/api/qualityprofiles/deactivate_rule" \
    --data-urlencode "key=${PROFILE_KEY}" --data-urlencode "rule=$1" >/dev/null
  echo "deactivated $1"
}

# 2. Tune rules for our codebase.
activate java:S3776 CRITICAL "Threshold=12"      # Cognitive complexity per method (default 15)
activate java:S107  MAJOR    "max=6"             # Too many method parameters
activate java:S2139 MAJOR                        # Log or rethrow, not both
activate java:S1166 MAJOR                        # Exception handlers should preserve the original exception
activate java:S2142 CRITICAL                     # InterruptedException must not be ignored
activate java:S1192 MINOR    "threshold=4"       # Duplicated string literals
activate java:S6212 MINOR                        # Use 'var' where it improves readability (Java 10+)
activate java:S6204 MINOR                        # Prefer Stream.toList() (Java 16+)
deactivate java:S1135                            # "TODO" comments: tracked in Jira instead

# 3. Make it the default Java profile.
api -X POST "${SONAR_URL}/api/qualityprofiles/set_default" \
  --data-urlencode "language=java" --data-urlencode "qualityProfile=${PROFILE}"

# 4. Quality gate on NEW code (clean-as-you-code).
api -X POST "${SONAR_URL}/api/qualitygates/create" --data-urlencode "name=${GATE}" >/dev/null || true
for cond in "new_coverage:LT:80" "new_duplicated_lines_density:GT:3" \
            "new_reliability_rating:GT:1" "new_security_rating:GT:1" "new_maintainability_rating:GT:1"; do
  IFS=: read -r metric op error <<<"$cond"
  api -X POST "${SONAR_URL}/api/qualitygates/create_condition" \
    --data-urlencode "gateName=${GATE}" --data-urlencode "metric=${metric}" \
    --data-urlencode "op=${op}" --data-urlencode "error=${error}" >/dev/null || true
  echo "gate condition ${metric} ${op} ${error}"
done
api -X POST "${SONAR_URL}/api/qualitygates/select" \
  --data-urlencode "gateName=${GATE}" --data-urlencode "projectKey=skyroute-api"
echo "Done."
