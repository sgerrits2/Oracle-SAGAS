# Lab 5: Oracle Sagas in Action — The CloudBank Application

## **Introduction**

In this lab, you will deploy and explore the **CloudBank application** — a comprehensive banking demonstration that showcases the **Oracle Saga Pattern in action**. CloudBank is a reference implementation where money transfers are modeled as **distributed Sagas** with multiple participants across microservices (Debit, Credit, Ledger, Notifications).

The application uses **Podman containers** for seamless deployment and orchestration, allowing you to:
- Transfer the complete CloudBank codebase to your compute instance
- Verify database initialization and setup
- Deploy containerized microservices with Podman
- Access frontend UI, Swagger APIs, and monitoring endpoints
- Execute **normal transactions** (intra-bank and inter-bank transfers)
- Simulate **failure scenarios** and observe compensation patterns
- Test **crash recovery** mechanisms and validate consistency

</br>

<details open>
<summary><mark>CloudBank Architecture</mark></summary>

**CloudBank** implements a **multi-bank distributed architecture** with the following components:

- **Frontend (Python Flask):** Web-based interface for initiating transfers and viewing transaction history
- **Backend Microservices:** SpringBoot-Jersey services implementing saga participants (BankChicago, BankMex, CloudBank)
- **Saga Coordinator & Broker:** Oracle Database 23ai Saga framework ensuring ACID properties
- **Database Layer:** Single PDB with multiple schemas (ORDSYS, bankchicago, bankmex) using RESERVABLE columns
- **Monitoring & Tracing:** OpenTelemetry integration for distributed tracing and saga lifecycle visibility
- **Configuration Management:** YAML-based Kubernetes/Podman orchestration with ADB wallet integration

**Key Features:**
- **Lock-free reservations** using Oracle Database RESERVABLE columns
- **Compensation patterns** with withdrawal checks and automatic rollbacks  
- **Cross-bank transfers** with distributed coordination
- **Failure simulation** and crash recovery validation
- **Real-time monitoring** with distributed tracing

</details>

*Estimated Time: 45–60 minutes*

---

### Objectives

In this lab, you will:

- **Deploy CloudBank** to the compute instance using configuration from the previous labs
- **Verify database setup** and initialization script completion  
- **Start containerized services** using Podman with proper profiles
- **Access endpoints** for frontend UI, Swagger APIs, and monitoring tools
- **Execute transaction scenarios** including normal, failure, and crash recovery flows
- **Observe Saga lifecycle** through database logs and distributed tracing

---

### Prerequisites

- Completion of **Lab 3 (Saga Core Setup)** and **Lab 4 (Saga Client)**
- Cloud Shell with **Oracle ADB wallet** configured and **CloudBank codebase** ready
- Compute instance with **Podman and Podman Compose** installed
- Security rules allowing ports **3000 (UI)**, **8080 (API)**, and **9411 (Zipkin)**

---

## Task 1: Transfer CloudBank to Compute Instance

---

Before deploying CloudBank, we need to transfer the complete application codebase from Cloud Shell to your compute instance and configure the environment variables.

### Step 1: Prepare the CloudBank Package

In your **Cloud Shell**, navigate to the parent directory of oracle-saga-cloudbank. This path may vary based on your setup - ensure you're in the directory that contains oracle-saga-cloudbank/.

  ```
    <copy>
    ls -la oracle-saga-cloudbank/
    </copy>
  ```

Expected directory structure:
```
oracle-saga-cloudbank/
├── CloudBank/             # Main application directory
│   ├── Website/           # Python Flask frontend application
│   │   ├── app.py
│   │   ├── requirements.txt
│   │   ├── static/        # CSS, images, and assets
│   │   └── templates/     # HTML templates
│   ├── banka/            # BankChicago SpringBoot microservice
│   │   ├── pom.xml
│   │   ├── src/main/java/ # Java source code
│   │   └── target/        # Compiled artifacts
│   ├── bankb/            # BankMex SpringBoot microservice
│   │   ├── pom.xml
│   │   ├── src/main/java/ # Java source code
│   │   └── target/        # Compiled artifacts
│   └── orchestrator/     # Saga orchestrator service
│       ├── pom.xml
│       ├── src/main/java/ # Java source code
│       └── target/        # Compiled artifacts
├── adbsSetup/            # Database setup and configuration
│   ├── adb_wallet/       # Oracle ADB wallet files
│   ├── adbsSetupScript.sql
│   └── adbsSagaCleanup.sql
├── osagaAdbsSetup.yaml   # Kubernetes/Podman setup configuration
└── swagger-ui-config/    # API documentation configuration
```

### Step 2: Enter Your Environment Information

Enter the values specific to your environment to generate the scripts below. The Saga schemas, usernames, passwords, Broker name, Coordinator name, and compute username are fixed by the previous setup labs.

<div class="input-section">
<strong>ADB TNS Alias:</strong> 
<input type="text" id="tnsDatabaseName" placeholder="Enter TNS alias (e.g., alpha1234_high)" class="input-field" oninput="updateLabValues()"><br/>
<strong>SSH Key Filename:</strong> 
<input type="text" id="sshKeyName" placeholder="Enter SSH key filename (e.g., ssh-key-2025-01-01.key)" class="input-field" oninput="updateLabValues()"><br/>
<strong>Compute Instance IP:</strong> 
<input type="text" id="computeInstanceIP" placeholder="Enter compute instance IP (e.g., 195.168.2.124)" class="input-field" oninput="updateLabValues()"><br/>
<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>The generated commands use the fixed CloudBank schemas from the previous labs and <strong>ubuntu</strong> as the compute instance username.</em>
</div>
</div>

> **Note:** If these values were saved in an earlier lab, they are populated automatically. Review them and change only the values that are different in your environment.

<div id="configWarning" style="display:none; background:#fff4e5; border:1px solid #f0ad4e; padding:12px 14px; border-radius:8px; margin:12px 0; color:#7a4b00;">
<strong>Required environment information is missing.</strong> Enter the ADB TNS Alias, SSH Key Filename, and Compute Instance IP before copying the deployment script.
</div>

### Step 3: Configure and Transfer CloudBank

The following script performs the complete deployment from **Cloud Shell**. It creates the `.env` file using your TNS Alias together with the fixed CloudBank users and passwords established in the previous labs, packages the application, transfers it to the compute instance, extracts it, and verifies the result.

<pre id="deployCloudBank" class="interactive-command"><code># Run this complete block from the directory that contains oracle-saga-cloudbank/
# Enter the TNS Alias, SSH Key Filename, and Compute Instance IP above before copying this script.
set -e

PROJECT_DIR="$(pwd)/oracle-saga-cloudbank"
if [ ! -d "$PROJECT_DIR" ]; then
  echo "ERROR: oracle-saga-cloudbank/ was not found in $(pwd)"
  exit 1
fi

cd "$PROJECT_DIR"

# Use the environment-specific TNS Alias and the fixed CloudBank values from the previous labs
cat > .env &lt;&lt;'EOF'
# Database Connection Settings
TNS_ALIAS_CONTAINER=<span class="tns-value">DATABASE_CONNECTION_TNS_NAME</span>
ADBS_USER=ADMIN
ADBS_PASSWORD=Welcome_123#

# Bank Service Credentials
BANKA_USER=bankchicago
BANKA_PASSWORD=Welcome_123#
BANKB_USER=bankmex
BANKB_PASSWORD=Welcome_123#

# Orchestrator and Broker
ORCHESTRATOR_USER=orchestratorchicago
ORCHESTRATOR_PASSWORD=Welcome_123#
BROKER_USER=brokerchicago
BROKER_PASSWORD=Welcome_123#

# Container and Monitoring Configuration
TNS_ADMIN_CONTAINER=/opt/adb_wallet
ENABLE_ZIPKIN="true"
ZIPKIN_URL="http://zipkin:9411/api/v2/spans"
EOF

# Package the complete project
cd ..
tar -czf oracle-saga-cloudbank.tar.gz oracle-saga-cloudbank/

# Transfer to the compute instance
scp -i <span class="ssh-key-value">your-key.pem</span> oracle-saga-cloudbank.tar.gz ubuntu@<span class="instance-ip-value">INSTANCE_IP</span>:~/

# Connect, extract, and verify
ssh -i <span class="ssh-key-value">your-key.pem</span> ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e
cd ~
rm -rf oracle-saga-cloudbank
tar -xzf oracle-saga-cloudbank.tar.gz
cd oracle-saga-cloudbank

echo "CloudBank application deployed successfully."
echo "Project directory: $(pwd)"
ls -la
REMOTE</code></pre>

<div class="button-center">
<button onclick="copyBlock('deployCloudBank', this)" class="copy-btn-pastel">📋 Copy Deployment Script</button>
</div>

**Expected Result:**
- The `.env` configuration is created without manual editing
- `oracle-saga-cloudbank.tar.gz` is transferred to the compute instance
- The application is extracted under `~/oracle-saga-cloudbank/`
- The project files and ADB wallet are available on the compute instance

> **Why this is automated:** The CloudBank schemas and passwords are fixed by the previous setup labs (`brokerchicago`, `orchestratorchicago`, `bankchicago`, `bankmex`, and `Welcome_123#`). Only the TNS Alias and compute-instance information are environment-specific, so those are the only values requested here.

<style>
.input-section {
    background-color: #f9f9f9;
    padding: 15px;
    margin: 10px 0;
    border-radius: 8px;
    border: 1px solid #ddd;
}

.input-field {
    width: 300px;
    padding: 8px;
    font-size: 14px;
    border: 1px solid #ccc;
    border-radius: 4px;
    margin: 5px 0;
}

.interactive-command {
    background-color: #f5f5f5;
    border: 1px solid #ddd;
    padding: 10px;
    border-radius: 4px;
    font-family: monospace;
    margin: 10px 0;
    word-wrap: break-word;
}

.save-btn, .save-btn-small {
    background-color: #90EE90;
    color: #2E7D32;
    padding: 8px 12px;
    border: none;
    border-radius: 12px;
    cursor: pointer;
    font-size: 12px;
    margin: 2px;
    font-weight: 500;
}

.save-btn:hover, .save-btn-small:hover {
    background-color: #7FDD7F;
    color: #2E7D32;
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
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
    transition: all 0.3s ease;
}

.copy-btn-pastel:hover {
    background-color: #7FDD7F;
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.button-center {
    text-align: left;
    margin: 15px 0;
}

.delete-btn, .delete-btn-small {
    background-color: #f44336;
    color: white;
    padding: 8px 12px;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    margin: 2px;
}

.clear-btn, .clear-btn-small {
    background-color: #008CBA;
    color: white;
    padding: 8px 12px;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    margin: 2px;
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
    const tnsElement = document.getElementById('tnsDatabaseName');
    const sshKeyElement = document.getElementById('sshKeyName');
    const instanceIPElement = document.getElementById('computeInstanceIP');

    const tnsAlias = (tnsElement ? tnsElement.value : '').trim() || 'DATABASE_CONNECTION_TNS_NAME';
    const sshKey = (sshKeyElement ? sshKeyElement.value : '').trim() || 'your-key.pem';
    const instanceIP = (instanceIPElement ? instanceIPElement.value : '').trim() || 'INSTANCE_IP';

    setTextForClass('tns-value', tnsAlias);
    setTextForClass('ssh-key-value', sshKey);
    setTextForClass('instance-ip-value', instanceIP);

    if (tnsElement && tnsElement.value.trim()) sessionStorage.setItem('adbConnectionString', tnsElement.value.trim());
    if (sshKeyElement && sshKeyElement.value.trim()) sessionStorage.setItem('sshKeyName', sshKeyElement.value.trim());
    if (instanceIPElement && instanceIPElement.value.trim()) sessionStorage.setItem('computePublicIP', instanceIPElement.value.trim());

    const warning = document.getElementById('configWarning');
    const missing = !tnsElement || !tnsElement.value.trim() || !sshKeyElement || !sshKeyElement.value.trim() || !instanceIPElement || !instanceIPElement.value.trim();
    if (warning) warning.style.display = missing ? 'block' : 'none';
}

function loadPreviousLabValues() {
    try {
        const savedTns = getFirstSessionValue([
            'adbConnectionString',
            'DATABASE_CONNECTION_TNS_NAME',
            'cloudbank_DATABASE_CONNECTION_TNS_NAME',
            'tnsDatabaseName'
        ]);
        const savedKey = getFirstSessionValue(['sshKeyName']);
        const savedIP = getFirstSessionValue(['computePublicIP', 'computeInstanceIP']);

        const tnsElement = document.getElementById('tnsDatabaseName');
        const sshKeyElement = document.getElementById('sshKeyName');
        const instanceIPElement = document.getElementById('computeInstanceIP');

        if (savedTns && tnsElement) tnsElement.value = savedTns;
        if (savedKey && sshKeyElement) sshKeyElement.value = savedKey;
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
        const tnsElement = document.getElementById('tnsDatabaseName');
        const sshKeyElement = document.getElementById('sshKeyName');
        const instanceIPElement = document.getElementById('computeInstanceIP');
        const tns = tnsElement ? tnsElement.value.trim() : '';
        const sshKey = sshKeyElement ? sshKeyElement.value.trim() : '';
        const instanceIP = instanceIPElement ? instanceIPElement.value.trim() : '';
        if (!tns || !sshKey || !instanceIP) {
            alert('Enter the ADB TNS Alias, SSH Key Filename, and Compute Instance IP before copying the deployment script.');
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


## Task 2: Verify CloudBank Environment Setup

---

Before starting the application services, verify that the compute environment prepared in the earlier setup lab is ready. Podman, Podman Compose, the CloudBank directory, and the required container runtime should already be available.

### Step 1: Run the Environment Verification

Copy and run the complete validation script from **Cloud Shell**:

<pre id="verifyCompute" class="interactive-command"><code>ssh -i <span class="ssh-key-value">your-key.pem</span> ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e

echo "=== Podman ==="
podman --version

echo
echo "=== Podman Compose ==="
podman-compose --version

echo
echo "=== CloudBank Directory ==="
test -d ~/oracle-saga-cloudbank
ls -ld ~/oracle-saga-cloudbank

echo
echo "=== Current User and Groups ==="
whoami
groups

echo
echo "=== Podman System ==="
podman system info >/dev/null
echo "Podman system check passed."

echo
echo "=== Available Images ==="
podman images

echo
echo "CloudBank environment verification completed successfully."
REMOTE</code></pre>

<div class="button-center">
<button onclick="copyBlock('verifyCompute', this)" class="copy-btn-pastel">📋 Copy Verification Script</button>
</div>

**Expected Results:**
- Podman returns a valid version
- Podman Compose is available
- `~/oracle-saga-cloudbank/` exists
- The current compute user is `ubuntu`
- Podman system validation succeeds
- Required images are available or can be pulled by Podman Compose

> **If verification fails:** Return to the compute environment setup from the previous lab and complete the missing Podman or Podman Compose configuration before continuing. The infrastructure installation procedure is intentionally not repeated here.

---

## Task 3: Start Podman Services

---

CloudBank uses a **two-stage deployment** approach with different Podman profiles for database setup and application runtime:

- **adbsSetup profile:** Initializes the database-facing application resources and connections
- **adbs profile:** Starts the CloudBank frontend, bank services, orchestrator, Swagger components, and tracing services

### Step 1: Start CloudBank

Run the following complete script from **Cloud Shell**. It starts the setup profile, waits for the setup containers to finish successfully, switches to the application profile, and performs a final service and log check.

<pre id="startCloudBank" class="interactive-command"><code>ssh -i <span class="ssh-key-value">your-key.pem</span> ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e
cd ~/oracle-saga-cloudbank

echo "=== Starting database setup profile ==="
podman-compose --profile adbsSetup up -d

setup_ids="$(podman-compose --profile adbsSetup ps -q)"
if [ -z "$setup_ids" ]; then
  echo "ERROR: No containers were created for the adbsSetup profile."
  exit 1
fi

echo "Waiting for database setup containers to complete..."
completed=false
for attempt in $(seq 1 60); do
  running=false
  for id in $setup_ids; do
    if [ "$(podman inspect -f '{{.State.Running}}' "$id")" = "true" ]; then
      running=true
      break
    fi
  done

  if [ "$running" = "false" ]; then
    completed=true
    break
  fi

  sleep 5
done

if [ "$completed" != "true" ]; then
  echo "ERROR: Database setup did not finish within the expected time."
  for id in $setup_ids; do
    podman logs --tail 50 "$id" 2>&1 || true
  done
  exit 1
fi

for id in $setup_ids; do
  exit_code="$(podman inspect -f '{{.State.ExitCode}}' "$id")"
  if [ "$exit_code" != "0" ]; then
    echo "ERROR: Database setup container $id exited with code $exit_code."
    podman logs --tail 50 "$id" 2>&1 || true
    exit 1
  fi
done

echo "Database setup completed successfully."

echo
echo "=== Switching to application profile ==="
podman-compose --profile adbsSetup down
podman-compose --profile adbs up -d

sleep 10

echo
echo "=== Running CloudBank Services ==="
podman ps

echo
echo "=== Recent Service Logs ==="
for container in $(podman ps --format '{{.Names}}'); do
  echo
  echo "----- $container -----"
  podman logs --tail 20 "$container" 2>&1 || true
done
REMOTE</code></pre>

<div class="button-center">
<button onclick="copyBlock('startCloudBank', this)" class="copy-btn-pastel">📋 Copy Start Script</button>
</div>

**Expected Output:**
- Frontend service available on port `3000`
- Orchestrator/API service available on port `8080`
- Bank services running on their configured application ports
- Zipkin available on port `9411`
- Recent logs do not show startup failures

> **Note:** The exact container IDs and generated container names can vary between environments. The script validates the setup containers by state and exit code instead of relying on a fixed wait time or container name.

## Task 4: Access Application Endpoints

---

Once all services are running, you can access CloudBank through multiple endpoints. The same compute instance IP entered in Task 1 is reused automatically; you do not need to enter it again.

### Step 1: Frontend Application Access

**CloudBank Flask UI** - Main application interface for executing transactions:

<pre class="interactive-command">
<code>🌐 Frontend URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:3000</code>
</pre>

<div class="button-center">
<button onclick="openURL('frontend')" class="copy-btn-pastel">🌐 Open CloudBank UI</button>
<button onclick="copyURL('frontend', this)" class="copy-btn-pastel">📋 Copy Frontend URL</button>
</div>

**Features Available:**
- Account balance viewing
- Intra-bank money transfers  
- Inter-bank money transfers
- Transaction history
- Real-time saga status updates

**Expected Interface:**

![CloudBank UI](./images/lab5-frontend.png "CloudBank Flask frontend interface")

---

### Step 2: Swagger API Documentation

**CloudBank REST APIs** - Complete API documentation and testing interface:

<pre class="interactive-command">
<code>📡 Swagger URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:8080/swagger-ui.html</code>
</pre>

<div class="button-center">
<button onclick="openURL('swagger')" class="copy-btn-pastel">📡 Open Swagger APIs</button>
<button onclick="copyURL('swagger', this)" class="copy-btn-pastel">📋 Copy Swagger URL</button>
</div>

**Available API Endpoints:**
- `POST /transfer/intra-bank` - Execute intra-bank transfers
- `POST /transfer/inter-bank` - Execute inter-bank transfers  
- `GET /accounts/{accountId}` - Retrieve account details
- `GET /transactions/{transactionId}` - Get transaction status
- `GET /sagas/{sagaId}` - Query saga execution details

**Expected Interface:**

![CloudBank Swagger](./images/lab5-swagger.png "CloudBank Swagger API documentation")

---

### Step 3: Monitoring and Tracing

**Zipkin Distributed Tracing** - Monitor saga execution across microservices:

<pre class="interactive-command">
<code>🔍 Zipkin URL: http://<span class="instance-ip-value">INSTANCE_IP</span>:9411</code>
</pre>

<div class="button-center">
<button onclick="openURL('zipkin')" class="copy-btn-pastel">🔍 Open Zipkin Monitoring</button>
<button onclick="copyURL('zipkin', this)" class="copy-btn-pastel">📋 Copy Zipkin URL</button>
</div>

**Monitoring Capabilities:**
- End-to-end saga tracing
- Service dependency mapping  
- Performance bottleneck identification
- Failure point analysis
- Compensation flow visualization

During the scenarios in the next task, keep Zipkin available so you can compare traces generated by successful execution, participant failure, and recovery.

**Expected Interface:**

![Zipkin Tracing](./images/lab5-zipkin.png "Zipkin distributed tracing interface")

## Task 5: Execute Transaction Scenarios

---

Now that CloudBank is fully deployed, test several transaction scenarios to observe Oracle Sagas during successful processing, distributed coordination, compensation, and service recovery.

### Scenario 1: Successful Intra-Bank Transfer

Execute a transfer between accounts within the same bank:

**Via Frontend UI:**
1. Navigate to CloudBank UI: <code>http://<span class="instance-ip-value">INSTANCE_IP</span>:3000</code>
2. Select **"Intra-Bank Transfer"**
3. Choose source account: `BANKA-ACC-001` 
4. Choose destination account: `BANKA-ACC-002`
5. Enter amount: `250.00`
6. Click **"Execute Transfer"**

**Optional - Via REST API:**

<pre id="intraBankCurl" class="interactive-command"><code>curl -X POST "http://<span class="instance-ip-value">INSTANCE_IP</span>:8080/transfer/intra-bank" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccount": "BANKA-ACC-001",
    "targetAccount": "BANKA-ACC-002", 
    "amount": 250.00,
    "currency": "USD"
  }'</code></pre>

<div class="button-center">
<button onclick="copyBlock('intraBankCurl', this)" class="copy-btn-pastel">📋 Copy Intra-Bank Request</button>
</div>

**Expected Saga Flow:**
1. **Begin Saga** - Orchestrator starts intra-bank saga
2. **Reserve Funds** - Source account balance reserved using RESERVABLE column
3. **Execute Debit** - Funds debited from source account
4. **Execute Credit** - Funds credited to target account  
5. **Commit Saga** - Transaction completed successfully

**Verification:**

<pre id="verifyIntraBank" class="interactive-command"><code>-- Check saga status
SELECT saga_id, status, outcome
FROM DBA_SAGAS
ORDER BY start_time DESC
FETCH FIRST 1 ROWS ONLY;

-- Verify account balances
SELECT account_id, balance
FROM bankchicago.accounts
WHERE account_id IN ('BANKA-ACC-001', 'BANKA-ACC-002');</code></pre>

<div class="button-center">
<button onclick="copyBlock('verifyIntraBank', this)" class="copy-btn-pastel">📋 Copy Verification SQL</button>
</div>

Also review the corresponding Zipkin trace to see the request path for the successful transaction.

---

### Scenario 2: Successful Inter-Bank Transfer

Execute a transfer between accounts in different banks:

**Via Frontend UI:**
1. Select **"Inter-Bank Transfer"**
2. Source account: `BANKA-ACC-001`
3. Target account: `BANKB-ACC-001`  
4. Amount: `500.00`
5. Execute transfer

**Expected Saga Flow:**
1. **Begin Saga** - Orchestrator (CloudBankCoordinator) coordinates cross-bank saga
2. **BankChicago Debit Request** - Withdraw funds from source bank
3. **BankMex Credit Request** - Deposit funds to target bank
4. **Confirmation Phase** - Both banks confirm success
5. **Commit Saga** - Distributed transaction completed

**Monitor in Zipkin:**
- View distributed trace spanning multiple services
- Observe network calls between BankChicago and BankMex
- Verify saga coordination timing

---

### Scenario 3: Failure Handling (Insufficient Balance)

Test compensation when the source account cannot cover the requested transfer amount. This scenario does not modify the account balance directly, so no cleanup is required before the recovery scenario.

**Execute Transfer:**
1. In the CloudBank UI, review the available balance for `BANKA-ACC-001`
2. Start an **Inter-Bank Transfer** from `BANKA-ACC-001` to `BANKB-ACC-001`
3. Enter an amount greater than the available source balance
4. Execute the transfer

The transfer should fail because the requested amount exceeds the available balance.

**Expected Compensation Flow:**
1. **Begin Saga** - Transfer saga initiated
2. **Withdrawal Check** - BankChicago checks available balance
3. **Insufficient Funds** - Balance validation fails  
4. **Trigger Compensation** - Saga framework initiates the failure/rollback path
5. **Restore Consistency** - No partial distributed transfer is left as successful

**Verification:**

<pre id="verifyCompensation" class="interactive-command"><code>-- Check compensated saga activity
SELECT saga_id, status, outcome
FROM DBA_SAGAS
WHERE outcome = 'COMPENSATED'
ORDER BY start_time DESC;

-- Verify source account state
SELECT account_id, balance
FROM bankchicago.accounts
WHERE account_id = 'BANKA-ACC-001';</code></pre>

<div class="button-center">
<button onclick="copyBlock('verifyCompensation', this)" class="copy-btn-pastel">📋 Copy Compensation Verification</button>
</div>

**What to Observe in Zipkin:**
- Compare this trace with the successful inter-bank transfer
- Identify where the business validation failed
- Observe which downstream interactions are missing or different
- Relate the trace to the compensation outcome reported by the database

---

### Scenario 4: Crash Recovery Testing

Simulate a participant becoming unavailable while an inter-bank Saga request is being processed.

### Step 1: Initiate the Transfer and Stop BankMex

Run the following complete block from **Cloud Shell**. It identifies the BankMex container automatically, starts a small inter-bank transfer in the background, and stops BankMex immediately after the request begins.

<pre id="simulateCrash" class="interactive-command"><code>ssh -i <span class="ssh-key-value">your-key.pem</span> ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e

BANKB_CONTAINER="$(podman ps --format '{{.Names}}' | grep -i bankb | head -n 1)"
if [ -z "$BANKB_CONTAINER" ]; then
  echo "ERROR: BankMex container was not found."
  exit 1
fi

echo "BankMex container: $BANKB_CONTAINER"
echo "Starting inter-bank transfer..."

curl -sS -X POST "http://localhost:8080/transfer/inter-bank" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccount": "BANKA-ACC-001",
    "targetAccount": "BANKB-ACC-001",
    "amount": 1.00,
    "currency": "USD"
  }' > /tmp/lab5-crash-transfer.out 2>&1 &

TRANSFER_PID=$!
sleep 1

echo "Stopping BankMex while the transfer is being processed..."
podman stop "$BANKB_CONTAINER"

wait "$TRANSFER_PID" || true

echo
echo "=== Transfer Response ==="
cat /tmp/lab5-crash-transfer.out || true

echo
echo "=== BankMex State ==="
podman ps -a --filter "name=$BANKB_CONTAINER"
REMOTE</code></pre>

<div class="button-center">
<button onclick="copyBlock('simulateCrash', this)" class="copy-btn-pastel">📋 Copy Crash Simulation</button>
</div>

### Step 2: Observe Saga State

Use the database connection method from the previous labs:

<pre id="verifyCrashState" class="interactive-command"><code>-- Check incomplete sagas
SELECT saga_id, status, participants
FROM DBA_INCOMPLETE_SAGAS;

-- If available in this environment, inspect retry information
SELECT saga_id, retry_count, last_retry
FROM DBA_SAGA_RETRY_LOG;</code></pre>

<div class="button-center">
<button onclick="copyBlock('verifyCrashState', this)" class="copy-btn-pastel">📋 Copy Recovery Verification SQL</button>
</div>

### Step 3: Restart BankMex and Observe Recovery

Run the following complete block from **Cloud Shell**:

<pre id="restartBankMex" class="interactive-command"><code>ssh -i <span class="ssh-key-value">your-key.pem</span> ubuntu@<span class="instance-ip-value">INSTANCE_IP</span> 'bash -s' &lt;&lt;'REMOTE'
set -e

BANKB_CONTAINER="$(podman ps -a --format '{{.Names}}' | grep -i bankb | head -n 1)"
if [ -z "$BANKB_CONTAINER" ]; then
  echo "ERROR: BankMex container was not found."
  exit 1
fi

podman start "$BANKB_CONTAINER"
sleep 5

echo "=== BankMex State ==="
podman ps --filter "name=$BANKB_CONTAINER"

echo
echo "=== Recent BankMex Logs ==="
podman logs --tail 30 "$BANKB_CONTAINER" 2>&1 || true

echo
echo "=== Recent Orchestrator Logs ==="
ORCHESTRATOR_CONTAINER="$(podman ps --format '{{.Names}}' | grep -i orchestrator | head -n 1)"
if [ -n "$ORCHESTRATOR_CONTAINER" ]; then
  podman logs --tail 30 "$ORCHESTRATOR_CONTAINER" 2>&1 || true
fi
REMOTE</code></pre>

<div class="button-center">
<button onclick="copyBlock('restartBankMex', this)" class="copy-btn-pastel">📋 Copy Restart and Recovery Check</button>
</div>

**Expected Recovery:**
- The Saga cannot complete normally while BankMex is unavailable
- The temporary outage is visible through application or Saga state
- After BankMex returns, the workflow reaches a consistent final outcome according to the application's recovery behavior
- The transaction either completes successfully or compensates cleanly
- No data inconsistency remains after the participant failure

**What to Observe:**
- Compare the recovery trace with the normal inter-bank trace
- Identify the point where BankMex becomes unavailable
- Observe what changes after BankMex is restarted
- Relate the final application outcome to the database Saga state

---

✅ **Congratulations!** You have successfully deployed CloudBank, executed various transaction scenarios, and validated Oracle Saga's distributed transaction capabilities including normal flow, compensation handling, and crash recovery.

**Next Lab:** Continue to **Lab 6 — Blockchain Flashback Journals** to explore combining Oracle Sagas with blockchain technology for enhanced data protection and audit trails.

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