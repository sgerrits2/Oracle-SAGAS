# Lab 5: Oracle Sagas in Action — The CloudBank Application

## **Introduction**

In this lab, you use the **CloudBank application** to observe Oracle Sagas. This updated procedure reflects the environment that was actually validated in OCI Cloud Shell.

You will:

- Build the CloudBank Java and Flask runtime image

- Initialize the ADB objects only once

- Validate the stack locally in Cloud Shell

- Deploy and open the public UI only after a Compute instance with enough memory is available

</br>

<details open>

<summary><mark>Important environment status</mark></summary>

- **Cloud Shell preparation:** validated: source, ADB wallet, .env, Podman Compose, Java builds, runtime image, and ADB setup.

- **Current Compute instance:** not suitable for the complete stack. It has about 1 GB RAM; starting Bank A, Bank B, orchestrator, Zipkin, Flask, and Swagger exhausts memory and SSH becomes unavailable.

- **Public UI and Zipkin:** pending until an instance with at least 4 GB RAM (6 GB recommended) is available and responds locally and publicly.

- Do not put passwords, wallet files, or .env values in terminal history, screenshots, this document, or a repository.

</details>

*Estimated Time: 30–45 minutes, excluding Compute capacity wait time*

---

### Objectives

In this lab, you will:

- **Prepare Cloud Shell** with Podman Compose and the missing Dockerfiles

- **Validate the application image** before deployment

- **Initialize ADB once** and preserve the resulting objects

- **Check Compute resources** before starting services

- **Run the Saga scenarios** after the UI and monitoring endpoints are available

---

### Prerequisites

- Cloud Shell project at $HOME/cloudbank-setup/oracle-saga-cloudbank

- ADB wallet at adbsSetup/adb_wallet and an already configured .env

- SSH key at $HOME/.ssh/cloudbank_key for Compute deployment

- Compute with Podman, Podman Compose, and at least 4 GB RAM

- Stateful ingress rules for TCP 22, 3000, 8080, 8081, 8082, 8083, and 9411

---

## Task 1: Prepare and Validate Cloud Shell

---

### Step 1: Verify the project and install Podman Compose

Run this block in **Cloud Shell**. It checks the project without printing secrets and installs the Compose wrapper if necessary.

<pre id="prepareCloudShell" class="interactive-command"><code>set -e

PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"

if [ ! -d "$PROJECT_DIR" ]; then
echo "ERROR: $PROJECT_DIR was not found."
exit 1
fi

cd "$PROJECT_DIR"

python3 -m pip install --user podman-compose
export PATH="$HOME/.local/bin:$PATH"

echo "--- tools ---"
podman --version
podman-compose --version

echo "--- source modules ---"
find CloudBank -maxdepth 3 -name pom.xml -type f -print
find . -maxdepth 4 -type f ( -name app.py -o -name requirements.txt ) -print

echo "--- wallet and configuration ---"
test -d adbsSetup/adb_wallet && echo "OK: wallet present" || echo "ERROR: wallet missing"
test -f .env && echo "OK: .env present" || echo "ERROR: .env missing"
sed -n 's/^([A-Za-z_][A-Za-z0-9_])=./\1=<configured>/p' .env | sort
find adbsSetup/adb_wallet -maxdepth 1 -type f -printf '%f\n' | sort
</code></pre>

<div class="button-center">

<button onclick="copyBlock('prepareCloudShell', this)" class="copy-btn-pastel">📋 Copy Cloud Shell Preparation</button>

</div>

**Expected Result:**

- podman-compose is available from $HOME/.local/bin.

- The three Maven modules, the Flask application, wallet, and .env are present.

- Only variable names are printed; no .env values are exposed.

> **Note:** In a new Cloud Shell session, run export PATH="$HOME/.local/bin:$PATH" again or add it to your shell profile.

### Step 2: Create build files and apply compatibility fixes

The following block creates the validated multi-stage files and adds the Flask RESTX compatibility requirement.

<pre id="createBuildFiles" class="interactive-command"><code>set -e
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"

tee osagaJavaBuilder >/dev/null <<'EOF'
FROM docker.io/library/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY CloudBank ./CloudBank
RUN mvn -q -f /workspace/CloudBank/banka/pom.xml package -DskipTests && 
mvn -q -f /workspace/CloudBank/bankb/pom.xml package -DskipTests && 
mvn -q -f /workspace/CloudBank/orchestrator/pom.xml package -DskipTests
EOF

tee osagaJavaRuntime >/dev/null <<'EOF'
FROM docker.io/library/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY CloudBank ./CloudBank
RUN mvn -q -f /workspace/CloudBank/banka/pom.xml package -DskipTests && 
mvn -q -f /workspace/CloudBank/bankb/pom.xml package -DskipTests && 
mvn -q -f /workspace/CloudBank/orchestrator/pom.xml package -DskipTests

FROM docker.io/library/eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /opt/app
RUN apt-get update && 
apt-get install -y --no-install-recommends python3 python3-pip && 
rm -rf /var/lib/apt/lists/*
COPY --from=builder /workspace/CloudBank/banka/target/banka-1.jar /opt/app/bankA.jar
COPY --from=builder /workspace/CloudBank/bankb/target/bankb-1.jar /opt/app/bankB.jar
COPY --from=builder /workspace/CloudBank/orchestrator/target/orchestrator-1.jar /opt/app/orchestrator.jar
COPY CloudBank/Website /opt/app/flask_ui
RUN pip3 install --no-cache-dir -r /opt/app/flask_ui/requirements.txt
EXPOSE 8081 8082 8083 8084 8085
EOF

grep -qxF 'Werkzeug>=2.3.7,<3.0' CloudBank/Website/requirements.txt || 
printf '\nWerkzeug>=2.3.7,<3.0\n' >> CloudBank/Website/requirements.txt
</code></pre>

<div class="button-center">

<button onclick="copyBlock('createBuildFiles', this)" class="copy-btn-pastel">📋 Copy Build File Fix</button>

</div>

The runtime builds its own builder stage. This avoids the failure caused by COPY --from=localhost/osaga-builder:1.0, which Buildah attempts to resolve as a remote registry image.

### Step 3: Correct the Compose YAML and validate the image

Back up osagaAdbsSetup.yaml. Confirm these required corrections before building:

- osagas-cleanup-adbs is indented under services:.

- SQLcl scripts use $${TNS_ALIAS}, not $${TNS_ALIAS_CONTAINER}.

- Swagger image is docker.io/swaggerapi/swagger-ui:v5.20.7.

- Published ports are Flask 3000:8084, Swagger 8080:8080, and Zipkin 9411:9411.

<pre id="validateBuild" class="interactive-command"><code>set -e
cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"

cp osagaAdbsSetup.yaml osagaAdbsSetup.yaml.bak
sed -i 
-e 's/"8084:8084"/"3000:8084"/' 
-e 's/"8085:8080"/"8080:8080"/' 
osagaAdbsSetup.yaml

grep -nE 'docker.io/swaggerapi/swagger-ui|osagas-cleanup-adbs|TNS_ALIAS}' osagaAdbsSetup.yaml
grep -nE '"(3000:8084|8080:8080|9411:9411)"' osagaAdbsSetup.yaml

podman build --pull=always -f osagaJavaBuilder -t osaga-builder:1.0 --target builder .
podman build -f osagaJavaRuntime -t osaga-runtime:1.0 --target runtime .
podman run --rm --entrypoint /bin/sh osaga-runtime:1.0 -c 
'ls -l /opt/app/bankA.jar /opt/app/bankB.jar /opt/app/orchestrator.jar /opt/app/flask_ui/app.py'
</code></pre>

<div class="button-center">

<button onclick="copyBlock('validateBuild', this)" class="copy-btn-pastel">📋 Copy Build and Validation</button>

</div>

**Expected Result:** The build succeeds and the image contains bankA.jar, bankB.jar, orchestrator.jar, and flask_ui/app.py.

---

## Task 2: Initialize ADB and Test Locally

---

### Step 1: Run ADB setup only if it has never completed

The actual profile is adbssagasetup, not adbsSetup. If osagas-setup-adbs is Exited (0), database setup is complete. Do not rerun it because the schemas and objects already exist.

<pre id="verifyAdbSetup" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"
export PATH="$HOME/.local/bin:$PATH"

podman ps -a --format 'table {{.Names}}\t{{.Status}}'
podman logs --tail 30 osagas-setup-adbs

Run the next command only when osagas-setup-adbs has never completed:

COMPOSE_PROFILES=adbssagasetup podman-compose -f osagaAdbsSetup.yaml up osagas-setup-adbs

</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyAdbSetup', this)" class="copy-btn-pastel">📋 Copy ADB Setup Verification</button>

</div>

### Step 2: Temporary local stack test

Cloud Shell can validate the containers locally. It does not provide the public ingress required for the browser UI; do not treat this as the public demo deployment.

<pre id="localStackTest" class="interactive-command"><code>cd "$HOME/cloudbank-setup/oracle-saga-cloudbank"

podman start zipkin bankA bankB orchestrator flask swagger-ui
sleep 20
podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

Older containers retain their original host ports until recreated.

curl -sSI http://127.0.0.1:8084 | head -n 1 || true
curl -sSI http://127.0.0.1:8085 | head -n 1 || true
podman logs --tail 50 flask
podman logs --tail 50 zipkin
</code></pre>

<div class="button-center">

<button onclick="copyBlock('localStackTest', this)" class="copy-btn-pastel">📋 Copy Local Stack Test</button>

</div>

Expected local result: Flask and Swagger return HTTP 200. Zipkin may need separate log review if it reports unhealthy or a reset.

---

## Task 3: Compute Deployment and Public UI (Pending)

---

The current 1 GB Compute instance must not run the complete stack. Its disk space and OCI ingress rules are not the blocker; memory is. A VM.Standard.E2.1.Micro cannot be resized. Use a VM with at least 4 GB RAM, preferably 6 GB.

### Step 1: Check Compute resources before deployment

<div class="input-section">

<strong>Compute Instance Public IP:</strong>

<input type="text" id="computeInstanceIP" placeholder="Enter compute public IP (e.g., 129.146.123.45)" class="input-field" oninput="updateLabValues()"><br/>

<div style="font-size: 0.9em; color: #666; margin-top: 5px;">

💡 <em>Use an instance with at least 4 GB RAM. In Toronto, Always Free Ampere capacity may temporarily be unavailable (OUT_OF_HOST_CAPACITY).</em>

</div>

</div>

<pre id="checkCompute" class="interactive-command"><code>ssh -o ConnectTimeout=10 -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' <<'REMOTE'
free -h
df -h /
podman --version
podman-compose --version
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('checkCompute', this)" class="copy-btn-pastel">📋 Copy Compute Preflight</button>

</div>

Stop here if total memory is near 954Mi or below 4 GB. Wait for capacity, use a different eligible AD/region, or ask the instructor for an appropriately sized lab instance.

### Step 2: Deploy only to a suitable VM

After the preflight passes, package the corrected source and deploy it. ADB setup is intentionally not run on the VM again.

<pre id="deployCloudBank" class="interactive-command"><code>set -e
PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"
ARCHIVE="$HOME/oracle-saga-cloudbank-deploy.tar.gz"
COMPUTE_IP="<span class="instance-ip-value">INSTANCE_IP</span>"
SSH_KEY="$HOME/.ssh/cloudbank_key"

cd "$PROJECT_DIR"
rm -f "$ARCHIVE"
tar -czf "$ARCHIVE" -C "$HOME/cloudbank-setup" oracle-saga-cloudbank
scp -i "$SSH_KEY" "$ARCHIVE" "ubuntu@$COMPUTE_IP:~/"

ssh -i "$SSH_KEY" "ubuntu@$COMPUTE_IP" 'bash -s' <<'REMOTE'
set -e
cd "$HOME"
rm -rf oracle-saga-cloudbank
tar -xzf oracle-saga-cloudbank-deploy.tar.gz
cd oracle-saga-cloudbank
chmod 600 .env
podman build -f osagaJavaRuntime -t osaga-runtime:1.0 --target runtime .
COMPOSE_PROFILES=adbs podman-compose -f osagaAdbsSetup.yaml up -d
sleep 20
podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
sudo loginctl enable-linger ubuntu
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('deployCloudBank', this)" class="copy-btn-pastel">📋 Copy Deployment Script</button>

</div>

### Step 3: Verify service endpoints

<pre id="verifyEndpoints" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' <<'REMOTE'
for port in 3000 8080 9411; do
printf "localhost:%s -> " "$port"
curl -4 -sS -o /dev/null -w '%{http_code}\n' "http://127.0.0.1:$port" || true
done
podman ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyEndpoints', this)" class="copy-btn-pastel">📋 Copy Endpoint Verification</button>

</div>

Expected result: ports 3000 and 8080 return 200. If Zipkin is unhealthy, inspect podman logs --tail 100 zipkin.

<style>

.input-section { background-color: #f9f9f9; padding: 15px; margin: 12px 0; border-radius: 8px; border: 1px solid #ddd; }
.input-field { width: 300px; max-width: 100%; padding: 8px 10px; font-size: 14px; border: 1px solid #ccc; border-radius: 4px; margin: 6px 0; box-sizing: border-box; }
.interactive-command { position: relative; background-color: #f5f5f5; border: 1px solid #ddd; padding: 12px 14px; border-radius: 6px; margin: 12px 0; font-family: "Courier New", monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.button-center { text-align: left; margin: 15px 0; }
.copy-btn-pastel { background-color: #90EE90; color: #2E7D32; padding: 10px 16px; border: none; border-radius: 12px; cursor: pointer; font-size: 14px; margin: 10px 0; font-weight: 500; }
.copy-btn-pastel { background-color: #7FDD7F; transform: translateY(-1px); }
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

## Task 4: Open CloudBank and Saga Monitoring (Pending)

---

Do not open these endpoints until Task 3 succeeds locally on a suitable VM. After that, use the same Compute public IP.

### Step 1: Open CloudBank

<pre class="interactive-command">

<code>🌐 CloudBank URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:3000</code>

</pre>

Use this interface to execute the success, compensation, and recovery scenarios.

### Step 2: Open Zipkin

<pre class="interactive-command">

<code>🔍 Zipkin URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:9411</code>

</pre>

Optional Swagger API documentation: http://INSTANCE_IP:8080.

If the VM responds on localhost but not publicly, check the VNIC public IP, Internet Gateway/route table, Security List or NSG, and the OS firewall. Do not add duplicate ingress rules if a stateful TCP rule already exists.

## Task 5: Explore Oracle Sagas

---

Run these scenarios only after CloudBank, the Java services, ADB, and Zipkin are available.

### Scenario 1: Successful Distributed Saga

Select **Inter-Bank Transfer**.

Source account: BANKA-ACC-001; target account: BANKB-ACC-001; amount: 500.00.

Execute the transfer.

Expected outcome: debit, credit, participant confirmation, and a Saga with final status Committed or an equivalent completed state.

### Scenario 2: Saga Compensation

Check the balance of BANKA-ACC-001.

Start an inter-bank transfer above the available balance.

Execute the transfer.

Expected outcome: business failure, compensation, and no partial transfer remaining.

### Scenario 3: Participant Failure and Recovery

Start an inter-bank transfer.

Stop a participant according to the course instructions.

Restore it and observe recovery or compensation.

Use SQLcl to compare the latest database state with the Zipkin trace. Enter the ADMIN password interactively; do not place it in a command or evidence.

<pre id="verifySagaState" class="interactive-command"><code>CONNECT ADMIN@oraclesagademo_medium

SELECT saga_id, status, coordinator, start_time, saga_source
FROM (
SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source
FROM DBA_SAGAS WHERE is_initiator = 'YES'
UNION ALL
SELECT RAWTOHEX(id), status, coordinator, start_time, 'HISTORY'
FROM DBA_HIST_SAGAS WHERE is_initiator = 'YES'
)
ORDER BY start_time DESC
FETCH FIRST 10 ROWS ONLY;
</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifySagaState', this)" class="copy-btn-pastel">📋 Copy Saga Verification</button>

</div>

### Known L5 corrections included in this version

- Missing Java Dockerfiles are created.

- The runtime uses a self-contained multi-stage build.

- The actual profiles are adbssagasetup and adbs.

- The SQLcl alias environment variable and cleanup-service indentation are corrected.

- Public ports match this lab: UI 3000, Swagger 8080, Zipkin 9411.

- ADB setup is explicitly a one-time action.

- Public deployment is blocked until enough Compute RAM is available.

