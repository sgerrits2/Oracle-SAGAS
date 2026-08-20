# Lab 5: Oracle Sagas in Action — The CloudBank Application

## **Introduction**

In this lab, you will use the **CloudBank application** to focus on the **Oracle Saga Pattern in action**. The demo environment is automated so that most of your time is spent observing Saga behavior instead of configuring infrastructure.

You will:

- Execute a successful distributed Saga between BankChicago and BankMex

- Trigger a compensation flow and verify the resulting Saga state

- Simulate a participant failure and observe recovery

- Correlate database Saga state with distributed traces in Zipkin

The setup steps are intentionally minimized and only prepare the environment required for the Saga scenarios.

</br>

<details open>

<summary><mark>CloudBank Architecture</mark></summary>

**CloudBank** uses the following components for the Saga scenarios:

- **CloudBank UI:** Starts the money-transfer requests used in this lab

- **Saga Participants:** BankChicago, BankMex, and CloudBank

- **Saga Coordinator:** CloudBankCoordinator in schema orchestratorchicago

- **Saga Broker:** TEST in schema brokerchicago

- **Database Layer:** Oracle Database schemas bankchicago and bankmex

- **Tracing:** Zipkin for following the distributed request path

**Saga Focus:**

- Successful distributed execution

- Compensation after a business failure

- Participant failure and recovery

- Final consistency and Saga lifecycle visibility

</details>

*Estimated Time: 20–30 minutes*

---

### Objectives

In this lab, you will:

- **Launch CloudBank** using one automated setup script

- **Execute a successful distributed Saga** between BankChicago and BankMex

- **Trigger compensation** and inspect the resulting Saga state

- **Simulate participant failure** and observe recovery

- **Use database views and Zipkin** to correlate Saga state with distributed execution

---

### Prerequisites

- Completion of **Lab 3 (Saga Core Setup)** and **Lab 4 (Saga Client)**

- Cloud Shell with **Oracle ADB wallet** configured and **CloudBank codebase** ready

- Compute instance with **Podman and Podman Compose** installed

- Security rules allowing ports **3000 (UI)**, **8080 (API)**, and **9411 (Zipkin)**

---

## Task 1: Launch CloudBank

---

This task performs only the environment work required for the Saga scenarios. The ADB TNS alias and SSH private key path are fixed for this LiveLab; the only value you need to provide is the Compute instance public IP.

### Step 1: Enter Your Compute Instance IP

<div class="input-section">

<strong>Compute Instance Public IP:</strong>

<input type="text" id="computeInstanceIP" placeholder="Enter compute public IP (e.g., 129.146.123.45)" class="input-field" oninput="updateLabValues()"><br/>

<div style="font-size: 0.9em; color: #666; margin-top: 5px;">

💡 <em>This lab always uses TNS alias oraclesagademo_medium and SSH private key $HOME/.ssh/cloudbank_key.</em>

</div>

</div>

**How to obtain the Compute Instance Public IP:**

In the OCI Console, open the navigation menu and select **Compute → Instances**.

Select the Compute instance used for the CloudBank demo.

In **Instance information**, locate the primary VNIC / instance access information.

Copy the **Public IP address** and paste it in the field above.

> **Note:** Use the **Public IP address**, not the private IP address.

<div id="configWarning" style="display:none; background:#fff4e5; border:1px solid #f0ad4e; padding:12px 14px; border-radius:8px; margin:12px 0; color:#7a4b00;">

<strong>Required environment information is missing.</strong> Enter the Compute Instance Public IP before copying the launch script.

</div>

<br>

### Step 2: Deploy and Start CloudBank

Run the following complete block directly in **Cloud Shell**. The CloudBank project is located at $HOME/cloudbank-setup/oracle-saga-cloudbank, so the script does not depend on your current working directory.

<pre id="deployCloudBank" class="interactive-command"><code>set -e

PROJECT_DIR="$HOME/cloudbank-setup/oracle-saga-cloudbank"

SSH_KEY="$HOME/.ssh/cloudbank_key"

COMPUTE_IP="<span class="instance-ip-value">INSTANCE_IP</span>"

TNS_ALIAS="oraclesagademo_medium"

if [ ! -d "$PROJECT_DIR" ]; then

echo "ERROR: $PROJECT_DIR was not found."

exit 1

fi

if [ ! -f "$SSH_KEY" ]; then

echo "ERROR: SSH private key was not found: $SSH_KEY"

exit 1

fi

cd "$PROJECT_DIR"

cat > .env <<EOF

Database Connection Settings

TNS_ALIAS_CONTAINER=$TNS_ALIAS

ADBS_USER=ADMIN

ADBS_PASSWORD=Welcome_123#

Bank Service Credentials

BANKA_USER=bankchicago

BANKA_PASSWORD=Welcome_123#

BANKB_USER=bankmex

BANKB_PASSWORD=Welcome_123#

Orchestrator and Broker

ORCHESTRATOR_USER=orchestratorchicago

ORCHESTRATOR_PASSWORD=Welcome_123#

BROKER_USER=brokerchicago

BROKER_PASSWORD=Welcome_123#

Container and Monitoring Configuration

TNS_ADMIN_CONTAINER=/opt/adb_wallet

ENABLE_ZIPKIN=true

ZIPKIN_URL=http://zipkin:9411/api/v2/spans

EOF

cd "$HOME"

tar -czf oracle-saga-cloudbank.tar.gz oracle-saga-cloudbank*/*

scp -i "$SSH_KEY" oracle-saga-cloudbank.tar.gz "ubuntu@$COMPUTE_IP:~/"

ssh -i "$SSH_KEY" "ubuntu@$COMPUTE_IP" 'bash -s*'* <<'REMOTE'

set -e

cd "$HOME"

rm -rf oracle-saga-cloudbank

tar -xzf oracle-saga-cloudbank.tar.gz

cd oracle-saga-cloudbank

command -v podman >/dev/null || { echo "ERROR: podman was not found."; exit 1; }

command -v podman-compose >/dev/null || { echo "ERROR: podman-compose was not found."; exit 1; }

echo "=== Starting database setup ==="

podman-compose --profile adbsSetup up -d

SETUP_IDS="$(podman-compose --profile adbsSetup ps -q)"

if [ -z "$SETUP_IDS" ]; then

echo "ERROR: No database setup containers were created."

podman-compose --profile adbsSetup ps

exit 1

fi

COMPLETED=false

for attempt in $(seq 1 60); do

RUNNING=false

for id in $SETUP_IDS; do

if [ "$(podman inspect -f '{{.State.Running}}' "$id")" = "true" ]; then

RUNNING=true

break

fi

done

if [ "$RUNNING" = "false" ]; then

COMPLETED=true

break

fi

sleep 5

done

if [ "$COMPLETED" != "true" ]; then

echo "ERROR: Database setup did not finish within the expected time."

for id in $SETUP_IDS; do

podman logs --tail 50 "$id" 2>&1 || true

done

exit 1

fi

for id in $SETUP_IDS; do

EXIT_CODE="$(podman inspect -f '{{.State.ExitCode}}' "$id")"

if [ "$EXIT_CODE" != "0" ]; then

echo "ERROR: Database setup failed in container $id."

podman logs --tail 50 "$id" 2>&1 || true

exit 1

fi

done

podman-compose --profile adbsSetup down

echo "=== Starting CloudBank services ==="

podman-compose --profile adbs up -d

sleep 10

echo "=== CloudBank Services ==="

podman ps

echo "CloudBank is ready for the Saga scenarios."

REMOTE</code></pre>

<div class="button-center">

<button onclick="copyBlock('deployCloudBank', this)" class="copy-btn-pastel">📋 Copy Launch Script</button>

</div>

**Expected Result:**

- CloudBank is deployed to the compute instance

- Database setup completes successfully

- CloudBank services are running and ready for the Saga scenarios

<style>

.input-section {

background-color: #f9f9f9;

padding: 15px;

margin: 12px 0;

border-radius: 8px;

border: 1px solid #ddd;

}

.input-field {

width: 300px;

max-width: 100%;

padding: 8px 10px;

font-size: 14px;

border: 1px solid #ccc;

border-radius: 4px;

margin: 6px 0;

box-sizing: border-box;

}

.interactive-command {

position: relative;

background-color: #f5f5f5;

border: 1px solid #ddd;

padding: 12px 14px;

border-radius: 6px;

margin: 12px 0;

font-family: "Courier New", monospace;

white-space: pre-wrap;

overflow-wrap: anywhere;

}

.button-center {

text-align: left;

margin: 15px 0;

}

.copy-btn-pastel {

background-color: #90EE90;

color: #2E7D32;

padding: 10px 16px;

border: none;

border-radius: 12px;

cursor: pointer;

font-size: 14px;

margin: 10px 0;

font-weight: 500;

transition: all 0.2s ease;

}

.copy-btn-pastel:hover {

background-color: #7FDD7F;

transform: translateY(-1px);

box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);

}

.save-btn, .save-btn-small,

.delete-btn, .delete-btn-small,

.clear-btn, .clear-btn-small {

padding: 8px 12px;

border: none;

border-radius: 12px;

cursor: pointer;

font-size: 12px;

margin: 2px;

font-weight: 500;

}

.save-btn, .save-btn-small {

background-color: #90EE90;

color: #2E7D32;

}

.delete-btn, .delete-btn-small {

background-color: #f44336;

color: white;

}

.clear-btn, .clear-btn-small {

background-color: #008CBA;

color: white;

}

@media (max-width: 768px) {

.input-section {

grid-template-columns: 1fr;

}

.input-field {

width: 100%;

max-width: 300px;

}

}

</style>

<script>

function getFirstSessionValue(keys, fallback = '') {

for (const key of keys) {

const value = sessionStorage.getItem(key);

if (value && value.trim() !== '') return value.trim();

}

return fallback;

}

function setTextForClass(className, value) {

document.querySelectorAll('.' + className).forEach(function(element) {

element.textContent = value;

});

}

function updateLabValues() {

const instanceIPElement = document.getElementById('computeInstanceIP');

const instanceIP = (instanceIPElement ? instanceIPElement.value : '').trim() || 'INSTANCE_IP';

document.querySelectorAll('.instance-ip-value').forEach(function(element) {

element.textContent = instanceIP;

});

setTextForClass('instance-ip-value', instanceIP);

if (instanceIPElement && instanceIPElement.value.trim()) sessionStorage.setItem('computePublicIP', instanceIPElement.value.trim());

const warning = document.getElementById('configWarning');

const missing = !instanceIPElement || !instanceIPElement.value.trim();

if (warning) warning.style.display = missing ? 'block' : 'none';

}

function loadPreviousLabValues() {

try {

const savedIP = getFirstSessionValue(['computePublicIP', 'computeInstanceIP']);

const instanceIPElement = document.getElementById('computeInstanceIP');

if (savedIP && instanceIPElement) instanceIPElement.value = savedIP;

updateLabValues();

} catch (error) {

console.error('Error loading environment values:', error);

}

}

function fallbackCopyTextToClipboard(text, callback) {

const textArea = document.createElement('textarea');

textArea.value = text;

textArea.style.position = 'fixed';

textArea.style.left = '-999999px';

textArea.style.top = '-999999px';

document.body.appendChild(textArea);

textArea.focus();

textArea.select();

try {

document.execCommand('copy');

} catch (error) {

console.error('Fallback copy failed:', error);

}

document.body.removeChild(textArea);

if (callback) callback();

}

function copyBlock(elementId, button) {

const element = document.getElementById(elementId);

if (!element) return;

const text = element.innerText;

if (elementId === 'deployCloudBank') {

const instanceIPElement = document.getElementById('computeInstanceIP');

const instanceIP = instanceIPElement ? instanceIPElement.value.trim() : '';

if (!instanceIP) {

alert('Enter the Compute Instance Public IP before copying the deployment script.');

return;

}

}

const originalText = button ? button.innerHTML : '';

const done = function() {

if (button) {

button.innerHTML = '✅ Copied!';

button.style.backgroundColor = '#A8E6A8';

setTimeout(function() {

    button.innerHTML = originalText;

    button.style.backgroundColor = '#90EE90';

}, 2000);

}

};

if (navigator.clipboard && navigator.clipboard.writeText) {

navigator.clipboard.writeText(text).then(done).catch(function() {

fallbackCopyTextToClipboard(text, done);

});

} else {

fallbackCopyTextToClipboard(text, done);

}

}

function getComputeIP() {

const element = document.getElementById('computeInstanceIP');

return element ? element.value.trim() : '';

}

function openURL(type) {

const instanceIP = getComputeIP();

if (!instanceIP) {

alert('Please enter your compute instance IP address first!');

return;

}

const urls = {

frontend: `http://${instanceIP}:3000`,

swagger: `http://${instanceIP}:8080/swagger-ui.html`,

zipkin: `http://${instanceIP}:9411`

};

if (urls[type]) window.open(urls[type], '_blank');

}

function copyURL(type, button) {

const instanceIP = getComputeIP();

if (!instanceIP) {

alert('Please enter your compute instance IP address first!');

return;

}

const urls = {

frontend: `http://${instanceIP}:3000`,

swagger: `http://${instanceIP}:8080/swagger-ui.html`,

zipkin: `http://${instanceIP}:9411`

};

const text = urls[type];

if (!text) return;

const originalText = button ? button.innerHTML : '';

const done = function() {

if (button) {

button.innerHTML = '✅ Copied!';

button.style.backgroundColor = '#A8E6A8';

setTimeout(function() {

    button.innerHTML = originalText;

    button.style.backgroundColor = '#90EE90';

}, 2000);

}

};

if (navigator.clipboard && navigator.clipboard.writeText) {

navigator.clipboard.writeText(text).then(done).catch(function() {

fallbackCopyTextToClipboard(text, done);

});

} else {

fallbackCopyTextToClipboard(text, done);

}

}

if (document.readyState === 'loading') {

document.addEventListener('DOMContentLoaded', loadPreviousLabValues);

} else {

loadPreviousLabValues();

}

window.addEventListener('load', loadPreviousLabValues);

</script>

## Task 2: Open CloudBank and Saga Monitoring

---

Keep the CloudBank UI and Zipkin open while running the Saga scenarios. The same compute instance IP entered in Task 1 is reused automatically.

### Step 1: Open CloudBank

Use the CloudBank UI to initiate the distributed transactions used in Task 3.

<pre class="interactive-command">

<code>🌐 CloudBank URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:3000</code>

</pre>

<div class="button-center">

<button onclick="openURL('frontend')" class="copy-btn-pastel">🌐 Open CloudBank UI</button>

<button onclick="copyURL('frontend', this)" class="copy-btn-pastel">📋 Copy CloudBank URL</button>

</div>

Use this interface to execute the success, compensation, and recovery scenarios.

**Expected Interface:**

![CloudBank UI](./images/lab5-frontend.png "CloudBank Flask frontend interface")

---

Optional: Swagger API documentation remains available on port 8080 at /swagger-ui.html if you want to inspect the REST endpoints.

### Step 2: Open Zipkin

Use Zipkin to correlate the Saga database state with calls between CloudBank, BankChicago, BankMex, and the coordinator.

<pre class="interactive-command">

<code>🔍 Zipkin URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:9411</code>

</pre>

<div class="button-center">

<button onclick="openURL('zipkin')" class="copy-btn-pastel">🔍 Open Zipkin Monitoring</button>

<button onclick="copyURL('zipkin', this)" class="copy-btn-pastel">📋 Copy Zipkin URL</button>

</div>

For each scenario, compare the latest trace with the Saga state returned by Oracle Database.

**Expected Interface:**

![Zipkin Tracing](./images/lab5-zipkin.png "Zipkin distributed tracing interface")

## Task 3: Explore Oracle Sagas

---

Run the following three scenarios to compare successful execution, compensation, and participant recovery. Keep CloudBank and Zipkin open. For SQL verification, open SQLcl once in **Cloud Shell** with sql /nolog and keep the session open.

### Scenario 1: Successful Distributed Saga

Execute an inter-bank transfer so that the Saga spans BankChicago and BankMex.

**Via CloudBank UI:**

1. Select **"Inter-Bank Transfer"**

2. Source account: BANKA-ACC-001

3. Target account: BANKB-ACC-001

4. Amount: 500.00

5. Execute the transfer

**Expected Saga Flow:**

1. **Begin Saga** - CloudBankCoordinator starts the distributed Saga

2. **BankChicago Debit** - Source participant processes the debit

3. **BankMex Credit** - Destination participant processes the credit

4. **Confirm** - Participants confirm successful work

5. **Commit Saga** - The Saga reaches a successful final outcome

**Verify the Saga:**

<pre id="verifySuccessfulSaga" class="interactive-command"><code>CONNECT ADMIN/Welcome_123#@'oraclesagademo_medium'

SELECT saga_id, status, coordinator, start_time, saga_source

FROM (

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source

FROM DBA_SAGAS

WHERE is_initiator = 'YES'

UNION ALL

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'HISTORY' AS saga_source

FROM DBA_HIST_SAGAS

WHERE is_initiator = 'YES'

)

ORDER BY start_time DESC

FETCH FIRST 1 ROW ONLY;</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifySuccessfulSaga', this)" class="copy-btn-pastel">📋 Copy Saga Verification</button>

</div>

**What to Observe:**

- The latest completed Saga appears in DBA_HIST_SAGAS with a successful final status such as Committed

- Zipkin shows the distributed request path between the participating services

- The database view and trace describe the same transaction lifecycle

### Scenario 2: Saga Compensation

Trigger a business failure by requesting more than the available source balance.

**Via CloudBank UI:**

1. Review the balance of BANKA-ACC-001

2. Start an **Inter-Bank Transfer** to BANKB-ACC-001

3. Enter an amount greater than the available balance

4. Execute the transfer

**Expected Saga Flow:**

1. **Begin Saga** - The distributed transaction starts

2. **BankChicago Validation** - The source participant checks available funds

3. **Failure** - The requested amount cannot be processed

4. **Compensation** - The Saga follows the failure/compensation path

5. **Consistency** - No partial successful transfer remains

**Verify Saga and Business State:**

<pre id="verifyCompensation" class="interactive-command"><code>CONNECT ADMIN/Welcome_123#@'oraclesagademo_medium'

SELECT saga_id, status, coordinator, start_time, saga_source

FROM (

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source

FROM DBA_SAGAS

WHERE is_initiator = 'YES'

UNION ALL

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'HISTORY' AS saga_source

FROM DBA_HIST_SAGAS

WHERE is_initiator = 'YES'

)

ORDER BY start_time DESC

FETCH FIRST 1 ROW ONLY;

CONNECT bankchicago/Welcome_123#@'oraclesagademo_medium'

SELECT account_id, balance

FROM accounts

WHERE account_id = 'BANKA-ACC-001';</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyCompensation', this)" class="copy-btn-pastel">📋 Copy Compensation Verification</button>

</div>

**What to Observe:**

- ADMIN is used to inspect Saga metadata

- bankchicago is used to inspect the participant's business data

- Zipkin shows where the failure occurred and how the distributed flow differs from Scenario 1

### Scenario 3: Participant Failure and Recovery

Simulate BankMex becoming unavailable while an inter-bank Saga is being processed.

### Step 1: Start the Saga and Stop BankMex

Run the following block from **Cloud Shell**. The technical container name still uses bankb, while the registered Saga participant is BankMex.

<pre id="simulateCrash" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' <<'REMOTE'

set -e

BANKB_CONTAINER="$(podman ps --format '{{.Names}}' | grep -i bankb | head -n 1)"

if [ -z "$BANKB_CONTAINER" ]; then

echo "ERROR: BankMex container was not found."

exit 1

fi

curl -sS -X POST "http://localhost:8080/transfer/inter-bank" -H "Content-Type: application/json" -d '{"sourceAccount":"BANKA-ACC-001","targetAccount":"BANKB-ACC-001","amount":1.00,"currency":"USD"}' > /tmp/lab5-crash-transfer.out 2>&1 &

TRANSFER_PID=$!

sleep 1

podman stop "$BANKB_CONTAINER"

wait "$TRANSFER_PID" || true

cat /tmp/lab5-crash-transfer.out || true

podman ps -a --filter "name=$BANKB_CONTAINER"

REMOTE</code></pre>

<div class="button-center">

<button onclick="copyBlock('simulateCrash', this)" class="copy-btn-pastel">📋 Copy Failure Simulation</button>

</div>

### Step 2: Observe the Incomplete Saga

Use the same SQLcl session from the previous scenarios:

<pre id="verifyCrashState" class="interactive-command"><code>CONNECT ADMIN/Welcome_123#@'oraclesagademo_medium'

SELECT RAWTOHEX(id) AS saga_id,

coordinator,

participant,

status,

start_time

FROM DBA_INCOMPLETE_SAGAS

ORDER BY start_time DESC

FETCH FIRST 1 ROW ONLY;</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyCrashState', this)" class="copy-btn-pastel">📋 Copy Incomplete Saga Query</button>

</div>

This view shows the participant and failure state for an incomplete Saga while recovery is still required.

### Step 3: Restart BankMex

Run the following block from **Cloud Shell**:

<pre id="restartBankMex" class="interactive-command"><code>ssh -i "$HOME/.ssh/cloudbank_key" ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' <<'REMOTE'

set -e

BANKB_CONTAINER="$(podman ps -a --format '{{.Names}}' | grep -i bankb | head -n 1)"

if [ -z "$BANKB_CONTAINER" ]; then

echo "ERROR: BankMex container was not found."

exit 1

fi

podman start "$BANKB_CONTAINER"

sleep 5

podman ps --filter "name=$BANKB_CONTAINER"

REMOTE</code></pre>

<div class="button-center">

<button onclick="copyBlock('restartBankMex', this)" class="copy-btn-pastel">📋 Copy Restart Script</button>

</div>

**Verify the Final Saga State:**

<pre id="verifyRecoveryFinal" class="interactive-command"><code>CONNECT ADMIN/Welcome_123#@'oraclesagademo_medium'

SELECT saga_id, status, coordinator, start_time, saga_source

FROM (

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'ACTIVE' AS saga_source

FROM DBA_SAGAS

WHERE is_initiator = 'YES'

UNION ALL

SELECT RAWTOHEX(id) AS saga_id, status, coordinator, start_time, 'HISTORY' AS saga_source

FROM DBA_HIST_SAGAS

WHERE is_initiator = 'YES'

)

ORDER BY start_time DESC

FETCH FIRST 1 ROW ONLY;</code></pre>

<div class="button-center">

<button onclick="copyBlock('verifyRecoveryFinal', this)" class="copy-btn-pastel">📋 Copy Final Saga Verification</button>

</div>

**What to Observe:**

- The Saga cannot complete normally while BankMex is unavailable

- The incomplete state is visible while the participant is down

- After BankMex returns, the workflow reaches a consistent final outcome

- Compare the recovery trace in Zipkin with the successful Saga from Scenario 1

---

✅ **Congratulations!** You have executed a successful distributed Saga, observed compensation, and validated participant failure and recovery with Oracle Sagas.

**Next Lab:** Continue to **Lab 6 — Extended Lab** to manually add a Saga participant to the topology created in the previous labs.

---

## Learn More

- [Oracle Sagas Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)

- [Oracle Database RESERVABLE Columns](https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/CREATE-TABLE.html)

- [Podman Documentation](https://podman.io/)

- [Distributed Tracing with Zipkin](https://zipkin.io/)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney

* **Created By/Date** — Vinay Pandhariwal, August 2025

* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
