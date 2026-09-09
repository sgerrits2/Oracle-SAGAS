# Lab 5: Oracle Sagas in Action — The CloudBank Application

## Introduction

This lab runs the CloudBank application with Podman and lets you observe Oracle Sagas. The supplied application archive now includes the required Java build files and a corrected Compose configuration.

You will build the Java/Flask image, start the existing ADB-backed services, run transfers through the CloudBank UI, and observe the asynchronous outcomes in the dashboard.

> **🔒 Important:** Keep passwords, wallet files, and `.env` values private. The Lab 3 Saga topology and the CloudBank business schema are separate prerequisites. Before running the application, verify the business objects as instructed below; run ADB setup exactly once only when none of those objects exists.

- **Estimated time:** 30–45 minutes

---

## Prerequisites

- **Project directory:** `$HOME/cloudbank-setup/oracle-saga-cloudbank`
- **ADB wallet:** `adbsSetup/adb_wallet`
- **Environment file:** A configured `.env` file. Do not overwrite or publish it.
- **Container tools:** Podman 4.9+ and `podman-compose`.
- **Network access:** OCI ingress for TCP ports `22` and `3000`. The Java APIs, Swagger UI, and Zipkin stay private; use SSH or an SSH tunnel when accessing them.
- **Compute requirements:** A persistent 2 GB swap file and `Linger=yes` on the 1 GB Compute instance. Lab 2 configures both automatically; Task 3 verifies them before deployment.

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

<details>
<summary><strong>✅ Expected output</strong></summary>

The exact versions may differ. Confirm these key results:

```text
...:oracle-saga-cloudbank (... )$
CloudBank/bankb/pom.xml
CloudBank/banka/pom.xml
CloudBank/orchestrator/pom.xml
CloudBank/Website/app.py

ADBS_ADMIN_PWD=[configured]
ORCHESTRATOR_PASSWORD=[configured]
TNS_ADMIN_CONTAINER=[configured]
TNS_ALIAS_CONTAINER=[configured]
```

The four `test` commands are silent when successful. Continue if there is no `ERROR:` message and every displayed `.env` value is `[configured]`.
</details>

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

<details>
<summary><strong>✅ Expected output</strong></summary>

The first build can download images and dependencies, so intermediate output varies. Confirm these final lines:

```text
OK: Java builder base image
OK: Java runtime base image
OK: Werkzeug compatibility pin
OK: Swagger UI image
OK: ADB TNS alias variable
OK: obsolete container TNS alias is absent
OK: ADB cleanup service
OK: Website port mapping
OK: Swagger UI port mapping

Successfully tagged localhost/osaga-builder:1.0
Successfully tagged localhost/osaga-runtime:1.0
OK: Java runtime image contains all application artifacts
```

Continue only if every check is `OK:` and the final artifact message appears without an `ERROR:` message.
</details>

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

<details>
<summary><strong>✅ Expected output</strong></summary>

On a new database, the expected result is:

```text
no rows selected
```

After successful setup, the query returns these objects:

```text
OWNER              OBJECT_TYPE    OBJECT_NAME
-----------------  -------------  ----------------------------
BANKCHICAGO        SEQUENCE       SEQ_ACCOUNTS_BANK_A_LOGS
BANKCHICAGO        SEQUENCE       SEQ_ACCOUNT_NUMBER_BANK_A
BANKCHICAGO        TABLE          BANKA
BANKCHICAGO        TABLE          BANKA_BOOK
BANKMEX            SEQUENCE       SEQ_ACCOUNTS_BANK_B_LOGS
BANKMEX            SEQUENCE       SEQ_ACCOUNT_NUMBER_BANK_B
BANKMEX            TABLE          BANKB
BANKMEX            TABLE          BANKB_BOOK
ORCHESTRATORHUB    SEQUENCE       SEQ_CLOUDBANK_CUSTOMER_ID
ORCHESTRATORHUB    SEQUENCE       SEQ_CLOUDBANK_LOG_ID
ORCHESTRATORHUB    TABLE          CLOUDBANK_BOOK
ORCHESTRATORHUB    TABLE          CLOUDBANK_CUSTOMER
ORCHESTRATORHUB    TRIGGER        TRG_CUSTOMER_ID

13 rows selected.
```
</details>

- If the query returns the CloudBank, BankA, and BankB tables (and their sequences/trigger), the business schema is ready. Do **not** run setup again.
- If it returns `no rows selected`, the business schema is absent. The Saga objects from Lab 3 can still be correct. Run the following command once from the project directory and wait for the table-creation output:

<pre id="initializeAdbBusinessSchema" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"
COMPOSE_PROFILES=adbssagasetup podman-compose -f osagaAdbsSetup.yaml up osagas-setup-adbs
</code></pre>

<div class="button-center">

<button onclick="copyBlock('initializeAdbBusinessSchema', this)" class="copy-btn-pastel">📋 Copy One-Time ADB Setup</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

```text
[osagas-setup-adbs] | Connected! Now running setup script...
[osagas-setup-adbs] | Table CLOUDBANK_CUSTOMER created.
[osagas-setup-adbs] | Table BANKA created.
[osagas-setup-adbs] | Table BANKB created.
[osagas-setup-adbs] | Commit complete.
```

Image downloads and additional creation messages vary. The setup is successful only when no `ORA-` error is printed.
</details>

Do not use `-d`; the attached output must show each table creation and the final commits. If the inventory shows only some of the listed objects, stop and investigate rather than rerunning the non-idempotent setup.

If `podman ps` reports an invalid internal status or a rootless-network error, **do not run `podman system migrate` or `podman system reset` automatically**. First force a new Cloud Shell VM: from the Cloud Shell **Actions** menu, select **Architecture**, choose **x86_64** when it is available, and select **Confirm and Restart**. Cloud Shell preserves the home directory. If the error remains after the restart, stop the lab and capture the stale rootless PID files with `find "$HOME/.local/share/containers/storage/overlay-containers" -type f \( -name pause.pid -o -name conmon.pid \) -print`; obtain support before deleting any Podman state.

---

## Task 2: Start and Verify the Local Stack

Use the COMPOSE_PROFILES environment variable. It works with Cloud Shell and Compute versions of podman-compose; do not rely on --profile.

<pre id="startLocalStack" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"
export ENABLE_ZIPKIN=false

COMPOSE_PROFILES=adbs podman-compose -f osagaAdbsSetup.yaml up -d --build

for endpoint in \
  'http://127.0.0.1:8081/orchestrator/version' \
  'http://127.0.0.1:8082/banka/version' \
  'http://127.0.0.1:8083/bankb/version' \
  'http://127.0.0.1:3000/'; do
  printf 'Waiting for %s ' "$endpoint"
  for attempt in $(seq 1 30); do
    if curl -fsS "$endpoint" &gt;/dev/null; then echo 'OK'; break; fi
    if [ "$attempt" -eq 30 ]; then echo 'FAILED'; exit 1; fi
    sleep 2
  done
done

podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
</code></pre>

<div class="button-center">

<button onclick="copyBlock('startLocalStack', this)" class="copy-btn-pastel">📋 Copy Local Stack Start</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

The first Java startup can take about two minutes. Each endpoint has a five-minute deadline and every individual request is bounded, so dots or a quiet startup period are normal. Continue when every endpoint reaches `OK`:

```text
Waiting for http://127.0.0.1:8081/orchestrator/version OK
Waiting for http://127.0.0.1:8082/banka/version OK
Waiting for http://127.0.0.1:8083/bankb/version OK
Waiting for http://127.0.0.1:3000/ OK
Local stack validation: OK
```

`podman ps` then lists `bankA`, `bankB`, `orchestrator`, and `flask` as `Up`. Swagger UI and Zipkin are not started by the default profile.
</details>

Core ports are Flask 3000 and Java APIs 8081–8083. Swagger 8080 and Zipkin 9411 are optional.

---

## Task 3: Deploy to the Existing Compute Instance

Before deployment, complete Task 1, Step 3 to initialize the CloudBank business schema.

### Step 1: Prepare and check the instance

In the OCI Console, open the navigation menu (☰) in the upper-left corner, then select **Compute** → **Instances**. Open `oracle-saga-compute-instance` and copy its **Public IP address** field.

<div class="input-section">

<strong>Compute Instance Public IP:</strong>

<input type="text" id="computeInstanceIP" placeholder="Enter compute public IP (for example, 129.146.123.45)" class="input-field" oninput="updateLabValues()"><br/>

</div>

<pre id="checkCompute" class="interactive-command"><code>ssh -o ConnectTimeout=10 -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"

sudo loginctl enable-linger ubuntu

if [ ! -f /swapfile ]; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
fi
if ! sudo swapon --show=NAME --noheadings | grep -qx '/swapfile'; then
  sudo swapon /swapfile
fi
if ! grep -qE '^/swapfile[[:space:]]' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab &gt;/dev/null
fi

free -h
sudo swapon --show
loginctl show-user ubuntu -p Linger
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
<summary><strong>✅ Expected output</strong></summary>

Memory and disk values vary by instance. Confirm that swap is 2 GB, lingering is enabled, and the tool versions print successfully:

```text
total        used        free      shared  buff/cache   available
Mem:           ...
Swap:          2.0Gi ...

NAME      TYPE SIZE USED PRIO
/swapfile file   2G  ...   -2

Linger=yes

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
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
DEPLOY_DIR="$HOME/oracle-saga-cloudbank"
STAGING_DIR="$HOME/oracle-saga-cloudbank.staging"

sudo loginctl enable-linger ubuntu
podman rm -f swagger-ui zipkin bankA bankB orchestrator flask 2&gt;/dev/null || true

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
export ENABLE_ZIPKIN=false

grep -qxF 'osaga.banka.numPublishers=0' CloudBank/banka/src/main/resources/application.properties
grep -qxF 'osaga.bankb.numPublishers=0' CloudBank/bankb/src/main/resources/application.properties
grep -qxF 'osaga.cloudbank.numPublishers=1' CloudBank/orchestrator/src/main/resources/application.properties

podman build -f osagaJavaRuntime -t osaga-runtime:1.0 --target runtime .
podman run --rm --entrypoint /bin/sh osaga-runtime:1.0 -c \
  'test -f /opt/app/bankA.jar &amp;&amp; test -f /opt/app/bankB.jar &amp;&amp; test -f /opt/app/orchestrator.jar &amp;&amp; test -f /opt/app/flask_ui/app.py'
echo 'Runtime image validation: OK'

COMPOSE_PROFILES=adbs podman-compose -f osagaAdbsSetup.yaml up -d
echo 'Deployment start: OK'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('deployCloudBank', this)" class="copy-btn-pastel">📋 Copy Deployment Script</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

The archive transfer reaches `100%`. A first deployment downloads images and packages, so build details vary. Confirm the final image tag and created containers:

```text
oracle-saga-cloudbank-deploy.tar.gz  100%  ...
Successfully tagged localhost/osaga-runtime:1.0
bankA
bankB
orchestrator
flask
```

No `ERROR:` message should appear. Swagger UI and Zipkin are omitted from the default deployment.
</details>

### Step 3: Verify service endpoints

<pre id="verifyEndpoints" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
set -euo pipefail

start_and_verify() {
  local container="$1"
  local endpoint="$2"
  local deadline=$((SECONDS + 300))
  local status

  status="$(podman inspect --format '{{.State.Status}}' "$container" 2&gt;/dev/null || true)"
  if [ "$status" != 'running' ]; then
    echo "Starting $container..."
    podman start "$container" &gt;/dev/null
  fi

  printf 'Waiting for %s ' "$endpoint"
  while (( SECONDS &lt; deadline )); do
    if curl -4 --connect-timeout 2 --max-time 4 -fsS "$endpoint" &gt;/dev/null 2&gt;&amp;1; then
      echo 'OK'
      return 0
    fi

    status="$(podman inspect --format '{{.State.Status}}' "$container")"
    if [ "$status" != 'running' ]; then
      echo "FAILED: $container is $status"
      podman logs --tail 120 "$container" 2&gt;&amp;1
      return 1
    fi

    printf '.'
    sleep 2
  done

  echo "FAILED: $container readiness timeout"
  podman logs --tail 120 "$container" 2&gt;&amp;1
  return 1
}

start_and_verify bankA http://127.0.0.1:8082/banka/version
start_and_verify bankB http://127.0.0.1:8083/bankb/version
start_and_verify orchestrator http://127.0.0.1:8081/orchestrator/version
start_and_verify flask http://127.0.0.1:3000/

podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
timeout 15s podman stats --no-stream --format 'table {{.Name}}\t{{.CPU}}\t{{.MemUsage}}' || true
free -h
sudo swapon --show
echo 'Compute stack validation: OK'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyEndpoints', this)" class="copy-btn-pastel">📋 Copy Endpoint Verification</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

```text
Waiting for http://127.0.0.1:8082/banka/version ... OK
Waiting for http://127.0.0.1:8083/bankb/version ... OK
Waiting for http://127.0.0.1:8081/orchestrator/version ... OK
Waiting for http://127.0.0.1:3000/ ... OK

NAMES         STATUS
bankA         Up ...
bankB         Up ...
orchestrator  Up ...
flask         Up ...

Compute stack validation: OK
```

The services start in dependency order. After an instance reboot, this same command restarts any stopped containers. Five-minute service deadlines and per-request timeouts prevent indefinite waits.
</details>

---

## Task 4: Configure and Open the CloudBank UI

Flask is the CloudBank UI used for the transfer exercises in Task 5. Swagger UI and Zipkin remain optional and are not required for this lab path. On a constrained Compute instance, leave optional services stopped.

### Step 1: Start and verify Flask on the Compute instance

The command starts Flask only when it is not already running, then verifies the local UI endpoint and shows container status.

<pre id="startFlaskUi" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
set -euo pipefail
flask_state=$(podman inspect --format '{{.State.Status}}' flask 2&gt;/dev/null || true)
if [ "$flask_state" != 'running' ]; then
  podman start flask
fi

deadline=$((SECONDS + 300))
while (( SECONDS &lt; deadline )); do
  if curl --connect-timeout 2 --max-time 4 -fsS http://127.0.0.1:3000/ &gt;/dev/null 2&gt;&amp;1; then
    echo 'Flask UI is ready on localhost:3000.'
    break
  fi

  flask_state=$(podman inspect --format '{{.State.Status}}' flask)
  if [ "$flask_state" != 'running' ]; then
    echo "ERROR: Flask is $flask_state."
    podman logs --tail 100 flask 2&gt;&amp;1
    exit 1
  fi

  sleep 2
done

if ! curl --connect-timeout 2 --max-time 4 -fsS http://127.0.0.1:3000/ &gt;/dev/null 2&gt;&amp;1; then
  echo 'ERROR: Flask did not become ready within five minutes.'
  podman logs --tail 100 flask 2&gt;&amp;1
  exit 1
fi

podman ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
free -h
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('startFlaskUi', this)" class="copy-btn-pastel">📋 Copy Flask Start and Check</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

Brief `Connection reset by peer` messages can occur while Flask starts. Continue when this message appears:

```text
Flask UI is ready on localhost:3000.
```

`podman ps -a` shows `flask` as `Up` with `0.0.0.0:3000-&gt;8084/tcp`. The Java services may also be `Up`; optional `swagger-ui` and `zipkin` containers are not listed unless you started them. `free -h` prints the instance memory summary.
</details>

### Step 2: Allow external access to Flask

Provisioning configures both the OCI security list and a persistent host-firewall rule for TCP 3000. For an instance created with an earlier version of the provisioning script, run the following one-time repair. It inserts the allow rule before a catch-all `REJECT` rule when present. The command persists the rule if `netfilter-persistent` is available.

<pre id="allowFlaskUi" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
set -euo pipefail

while sudo iptables -C INPUT -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT 2&gt;/dev/null; do
  sudo iptables -D INPUT -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT
done

reject_line=$(sudo iptables -L INPUT -n --line-numbers | awk '$2 == "REJECT" { print $1; exit }')
if [ -n "$reject_line" ]; then
  sudo iptables -I INPUT "$reject_line" -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT
else
  sudo iptables -A INPUT -p tcp --dport 3000 -m conntrack --ctstate NEW -j ACCEPT
fi

if command -v netfilter-persistent &gt;/dev/null; then
  sudo netfilter-persistent save
else
  echo 'WARNING: TCP 3000 is allowed until the next reboot; install a persistent firewall service or reprovision with this branch.'
fi

echo 'The TCP 3000 ACCEPT rule must appear before REJECT:'
sudo iptables -L INPUT -n -v --line-numbers | grep -E 'Chain INPUT|dpt:3000|REJECT'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('allowFlaskUi', this)" class="copy-btn-pastel">📋 Copy Flask Firewall Configuration</button>

</div>

<details>
<summary><strong>✅ Expected output</strong></summary>

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

<details>
<summary><strong>✅ Expected output</strong></summary>

```text
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
Content-Length: ...
Date: ...
```
</details>

**Then open the CloudBank UI:** http://<span class="instance-ip-value">INSTANCE_IP</span>:3000 in a browser. Use these CloudBank customer credentials:

```text
User ID:  ORACLE001
Password: cb1
```

<details>
<summary><strong>📋 Optional: Start Swagger UI and Zipkin</strong></summary>

Skip this section on the 1 GB Always Free instance. On an instance with additional memory, start both optional services with:

<pre id="startOptionalServices" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@INSTANCE_IP 'bash -s' &lt;&lt;'REMOTE'
cd "$HOME/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"
export ENABLE_ZIPKIN=true
COMPOSE_PROFILES=adbs,optional podman-compose -f osagaAdbsSetup.yaml up -d
podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('startOptionalServices', this)" class="copy-btn-pastel">📋 Copy Optional Services Start</button>

</div>

The optional ports are not exposed publicly. From a separate Cloud Shell terminal, create an SSH tunnel and keep it open:

<pre id="tunnelOptionalServices" class="interactive-command"><code>ssh -N \
  -L 8080:127.0.0.1:8080 \
  -L 9411:127.0.0.1:9411 \
  -i "$HOME/.ssh/cloudbank_key" \
  ubuntu@INSTANCE_IP
</code></pre>

<div class="button-center">

<button onclick="copyBlock('tunnelOptionalServices', this)" class="copy-btn-pastel">📋 Copy Optional-Service Tunnel</button>

</div>

Then use `http://127.0.0.1:8080` for Swagger UI and `http://127.0.0.1:9411` for Zipkin from the tunneled client. Do not enable these services on the 1 GB shape.

</details>

---

## Task 5: Run Sagas with the CloudBank UI

Use the CloudBank UI to initiate both transfers. Complete Task 4 first, open the UI, and sign in as `ORACLE001` with password `cb1`. If the database still uses legacy numeric customer IDs (`1`–`4`), complete **(Optional) Task 6: Align Data from a Legacy Archive** before starting Scenario 1.

<table class="task5-scenario-table">
<thead>
<tr><th>Scenario</th><th>Source</th><th>Destination</th><th>Amount</th><th>Expected outcome</th></tr>
</thead>
<tbody>
<tr class="task5-success-row">
<td><span class="task5-badge task5-success-badge">1 · Successful</span></td>
<td><code>1234560001</code><br/><small>BankChicago</small></td>
<td><code>1234560301</code><br/><small>BankMex</small></td>
<td><strong>$10.00</strong></td>
<td><strong>Saga commits</strong><br/><small>Balances move by $10.00</small></td>
</tr>
<tr class="task5-rejection-row">
<td><span class="task5-badge task5-rejection-badge">2 · Rejected</span></td>
<td><code>1234560001</code><br/><small>BankChicago</small></td>
<td><code>1234560301</code><br/><small>BankMex</small></td>
<td><strong>$999,999.00</strong></td>
<td><strong>Validation fails</strong><br/><small>No balance change</small></td>
</tr>
</tbody>
</table>

### Scenario 1: Successful transfer with the UI

The seeded source account is `1234560001` at **BankChicago**. The destination account is `1234560301` at **BankMex**. The source balance starts at `$2,000.00`, so the `$10.00` transfer has sufficient funds.

### Step 1: Submit the transfer

Open **Transfer** in the CloudBank UI and enter the following values:

<table class="task5-input-table">
<tbody>
<tr><th>From account</th><td><code>1234560001</code> — BankChicago</td></tr>
<tr><th>To account</th><td><code>1234560301</code> — BankMex</td></tr>
<tr><th>Amount</th><td><strong><code>10.00</code></strong></td></tr>
<tr><th>Password</th><td><code>cb1</code></td></tr>
</tbody>
</table>

Click **Initiate Transfer**.

<div class="task5-result-note">
<strong>Accepted response:</strong> The dashboard opens a <strong>SAGA REQUEST ACCEPTED</strong> dialog. This confirms that the asynchronous Saga was accepted; it does not yet confirm completion.
</div>

### Step 2: Follow the successful transfer in the UI

Use **Copy request ID** if you want to keep the request reference, then close the confirmation dialog. Wait about 10 seconds and click **Refresh** if the latest status is not visible yet.

<div class="task5-result-note">
<strong>Expected dashboard result:</strong> The CloudBank Logs tab shows the transfer request as completed. Starting from a clean seed, the BankChicago account card changes from `$2,000.00` to `$1,990.00`.
</div>

### Scenario 2: Expected validation rejection in the UI

This scenario deliberately requests more than the source account can cover. It exercises the withdrawal-check validation before a debit or deposit is performed; it is **not** a compensation demonstration.

### Step 1: Submit the rejection test

Return to **Transfer** in the CloudBank UI and enter the following values:

<table class="task5-input-table">
<tbody>
<tr><th>From account</th><td><code>1234560001</code> — BankChicago</td></tr>
<tr><th>To account</th><td><code>1234560301</code> — BankMex</td></tr>
<tr><th>Amount</th><td><strong><code>999999.00</code></strong></td></tr>
<tr><th>Password</th><td><code>cb1</code></td></tr>
</tbody>
</table>

Click **Initiate Transfer**.

<div class="task5-result-note">
<strong>Accepted response:</strong> The UI displays another <strong>SAGA REQUEST ACCEPTED</strong> dialog. Save the request ID before closing the dialog. Wait about 10 seconds, then close it. The dashboard refreshes automatically; if the history has not updated yet, click **Refresh** once more.
</div>

### Step 2: Follow the validation result in the UI

Use **Copy request ID** if you want to keep the request reference, then close the confirmation dialog. Wait about 10 seconds and click **Refresh** if the latest status is not visible yet.

<div class="task5-result-note">
<strong>Expected dashboard result:</strong> The CloudBank Logs tab shows the transfer request as failed. The BankChicago account card remains unchanged from before the rejection test.
</div>

<details>
<summary><strong>🔎 (Optional) SQLcl verification of the UI-generated Sagas</strong></summary>

If you want database-level evidence after completing the UI scenarios, use the Saga ID shown in the CloudBank confirmation dialog. Run this check once for each scenario. It is optional and does not change the UI workflow above.

<table class="task5-scenario-table">
<thead>
<tr><th>UI scenario</th><th>Expected database result</th></tr>
</thead>
<tbody>
<tr class="task5-success-row">
<td><span class="task5-badge task5-success-badge">1 · Successful</span></td>
<td><code>Committed</code> Saga, completed transfer, BankChicago debit, and BankMex deposit.</td>
</tr>
<tr class="task5-rejection-row">
<td><span class="task5-badge task5-rejection-badge">2 · Rejected</span></td>
<td>Non-committed Saga, failed transfer, and no balance change.</td>
</tr>
</tbody>
</table>

On the Compute instance, start SQLcl and enter database passwords at the prompts. Do not place passwords in commands or shell history. Replace the values in angle brackets with the configured usernames and TNS alias.

<pre id="optionalSagaVerificationSql" class="interactive-command"><code>export TNS_ADMIN="$HOME/oracle-saga-cloudbank/adbsSetup/adb_wallet"
cd /tmp
SQLPATH=/nonexistent sql /nolog

CONNECT &lt;ADMIN_USERNAME&gt;@&lt;TNS_ALIAS&gt;
ACCEPT saga_id CHAR PROMPT 'Saga ID from CloudBank UI: '

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

CONNECT &lt;BANKA_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT account_number, balance_amount FROM banka WHERE account_number = 1234560001;

CONNECT &lt;BANKB_USERNAME&gt;@&lt;TNS_ALIAS&gt;
SELECT account_number, balance_amount FROM bankb WHERE account_number = 1234560301;
</code></pre>

<div class="button-center">

<button onclick="copyBlock('optionalSagaVerificationSql', this)" class="copy-btn-pastel">📋 Copy Optional SQL Verification</button>

</div>

Type `EXIT` when you finish, then continue to the next optional task if needed.

</details>

---

<details>
<summary><strong>🧹 (Optional) Task 6: Align Data from a Legacy Archive</strong></summary>

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

</details>

## Learn More

- [Oracle Database Saga Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)
- [Podman Documentation](https://docs.podman.io/)

<style>
/* LiveLabs can add generated "Table N: ..." captions; Task 5 tables should not show them. */
.task5-scenario-table caption, .task5-input-table caption { display: none !important; visibility: hidden !important; height: 0; padding: 0; margin: 0; }
.task5-scenario-table, .task5-input-table { width: 100%; border-collapse: separate; border-spacing: 0; margin: 18px 0 24px; border: 1px solid #d9e2ec; border-radius: 9px; overflow: hidden; box-shadow: 0 2px 7px rgba(33, 37, 41, .07); background: #fff; }
.task5-scenario-table th { background: #312D2A; color: #fff; padding: 12px 13px; text-align: left; font-weight: 600; }
.task5-scenario-table td { padding: 13px; vertical-align: top; border-top: 1px solid #e8edf2; color: #263746; }
.task5-scenario-table .task5-success-row td:first-child { border-left: 5px solid #2e7d32; }
.task5-scenario-table .task5-rejection-row td:first-child { border-left: 5px solid #d97706; }
.task5-scenario-table small { color: #64748b; }
.task5-badge { display: inline-block; padding: 5px 8px; border-radius: 999px; font-weight: 700; white-space: nowrap; }
.task5-success-badge { background: #e8f5e9; color: #216e39; }
.task5-rejection-badge { background: #fff4e5; color: #9a5b00; }
.task5-input-table th { width: 28%; background: #f5f7fa; color: #34495e; padding: 11px 13px; text-align: left; border-top: 1px solid #e8edf2; }
.task5-input-table td { padding: 11px 13px; border-top: 1px solid #e8edf2; color: #263746; }
.task5-input-table tr:first-child th, .task5-input-table tr:first-child td { border-top: 0; }
.task5-result-note { background: #f8fbff; border: 1px solid #d9e8f5; border-left: 4px solid #1976d2; padding: 12px 14px; margin: 16px 0; border-radius: 6px; color: #263746; }
@media (max-width: 700px) { .task5-scenario-table, .task5-input-table { display: block; overflow-x: auto; } .task5-scenario-table { white-space: nowrap; } .task5-scenario-table td, .task5-scenario-table th { padding: 10px; } }
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
function updateLabValues() {
  const ipInput = document.getElementById("computeInstanceIP");
  const instanceIP = getComputeIP() || "INSTANCE_IP";
  setTextForClass("instance-ip-value", instanceIP);
  if (ipInput && ipInput.value.trim()) sessionStorage.setItem("computePublicIP", ipInput.value.trim());
}
function loadPreviousLabValues() {
  const ipInput = document.getElementById("computeInstanceIP");
  const savedIP = sessionStorage.getItem("computePublicIP");
  if (ipInput && savedIP) ipInput.value = savedIP;
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
  let resolvedText = instanceIP ? text.replace(/\bINSTANCE_IP\b/g, instanceIP) : text;
  const originalText = button ? button.innerHTML : "";
  const done = function() { if (button) { button.innerHTML = "✅ Copied!"; setTimeout(function() { button.innerHTML = originalText; }, 2000); } };
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(resolvedText).then(done);
  else { const area = document.createElement("textarea"); area.value = resolvedText; document.body.appendChild(area); area.select(); document.execCommand("copy"); document.body.removeChild(area); done(); }
}
if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", loadPreviousLabValues); else loadPreviousLabValues();
</script>

## Acknowledgements

- **Contributors** — Amit Ketkar, Pavas Navaney, Vinay Pandhariwal, Luis Cruz, Sebastian Gerritsen
