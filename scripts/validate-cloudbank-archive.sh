#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ARCHIVE="${1:-$REPO_ROOT/l2-provision/files/oracle-saga-cloudbank.zip}"

for command_name in grep mktemp unzip; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "ERROR: $command_name is required." >&2
    exit 1
  }
done

test -f "$ARCHIVE" || {
  echo "ERROR: CloudBank archive is missing: $ARCHIVE" >&2
  exit 1
}

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
unzip -q "$ARCHIVE" -d "$TEMP_DIR"

APP_DIR="$TEMP_DIR/oracle-saga-cloudbank"
COMPOSE_FILE="$APP_DIR/osagaAdbsSetup.yaml"
RUNTIME_FILE="$APP_DIR/osagaJavaRuntime"
SETUP_SQL="$APP_DIR/adbsSetup/adbsSetupScript.sql"
BANKA_PROPERTIES="$APP_DIR/CloudBank/banka/src/main/resources/application.properties"
BANKB_PROPERTIES="$APP_DIR/CloudBank/bankb/src/main/resources/application.properties"
ORCHESTRATOR_PROPERTIES="$APP_DIR/CloudBank/orchestrator/src/main/resources/application.properties"
FLASK_APP="$APP_DIR/CloudBank/Website/app.py"

require_file() {
  test -f "$1" || {
    echo "ERROR: Missing archive file: $1" >&2
    exit 1
  }
}

require_exact_line() {
  local description="$1" expected="$2" file="$3"
  grep -qxF -- "$expected" "$file" || {
    echo "ERROR: $description" >&2
    exit 1
  }
  echo "OK: $description"
}

require_contains() {
  local description="$1" pattern="$2" file="$3"
  grep -qE -- "$pattern" "$file" || {
    echo "ERROR: $description" >&2
    exit 1
  }
  echo "OK: $description"
}

for required_file in \
  "$COMPOSE_FILE" \
  "$RUNTIME_FILE" \
  "$SETUP_SQL" \
  "$BANKA_PROPERTIES" \
  "$BANKB_PROPERTIES" \
  "$ORCHESTRATOR_PROPERTIES" \
  "$FLASK_APP"; do
  require_file "$required_file"
done

require_exact_line 'BankA publishers are disabled' 'osaga.banka.numPublishers=0' "$BANKA_PROPERTIES"
require_exact_line 'BankA listener count is 1' 'osaga.banka.numListeners=1' "$BANKA_PROPERTIES"
require_exact_line 'BankA maximum pool is 5' 'osaga.banka.maxpool=5' "$BANKA_PROPERTIES"
require_exact_line 'BankA initial pool is 2' 'osaga.banka.initialPoolSize=2' "$BANKA_PROPERTIES"

require_exact_line 'BankB publishers are disabled' 'osaga.bankb.numPublishers=0' "$BANKB_PROPERTIES"
require_exact_line 'BankB listener count is 1' 'osaga.bankb.numListeners=1' "$BANKB_PROPERTIES"
require_exact_line 'BankB maximum pool is 5' 'osaga.bankb.maxpool=5' "$BANKB_PROPERTIES"
require_exact_line 'BankB initial pool is 2' 'osaga.bankb.initialPoolSize=2' "$BANKB_PROPERTIES"

require_exact_line 'CloudBank publisher count is 1' 'osaga.cloudbank.numPublishers=1' "$ORCHESTRATOR_PROPERTIES"
require_exact_line 'CloudBank listener count is 1' 'osaga.cloudbank.numListeners=1' "$ORCHESTRATOR_PROPERTIES"
require_exact_line 'CloudBank maximum pool is 5' 'osaga.cloudbank.maxpool=5' "$ORCHESTRATOR_PROPERTIES"
require_exact_line 'CloudBank initial pool is 2' 'osaga.cloudbank.initialPoolSize=2' "$ORCHESTRATOR_PROPERTIES"

require_exact_line 'Maven build heap is limited to 256 MB' 'ENV MAVEN_OPTS="-Xmx256m"' "$RUNTIME_FILE"
require_contains 'SQL setup exits on the first SQL error' '^WHENEVER SQLERROR EXIT SQL\.SQLCODE ROLLBACK$' "$SETUP_SQL"
require_contains 'SQL setup prints a success marker' '^PROMPT Business schema setup: OK$' "$SETUP_SQL"

seed_count="$(grep -c 'VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL' "$SETUP_SQL")"
test "$seed_count" -eq 4 || {
  echo "ERROR: Expected four sequence-backed CloudBank customer seeds; found $seed_count." >&2
  exit 1
}
if grep -q "VALUES ('ORACLE00[1-4]','cb" "$SETUP_SQL"; then
  echo 'ERROR: Found a preformatted customer ID that bypasses the sequence contract.' >&2
  exit 1
fi
echo 'OK: CloudBank customer seeds use the sequence exactly four times'

optional_profile_count="$(grep -c '^      - optional$' "$COMPOSE_FILE")"
test "$optional_profile_count" -eq 2 || {
  echo "ERROR: Expected exactly two optional services; found $optional_profile_count." >&2
  exit 1
}
require_contains 'Swagger UI is pinned' 'image: docker\.io/swaggerapi/swagger-ui:v5\.20\.7' "$COMPOSE_FILE"

request_count="$(grep -cE 'requests\.(get|post)\(' "$FLASK_APP")"
timeout_count="$(grep -c 'timeout=HTTP_TIMEOUT' "$FLASK_APP")"
test "$request_count" -eq "$timeout_count" || {
  echo "ERROR: Flask has $request_count outbound requests but only $timeout_count bounded timeouts." >&2
  exit 1
}
echo 'OK: Every Flask outbound HTTP request has a timeout'

grep -qF 'lab5_test_sgerrits' "$REPO_ROOT/l2-provision/files/provision.sh" || {
  echo 'ERROR: Provisioning does not default to lab5_test_sgerrits.' >&2
  exit 1
}
if grep -R -q 'lab5_test_lacruz' \
  "$REPO_ROOT/l2-provision/provision.md" \
  "$REPO_ROOT/l2-provision/files/provision.sh"; then
  echo 'ERROR: Found a stale lab5_test_lacruz reference.' >&2
  exit 1
fi
grep -qF 'awk '\''$2 == "REJECT"' "$REPO_ROOT/l2-provision/files/provision.sh" || {
  echo 'ERROR: Provisioning firewall logic does not locate REJECT in column 2.' >&2
  exit 1
}
grep -qF 'local deadline=$((SECONDS + 300))' "$REPO_ROOT/l5-demo/demo.md" || {
  echo 'ERROR: Lab 5 does not contain the five-minute readiness helper.' >&2
  exit 1
}

echo 'CloudBank archive validation: OK'
