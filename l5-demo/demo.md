# Lab 5: Oracle Sagas in Action — The CloudBank Application

## Introduction

This lab runs the CloudBank application with Podman and lets you observe Oracle Sagas. The supplied application archive now includes the required Java build files and a corrected Compose configuration.

You will build the Java/Flask image, start the existing ADB-backed services, run a transfer through the UI or API, and verify the specific saga with SQLcl.

> Keep passwords, wallet files, and .env values private. The Lab 3 Saga topology and the CloudBank business schema are separate prerequisites. Before running the application, verify the business objects as instructed below; run ADB setup exactly once only when none of those objects exists.

*Estimated time: 30–45 minutes*

---

## Prerequisites

- Project directory: $HOME/cloudbank-setup/oracle-saga-cloudbank
- ADB wallet: adbsSetup/adb_wallet
- A configured .env file. Do not overwrite or publish it.
- Podman 4.9+ and podman-compose.
- OCI ingress for TCP 22, 3000, 8080, and 9411 when accessing public endpoints.

Docker Engine is not required.

## Task 1: Prepare and Validate Cloud Shell

### Step 1: Verify files and tools

Run this in **Cloud Shell**. It lists only .env variable names.

<pre id="prepareCloudShell" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank" || { echo "ERROR: project directory is missing"; exit 1; }

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
sed -nE 's/^([A-Za-z_][A-Za-z0-9_]*)=.*/\1=[configured]/p' .env | sort
</code></pre>

<div class="button-center">

<button onclick="copyBlock('prepareCloudShell', this)" class="copy-btn-pastel">📋 Copy Cloud Shell Verification</button>

</div>

### Step 2: Validate and build the supplied files

The archive provides osagaJavaBuilder and osagaJavaRuntime. The runtime compiles the Maven modules in its own build stage, avoiding a remote lookup for a local builder image.

<pre id="validateFiles" class="interactive-command"><code>(
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank" || { echo "ERROR: project directory is missing"; exit 1; }

require_contains() {
  if grep -q -- "$2" "$3"; then printf 'OK: %s\n' "$1"; else printf 'ERROR: %s\n' "$1"; exit 1; fi
}
require_exact_line() {
  if grep -qxF -- "$2" "$3"; then printf 'OK: %s\n' "$1"; else printf 'ERROR: %s\n' "$1"; exit 1; fi
}
require_absent() {
  if grep -q -- "$2" "$3"; then printf 'ERROR: %s\n' "$1"; exit 1; else printf 'OK: %s\n' "$1"; fi
}

require_contains 'Java builder base image' 'maven:3.9.9-eclipse-temurin-17' osagaJavaBuilder
require_contains 'Java runtime base image' 'eclipse-temurin:17-jre-jammy' osagaJavaRuntime
require_exact_line 'Werkzeug compatibility pin' 'Werkzeug&gt;=2.3.7,&lt;3.0' CloudBank/Website/requirements.txt
require_contains 'Swagger UI image' 'image: docker.io/swaggerapi/swagger-ui:v5.20.7' osagaAdbsSetup.yaml
require_contains 'ADB TNS alias variable' '\$\${TNS_ALIAS}' osagaAdbsSetup.yaml
require_absent 'obsolete container TNS alias is absent' '\$\${TNS_ALIAS_CONTAINER}' osagaAdbsSetup.yaml
require_contains 'ADB cleanup service' '^  osagas-cleanup-adbs:' osagaAdbsSetup.yaml
require_contains 'Website port mapping' '"3000:8084"' osagaAdbsSetup.yaml
require_contains 'Swagger UI port mapping' '"8080:8080"' osagaAdbsSetup.yaml
require_exact_line 'BankA reduced listeners' 'osaga.banka.numListeners=1' CloudBank/banka/src/main/resources/application.properties
require_exact_line 'BankA reduced publishers' 'osaga.banka.numPublishers=1' CloudBank/banka/src/main/resources/application.properties
require_exact_line 'BankA reduced pool size' 'osaga.banka.maxpool=5' CloudBank/banka/src/main/resources/application.properties
require_exact_line 'BankA reduced initial pool size' 'osaga.banka.initialPoolSize=2' CloudBank/banka/src/main/resources/application.properties
require_exact_line 'BankB reduced listeners' 'osaga.bankb.numListeners=1' CloudBank/bankb/src/main/resources/application.properties
require_exact_line 'BankB reduced publishers' 'osaga.bankb.numPublishers=1' CloudBank/bankb/src/main/resources/application.properties
require_exact_line 'BankB reduced pool size' 'osaga.bankb.maxpool=5' CloudBank/bankb/src/main/resources/application.properties
require_exact_line 'BankB reduced initial pool size' 'osaga.bankb.initialPoolSize=2' CloudBank/bankb/src/main/resources/application.properties
require_exact_line 'Orchestrator reduced listeners' 'osaga.cloudbank.numListeners=1' CloudBank/orchestrator/src/main/resources/application.properties
require_exact_line 'Orchestrator reduced publishers' 'osaga.cloudbank.numPublishers=1' CloudBank/orchestrator/src/main/resources/application.properties
require_exact_line 'Orchestrator reduced pool size' 'osaga.cloudbank.maxpool=5' CloudBank/orchestrator/src/main/resources/application.properties
require_exact_line 'Orchestrator reduced initial pool size' 'osaga.cloudbank.initialPoolSize=2' CloudBank/orchestrator/src/main/resources/application.properties

podman build --pull=always -f osagaJavaBuilder -t osaga-builder:1.0 --target builder . || exit 1
podman build -f osagaJavaRuntime -t osaga-runtime:1.0 --target runtime . || exit 1
podman run --rm --entrypoint /bin/sh osaga-runtime:1.0 -c \
  'test -f /opt/app/bankA.jar &amp;&amp; test -f /opt/app/bankB.jar &amp;&amp; test -f /opt/app/orchestrator.jar &amp;&amp; test -f /opt/app/flask_ui/app.py' || exit 1
echo 'OK: Java runtime image contains all application artifacts'
)
</code></pre>

<div class="button-center">

<button onclick="copyBlock('validateFiles', this)" class="copy-btn-pastel">📋 Copy Build Validation</button>

</div>

### Step 3: Verify or initialize the CloudBank business schema

Lab 3 verifies the Broker, coordinator, and participants. It does **not** create the CloudBank application tables. Check ADB directly rather than using the existence or exit code of an old setup container as evidence.

<pre id="verifyAdbSetup" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
ADBS_USER="$(sed -n 's/^ADBS_USERNAME=//p' .env)"
TNS_ALIAS="$(sed -n 's/^TNS_ALIAS_CONTAINER=//p' .env)"
test -n "$ADBS_USER" &amp;&amp; test -n "$TNS_ALIAS" || { echo 'ERROR: ADBS_USERNAME or TNS_ALIAS_CONTAINER is missing from .env'; exit 1; }
export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql -L "$ADBS_USER@$TNS_ALIAS"

SELECT owner, object_type, object_name
FROM dba_objects
WHERE object_name IN (
  'SEQ_CLOUDBANK_CUSTOMER_ID', 'SEQ_CLOUDBANK_LOG_ID', 'TRG_CUSTOMER_ID',
  'SEQ_ACCOUNTS_BANK_A_LOGS', 'SEQ_ACCOUNT_NUMBER_BANK_A',
  'SEQ_ACCOUNTS_BANK_B_LOGS', 'SEQ_ACCOUNT_NUMBER_BANK_B',
  'CLOUDBANK_CUSTOMER', 'CLOUDBANK_BOOK',
  'BANKA', 'BANKA_BOOK', 'BANKB', 'BANKB_BOOK'
)
ORDER BY owner, object_type, object_name;

EXIT
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyAdbSetup', this)" class="copy-btn-pastel">📋 Copy ADB Setup Check</button>

</div>

- If the query returns the CloudBank, BankA, and BankB tables (and their sequences/trigger), the business schema is ready. Do **not** run setup again.
- If it returns `no rows selected`, the business schema is absent. The Saga objects from Lab 3 can still be correct. Run the following command once from the project directory and wait for the table-creation output:

<pre id="initializeAdbBusinessSchema" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"
COMPOSE_PROFILES=adbssagasetup podman-compose -f osagaAdbsSetup.yaml up osagas-setup-adbs
</code></pre>

<div class="button-center">

<button onclick="copyBlock('initializeAdbBusinessSchema', this)" class="copy-btn-pastel">📋 Copy One-Time ADB Setup</button>

</div>

Do not use `-d`; the attached output must show each table creation and the final commits. If the inventory shows only some of the listed objects, stop and investigate rather than rerunning the non-idempotent setup.

If `podman ps` reports an invalid internal status or a rootless-network error, **do not run `podman system migrate` or `podman system reset` automatically**. First force a new Cloud Shell VM: from the Cloud Shell **Actions** menu, select **Architecture**, choose **x86_64** when it is available, and select **Confirm and Restart**. Cloud Shell preserves the home directory. If the error remains after the restart, stop the lab and capture the stale rootless PID files with `find "$HOME/.local/share/containers/storage/overlay-containers" -type f \( -name pause.pid -o -name conmon.pid \) -print`; obtain support before deleting any Podman state.

---

## Task 2: Start and Verify the Local Stack

Use the COMPOSE_PROFILES environment variable. It works with Cloud Shell and Compute versions of podman-compose; do not rely on --profile.

<pre id="startLocalStack" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
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

<details>
<summary><strong>Expected output ‼️</strong></summary>

Startup retries can briefly report a connection reset. Continue when every endpoint reaches `OK`:

```text
Waiting for http://127.0.0.1:8081/orchestrator/version OK
Waiting for http://127.0.0.1:8082/banka/version OK
Waiting for http://127.0.0.1:8083/bankb/version OK
Waiting for http://127.0.0.1:3000/ OK
Waiting for http://127.0.0.1:8080/ OK
```

`podman ps` then lists `zipkin`, `bankA`, `bankB`, `orchestrator`, `flask`, and `swagger-ui` as `Up`. The final health check returns:

```json
{ "status" : "UP" }
```
</details>

Local ports: Flask 3000, Swagger 8080, Zipkin 9411, and Java APIs 8081–8083.

---

## Task 3: Deploy to the Existing Compute Instance

Before deployment, complete Task 1, Step 3 to initialize the CloudBank business schema.

### Step 1: Check the instance

In the OCI Console, open the navigation menu (☰) in the upper-left corner, then select **Compute** → **Instances**. Open `oracle-saga-compute-instance` and copy its **Public IP address** field.

<div class="input-section">

<strong>Compute Instance Public IP:</strong>

<input type="text" id="computeInstanceIP" placeholder="Enter compute public IP (for example, 129.146.123.45)" class="input-field" oninput="updateLabValues()"><br/>

</div>

<pre id="checkCompute" class="interactive-command"><code>ssh -o ConnectTimeout=10 -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
export PATH="$HOME/.local/bin:$PATH"
free -h
df -h /
podman --version
podman-compose --version
REMOTE
</code></pre>

If SSH reports a changed host key, first confirm that `INSTANCE_IP` is the intended instance. If it was rebuilt, remove its old key with `ssh-keygen -R INSTANCE_IP`, then reconnect. Do not bypass host-key verification.

<div class="button-center">

<button onclick="copyBlock('checkCompute', this)" class="copy-btn-pastel">📋 Copy Compute Check</button>

</div>

<details>
<summary><strong>Expected output ‼️</strong></summary>

Memory and disk values vary by instance. Confirm that `free -h` and `df -h /` return system information, followed by Podman and Podman Compose versions:

```text
total        used        free      shared  buff/cache   available
Mem:           ...

Filesystem      Size  Used Avail Use% Mounted on
/dev/sda1        ...

podman version 4.9.x
podman-compose version 1.6.0
```
</details>

### Step 2: Package and deploy

The archive includes hidden files such as .env and does not run ADB setup.

<pre id="deployCloudBank" class="interactive-command"><code>PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"
ARCHIVE="$HOME/oracle-saga-cloudbank-deploy.tar.gz"
COMPUTE_IP="INSTANCE_IP"
SSH_KEY="$HOME/.ssh/cloudbank_key"
PROJECT_PARENT="$(dirname "$PROJECT_DIR")"
PROJECT_NAME="$(basename "$PROJECT_DIR")"

tar -C "$PROJECT_PARENT" -czf "$ARCHIVE" "$PROJECT_NAME"
scp -i "$SSH_KEY" "$ARCHIVE" "ubuntu@$COMPUTE_IP:~/"

ssh -i "$SSH_KEY" "ubuntu@$COMPUTE_IP" 'bash -s' &lt;&lt;'REMOTE'
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

<details>
<summary><strong>Expected output ‼️</strong></summary>

The archive transfer reaches `100%`. A first deployment downloads images and packages, so build details vary. Confirm the final image tag and created containers:

```text
oracle-saga-cloudbank-deploy.tar.gz  100%  ...
Successfully tagged localhost/osaga-runtime:1.0
zipkin
bankA
bankB
orchestrator
flask
swagger-ui
```

No `ERROR:` message should appear.
</details>

### Step 3: Verify service endpoints

<pre id="verifyEndpoints" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
for port in 3000 8080 9411; do
  printf 'localhost:%s -&gt; ' "$port"
  curl -4 -fsS --connect-timeout 5 --max-time 15 -o /dev/null -w '%{http_code}\n' "http://127.0.0.1:$port"
done
podman ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyEndpoints', this)" class="copy-btn-pastel">📋 Copy Endpoint Verification</button>

</div>

<details>
<summary><strong>Expected output ‼️</strong></summary>

```text
localhost:3000 -&gt; 200
localhost:8080 -&gt; 200
localhost:9411 -&gt; 200

NAMES         STATUS
zipkin        Up ...
bankA         Up ...
bankB         Up ...
orchestrator  Up ...
flask         Up ...
swagger-ui    Up ...
```

After an instance reboot, containers may show `Created` and return `000` until started again. The time limits prevent this verification from waiting indefinitely.
</details>

---

## Task 4: Configure and Open the CloudBank UI

Flask is the CloudBank UI. Swagger UI and Zipkin are optional for the API and SQL scenarios in Task 5. On a constrained Compute instance, leave optional services stopped unless you specifically need them.

### Step 1: Start and verify Flask on the Compute instance

The command starts Flask only when it is not already running, then verifies the local UI endpoint and shows container status.

<pre id="startFlaskUi" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
flask_state=$(podman inspect --format '{{.State.Status}}' flask 2&gt;/dev/null || true)
if [ "$flask_state" != 'running' ]; then
  podman start flask
fi

for attempt in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:3000/ &gt;/dev/null; then
    echo 'Flask UI is ready on localhost:3000.'
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    echo 'ERROR: Flask did not become ready.'
    exit 1
  fi
  sleep 2
done

podman ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
free -h
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('startFlaskUi', this)" class="copy-btn-pastel">📋 Copy Flask Start and Check</button>

</div>

<details>
<summary><strong>Expected output ‼️</strong></summary>

Brief `Connection reset by peer` messages can occur while Flask starts. Continue when this message appears:

```text
Flask UI is ready on localhost:3000.
```

`podman ps -a` shows `flask` as `Up` with `0.0.0.0:3000-&gt;8084/tcp`. The Java services may also be `Up`; `swagger-ui` can remain `Created` because it is optional. `free -h` prints the instance memory summary.
</details>

### Step 2: Allow external access to Flask

Provisioning configures both the OCI security list and a persistent host-firewall rule for TCP 3000. For an instance created with an earlier version of the provisioning script, run the following one-time repair. It inserts the allow rule before a catch-all `REJECT` rule when present. The command persists the rule if `netfilter-persistent` is available.

<pre id="allowFlaskUi" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
if ! sudo iptables -C INPUT -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT 2&gt;/dev/null; then
  reject_line=$(sudo iptables -L INPUT -n --line-numbers | awk '$4 == "REJECT" { print $1; exit }')
  if [ -n "$reject_line" ]; then
    sudo iptables -I INPUT "$reject_line" -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT
  else
    sudo iptables -A INPUT -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT
  fi
fi

if command -v netfilter-persistent &gt;/dev/null; then
  sudo netfilter-persistent save
else
  echo 'WARNING: TCP 3000 is allowed until the next reboot; install a persistent firewall service or reprovision with this branch.'
fi

sudo iptables -L INPUT -n -v --line-numbers
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('allowFlaskUi', this)" class="copy-btn-pastel">📋 Copy Flask Firewall Configuration</button>

</div>

<details>
<summary><strong>Expected output ‼️</strong></summary>

```text
run-parts: executing .../15-ip4tables save
run-parts: executing .../25-ip6tables save

Chain INPUT (policy ACCEPT ...)
num  target  prot  ...
1    ACCEPT  tcp   ... tcp dpt:3000 ctstate NEW
...  REJECT  ...
```

Rule numbers and packet counts vary. The TCP 3000 `ACCEPT` rule must appear before `REJECT`.
</details>

### Step 3: Verify the public UI and sign in

Run this from Cloud Shell. `--max-time` prevents an unresponsive endpoint from waiting indefinitely.

<pre id="verifyFlaskPublicUi" class="interactive-command"><code>curl -I --connect-timeout 5 --max-time 15 http://INSTANCE_IP:3000/
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyFlaskPublicUi', this)" class="copy-btn-pastel">📋 Copy Public Flask Check</button>

</div>

Expected output includes `HTTP/1.1 200 OK`. Then open http://<span class="instance-ip-value">INSTANCE_IP</span>:3000 in a browser and use these CloudBank customer credentials:

```text
User ID:  ORACLE001
Password: cb1
```

Optional endpoints:

- Swagger UI: http://<span class="instance-ip-value">INSTANCE_IP</span>:8080
- Zipkin: http://<span class="instance-ip-value">INSTANCE_IP</span>:9411

If an endpoint works on the instance but not externally, verify the current public IP, Internet Gateway route, security-list/NSG rule, and host firewall.

---

## Task 5: Run Sagas Without the UI

These commands use the orchestrator API directly from Cloud Shell or the Compute host. They do not put the transfer password in shell history. If your `cloudbank_customer` table has legacy numeric IDs (`1`–`4`) instead of `ORACLE001`–`ORACLE004`, complete **Task 6: Optional—Align Data from a Legacy Archive** before running Scenario 1.

### Scenario 1: Successful transfer with curl

The seeded transfer uses customer/account UCID `ORACLE001`, source account 1234560001 (`BankChicago`), and target account 1234560301 (`BankMex`). This scenario is intentionally split into a definition step and an execution step. Paste the definition first; it does **not** start a transfer or prompt for a password. Then run the short execution command separately and type the transfer password manually when prompted. Do not paste a script while a hidden password prompt is active.

<pre id="runSagaCurl" class="interactive-command"><code>run_cloudbank_transfer() {
  local api_base="http://127.0.0.1:8081/orchestrator"
  local ucid="ORACLE001"
  local from_account="1234560001"
  local to_account="1234560301"
  local amount="10.00"
  local transfer_password payload transfer_response saga_id http_status response_file

  read -r -s -p 'Transfer password: ' transfer_password
  echo
  payload=$(printf '{"ucid":"%s","fromAccountNumber":"%s","toAccountNumber":"%s","amount":"%s","password":"%s"}' \
    "$ucid" "$from_account" "$to_account" "$amount" "$transfer_password")
  response_file=$(mktemp)
  http_status=$(curl -sS -o "$response_file" -w '%{http_code}' -X POST "$api_base/transfer" \
    -H 'Content-Type: application/json' \
    --data "$payload")
  unset transfer_password
  transfer_response=$(cat "$response_file")
  rm -f "$response_file"
  printf 'HTTP status: %s\n' "$http_status"
  printf '%s\n' "$transfer_response"

  if [ "$http_status" != '202' ]; then
    echo 'Transfer was not accepted. Verify that the typed transfer password is correct, then retry.'
    return 1
  fi

  saga_id=$(printf '%s' "$transfer_response" | sed -nE 's/.*\"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\".*/\1/p')
  test -n "$saga_id" || { echo 'ERROR: no saga ID was returned'; return 1; }
  printf 'Saga ID: %s\n' "$saga_id"
}
</code></pre>

<div class="button-center">

<button onclick="copyBlock('runSagaCurl', this)" class="copy-btn-pastel">📋 Copy API Transfer</button>

</div>

After the paste finishes and the normal shell prompt returns, run this command **separately**. When `Transfer password:` appears, type the password manually (the characters will not be displayed) and press Enter. For the seeded `ORACLE001` user, the transfer password is `cb1`.

<pre id="executeSagaCurl" class="interactive-command"><code>run_cloudbank_transfer</code></pre>

<div class="button-center">

<button onclick="copyBlock('executeSagaCurl', this)" class="copy-btn-pastel">📋 Copy Transfer Execution</button>

</div>

Success prints `HTTP status: 202`, an `Accepted` JSON response, and a new `Saga ID`. Save that ID for Scenario 3. If the status is `401`, the password entry did not match; re-run only the one-line execution command and type `cb1` manually. When the demonstration is complete, remove the temporary shell function with `unset -f run_cloudbank_transfer`.

The operation is asynchronous; save the returned saga ID and wait briefly before querying it.

### Scenario 1 follow-up: Check the returned Saga status

Wait about 10 seconds, then query the exact Saga ID returned by Scenario 1. The following shell command only opens SQLcl; when it prompts for the database password, type the ADB administrator password manually. It is **not** the transfer password (`cb1`).

<pre id="openSagaStatusSqlcl" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
ADBS_USER="$(sed -n 's/^ADBS_USERNAME=//p' .env)"
TNS_ALIAS="$(sed -n 's/^TNS_ALIAS_CONTAINER=//p' .env)"
test -n "$ADBS_USER" &amp;&amp; test -n "$TNS_ALIAS" || { echo 'ERROR: ADBS_USERNAME or TNS_ALIAS_CONTAINER is missing from .env'; exit 1; }
export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql -L "$ADBS_USER@$TNS_ALIAS"</code></pre>

<div class="button-center">

<button onclick="copyBlock('openSagaStatusSqlcl', this)" class="copy-btn-pastel">📋 Copy SQLcl Start</button>

</div>

<div class="input-section">

<strong>Saga ID returned by Scenario 1:</strong>

<input type="text" id="returnedSagaId" placeholder="Paste the 32-character Saga ID returned by the transfer" class="input-field" oninput="updateLabValues()"><br/>

</div>

After `Connected to:` appears, paste the Saga ID returned by Scenario 1 into the field above. The query below updates automatically; then copy it and paste it at the `SQL>` prompt. Do not paste it while SQLcl is asking for a password.

<pre id="checkReturnedSagaStatus" class="interactive-command"><code>SELECT saga_id, status, coordinator, start_time, saga_source
FROM (
  SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source
  FROM DBA_SAGAS
  WHERE RAWTOHEX(id) = UPPER('<span class="saga-id-value">PASTE_SAGA_ID_HERE</span>')
  UNION
  SELECT RAWTOHEX(id), status, coordinator, start_time, 'HISTORY'
  FROM DBA_HIST_SAGAS
  WHERE RAWTOHEX(id) = UPPER('<span class="saga-id-value">PASTE_SAGA_ID_HERE</span>')
)
ORDER BY start_time DESC;</code></pre>

<div class="button-center">

<button onclick="copyBlock('checkReturnedSagaStatus', this)" class="copy-btn-pastel">📋 Copy Saga Status Query</button>

</div>

`SAGA_SOURCE` is a label added by this query, not a status: `ACTIVE` means the row came from `DBA_SAGAS`, while `HISTORY` means it came from `DBA_HIST_SAGAS`. A `Committed` row from the history view is successful completion. It is normal to briefly also see `Committing` in the active view while Oracle finishes lifecycle cleanup. The query uses `UNION` to avoid duplicate identical history rows. Continue to Scenario 3 to show the corresponding orchestrator and bank ledger entries, plus the changed balances.

### Scenario 2: Expected validation rejection

This scenario deliberately requests more than the source account can cover. It exercises the withdrawal-check validation before a debit or deposit is performed; it is **not** a compensation demonstration. The API initially returns `202` because the request was accepted for asynchronous processing. The later Saga result must be a rollback/non-committed outcome, and both account balances must remain unchanged.

Paste this definition first. It does not submit a transfer or prompt for a password.

<pre id="runRejectedSagaCurl" class="interactive-command"><code>run_insufficient_funds_transfer() {
  local api_base="http://127.0.0.1:8081/orchestrator"
  local ucid="ORACLE001"
  local from_account="1234560001"
  local to_account="1234560301"
  local amount="999999.00"
  local transfer_password payload transfer_response saga_id http_status response_file

  read -r -s -p 'Transfer password: ' transfer_password
  echo
  payload=$(printf '{"ucid":"%s","fromAccountNumber":"%s","toAccountNumber":"%s","amount":"%s","password":"%s"}' \
    "$ucid" "$from_account" "$to_account" "$amount" "$transfer_password")
  response_file=$(mktemp)
  http_status=$(curl -sS -o "$response_file" -w '%{http_code}' -X POST "$api_base/transfer" \
    -H 'Content-Type: application/json' \
    --data "$payload")
  unset transfer_password
  transfer_response=$(cat "$response_file")
  rm -f "$response_file"
  printf 'HTTP status: %s\n' "$http_status"
  printf '%s\n' "$transfer_response"

  if [ "$http_status" != '202' ]; then
    echo 'The request was not accepted for validation. Verify the typed transfer password, then retry.'
    return 1
  fi

  saga_id=$(printf '%s' "$transfer_response" | sed -nE 's/.*"id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')
  test -n "$saga_id" || { echo 'ERROR: no saga ID was returned'; return 1; }
  printf 'Saga ID: %s\n' "$saga_id"
}
</code></pre>

<div class="button-center">

<button onclick="copyBlock('runRejectedSagaCurl', this)" class="copy-btn-pastel">📋 Copy Rejection Test Definition</button>

</div>

After the normal shell prompt returns, run this command **separately**. At the password prompt, type `cb1` manually and press Enter.

<pre id="executeRejectedSagaCurl" class="interactive-command"><code>run_insufficient_funds_transfer</code></pre>

<div class="button-center">

<button onclick="copyBlock('executeRejectedSagaCurl', this)" class="copy-btn-pastel">📋 Copy Rejection Test Execution</button>

</div>

Save the returned Saga ID and wait about 10 seconds before checking its final state.

### Scenario 2 follow-up: Check the rejected Saga status

The following shell command only opens SQLcl. When it prompts for the database password, type the ADB administrator password manually; it is **not** the transfer password (`cb1`).

<pre id="openRejectedSagaStatusSqlcl" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
ADBS_USER="$(sed -n 's/^ADBS_USERNAME=//p' .env)"
TNS_ALIAS="$(sed -n 's/^TNS_ALIAS_CONTAINER=//p' .env)"
test -n "$ADBS_USER" &amp;&amp; test -n "$TNS_ALIAS" || { echo 'ERROR: ADBS_USERNAME or TNS_ALIAS_CONTAINER is missing from .env'; exit 1; }
export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql -L "$ADBS_USER@$TNS_ALIAS"</code></pre>

<div class="button-center">

<button onclick="copyBlock('openRejectedSagaStatusSqlcl', this)" class="copy-btn-pastel">📋 Copy SQLcl Start</button>

</div>

After `Connected to:` appears, paste the Saga ID returned by Scenario 2 into this field. The query below updates automatically; copy it and paste it at the `SQL>` prompt. Do not paste it while SQLcl is asking for a password.

<div class="input-section">

<strong>Saga ID returned by Scenario 2:</strong>

<input type="text" id="rejectedSagaId" placeholder="Paste the 32-character Saga ID returned by the rejection test" class="input-field" oninput="updateLabValues()"><br/>

</div>

<pre id="checkRejectedSagaStatus" class="interactive-command"><code>SELECT saga_id, status, coordinator, start_time, saga_source
FROM (
  SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source
  FROM DBA_SAGAS
  WHERE RAWTOHEX(id) = UPPER('<span class="rejected-saga-id-value">PASTE_REJECTED_SAGA_ID_HERE</span>')
  UNION
  SELECT RAWTOHEX(id), status, coordinator, start_time, 'HISTORY'
  FROM DBA_HIST_SAGAS
  WHERE RAWTOHEX(id) = UPPER('<span class="rejected-saga-id-value">PASTE_REJECTED_SAGA_ID_HERE</span>')
)
ORDER BY start_time DESC;</code></pre>

<div class="button-center">

<button onclick="copyBlock('checkRejectedSagaStatus', this)" class="copy-btn-pastel">📋 Copy Rejection Status Query</button>

</div>

Expected evidence:

- The initial HTTP response is `202 Accepted`; this confirms the validation Saga started, not that money moved.
- The Saga later has a rollback/non-committed terminal result instead of `Committed`.
- The orchestrator ledger records the rejected transfer workflow.
- No successful debit appears for BankChicago, no successful deposit appears for BankMex, and both balances are unchanged.

When finished, remove the temporary function with `unset -f run_insufficient_funds_transfer`.

### Scenario 3: Query the specific saga with SQLcl

Set the wallet path, start SQLcl, enter database passwords interactively, and paste the following. Starting from `/tmp` with `SQLPATH=/nonexistent` prevents local startup scripts from delaying SQLcl. Replace placeholders with your configured alias and usernames; do not put passwords in commands or history.

<pre id="verifySagaState" class="interactive-command"><code>export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql /nolog

CONNECT &lt;ADMIN_USERNAME&gt;@&lt;TNS_ALIAS&gt;
ACCEPT saga_id CHAR PROMPT 'Saga ID: '

SELECT saga_id, status, coordinator, start_time, saga_source
FROM (
  SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source
  FROM DBA_SAGAS
  WHERE RAWTOHEX(id) = UPPER('&amp;saga_id')
  UNION
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

---

## Task 6 (Optional): Align Data from a Legacy Archive

Run this one-time task **only** when `cloudbank_customer` contains the legacy numeric customer IDs `1`, `2`, `3`, and `4`. Skip it when Task 1 setup was just completed or when the table already shows `ORACLE001` through `ORACLE004`. The current archive seeds the matching UCIDs directly. The command reads the application database schema username from `ORCHESTRATOR_USERNAME` in `.env`; it does not use the Lab 3 participant-owner name.

<pre id="alignSeededIdentities" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
ORCHESTRATOR_USER="$(sed -n 's/^ORCHESTRATOR_USERNAME=//p' .env)"
TNS_ALIAS="$(sed -n 's/^TNS_ALIAS_CONTAINER=//p' .env)"
test -n "$ORCHESTRATOR_USER" &amp;&amp; test -n "$TNS_ALIAS" || { echo 'ERROR: ORCHESTRATOR_USERNAME or TNS_ALIAS_CONTAINER is missing from .env'; exit 1; }
export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql -L "$ORCHESTRATOR_USER@$TNS_ALIAS"

SELECT table_name
FROM user_tables
WHERE table_name = 'CLOUDBANK_CUSTOMER';

UPDATE cloudbank_customer
SET customer_id = CASE customer_id
  WHEN '1' THEN 'ORACLE001'
  WHEN '2' THEN 'ORACLE002'
  WHEN '3' THEN 'ORACLE003'
  WHEN '4' THEN 'ORACLE004'
END
WHERE customer_id IN ('1', '2', '3', '4');

COMMIT;

SELECT customer_id, bank
FROM cloudbank_customer
WHERE customer_id IN ('ORACLE001', 'ORACLE002', 'ORACLE003', 'ORACLE004')
ORDER BY customer_id;

EXIT
</code></pre>

<div class="button-center">

<button onclick="copyBlock('alignSeededIdentities', this)" class="copy-btn-pastel">📋 Copy Identity Alignment</button>

</div>

`0 rows updated` means the IDs were already aligned; it is successful and requires no further action.

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
function getComputeIP() {
  const input = document.getElementById("computeInstanceIP");
  return (input ? input.value : "").trim();
}
function getReturnedSagaId() {
  const input = document.getElementById("returnedSagaId");
  const sagaId = (input ? input.value : "").trim().toUpperCase();
  return /^[0-9A-F]{32}$/.test(sagaId) ? sagaId : "";
}
function getRejectedSagaId() {
  const input = document.getElementById("rejectedSagaId");
  const sagaId = (input ? input.value : "").trim().toUpperCase();
  return /^[0-9A-F]{32}$/.test(sagaId) ? sagaId : "";
}
function updateLabValues() {
  const ipInput = document.getElementById("computeInstanceIP");
  const instanceIP = getComputeIP() || "INSTANCE_IP";
  const sagaInput = document.getElementById("returnedSagaId");
  const sagaId = getReturnedSagaId();
  const rejectedSagaInput = document.getElementById("rejectedSagaId");
  const rejectedSagaId = getRejectedSagaId();
  setTextForClass("instance-ip-value", instanceIP);
  setTextForClass("saga-id-value", sagaId || "PASTE_SAGA_ID_HERE");
  setTextForClass("rejected-saga-id-value", rejectedSagaId || "PASTE_REJECTED_SAGA_ID_HERE");
  if (ipInput && ipInput.value.trim()) sessionStorage.setItem("computePublicIP", ipInput.value.trim());
  if (sagaInput && sagaId) sessionStorage.setItem("returnedSagaId", sagaId);
  if (rejectedSagaInput && rejectedSagaId) sessionStorage.setItem("rejectedSagaId", rejectedSagaId);
}
function loadPreviousLabValues() {
  const ipInput = document.getElementById("computeInstanceIP");
  const sagaInput = document.getElementById("returnedSagaId");
  const rejectedSagaInput = document.getElementById("rejectedSagaId");
  const savedIP = sessionStorage.getItem("computePublicIP");
  const savedSagaId = sessionStorage.getItem("returnedSagaId");
  const savedRejectedSagaId = sessionStorage.getItem("rejectedSagaId");
  if (ipInput && savedIP) ipInput.value = savedIP;
  if (sagaInput && savedSagaId) sagaInput.value = savedSagaId;
  if (rejectedSagaInput && savedRejectedSagaId) rejectedSagaInput.value = savedRejectedSagaId;
  updateLabValues();
}
function copyBlock(elementId, button) {
  const element = document.getElementById(elementId);
  if (!element) return;
  const text = element.textContent
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/<\/?span\b[^>]*>/gi, "");
  const instanceIP = getComputeIP();
  const sagaId = getReturnedSagaId();
  const rejectedSagaId = getRejectedSagaId();
  let resolvedText = instanceIP ? text.replace(/\bINSTANCE_IP\b/g, instanceIP) : text;
  if (sagaId) resolvedText = resolvedText.replace(/PASTE_SAGA_ID_HERE/g, sagaId);
  if (rejectedSagaId) resolvedText = resolvedText.replace(/PASTE_REJECTED_SAGA_ID_HERE/g, rejectedSagaId);
  const originalText = button ? button.innerHTML : "";
  const done = function() { if (button) { button.innerHTML = "✅ Copied!"; setTimeout(function() { button.innerHTML = originalText; }, 2000); } };
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(resolvedText).then(done);
  else { const area = document.createElement("textarea"); area.value = resolvedText; document.body.appendChild(area); area.select(); document.execCommand("copy"); document.body.removeChild(area); done(); }
}
if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", loadPreviousLabValues); else loadPreviousLabValues();
</script>
