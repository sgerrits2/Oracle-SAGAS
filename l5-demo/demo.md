# Lab 5: Oracle Sagas in Action — The CloudBank Application

## Introduction

This lab runs the CloudBank application with Podman and lets you observe Oracle Sagas. The supplied application archive now includes the required Java build files and a corrected Compose configuration.

You will build the Java/Flask image, start the existing ADB-backed services, run a transfer through the UI or API, and verify the specific saga with SQLcl.

> Keep passwords, wallet files, and .env values private. ADB initialization is a one-time operation; do not run it again when the schemas already exist.

*Estimated time: 30–45 minutes*

---

## Prerequisites

- Project directory: $HOME/cloudbank-setup/oracle-saga-cloudbank
- ADB wallet: adbsSetup/adb_wallet
- A configured .env file. Do not overwrite or publish it.
- Podman 4.9+ and podman-compose.
- OCI ingress for TCP 22, 3000, 8080, and 9411 when accessing public endpoints.

Docker Engine is not required.

### Required Oracle Saga participant names

> **Important:** The application may use `BankA` and `BankB` in container names, source paths, environment variables, and database table names. Those are implementation labels, **not** Oracle Saga participant names. Saga registration, `sendRequest()` routing, callback matching, and trace interpretation must use the registered names below.

| Incorrect legacy value | Registered Oracle Saga participant |
| --- | --- |
| `BankA` | `BankChicago` |
| `BankB` | `BankMex` |

This lab migrates the incorrect `BankA`/`BankB` values to the registered `BankChicago`/`BankMex` names wherever a Saga participant identity is required. The orchestrator constants and participant annotations in the bundled application already use these registered names.

---

## Task 1: Prepare and Validate Cloud Shell

### Step 1: Verify files and tools

Run this in **Cloud Shell**. It lists only .env variable names.

<pre id="prepareCloudShell" class="interactive-command"><code>set -e

PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"
test -d "$PROJECT_DIR" || { echo "ERROR: project directory is missing"; exit 1; }
cd "$PROJECT_DIR"

python3 -m pip install --user podman-compose
export PATH="$HOME/.local/bin:$PATH"

podman --version
podman-compose --version
find CloudBank -mindepth 2 -maxdepth 2 -name pom.xml -type f -print
find CloudBank -maxdepth 3 -type f \( -name app.py -o -name requirements.txt \) -print
test -f osagaJavaBuilder || { echo "ERROR: osagaJavaBuilder is missing"; exit 1; }
test -f osagaJavaRuntime || { echo "ERROR: osagaJavaRuntime is missing"; exit 1; }
test -d adbsSetup/adb_wallet || { echo "ERROR: ADB wallet is missing"; exit 1; }
test -f .env || { echo "ERROR: .env is missing"; exit 1; }
sed -nE 's/^([A-Za-z_][A-Za-z0-9_]*)=.*/\1=&lt;configured&gt;/p' .env | sort
</code></pre>

<div class="button-center">

<button onclick="copyBlock('prepareCloudShell', this)" class="copy-btn-pastel">📋 Copy Cloud Shell Verification</button>

</div>

### Step 2: Validate and build the supplied files

The archive provides osagaJavaBuilder and osagaJavaRuntime. The runtime compiles the Maven modules in its own build stage, avoiding a remote lookup for a local builder image.

<pre id="validateFiles" class="interactive-command"><code>set -e
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"

grep -q 'maven:3.9.9-eclipse-temurin-17' osagaJavaBuilder
grep -q 'eclipse-temurin:17-jre-jammy' osagaJavaRuntime
grep -qxF 'Werkzeug&gt;=2.3.7,&lt;3.0' CloudBank/Website/requirements.txt
grep -q 'image: docker.io/swaggerapi/swagger-ui:v5.20.7' osagaAdbsSetup.yaml
grep -q '\$\${TNS_ALIAS}' osagaAdbsSetup.yaml
! grep -q '\$\${TNS_ALIAS_CONTAINER}' osagaAdbsSetup.yaml
grep -q '^  osagas-cleanup-adbs:' osagaAdbsSetup.yaml
grep -q '"3000:8084"' osagaAdbsSetup.yaml
grep -q '"8080:8080"' osagaAdbsSetup.yaml

podman build --pull=always -f osagaJavaBuilder -t osaga-builder:1.0 --target builder .
podman build -f osagaJavaRuntime -t osaga-runtime:1.0 --target runtime .
podman run --rm --entrypoint /bin/sh osaga-runtime:1.0 -c \
  'test -f /opt/app/bankA.jar &amp;&amp; test -f /opt/app/bankB.jar &amp;&amp; test -f /opt/app/orchestrator.jar &amp;&amp; test -f /opt/app/flask_ui/app.py'
</code></pre>

<div class="button-center">

<button onclick="copyBlock('validateFiles', this)" class="copy-btn-pastel">📋 Copy Build Validation</button>

</div>

### Step 3: Confirm ADB is already initialized

The adbssagasetup profile is only for first-time schema creation. Do not run it when ADB already contains the CloudBank schemas and seed data.

<pre id="verifyAdbSetup" class="interactive-command"><code>set -e
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"

podman ps -a --format 'table {{.Names}}\t{{.Status}}'
echo 'If osagas-setup-adbs exists, inspect its final log lines:'
podman logs --tail 30 osagas-setup-adbs 2&gt;/dev/null || true
echo 'Do not run COMPOSE_PROFILES=adbssagasetup unless ADB has never been initialized.'
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyAdbSetup', this)" class="copy-btn-pastel">📋 Copy ADB Setup Check</button>

</div>

---

## Task 2: Start and Verify the Local Stack

Use the COMPOSE_PROFILES environment variable. It works with Cloud Shell and Compute versions of podman-compose; do not rely on --profile.

<pre id="startLocalStack" class="interactive-command"><code>set -e
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"

COMPOSE_PROFILES=adbs podman-compose -f osagaAdbsSetup.yaml up -d --build

for endpoint in \
  'http://127.0.0.1:8081/orchestrator/version' \
  'http://127.0.0.1:8082/banka/version' \
  'http://127.0.0.1:8083/bankb/version' \
  'http://127.0.0.1:3000/' \
  'http://127.0.0.1:8080/'; do
  printf 'Waiting for %s ' "$endpoint"
  for attempt in $(seq 1 30); do
    if curl -fsS "$endpoint" &gt;/dev/null; then echo 'OK'; break; fi
    if [ "$attempt" -eq 30 ]; then echo 'FAILED'; exit 1; fi
    sleep 2
  done
done

podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
curl -fsS http://127.0.0.1:9411/health || podman logs --tail 100 zipkin
</code></pre>

<div class="button-center">

<button onclick="copyBlock('startLocalStack', this)" class="copy-btn-pastel">📋 Copy Local Stack Start</button>

</div>

Expected ports are Flask 3000, Swagger 8080, and Zipkin 9411. Java services remain on 8081–8083 for direct API use. Swagger derives API URLs from the page host, so it does not target the browser's localhost.

---

## Task 3: Deploy to the Existing Compute Instance

Use the existing instance. ADB is already initialized, so deployment starts only the adbs services.

### Step 1: Check the instance

<div class="input-section">

<strong>Compute Instance Public IP:</strong>

<input type="text" id="computeInstanceIP" placeholder="Enter compute public IP (for example, 129.146.123.45)" class="input-field" oninput="updateLabValues()"><br/>

</div>

<pre id="checkCompute" class="interactive-command"><code>ssh -o ConnectTimeout=10 -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e
export PATH="$HOME/.local/bin:$PATH"
free -h
df -h /
podman --version
podman-compose --version
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('checkCompute', this)" class="copy-btn-pastel">📋 Copy Compute Check</button>

</div>

### Step 2: Package and deploy

The archive includes hidden files such as .env and does not run ADB setup.

<pre id="deployCloudBank" class="interactive-command"><code>set -e
PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"
ARCHIVE="$HOME/oracle-saga-cloudbank-deploy.tar.gz"
COMPUTE_IP="<span class="instance-ip-value">INSTANCE_IP</span>"
SSH_KEY="$HOME/.ssh/cloudbank_key"
PROJECT_PARENT="$(dirname "$PROJECT_DIR")"
PROJECT_NAME="$(basename "$PROJECT_DIR")"

tar -C "$PROJECT_PARENT" -czf "$ARCHIVE" "$PROJECT_NAME"
scp -i "$SSH_KEY" "$ARCHIVE" "ubuntu@$COMPUTE_IP:~/"

ssh -i "$SSH_KEY" "ubuntu@$COMPUTE_IP" 'bash -s' &lt;&lt;'REMOTE'
set -e
export PATH="$HOME/.local/bin:$PATH"
DEPLOY_DIR="$HOME/oracle-saga-cloudbank"
STAGING_DIR="$HOME/oracle-saga-cloudbank.staging"
rm -rf "$STAGING_DIR"
mkdir "$STAGING_DIR"
tar -xzf "$HOME/oracle-saga-cloudbank-deploy.tar.gz" -C "$STAGING_DIR"
test -f "$STAGING_DIR/oracle-saga-cloudbank/.env"
test -d "$STAGING_DIR/oracle-saga-cloudbank/adbsSetup/adb_wallet"
rm -rf "$DEPLOY_DIR"
mv "$STAGING_DIR/oracle-saga-cloudbank" "$DEPLOY_DIR"
rmdir "$STAGING_DIR"
cd "$DEPLOY_DIR"
chmod 600 .env
COMPOSE_PROFILES=adbs podman-compose -f osagaAdbsSetup.yaml up -d --build
sudo loginctl enable-linger ubuntu
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('deployCloudBank', this)" class="copy-btn-pastel">📋 Copy Deployment Script</button>

</div>

### Step 3: Verify service endpoints

<pre id="verifyEndpoints" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e
for port in 3000 8080 9411; do
  printf 'localhost:%s -&gt; ' "$port"
  curl -4 -fsS -o /dev/null -w '%{http_code}\n' "http://127.0.0.1:$port"
done
podman ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyEndpoints', this)" class="copy-btn-pastel">📋 Copy Endpoint Verification</button>

</div>

---

## Task 4: Open CloudBank and Monitoring

- CloudBank UI: http://INSTANCE_IP:3000
- Swagger UI: http://INSTANCE_IP:8080
- Zipkin: http://INSTANCE_IP:9411

If an endpoint works on the instance but not externally, verify the public IP, Internet Gateway route, security-list/NSG rule, and host firewall.

---

## Task 5: Run Sagas Without the UI

These commands use the orchestrator API directly from Cloud Shell or the Compute host. They do not put the transfer password in shell history.

### Scenario 1: Successful transfer with curl

The seeded example uses source account 1234560001 (`BankChicago`) and target account 1234560301 (`BankMex`). Enter the transfer password only when prompted.

<pre id="runSagaCurl" class="interactive-command"><code>set -e
API_BASE="http://127.0.0.1:8081/orchestrator"
UCID="ORACLE001"
FROM_ACCOUNT="1234560001"
TO_ACCOUNT="1234560301"
AMOUNT="10.00"

read -r -s -p 'Transfer password: ' TRANSFER_PASSWORD
echo
TRANSFER_RESPONSE=$(curl -fsS -X POST "$API_BASE/transfer" \
  -H 'Content-Type: application/json' \
  --data "{\"ucid\":\"$UCID\",\"fromAccountNumber\":\"$FROM_ACCOUNT\",\"toAccountNumber\":\"$TO_ACCOUNT\",\"amount\":\"$AMOUNT\",\"password\":\"$TRANSFER_PASSWORD\"}")
unset TRANSFER_PASSWORD
printf '%s\n' "$TRANSFER_RESPONSE"

SAGA_ID=$(printf '%s' "$TRANSFER_RESPONSE" | sed -nE 's/.*\"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\".*/\1/p')
test -n "$SAGA_ID" || { echo 'ERROR: no saga ID was returned'; exit 1; }
printf 'Saga ID: %s\n' "$SAGA_ID"
</code></pre>

<div class="button-center">

<button onclick="copyBlock('runSagaCurl', this)" class="copy-btn-pastel">📋 Copy API Transfer</button>

</div>

The operation is asynchronous; save the returned saga ID and wait briefly before querying it.

### Scenario 2: Expected validation rejection

Repeat Scenario 1 with an amount greater than the available source balance. This exercises withdrawal validation; it is not evidence that a previously completed participant was compensated.

### Scenario 3: Query the specific saga with SQLcl

Set the wallet path, start SQLcl, enter database passwords interactively, and paste the following. Replace placeholders with your configured alias and usernames; do not put passwords in commands or history.

<pre id="verifySagaState" class="interactive-command"><code>export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
sql /nolog

CONNECT &lt;ADMIN_USERNAME&gt;@&lt;TNS_ALIAS&gt;
ACCEPT saga_id CHAR PROMPT 'Saga ID: '

SELECT saga_id, status, coordinator, start_time, saga_source
FROM (
  SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source
  FROM DBA_SAGAS
  WHERE RAWTOHEX(id) = UPPER('&amp;saga_id')
  UNION ALL
  SELECT RAWTOHEX(id), status, coordinator, start_time, 'HISTORY'
  FROM DBA_HIST_SAGAS
  WHERE RAWTOHEX(id) = UPPER('&amp;saga_id')
)
ORDER BY start_time DESC;

CONNECT &lt;ORCHESTRATOR_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT saga_id, operationtype, operation_status, transfer_type, created_at
FROM cloudbank_book
WHERE saga_id = '&amp;saga_id'
ORDER BY created_at;

CONNECT &lt;BANKA_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT saga_id, operationtype, transactiontype, transaction_amount, operation_status, account_number, created_at
FROM banka_book
WHERE saga_id = '&amp;saga_id'
ORDER BY created_at;

CONNECT &lt;BANKB_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT saga_id, operationtype, transactiontype, transaction_amount, operation_status, account_number, created_at
FROM bankb_book
WHERE saga_id = '&amp;saga_id'
ORDER BY created_at;
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifySagaState', this)" class="copy-btn-pastel">📋 Copy SQLcl Saga Verification</button>

</div>

Check the balances from the request:

<pre id="verifyBalances" class="interactive-command"><code>CONNECT &lt;BANKA_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT account_number, balance_amount FROM banka WHERE account_number = 1234560001;

CONNECT &lt;BANKB_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT account_number, balance_amount FROM bankb WHERE account_number = 1234560301;
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyBalances', this)" class="copy-btn-pastel">📋 Copy Balance Verification</button>

</div>

<style>
.input-section { background-color: #f9f9f9; padding: 15px; margin: 12px 0; border-radius: 8px; border: 1px solid #ddd; }
.input-field { width: 300px; max-width: 100%; padding: 8px 10px; font-size: 14px; border: 1px solid #ccc; border-radius: 4px; margin: 6px 0; box-sizing: border-box; }
.interactive-command { position: relative; background-color: #f5f5f5; border: 1px solid #ddd; padding: 12px 14px; border-radius: 6px; margin: 12px 0; font-family: "Courier New", monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.button-center { text-align: left; margin: 15px 0; }
.copy-btn-pastel { background-color: #90EE90; color: #2E7D32; padding: 10px 16px; border: none; border-radius: 12px; cursor: pointer; font-size: 14px; margin: 10px 0; font-weight: 500; }
.copy-btn-pastel:hover { background-color: #7FDD7F; transform: translateY(-1px); }
</style>

<script>
function setTextForClass(className, value) { document.querySelectorAll("." + className).forEach(function(element) { element.textContent = value; }); }
function updateLabValues() {
  const input = document.getElementById("computeInstanceIP");
  const instanceIP = (input ? input.value : "").trim() || "INSTANCE_IP";
  setTextForClass("instance-ip-value", instanceIP);
  if (input && input.value.trim()) sessionStorage.setItem("computePublicIP", input.value.trim());
}
function loadPreviousLabValues() {
  const input = document.getElementById("computeInstanceIP");
  const savedIP = sessionStorage.getItem("computePublicIP");
  if (input && savedIP) input.value = savedIP;
  updateLabValues();
}
function copyBlock(elementId, button) {
  const element = document.getElementById(elementId);
  if (!element) return;
  const text = element.innerText;
  const originalText = button ? button.innerHTML : "";
  const done = function() { if (button) { button.innerHTML = "✅ Copied!"; setTimeout(function() { button.innerHTML = originalText; }, 2000); } };
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done);
  else { const area = document.createElement("textarea"); area.value = text; document.body.appendChild(area); area.select(); document.execCommand("copy"); document.body.removeChild(area); done(); }
}
if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", loadPreviousLabValues); else loadPreviousLabValues();
</script>
