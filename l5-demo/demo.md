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
- **Backend Microservices:** SpringBoot-Jersey services implementing saga participants (BankA, BankB, Orchestrator)
- **Saga Coordinator & Broker:** Oracle Database 23ai Saga framework ensuring ACID properties
- **Database Layer:** Single PDB with multiple schemas (ORDSYS, BANKA, BANKB) using RESERVABLE columns
- **Monitoring & Tracing:** OpenTelemetry integration for distributed tracing and saga lifecycle visibility
- **Configuration Management:** YAML-based Kubernetes/Podman orchestration with ADB wallet integration

**Key Features:**
- **Lock-free reservations** using Oracle Database RESERVABLE columns
- **Compensation patterns** with withdrawal checks and automatic rollbacks  
- **Cross-bank transfers** with distributed coordination
- **Failure simulation** and crash recovery validation
- **Real-time monitoring** with distributed tracing

</details>

*Estimated Time: 60–75 minutes*

---

### Objectives

In this lab, you will:

- **Deploy CloudBank** to compute instance and configure environment variables
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

In your **Cloud Shell**, navigate to the parent directory of oracle-saga-cloudbank:

```bash
# Navigate to the parent directory containing oracle-saga-cloudbank
# This path may vary based on your setup - ensure you're in the directory that contains oracle-saga-cloudbank/
ls -la oracle-saga-cloudbank/
```
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
│   ├── banka/            # BankA SpringBoot microservice
│   │   ├── pom.xml
│   │   ├── src/main/java/ # Java source code
│   │   └── target/        # Compiled artifacts
│   ├── bankb/            # BankB SpringBoot microservice
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

### Step 2: Configure Environment Variables

Before transfer, update the `.env` file with your database connection details. Enter your values below to auto-generate the configuration:

**Database Connection Details:**
<div class="input-section">
<strong>ADB TNS Alias:</strong> 
<input type="text" id="tnsDatabaseName" placeholder="Enter TNS alias (e.g., alpha1234_high)" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>ADBS Username:</strong> 
<input type="text" id="adbsUsername" placeholder="Enter ADBS username" value="adbs" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>ADBS Password:</strong> 
<input type="text" id="adbsPassword" placeholder="Enter ADBS password" class="input-field" oninput="updateEnvConfig()"><br/>
</div>

**CloudBank Service Credentials:**
<div class="input-section">
<strong>BankA Username:</strong> 
<input type="text" id="bankaUser" placeholder="Enter BankA username" value="banka" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>BankA Password:</strong> 
<input type="text" id="bankaPassword" placeholder="Enter BankA password" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>BankB Username:</strong> 
<input type="text" id="bankbUser" placeholder="Enter BankB username" value="bankb" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>BankB Password:</strong> 
<input type="text" id="bankbPassword" placeholder="Enter BankB password" class="input-field" oninput="updateEnvConfig()"><br/>
</div>

**Orchestrator and Broker Credentials:**
<div class="input-section">
<strong>Orchestrator Username:</strong> 
<input type="text" id="orchestratorUser" placeholder="Enter Orchestrator username" value="orchestrator1" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>Orchestrator Password:</strong> 
<input type="text" id="orchestratorPassword" placeholder="Enter Orchestrator password" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>Broker Username:</strong> 
<input type="text" id="brokerUser" placeholder="Enter Broker username" value="broker1" class="input-field" oninput="updateEnvConfig()"><br/>
<strong>Broker Password:</strong> 
<input type="text" id="brokerPassword" placeholder="Enter Broker password" class="input-field" oninput="updateEnvConfig()"><br/>
</div>

**Generated .env Configuration:**

<pre id="envConfigContainer">
<code id="envConfigOutput"># Database Connection Settings
TNS_ALIAS_CONTAINER=<span id="envTnsAlias">your_adb_alias</span>
ADBS_USER=<span id="envAdbsUser">adbs</span>
ADBS_PASSWORD=<span id="envAdbsPassword">your_adbs_password</span>

# Bank Service Credentials  
BANKA_USER=<span id="envBankaUser">banka</span>
BANKA_PASSWORD=<span id="envBankaPassword">your_banka_password</span>
BANKB_USER=<span id="envBankbUser">bankb</span>
BANKB_PASSWORD=<span id="envBankbPassword">your_bankb_password</span>

# Orchestrator and Broker
ORCHESTRATOR_USER=<span id="envOrchestratorUser">orchestrator1</span>
ORCHESTRATOR_PASSWORD=<span id="envOrchestratorPassword">your_orchestrator_password</span>
BROKER_USER=<span id="envBrokerUser">broker1</span>
BROKER_PASSWORD=<span id="envBrokerPassword">your_broker_password</span>

# Container and Monitoring Configuration (Do not change these values)
TNS_ADMIN_CONTAINER=/opt/adb_wallet
ENABLE_ZIPKIN="true"
ZIPKIN_URL="http://zipkin:9411/api/v2/spans"</code>
</pre>

<div class="button-center">
<button onclick="copyEnvConfig()" class="copy-btn-pastel">📋 Copy .env Contents</button>
</div>

**Apply Configuration to Cloud Shell:**

Once you've filled out the form above and copied the generated .env configuration:

1. **Navigate to CloudBank Directory in Cloud Shell:**
   ```bash
   cd oracle-saga-cloudbank
   ```

2. **Edit the existing .env file:**
   ```bash
   nano .env
   ```

3. **Replace the contents** with your copied configuration from the form above

4. **Save and exit** (Ctrl+X, then Y, then Enter in nano)

5. **Verify the configuration:**
   ```bash
   cat .env
   ```

6. **Continue to Step 3** to transfer the configured application to your compute instance

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
function updateEnvConfig() {
    try {
        // Get input elements first
        const tnsElement = document.getElementById('tnsDatabaseName');
        const adbsUserElement = document.getElementById('adbsUsername');
        const adbsPasswordElement = document.getElementById('adbsPassword');
        const bankaUserElement = document.getElementById('bankaUser');
        const bankaPasswordElement = document.getElementById('bankaPassword');
        const bankbUserElement = document.getElementById('bankbUser');
        const bankbPasswordElement = document.getElementById('bankbPassword');
        const orchestratorUserElement = document.getElementById('orchestratorUser');
        const orchestratorPasswordElement = document.getElementById('orchestratorPassword');
        const brokerUserElement = document.getElementById('brokerUser');
        const brokerPasswordElement = document.getElementById('brokerPassword');

        // Check if input elements exist
        if (!tnsElement || !adbsUserElement || !adbsPasswordElement || !bankaUserElement || 
            !bankaPasswordElement || !bankbUserElement || !bankbPasswordElement || 
            !orchestratorUserElement || !orchestratorPasswordElement || !brokerUserElement || !brokerPasswordElement) {
            console.log('Input elements not ready, skipping updateEnvConfig');
            return;
        }

        // Get input values
        const tnsAlias = tnsElement.value || '<DATABASE_CONNECTION_TNS_NAME>';
        const adbsUser = adbsUserElement.value || '<ADMIN_SCHEMA>';
        const adbsPassword = adbsPasswordElement.value || '<DATABASE_ADMIN_PASSWORD>';
        const bankaUser = bankaUserElement.value || '<BANKA_SCHEMA>';
        const bankaPassword = bankaPasswordElement.value || '<BANKA_SCHEMA_PASSWORD>';
        const bankbUser = bankbUserElement.value || '<BANKB_SCHEMA>';
        const bankbPassword = bankbPasswordElement.value || '<BANKB_SCHEMA_PASSWORD>';
        const orchestratorUser = orchestratorUserElement.value || '<ORCHESTRATOR_SCHEMA>';
        const orchestratorPassword = orchestratorPasswordElement.value || '<ORCHESTRATOR_SCHEMA_PASSWORD>';
        const brokerUser = brokerUserElement.value || '<BROKER_SCHEMA>';
        const brokerPassword = brokerPasswordElement.value || '<BROKER_SCHEMA_PASSWORD>';

        // Update display spans (check if they exist)
        const displayElements = [
            { id: 'envTnsAlias', value: tnsAlias },
            { id: 'envAdbsUser', value: adbsUser },
            { id: 'envAdbsPassword', value: adbsPassword },
            { id: 'envBankaUser', value: bankaUser },
            { id: 'envBankaPassword', value: bankaPassword },
            { id: 'envBankbUser', value: bankbUser },
            { id: 'envBankbPassword', value: bankbPassword },
            { id: 'envOrchestratorUser', value: orchestratorUser },
            { id: 'envOrchestratorPassword', value: orchestratorPassword },
            { id: 'envBrokerUser', value: brokerUser },
            { id: 'envBrokerPassword', value: brokerPassword }
        ];

        displayElements.forEach(item => {
            const element = document.getElementById(item.id);
            if (element) {
                element.textContent = item.value;
            }
        });

        console.log('Successfully updated .env configuration display');
    } catch (error) {
        console.error('Error in updateEnvConfig:', error);
    }
}

// Copy .env configuration to clipboard
function copyEnvConfig() {
    const envConfig = document.getElementById('envConfigOutput').textContent;
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(envConfig).then(function() {
            // Visual feedback
            const button = document.querySelector('.copy-btn-pastel');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }).catch(function(err) {
            console.error('Could not copy text: ', err);
            fallbackCopyTextToClipboard(envConfig);
        });
    } else {
        fallbackCopyTextToClipboard(envConfig);
    }
}

// Fallback copy function for older browsers
function fallbackCopyTextToClipboard(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.left = "-999999px";
    textArea.style.top = "-999999px";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful) {
            const button = document.querySelector('.copy-btn-pastel');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }
    } catch (err) {
        console.error('Fallback: Could not copy text: ', err);
    }
    
    document.body.removeChild(textArea);
}

// Update transfer commands based on input fields
function updateTransferCommands() {
    try {
        const sshKeyElement = document.getElementById('sshKeyName');
        const instanceIPElement = document.getElementById('computeInstanceIP');
        const usernameElement = document.getElementById('computeUsername');
        
        const sshKeyDisplayElement = document.getElementById('sshKeyDisplay');
        const ipDisplayElement = document.getElementById('ipDisplay');
        const usernameDisplayElement = document.getElementById('usernameDisplay');
        
        // Check if all elements exist
        if (!sshKeyElement || !instanceIPElement || !usernameElement || 
            !sshKeyDisplayElement || !ipDisplayElement || !usernameDisplayElement) {
            console.log('Transfer command elements not yet ready, skipping update');
            return;
        }
        
        const sshKey = sshKeyElement.value || 'your-key.pem';
        const instanceIP = instanceIPElement.value || 'INSTANCE_IP';
        const username = usernameElement.value || 'ubuntu';
        
        // Update display spans for transfer commands
        sshKeyDisplayElement.textContent = sshKey;
        ipDisplayElement.textContent = instanceIP;
        usernameDisplayElement.textContent = username;
        
        // Also update extract command spans if they exist
        const sshKeyExtractElement = document.getElementById('sshKeyDisplayExtract');
        const ipExtractElement = document.getElementById('ipDisplayExtract');
        const usernameExtractElement = document.getElementById('usernameDisplayExtract');
        
        if (sshKeyExtractElement) sshKeyExtractElement.textContent = sshKey;
        if (ipExtractElement) ipExtractElement.textContent = instanceIP;
        if (usernameExtractElement) usernameExtractElement.textContent = username;
        
        console.log('Updated transfer and extract commands:', { sshKey, instanceIP, username });
    } catch (error) {
        console.error('Error updating transfer commands:', error);
    }
}

// Copy transfer commands to clipboard
function copyTransferCommands() {
    const transferCommands = document.getElementById('transferCommandOutput').textContent;
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(transferCommands).then(function() {
            // Visual feedback
            const button = document.querySelector('button[onclick="copyTransferCommands()"]');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }).catch(function(err) {
            console.error('Could not copy text: ', err);
            fallbackCopyTransferCommands(transferCommands);
        });
    } else {
        fallbackCopyTransferCommands(transferCommands);
    }
}

// Fallback copy function for transfer commands
function fallbackCopyTransferCommands(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.left = "-999999px";
    textArea.style.top = "-999999px";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful) {
            const button = document.querySelector('button[onclick="copyTransferCommands()"]');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }
    } catch (err) {
        console.error('Fallback: Could not copy text: ', err);
    }
    
    document.body.removeChild(textArea);
}

// Copy extract commands to clipboard
function copyExtractCommands() {
    const extractCommands = document.getElementById('extractCommandOutput').textContent;
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(extractCommands).then(function() {
            // Visual feedback
            const button = document.querySelector('button[onclick="copyExtractCommands()"]');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }).catch(function(err) {
            console.error('Could not copy text: ', err);
            fallbackCopyExtractCommands(extractCommands);
        });
    } else {
        fallbackCopyExtractCommands(extractCommands);
    }
}

// Fallback copy function for extract commands
function fallbackCopyExtractCommands(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.left = "-999999px";
    textArea.style.top = "-999999px";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful) {
            const button = document.querySelector('button[onclick="copyExtractCommands()"]');
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }
    } catch (err) {
        console.error('Fallback: Could not copy text: ', err);
    }
    
    document.body.removeChild(textArea);
}

// Initialize CloudBank configuration similar to l2-provision pattern
function initializeCloudBankEnv() {
    try {
        // Debug: Log all session storage items to console
        console.log('All session storage items:');
        for (let i = 0; i < sessionStorage.length; i++) {
            let key = sessionStorage.key(i);
            console.log(key + ': ' + sessionStorage.getItem(key));
        }
        
        // Auto-populate from session storage if available
        if (typeof(Storage) !== "undefined" && sessionStorage) {
            // Database connection
            document.getElementById('tnsDatabaseName').value = sessionStorage.getItem('adbConnectionString') || '';
            document.getElementById('adbsPassword').value = sessionStorage.getItem('cloudbank_ADB_DATABASE_PASSWORD') || 'Welcome_123#' ; // Default admin password
            
            // CloudBank service credentials - mapping to actual session variables
            document.getElementById('bankaUser').value = sessionStorage.getItem('cloudbank_USER_BANKA') || 'banka';
            document.getElementById('bankaPassword').value = sessionStorage.getItem('cloudbank_PASSWORD_BANKC') || 'Welcome_123#';
            
            document.getElementById('bankbUser').value = sessionStorage.getItem('cloudbank_USER_BANKB') || 'bankb';
            document.getElementById('bankbPassword').value = sessionStorage.getItem('cloudbank_PASSWORD_BANKD') || 'Welcome_123#';
            
            // Orchestrator and Broker credentials
            document.getElementById('orchestratorUser').value = sessionStorage.getItem('cloudbank_USER_ORCHESTRATOR1') || 'orchestrator1';
            document.getElementById('orchestratorPassword').value = sessionStorage.getItem('cloudbank_PASSWORD_ORCHESTRATOR1') || 'Welcome_123#';
            
            document.getElementById('brokerUser').value = sessionStorage.getItem('cloudbank_USER_BROKER1') || 'broker1';
            document.getElementById('brokerPassword').value = sessionStorage.getItem('cloudbank_PASSWORD_BROKER1') || 'Welcome_123#';
            
        }
        
        // Auto-populate transfer command fields
        document.getElementById('sshKeyName').value = sessionStorage.getItem('sshKeyName') || '';
        document.getElementById('computeInstanceIP').value = sessionStorage.getItem('computePublicIP') || '';
        
        // Update the .env configuration display
        updateEnvConfig();
        
        // Update transfer commands display with multiple retry attempts
        setTimeout(updateTransferCommands, 100);
        setTimeout(updateTransferCommands, 500);
        setTimeout(updateTransferCommands, 1000);
    } catch (error) {
        console.error('Error loading CloudBank configuration:', error);
    }
}

// Initialize using multiple methods like l2-provision
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeCloudBankEnv);
} else {
    initializeCloudBankEnv();
}
window.addEventListener('load', initializeCloudBankEnv);
setTimeout(initializeCloudBankEnv, 500);  
setTimeout(initializeCloudBankEnv, 1500); 
setTimeout(initializeCloudBankEnv, 3000);

// Update access URLs based on instance IP input
function updateAccessURLs() {
    try {
        const instanceIPElement = document.getElementById('accessInstanceIP');
        
        if (!instanceIPElement) {
            console.log('Access instance IP element not ready, skipping update');
            return;
        }
        
        const instanceIP = instanceIPElement.value || 'INSTANCE_IP';
        
        // Update all URL display spans
        const urlElements = [
            { id: 'frontendIP', value: instanceIP },
            { id: 'swaggerIP', value: instanceIP },
            { id: 'zipkinIP', value: instanceIP },
            { id: 'quickAccessIP', value: instanceIP }
        ];
        
        urlElements.forEach(item => {
            const element = document.getElementById(item.id);
            if (element) {
                element.textContent = item.value;
            }
        });
        
        console.log('Updated access URLs for IP:', instanceIP);
    } catch (error) {
        console.error('Error updating access URLs:', error);
    }
}

// Initialize access URLs with session storage
function initializeAccessURLs() {
    try {
        // Auto-populate from session storage if available
        if (typeof(Storage) !== "undefined" && sessionStorage) {
            document.getElementById('accessInstanceIP').value = sessionStorage.getItem('computePublicIP') || '';
        }
        
        // Update URLs display
        updateAccessURLs();
    } catch (error) {
        console.error('Error initializing access URLs:', error);
    }
}

// Open URL in new tab
function openURL(type) {
    const instanceIP = document.getElementById('accessInstanceIP').value || 'INSTANCE_IP';
    
    if (instanceIP === 'INSTANCE_IP' || !instanceIP) {
        alert('Please enter your compute instance IP address first!');
        return;
    }
    
    let url;
    switch(type) {
        case 'frontend':
            url = `http://${instanceIP}:3000`;
            break;
        case 'swagger':
            url = `http://${instanceIP}:8080/swagger-ui.html`;
            break;
        case 'zipkin':
            url = `http://${instanceIP}:9411`;
            break;
        default:
            console.error('Unknown URL type:', type);
            return;
    }
    
    window.open(url, '_blank');
}

// Copy URL to clipboard
function copyURL(type) {
    const instanceIP = document.getElementById('accessInstanceIP').value || 'INSTANCE_IP';
    
    let textToCopy;
    switch(type) {
        case 'frontend':
            textToCopy = `http://${instanceIP}:3000`;
            break;
        case 'swagger':
            textToCopy = `http://${instanceIP}:8080/swagger-ui.html`;
            break;
        case 'zipkin':
            textToCopy = `http://${instanceIP}:9411`;
            break;
        case 'quickAccess':
            textToCopy = document.getElementById('quickAccessOutput').textContent;
            break;
        default:
            console.error('Unknown copy type:', type);
            return;
    }
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(textToCopy).then(function() {
            // Visual feedback
            const button = event.target;
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }).catch(function(err) {
            console.error('Could not copy text: ', err);
            fallbackCopyURL(textToCopy);
        });
    } else {
        fallbackCopyURL(textToCopy);
    }
}

// Fallback copy function for URLs
function fallbackCopyURL(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.left = "-999999px";
    textArea.style.top = "-999999px";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful) {
            const button = event.target;
            const originalText = button.innerHTML;
            button.innerHTML = '✅ Copied!';
            button.style.backgroundColor = '#A8E6A8';
            
            setTimeout(function() {
                button.innerHTML = originalText;
                button.style.backgroundColor = '#90EE90';
            }, 2000);
        }
    } catch (err) {
        console.error('Fallback: Could not copy text: ', err);
    }
    
    document.body.removeChild(textArea);
}

// Initialize access URLs when page loads
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeAccessURLs);
} else {
    initializeAccessURLs();
}
window.addEventListener('load', initializeAccessURLs);
setTimeout(initializeAccessURLs, 500);
setTimeout(initializeAccessURLs, 1500);
</script>

### Step 3: Transfer to Compute Instance

Enter your compute instance details to generate the transfer commands:

<div class="input-section">
<strong>SSH Key Filename:</strong> 
<input type="text" id="sshKeyName" placeholder="Enter SSH key filename (e.g., ssh-key-2025-01-01.key)" class="input-field" oninput="updateTransferCommands()"><br/>
<strong>Compute Instance IP:</strong> 
<input type="text" id="computeInstanceIP" placeholder="Enter compute instance IP (e.g., 195.168.2.124)" class="input-field" oninput="updateTransferCommands()"><br/>
<strong>Username:</strong> 
<input type="text" id="computeUsername" placeholder="ubuntu" value="ubuntu" class="input-field" oninput="updateTransferCommands()"><br/>
<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>Use <strong>ubuntu</strong> for Ubuntu instances</em>
</div>
</div>

**Generated Transfer Commands:**

<pre id="transferCommandContainer" class="interactive-command">
<code id="transferCommandOutput"># Create archive from the parent directory of oracle-saga-cloudbank
tar -czf oracle-saga-cloudbank.tar.gz oracle-saga-cloudbank/

# Transfer to compute instance
scp -i <span id="sshKeyDisplay">your-key.pem</span> oracle-saga-cloudbank.tar.gz <span id="usernameDisplay">ubuntu</span>@<span id="ipDisplay">INSTANCE_IP</span>:~/</code>
</pre>
<div class="button-center">
<button onclick="copyTransferCommands()" class="copy-btn-pastel">📋 Copy Transfer Commands</button>
</div>



### Step 4: Extract on Compute Instance

**Generated Connection and Extraction Commands:**

<pre id="extractCommandContainer" class="interactive-command">
<code id="extractCommandOutput"># Connect to compute instance
ssh -i <span id="sshKeyDisplayExtract">your-key.pem</span> <span id="usernameDisplayExtract">ubuntu</span>@<span id="ipDisplayExtract">INSTANCE_IP</span>

# Extract CloudBank application
cd ~/
tar -xzf oracle-saga-cloudbank.tar.gz
cd oracle-saga-cloudbank/

# Verify extraction
ls -la</code>
</pre>

<div class="button-center">
<button onclick="copyExtractCommands()" class="copy-btn-pastel">📋 Copy Connection & Extract Commands</button>
</div>


## Task 2: Verify CloudBank Environment Setup

---

Before starting the application services, we need to verify that the CloudBank environment setup from Lab 2 has completed successfully. In Lab 2, you ran a setup script that configured Podman, containers, and the CloudBank environment.

### Step 1: Verify Podman Installation

Check if Podman and related components were installed correctly:

  ```bash
  <copy>
  # Check Podman version
  podman --version

  # Check if podman-compose is available
  podman-compose --version

  # Verify container images were pulled
  podman images
  </copy>
  ```

**Expected Output:**
- Podman version 3.x or higher
- podman-compose available
- Container images including:
    - `ghcr.io/oracle/oraclelinux:8`
    - `container-registry.oracle.com/database/sqlcl:latest`
    - `docker.io/swaggerapi/swagger-ui:v5.20.7`
    - `docker.io/library/maven:3.8.6-openjdk-11`
    - `ghcr.io/openzipkin/zipkin:latest`

### Step 2: Verify CloudBank Directory Structure

Check if the CloudBank directories were created properly:

  ```bash
  <copy>
  # Check if cloudbank directory exists
  ls -la ~/cloudbank/

  # Verify current user permissions
  whoami

  # Check user groups (should include podman)
  groups
  </copy>
  ```

**Expected Output:**
- `/home/ubuntu/cloudbank/` directory exists
- Current user is `ubuntu`
- User belongs to `podman` group

### Step 3: Test Podman Functionality

Verify that Podman is working correctly:

  ```bash
  <copy>
  # Test podman socket
  systemctl status podman.socket

  # Check podman system info
  podman system info
  </copy>
  ```

**Expected Results:**
- Podman socket is active and running
- System info shows proper configuration

### Step 4: Retry Environment Setup (If Needed)

If any of the above verifications fail, you need to manually set up the CloudBank environment. Copy the following setup script and save it as `cloudbank-setup.sh`:

```bash
<copy>
#!/bin/bash
# CloudBank Environment Setup Script

# Update system packages
sudo apt-get update

# Install required components
sudo apt-get install -y podman curl wget pipx

# Add user to podman group
sudo usermod -aG podman ubuntu

# Create cloudbank directory
mkdir -p ~/cloudbank

# Pull required container images
podman pull ghcr.io/oracle/oraclelinux:8
podman pull container-registry.oracle.com/database/sqlcl:latest
podman pull docker.io/swaggerapi/swagger-ui:v5.20.7
podman pull docker.io/library/maven:3.8.6-openjdk-11
podman pull ghcr.io/openzipkin/zipkin:latest

echo "CloudBank environment setup completed!"
</copy>
```

**Run the setup:**
  ```bash
  <copy>
  # Make the script executable and run it
  chmod +x cloudbank-setup.sh
  sudo bash cloudbank-setup.sh

  # Log out and back in to refresh group membership
  exit
  </copy>
  ```

> **Note:** The setup script from Lab 2 configured Podman for the ubuntu user, pulled necessary container images, and created the CloudBank directory structure. If any component is missing, the CloudBank application deployment may fail.

---

## Task 3: Start Podman Services

---

CloudBank uses a **two-stage deployment** approach with different Podman profiles for database setup and application runtime.

### Step 1: Start Database Setup Profile

First, start services with the `adbsSetup` profile to initialize database connections:

```bash
cd ~/oracle-saga-cloudbank

# Start with database setup profile
podman-compose --profile adbsSetup up -d

# Check setup containers
podman ps
```

**Expected Output:**
```
CONTAINER ID  IMAGE                     STATUS        PORTS
a1b2c3d4e5f6  cloudbank/db-setup:latest Up 30 seconds 
```

Wait for database setup to complete (approximately 2-3 minutes):

```bash
# Monitor setup logs
podman logs -f <db-setup-container-id>

# Wait for completion message
echo "Waiting for database setup completion..."
```

### Step 2: Start Application Services

Once database setup completes, start the main application services with the `adbs` profile:

```bash
# Stop setup profile
podman-compose --profile adbsSetup down

# Start application profile
podman-compose --profile adbs up -d

# Verify all services are running
podman ps
```

**Expected Output:**
```
CONTAINER ID  IMAGE                        STATUS        PORTS
b2c3d4e5f6g7  cloudbank/frontend:latest    Up 1 minute   0.0.0.0:3000->3000/tcp
c3d4e5f6g7h8  cloudbank/banka:latest       Up 1 minute   0.0.0.0:8081->8080/tcp
d4e5f6g7h8i9  cloudbank/bankb:latest       Up 1 minute   0.0.0.0:8082->8080/tcp
e5f6g7h8i9j0  cloudbank/orchestrator:latest Up 1 minute  0.0.0.0:8080->8080/tcp
f6g7h8i9j0k1  cloudbank/zipkin:latest      Up 1 minute   0.0.0.0:9411->9411/tcp
```

### Step 3: Verify Service Health

Check the health status of all services:

```bash
# Check service logs
podman logs cloudbank_frontend_1
podman logs cloudbank_orchestrator_1
podman logs cloudbank_banka_1
podman logs cloudbank_bankb_1
```

## Task 4: Access Application Endpoints

---

Once all services are running, you can access CloudBank through multiple endpoints. The system will auto-populate your compute instance IP address for easy access.

### Step 1: Configure Instance IP Address

Enter your compute instance IP address to auto-generate all access URLs:

<div class="input-section">
<strong>Compute Instance IP:</strong> 
<input type="text" id="accessInstanceIP" placeholder="Enter your compute instance IP (e.g., 195.168.2.124)" class="input-field" oninput="updateAccessURLs()"><br/>
<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>Get your IP with: <code>curl -s ifconfig.me</code></em>
</div>
</div>

### Step 2: Frontend Application Access

**CloudBank Flask UI** - Main application interface for executing transactions:

<pre class="interactive-command">
<code>🌐 Frontend URL: http://<span id="frontendIP">INSTANCE_IP</span>:3000</code>
</pre>

<div class="button-center">
<button onclick="openURL('frontend')" class="copy-btn-pastel">🌐 Open CloudBank UI</button>
<button onclick="copyURL('frontend')" class="copy-btn-pastel">📋 Copy Frontend URL</button>
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

### Step 3: Swagger API Documentation

**CloudBank REST APIs** - Complete API documentation and testing interface:

<pre class="interactive-command">
<code>📡 Swagger URL: http://<span id="swaggerIP">INSTANCE_IP</span>:8080/swagger-ui.html</code>
</pre>

<div class="button-center">
<button onclick="openURL('swagger')" class="copy-btn-pastel">📡 Open Swagger APIs</button>
<button onclick="copyURL('swagger')" class="copy-btn-pastel">📋 Copy Swagger URL</button>
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

### Step 4: Monitoring and Tracing

**Zipkin Distributed Tracing** - Monitor saga execution across microservices:

<pre class="interactive-command">
<code>🔍 Zipkin URL: http://<span id="zipkinIP">INSTANCE_IP</span>:9411</code>
</pre>

<div class="button-center">
<button onclick="openURL('zipkin')" class="copy-btn-pastel">🔍 Open Zipkin Monitoring</button>
<button onclick="copyURL('zipkin')" class="copy-btn-pastel">📋 Copy Zipkin URL</button>
</div>

**Monitoring Capabilities:**
- End-to-end saga tracing
- Service dependency mapping  
- Performance bottleneck identification
- Failure point analysis
- Compensation flow visualization

**Expected Interface:**

![Zipkin Tracing](./images/lab5-zipkin.png "Zipkin distributed tracing interface")

## Task 5: Execute Transaction Scenarios

---

Now that CloudBank is fully deployed, you can test various transaction scenarios to observe Oracle Sagas in action.

### Scenario 1: Successful Intra-Bank Transfer

Execute a transfer between accounts within the same bank:

**Via Frontend UI:**
1. Navigate to CloudBank UI: `http://<INSTANCE_IP>:3000`
2. Select **"Intra-Bank Transfer"**
3. Choose source account: `BANKA-ACC-001` 
4. Choose destination account: `BANKA-ACC-002`
5. Enter amount: `250.00`
6. Click **"Execute Transfer"**

**Via Swagger API:**
```bash
curl -X POST "http://<INSTANCE_IP>:8080/transfer/intra-bank" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccount": "BANKA-ACC-001",
    "targetAccount": "BANKA-ACC-002", 
    "amount": 250.00,
    "currency": "USD"
  }'
```

**Expected Saga Flow:**
1. **Begin Saga** - Orchestrator starts intra-bank saga
2. **Reserve Funds** - Source account balance reserved using RESERVABLE column
3. **Execute Debit** - Funds debited from source account
4. **Execute Credit** - Funds credited to target account  
5. **Commit Saga** - Transaction completed successfully

**Verification:**
```sql
-- Check saga status
SELECT saga_id, status, outcome FROM DBA_SAGAS ORDER BY start_time DESC FETCH FIRST 1 ROWS ONLY;

-- Verify account balances
SELECT account_id, balance FROM banka.accounts WHERE account_id IN ('BANKA-ACC-001', 'BANKA-ACC-002');
```

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
1. **Begin Saga** - Orchestrator coordinates cross-bank saga
2. **BankA Debit Request** - Withdraw funds from source bank
3. **BankB Credit Request** - Deposit funds to target bank
4. **Confirmation Phase** - Both banks confirm success
5. **Commit Saga** - Distributed transaction completed

**Monitor in Zipkin:**
- View distributed trace spanning multiple services
- Observe network calls between BankA and BankB
- Verify saga coordination timing

---

### Scenario 3: Failure Handling (Insufficient Balance)

Test compensation when source account has insufficient funds:

**Setup:**
```sql  
-- Reduce account balance
UPDATE banka.accounts SET balance = 100.00 WHERE account_id = 'BANKA-ACC-001';
COMMIT;
```

**Execute Transfer:**
- Attempt to transfer `$1000.00` from `BANKA-ACC-001`
- Transfer should fail due to insufficient funds

**Expected Compensation Flow:**
1. **Begin Saga** - Transfer saga initiated
2. **Withdrawal Check** - BankA checks available balance
3. **Insufficient Funds** - Balance validation fails  
4. **Trigger Compensation** - Saga framework initiates rollback
5. **Rollback Saga** - All partial operations reversed

**Verification:**
```sql
-- Check compensated saga
SELECT saga_id, status, outcome FROM DBA_SAGAS WHERE outcome = 'COMPENSATED';

-- Verify balance unchanged  
SELECT account_id, balance FROM banka.accounts WHERE account_id = 'BANKA-ACC-001';
```

---

### Scenario 4: Crash Recovery Testing

Simulate service failure during saga execution:

**Step 1: Initiate Transfer**
```bash
# Start a large inter-bank transfer
curl -X POST "http://<INSTANCE_IP>:8080/transfer/inter-bank" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccount": "BANKA-ACC-001",
    "targetAccount": "BANKB-ACC-001", 
    "amount": 1000.00,
    "currency": "USD"
  }'
```

**Step 2: Simulate Service Crash**
```bash
# Kill BankB service during execution
podman stop cloudbank_bankb_1
```

**Step 3: Observe Saga State**
```sql
-- Check incomplete sagas
SELECT saga_id, status, participants FROM DBA_INCOMPLETE_SAGAS;

-- Monitor saga recovery attempts
SELECT saga_id, retry_count, last_retry FROM DBA_SAGA_RETRY_LOG;
```

**Step 4: Restart Service and Recovery**
```bash
# Restart BankB service
podman start cloudbank_bankb_1

# Monitor automatic recovery
watch "podman logs cloudbank_orchestrator_1 | tail -10"
```

**Expected Recovery:**
- Saga marked as `INCOMPLETE` during service outage
- Automatic retry mechanism engages after service restart
- Transaction either completes successfully or compensates cleanly
- No data inconsistency despite service failure

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