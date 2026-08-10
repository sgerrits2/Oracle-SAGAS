# Prepare your environment

## **Introduction**

This lab prepares an Autonomous Database, OCI network, compute instance, and Cloud Shell for the CloudBank demo application.

- Estimated time: 90 minutes

Watch the video below for a quick walk-through of the lab.

[Prepare your environment](videohub:1_nw8ufqzp:medium)

### Objectives

By completing this lab, you will be able to:

- Provision an Autonomous Database in OCI.
- Create and secure database users for saga orchestration.
- Set up networking (VCN, Subnet, Security Lists) for secure access.
- Launch and configure a Virtual Machine with Podman and the CloudBank demo containers.
- Connect to Autonomous Database with SQLcl and verify connectivity.

### Prerequisites

- A Free-Tier or LiveLabs Oracle Cloud account.
- Familiarity with OCI Console and basic Linux command line.

### Download CloudBank Demo Application Package

<div style="margin: 24px 0;">
  <a href="files/oracle-saga-cloudbank.zip?download=1" style="display: inline-block; padding: 16px 28px; background: #2e7d32; color: #ffffff !important; font-size: 18px; font-weight: 700; border-radius: 6px; text-decoration: none;">
    CloudBank Demo Application
  </a>
</div>

Download the CloudBank application source code, database setup files, and container configuration used in later labs.

## Task 1: Provision Infrastructure with a Script

---

### **Step 1: Download and Run the Provisioning Script**

Run the provisioning script in OCI Cloud Shell instead of completing the manual database, networking, and compute steps. It creates the Autonomous Database, VCN, internet gateway, route table, security list, public subnet, SSH key pair, and Ubuntu compute instance. It also installs Podman and pulls the required container images.

<div style="margin: 24px 0;">
  <a href="files/provision.sh?download=1" style="display: inline-block; padding: 16px 28px; background: #2e7d32; color: #ffffff !important; font-size: 18px; font-weight: 700; border-radius: 6px; text-decoration: none;">
    Provisioning Script
  </a>
</div>

Download the script, update `COMPARTMENT_ID` and `ADMIN_PASSWORD` in its configuration section, upload it to Cloud Shell, then run:

```bash
<copy>
chmod +x provision.sh
./provision.sh
</copy>
```


**The script does not create the CloudBank database users or prepare the application wallet. Complete Tasks 2 and 3 after it finishes.**

## Task 2: Create CloudBank Application–Specific Users via SQL Script

---

Before running the SQL script, let's configure the users we need to create. Rather than using fixed values, you can customize the usernames and passwords to match your preferences or organizational standards.

**We'll create eight different users as required for the CloudBank demo application with:**

- Correct **Saga roles** for each user type
- **500 MB** quota allocation on the `DATA` tablespace

### **Step 1: Configure User Details**

<div class="script-section">
<h5>SQL Script:</h5>
<pre id="generated-script" class="script-display">
-- Template Script (Click "Generate Script" to customize with your values)
-- ============================================================
-- CloudBank Demo - User creation
-- If a user already exists, it is simply skipped and execution continues.
-- ============================================================

-- 1) Create Broker & Orchestrator Schemas
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER brokerchicago IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('brokerchicago already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER orchestratorchicago IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('orchestratorchicago already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER brokermex IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('brokermex already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER orchestratormex IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('orchestratormex already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

-- 2) Create Bank Participant Schemas
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER bankchicago IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('bankchicago already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER bankmex IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('bankmex already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER banklondon IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('banklondon already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE USER banktokyo IDENTIFIED BY Welcome_123#';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1920 THEN
      DBMS_OUTPUT.PUT_LINE('banktokyo already exists, skipping.');
    ELSE
      RAISE;
    END IF;
END;
/

-- 3) Grant Saga Roles (GRANT is safe to run multiple times; it does not fail if already granted)
GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_CONNECT_ROLE
TO brokerchicago, brokermex;

GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_PARTICIPANT_ROLE
TO orchestratorchicago, orchestratormex,
bankchicago, bankmex, banklondon, banktokyo;

-- 4) Allocate Tablespace Quotas (ALTER is also safe to run multiple times)
ALTER USER brokerchicago QUOTA 500M ON DATA;
ALTER USER brokermex QUOTA 500M ON DATA;
ALTER USER orchestratorchicago QUOTA 500M ON DATA;
ALTER USER orchestratormex QUOTA 500M ON DATA;
ALTER USER bankchicago QUOTA 500M ON DATA;
ALTER USER bankmex QUOTA 500M ON DATA;
ALTER USER banklondon QUOTA 500M ON DATA;
ALTER USER banktokyo QUOTA 500M ON DATA;

-- 5) Final verification
SELECT username
FROM dba_users
WHERE username IN (
'BROKERCHICAGO','ORCHESTRATORCHICAGO','BROKERMEX','ORCHESTRATORMEX',
'BANKCHICAGO','BANKMEX','BANKLONDON','BANKTOKYO'
);

</pre>
<div class="copy-script-section">
<button onclick="copyScript()" class="copy-btn">Copy Script</button>
</div>
</div>

</div>

### **Step 2: Open SQL Worksheet**

On your **Oracle-Saga-Demo ATP** details page, click the **Database Actions** drop-down button, then select **SQL**. A new tab opens, already connected as `ADMIN`.

![Open Database Actions → SQL](./images/lab2-task2-1.png "Click Database Actions, then SQL")

### **Step 3: Paste Your Generated Script**

In the worksheet, paste the script you generated and copied in Step 1. The script will create all users with your specified names and passwords.

![Paste Generated Script](./images/lab2-task2-2.png "Paste your customized script in the SQL worksheet")

### **Step 4: Run as SQL Script**

Above the worksheet, click the **Run Script** option.

![Run SQL Script](./images/lab2-task2-3.png "Click the green Run button to execute the script")

### **Step 5: Confirm the DDL/DCL Messages**

In the output pane you'll see a sequence of messages for each statement, for example:

- `User BROKERCHICAGO created.`
- `Role SAGA_ADM_ROLE granted.`
- `User BROKERCHICAGO altered.`
 …and so on.

![Script output pane with DDL/DCL messages](./images/lab2-task2-4.png "Verify each CREATE, GRANT, and ALTER statement succeeded")

### **Step 6: Verify All Users (OPTIONAL)**

Run a quick `SELECT` to ensure all eight users exist:

```
<copy>
SELECT username
   FROM dba_users
   WHERE username IN (
   'BROKERCHICAGO','ORCHESTRATORCHICAGO','BROKERMEX','ORCHESTRATORMEX',
   'BANKCHICAGO','BANKMEX','BANKLONDON','BANKTOKYO'
   );
</copy>
```

![Verify user list output](./images/lab2-task2-5.png "All eight schemas should appear")

</div>

## Task 3: Connect to ADB & VM via Cloud Shell

---

Use Cloud Shell to prepare the CloudBank application, generate the database wallet, connect to Autonomous Database, and verify the compute instance.

### **Step 1: Access Cloud Shell**

From the OCI Console header, locate the **Developer Tools** icon (terminal icon) near the region selector on the top right corner and click **Cloud Shell**.

![Open Cloud Shell](./images/lab2-task5-1.png "Click Developer Tools → Cloud Shell")

### **Step 2: Initialize Cloud Shell Environment**

Cloud Shell is a free, browser-based terminal that provides:
- Pre-installed development tools including OCI CLI, kubectl, and SQL*Plus
- 5GB of persistent storage in your home directory
- Network access to all OCI services

The provisioning process takes 30-60 seconds. Once ready, you'll see a command prompt with your OCI username and a `$` prompt indicating the shell is ready for commands.

![Cloud Shell Ready](./images/lab2-task5-2.png "Cloud Shell provisioned and ready with command prompt")

### **Step 3: Review the CloudBank Demo Application Package**

Download the CloudBank application package using the button near the prerequisites. It contains:
- Microservice source code and configuration files
- Database setup scripts and schema definitions  
- Docker compose files for container orchestration
- Configuration templates for OCI integration

![Download CloudBank Package](./images/lab2-task5-3.png "Download CloudBank application files")

### **Step 4: Upload Files to Cloud Shell**

Upload the CloudBank application package and your SSH keys to Cloud Shell. Note that Cloud Shell requires uploading files one at a time:

1. In Cloud Shell, click the **Settings** icon (⚙️) in the top-right corner
2. Select **Upload** from the dropdown menu
3. Upload `oracle-saga-cloudbank.zip` (downloaded in Step 3) by selecting **"Choose from your computer"** or by drag-and-drop.

*Note: You must upload each file separately and click Upload for each one.*

![Upload Files to Cloud Shell](./images/lab2-task5-4.png "Upload files one at a time to Cloud Shell")

### **Step 5: Extract CloudBank Application**

Extract the CloudBank application package and inspect its contents:

```bash
<copy>
#!/usr/bin/env bash
set -euo pipefail

unzip oracle-saga-cloudbank.zip
cd oracle-saga-cloudbank
ls -la
</copy>
```

**What the script does:**

- **`unzip`** extracts the CloudBank application package.
- **`cd`** moves into the extracted application directory.
- **`ls -la`** lists the extracted files, including `adbsSetup/`, `CloudBank/`, `swagger-ui-config/`, and `osagaAdbsSetup.yaml`.

![Extract CloudBank Application](./images/lab2-task5-5.png "Extract and explore CloudBank files")

### **Step 6: Copy Your Autonomous Database OCID**

Navigate back to your Autonomous Database details page in the OCI Console:

1. Click the **navigation menu** (☰) in the top-left corner
2. Navigate to **Oracle Database** → **Autonomous Database**
3. Click on your **Oracle-Saga-Demo** database name (if you have multiple instances, it will be Oracle-Saga-Demo-1, Oracle-Saga-Demo-2, etc.)
4. In the database details page, locate the **OCID** field
5. Click **Copy** next to the OCID to copy the complete database identifier

![Copy ADB OCID](./images/lab2-task5-6.png "Copy Autonomous Database OCID from Oracle-Saga-Demo details page")

### **Step 7: Generate ADB Wallet using Interactive Builder**

Use the interactive form below to generate your wallet download command:

<div class="input-section">
<strong>Enter your Autonomous Database OCID:</strong><br/>
<strong>Autonomous Database OCID:</strong> <input type="text" id="adb-ocid" placeholder="ocid1.autonomousdatabase.oc1..example" class="input-field" oninput="updateWalletCommand()"><br/>
<strong>Wallet File Name:</strong> <input type="text" id="wallet-file" value="SagasWallet.zip" class="input-field" disabled><br/>
<strong>Wallet Password:</strong> <input type="text" id="wallet-password" value="Wallet123#" class="input-field" disabled>
</div>

**Generated Command (downloads wallet to oracle-saga-cloudbank/adbsSetup/adb_wallet directory):**

<pre id="wallet-command-container" class="interactive-command">
<span id="wallet-command" class="command-text">cd ..

oci db autonomous-database generate-wallet --autonomous-database-id &lt;ADB_OCID&gt; --file oracle-saga-cloudbank/adbsSetup/adb_wallet/&lt;WALLET_FILE&gt; --password &lt;WALLET_PASSWORD&gt;</span>
<button class="copy-btn" onclick="copyToClipboard('wallet-command', 'wallet-command-container')">Copy</button>
</pre>

This command will download the wallet directly into the `oracle-saga-cloudbank/adbsSetup/adb_wallet` directory where it's needed for database connections.

![Generate Wallet](./images/lab2-task5-7.png "Generate and download ADB wallet to adbsSetup directory")

### **Step 8: Extract Wallet Files**

Extract the wallet files in the adb_wallet directory using the command below (updates automatically based on your filename):

<pre id="unzip-command-container" class="interactive-command">
<span id="unzip-command" class="command-text">cd oracle-saga-cloudbank/adbsSetup/adb_wallet && unzip &lt;WALLET_FILE&gt;</span>
<button class="copy-btn" onclick="copyToClipboard('unzip-command', 'unzip-command-container')">Copy</button>
</pre>

![Extract Wallet](./images/lab2-task5-8.png "Extract wallet files in adb_wallet directory")

### **Step 9: Set `TNS_ADMIN` Environment Variable**

First, get your current directory path:

```
<copy>pwd</copy>
```

Use that path in the interactive form to set `TNS_ADMIN`:

<div class="input-section">
<strong>Current Directory Path:</strong> <input type="text" id="tns-path" placeholder="/home/username" class="input-field" oninput="updateTnsCommand()">
</div>

**Generated Command:**

<pre id="tns-command-container" class="interactive-command">
<span id="tns-command" class="command-text">export TNS_ADMIN=&lt;CURRENT_PATH&gt;/oracle-saga-cloudbank/adbsSetup/adb_wallet
echo $TNS_ADMIN</span>
<button class="copy-btn" onclick="copyToClipboard('tns-command', 'tns-command-container')">Copy</button>
</pre>

![Set TNS_ADMIN](./images/lab2-task5-9.png "Configure TNS_ADMIN environment variable")

### **Step 10: Connect to ADB using Interactive Builder**

Use the fixed connection string and enter the ADMIN password created in Task 1:

<div class="input-section">
<strong>Connection String:</strong> <input type="text" id="adb-connection-string" value="oraclesagademo_medium" class="input-field" disabled><br/>
<strong>Username:</strong> <input type="text" id="task5-db-username" value="ADMIN" class="input-field" disabled><br/>
<strong>Password:</strong> <input type="password" id="task5-db-password" placeholder="Enter the ADMIN password from Task 1" class="input-field" oninput="updateTask5ConnectionCommand()">
</div>

**Generated Command:**

<pre id="task5-connection-command-container" class="interactive-command">
<span id="task5-connection-command" class="command-text">sql &lt;USERNAME&gt;/&lt;PASSWORD&gt;@&lt;CONNECTION_STRING&gt;</span>
<button class="copy-btn" onclick="copyToClipboard('task5-connection-command', 'task5-connection-command-container')">Copy</button>
</pre>

![Connect to ADB](./images/lab2-task5-11.png "Connect to Autonomous Database")

### **Step 11: Verify Database Connection**

Once connected, verify your Saga roles:

```
<copy>SELECT granted_role
FROM user_role_privs
WHERE granted_role LIKE '%SAGA%';</copy>
```

![Verify Saga Roles](./images/lab2-task5-12.png "Check Saga user privileges")

### **Step 12: Exit Database Connection**

Exit SQLcl to return to Cloud Shell:

```
<copy>exit</copy>
```

### **Step 13: Verify SSH Key Permissions**

Secure your uploaded SSH keys and verify their permissions:

```bash
<copy>
chmod 600 ~/.ssh/cloudbank_key
ls -la ~/.ssh/cloudbank_key ~/.ssh/cloudbank_key.pub
</copy>
```

**What the script does:**

- **`chmod 600`** restricts private-key access to your user.
- **`ls -la`** verifies the key pair created by the provisioning script.

![Set Key Permissions](./images/lab2-task5-14.png "Set correct SSH key permissions")

### **Step 14: SSH to VM using Interactive Builder**

Use the `cloudbank_key` created by the provisioning script and the VM public IP printed at the end of Task 1 to generate your SSH command:

<div class="input-section">
<strong>VM Public IP:</strong> <input type="text" id="vm-ip" placeholder="xxx.xxx.xxx.xxx" class="input-field" oninput="updateSshCommand()"><br/>

<div style="display: contents; align-items: center; gap: 10px;">
<strong>SSH Key Filename:</strong> <input type="text" id="ssh-key-name" placeholder="ssh-key-2025-01-01.key" class="input-field" oninput="updateSshCommand()" style="flex: 1;">
<button onclick="saveSshKeyName()" class="save-btn-small">Save</button>
<button onclick="deleteSshKeyName()" class="delete-btn-small">Delete</button>
<button onclick="clearSshKeyName()" class="clear-btn-small">Clear</button>
</div>
</div>

<div id="ssh-save-status" style="display:none;" class="save-status">
<span id="ssh-save-message"></span>
</div>

**Generated Command:**

<pre id="ssh-command-container" class="interactive-command">
<span id="ssh-command" class="command-text">ssh -i &lt;SSH_KEY_FILE&gt; ubuntu@&lt;VM_PUBLIC_IP&gt;</span>
<button class="copy-btn" onclick="copyToClipboard('ssh-command', 'ssh-command-container')">Copy</button>
</pre>

![SSH to VM](./images/lab2-task5-15.png "SSH connection to compute instance")

### **Step 15: Verify VM Environment**

Once connected to your VM, verify that cloud-init setup completed:

```bash
<copy>
#!/usr/bin/env bash
set -euo pipefail

podman --version
podman images
ls -la /home/ubuntu/cloudbank
</copy>
```

**What the script does:**

- **`podman --version`** confirms that Podman is installed.
- **`podman images`** shows the container images pulled during initialization.
- **`ls -la`** confirms that the CloudBank working directory exists.

![Verify VM Setup](./images/lab2-task5-16.png "Verify Podman and CloudBank environment")

### **Step 16: Exit VM and Explore CloudBank Code**

Exit the VM SSH session with `exit`. Back in Cloud Shell, inspect the CloudBank application directory:

```bash
<copy>
#!/usr/bin/env bash
set -euo pipefail

cd ~/oracle-saga-cloudbank
ls -la
</copy>
```

**What the script does:**

- **`cd`** moves to the CloudBank application directory in Cloud Shell.
- **`ls -la`** lists the application files and directories.

![Explore CloudBank](./images/lab2-task5-17.png "Explore CloudBank application structure")

### **Step 17: Open Cloud Shell Editor**

From the Cloud Shell toolbar, click the **Editor** icon to explore the CloudBank code structure in detail.

![Open Editor](./images/lab2-task5-18.png "Open Cloud Shell code editor")

### **Step 18: Explore Application Structure in Editor**

In the editor, navigate through the CloudBank directory structure:

- `docker-compose.yml` - Container orchestration configuration
- `src/` - Java source code for microservices
- `adbsSetup/` - Database setup scripts
- `swagger-ui-config/` - API documentation configuration

![Explore Code Structure](./images/lab2-task5-19.png "Explore CloudBank application code structure")

### **Step 19: Configure Tab Layout for Better Workflow**

Optionally select **Actions** → **Tabs** to switch easily between Cloud Shell and the editor.

![Configure Tabs](./images/configure-tabs.png "Configure tabs for better workflow")

**Expected Output**:

- ✅ Cloud Shell successfully provisioned and accessible
- ✅ CloudBank application package downloaded and uploaded to Cloud Shell
- ✅ CloudBank application extracted and directory structure visible
- ✅ ADB wallet generated and configured with interactive commands
- ✅ TNS_ADMIN environment variable properly set
- ✅ Successful connection to Autonomous Database verified
- ✅ Saga user roles and privileges confirmed
- ✅ SSH keys uploaded and configured with correct permissions
- ✅ Successful SSH connection to Ubuntu compute instance established
- ✅ VM environment verified with Podman and required images
- ✅ Cloud Shell editor ready for code exploration and modification

### Benefits of This Approach

- **Offline Access**: CloudBank application files are available locally before upload
- **Version Control**: Specific tested version of CloudBank application provided
- **Faster Setup**: No dependency on external git repositories during lab execution
- **Interactive Commands**: Reduced errors with dynamic command generation
- **Consistent Environment**: Everyone uses the same application version

### CloudBank Application Structure

The uploaded package contains:

- **docker-compose.yml**: Container orchestration configuration
- **src/**: Java microservices source code
- **adbsSetup/**: Database initialization scripts
- **swagger-ui-config/**: API documentation setup
- **README.md**: Application setup and usage instructions

> **Note**: The interactive command builders ensure you use the correct parameters for your specific environment, reducing setup time and potential errors.

You may now [proceed to the next lab](#next).

<style>
/* Common Input Styles */
.input-section {
   background: #f9f9f9;
   padding: 15px;
   border-radius: 5px;
   margin: 10px 0;
   border: 1px solid #ddd;
}

.input-field {
   width: 300px;
   padding: 8px;
   font-size: 14px;
   border: 1px solid #ccc;
   border-radius: 4px;
   margin: 5px;
}

/* Common Button Styles */
.save-btn {
   background: #0572ce;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 4px;
   cursor: pointer;
   font-size: 14px;
   margin: 5px;
   min-width: 140px;
   height: 40px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.save-btn:hover {
   background: #054c99;
}

.clear-btn {
   background: #999999;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 4px;
   cursor: pointer;
   font-size: 14px;
   margin: 5px;
   min-width: 140px;
   height: 40px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.clear-btn:hover {
   background: #808080;
}

.delete-btn {
   background: #8b5a2b;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 4px;
   cursor: pointer;
   font-size: 14px;
   margin: 5px;
   min-width: 140px;
   height: 40px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.delete-btn:hover {
   background: #6d4520;
}

/* Small Button Styles for Connection String Section */
.save-btn-small {
   background: #0572ce;
   color: white;
   border: none;
   padding: 6px 12px;
   border-radius: 3px;
   cursor: pointer;
   font-size: 12px;
   margin: 3px;
   min-width: 60px;
   height: 28px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.save-btn-small:hover {
   background: #054c99;
}

.clear-btn-small {
   background: #999999;
   color: white;
   border: none;
   padding: 6px 12px;
   border-radius: 3px;
   cursor: pointer;
   font-size: 12px;
   margin: 3px;
   min-width: 60px;
   height: 28px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.clear-btn-small:hover {
   background: #808080;
}

.delete-btn-small {
   background: #8b5a2b;
   color: white;
   border: none;
   padding: 6px 12px;
   border-radius: 3px;
   cursor: pointer;
   font-size: 12px;
   margin: 3px;
   min-width: 60px;
   height: 28px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.delete-btn-small:hover {
   background: #6d4520;
}

.update-btn {
   background: #17a2b8;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 4px;
   cursor: pointer;
   font-size: 14px;
   margin: 5px;
   min-width: 140px;
   height: 40px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.update-btn:hover {
   background: #138496;
}

.reset-btn {
   background: #8b5a2b;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 4px;
   cursor: pointer;
   font-size: 14px;
   margin: 5px;
   min-width: 140px;
   height: 40px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.reset-btn:hover {
   background: #6d4520;
}

.copy-btn {
   background: #7FB069;
   color: white;
   border: none;
   padding: 10px 20px;
   border-radius: 12px;
   cursor: pointer;
   font-weight: 500;
   transition: all 0.3s ease;
   font-size: 14px;
   margin: 5px;
   min-width: 160px;
   height: 60px;
   display: inline-flex;
   align-items: center;
   justify-content: center;
}

.copy-btn:hover {
   background: #6FA055;
   color: white;
   transform: translateY(-1px);
   box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

/* Status Message Styles */
.save-status {
   padding: 15px;
   border-radius: 5px;
   margin: 10px 0;
   border: 1px solid #ddd;
   min-height: 60px;
   display: contents;
   align-items: center;
}

/* Section Layout Styles */
.save-controls {
   text-align: center;
   margin: 15px 0;
}

/* Task 2 Specific Styles */
.user-config-section {
   background: #f9f9f9;
   padding: 20px;
   border-radius: 5px;
   margin: 15px 0;
   border: 1px solid #ddd;
}

.java-users-section, .plsql-users-section {
   background: white;
   padding: 15px;
   border-radius: 4px;
   border: 1px solid #ddd;
   margin-bottom: 15px;
}

.java-users-section h5, .plsql-users-section h5 {
   margin-bottom: 15px;
   color: #2c3e50;
   border-bottom: 2px solid #ecf0f1;
   padding-bottom: 5px;
}

.user-grid {
   display: grid;
   gap: 10px;
}

.user-row {
   display: grid;
   grid-template-columns: 120px 150px 150px 60px 1fr;
   align-items: center;
   gap: 10px;
   padding: 8px;
   border-bottom: 1px solid #f1f1f1;
}

.user-row:last-child {
   border-bottom: none;
}

.user-row label {
   font-weight: bold;
   color: #2c3e50;
}

.user-field, .password-field {
   padding: 6px;
   font-size: 13px;
   border: 1px solid #ccc;
   border-radius: 3px;
}

.user-row small {
   color: #7f8c8d;
   font-style: italic;
}

.user-header {
   display: grid;
   grid-template-columns: 120px 150px 150px 60px 1fr;
   align-items: center;
   gap: 10px;
   padding: 8px;
   background: #ecf0f1;
   font-weight: bold;
   color: #2c3e50;
   border-bottom: 2px solid #bdc3c7;
   margin-bottom: 5px;
}

.clear-user-btn {
   background: #dc3545;
   color: white;
   border: none;
   padding: 4px 8px;
   border-radius: 3px;
   cursor: pointer;
   font-size: 11px;
   margin-left: 5px;
}

.clear-user-btn:hover {
   background: #c82333;
}

.script-section {
   background: white;
   padding: 15px;
   border-radius: 4px;
   border: 1px solid #ddd;
}

.script-section h5 {
   margin-bottom: 10px;
   color: #2c3e50;
}

.script-display {
   background: #f8f9fa;
   border: 1px solid #e9ecef;
   border-radius: 4px;
   padding: 15px;
   font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
   font-size: 12px;
   white-space: pre-wrap;
   max-height: 400px;
   overflow-y: auto;
   margin: 0;
}

.copy-script-section {
   text-align: center;
   margin-top: 10px;
   padding-top: 10px;
   border-top: 1px solid #e9ecef;
}

.script-controls {
   text-align: center;
   margin: 15px 0;
}

.save-user-config-section {
   background: #f9f9f9;
   padding: 20px;
   border-radius: 5px;
   margin: 15px 0;
   border: 1px solid #ddd;
}

.saved-config-section {
   background: #e8f6ff;
   padding: 20px;
   border-radius: 5px;
   margin: 15px 0;
   border: 1px solid #b3d9ff;
}

.readonly-config-grid {
   display: grid;
   grid-template-columns: 1fr 1fr;
   gap: 20px;
   margin-bottom: 15px;
}

.readonly-column {
   background: white;
   padding: 15px;
   border-radius: 4px;
   border: 1px solid #ddd;
}

.readonly-column h5 {
   margin-bottom: 10px;
   color: #2c3e50;
}

.saved-value {
   font-family: monospace;
   background: #f8f9fa;
   padding: 2px 6px;
   border-radius: 3px;
   color: #2c3e50;
   font-weight: bold;
}

/* Task 3 SSH configuration */

/* Task 3 interactive command styles */
.interactive-command {
   display: content;
   align-items: center;
   background: #f5f5f5;
   border: 1px solid #ccc;
   padding: 10px;
   border-radius: 5px;
   position: relative;
   transition: opacity 0.3s;
   margin: 10px 0;
}

.interactive-command .copy-btn {
   position: absolute;
   right: -10px;
   top: -10px;
   background: white;
   border: 1px solid #ccc;
   padding: 3px 8px;
   cursor: pointer;
   font-size: 15px;
   transition: background 0.2s, color 0.2s;
   min-width: auto;
   height: auto;
}

.interactive-command .copy-btn:hover {
   background: #6FA055;
   color: white;
}

.command-text {
   font-family: monospace;
   white-space: pre-line;
   flex-grow: 1;
}

/* Mobile Responsive */
@media (max-width: 768px) {
   .user-row, .user-header {
       grid-template-columns: 1fr;
       gap: 5px;
   }
   
   .user-header {
       display: none;
   }
   
   .user-row label {
       font-size: 14px;
   }
   
   .readonly-config-grid {
       grid-template-columns: 1fr;
   }
   
   .input-field {
       width: 100%;
       max-width: 300px;
   }
}
</style>

<script>
// Common utility functions
function copyToClipboard(elementId, containerId) {
   let text = document.getElementById(elementId).innerText;
   navigator.clipboard.writeText(text);
   let container = document.getElementById(containerId);
   container.style.opacity = "0.5";
   setTimeout(() => container.style.opacity = "1", 200);
}

// Task 1 Functions
function saveAdbConfig() {
   let adbName = document.getElementById('adb-name').value.trim();
   let adbPassword = document.getElementById('adb-password').value.trim();
   
   let savedItems = [];
   let skippedItems = [];
   
   if (adbName) {
       sessionStorage.setItem('cloudbank_ADB_DATABASE_NAME', adbName);
       savedItems.push(`Database Name: ${adbName}`);
   } else {
       skippedItems.push('Database Name (empty)');
   }
   
   if (adbPassword) {
       sessionStorage.setItem('cloudbank_ADB_DATABASE_PASSWORD', adbPassword);
       savedItems.push(`Password: ${adbPassword}`);
       
       const task5PasswordField = document.getElementById('task5-db-password');
       if (task5PasswordField) {
           task5PasswordField.value = adbPassword;
           updateTask5ConnectionCommand();
       }
   } else {
       skippedItems.push('Password (empty)');
   }
   
   let message = '';
   
   if (savedItems.length > 0) {
       message += `✅ <strong>Saved Successfully:</strong><br/>${savedItems.join('<br/>')}`;
   }
   
   if (skippedItems.length > 0) {
       if (savedItems.length > 0) message += '<br/><br/>';
       message += `⚠️ <strong>Not Saved:</strong><br/>${skippedItems.join('<br/>')}`;
   }
   
   if (savedItems.length === 0) {
       message = '⚠️ <strong>Nothing to Save</strong><br/>Please enter at least one value to save.';
       document.getElementById('save-status').style.background = '#fff3cd';
       document.getElementById('save-status').style.color = '#856404';
   } else {
       message += '<br/><small>Saved values will be automatically used in later labs.</small>';
       document.getElementById('save-status').style.background = '#d4edda';
       document.getElementById('save-status').style.color = '#155724';
   }
   
   document.getElementById('save-message').innerHTML = message;
   document.getElementById('save-status').style.display = 'block';
   
   const button = document.querySelector('button[onclick="saveAdbConfig()"]');
   const originalText = button.textContent;
   button.textContent = 'Saved!';
   button.style.background = '#28a745';
   setTimeout(() => {
       button.textContent = originalText;
       button.style.background = '#0572ce';
   }, 2000);
}

function deleteStoredConfig() {
   sessionStorage.removeItem('cloudbank_ADB_DATABASE_NAME');
   sessionStorage.removeItem('cloudbank_ADB_DATABASE_PASSWORD');
   
   const task5PasswordField = document.getElementById('task5-db-password');
   if (task5PasswordField) {
       task5PasswordField.value = '';
       updateTask5ConnectionCommand();
   }
   
   document.getElementById('save-message').innerHTML = `
       🗑️ <strong>Saved Configuration Deleted</strong><br/>
       All previously saved database configuration has been removed from browser session.
   `;
   document.getElementById('save-status').style.display = 'block';
   document.getElementById('save-status').style.background = '#f8d7da';
   document.getElementById('save-status').style.color = '#721c24';
}

function clearFields() {
   document.getElementById('adb-name').value = '';
   document.getElementById('adb-password').value = '';
   document.getElementById('save-status').style.display = 'none';
}

// Task 2 Functions
function generateScript() {
   const users = {
       brokerchicago: {
           name: document.getElementById('brokerchicago-user').value.trim() || null,
           password: document.getElementById('brokerchicago-password').value.trim() || null
       },
       orchestratorchicago: {
           name: document.getElementById('orchestratorchicago-user').value.trim() || null,
           password: document.getElementById('orchestratorchicago-password').value.trim() || null
       },
       brokermex: {
           name: document.getElementById('brokermex-user').value.trim() || null,
           password: document.getElementById('brokermex-password').value.trim() || null
       },
       orchestratormex: {
           name: document.getElementById('orchestratormex-user').value.trim() || null,
           password: document.getElementById('orchestratormex-password').value.trim() || null
       },
       bankchicago: {
           name: document.getElementById('bankchicago-user').value.trim() || null,
           password: document.getElementById('bankchicago-password').value.trim() || null
       },
       bankmex: {
           name: document.getElementById('bankmex-user').value.trim() || null,
           password: document.getElementById('bankmex-password').value.trim() || null
       },
       banklondon: {
           name: document.getElementById('banklondon-user').value.trim() || null,
           password: document.getElementById('banklondon-password').value.trim() || null
       },
       banktokyo: {
           name: document.getElementById('banktokyo-user').value.trim() || null,
           password: document.getElementById('banktokyo-password').value.trim() || null
       }
   };
   
   let script = `-- Customized Script Generated from User Configuration\n`;
   
   const brokers = [users.brokerchicago, users.brokermex].filter(u => u.name && u.password);
   const others = [users.orchestratorchicago, users.orchestratormex, users.bankchicago, users.bankmex, users.banklondon, users.banktokyo].filter(u => u.name && u.password);
   
   if (brokers.length === 0 && others.length === 0) {
       script = `-- No valid users configured (all fields are empty)\n-- Please fill in at least one complete user (username and password) to generate a script.`;
   } else {
       if (brokers.length > 0) {
           script += `-- 1) Create Broker & Orchestrator Schemas\n`;
           brokers.forEach(user => {
               script += `CREATE USER ${user.name} IDENTIFIED BY ${user.password};\n`;
           });
           script += `\n`;
       }
       
       if (others.length > 0) {
           script += `-- 2) Create Other User Schemas\n`;
           others.forEach(user => {
               script += `CREATE USER ${user.name} IDENTIFIED BY ${user.password};\n`;
           });
           script += `\n`;
       }
       
       if (brokers.length > 0) {
           script += `-- 3) Grant Saga Roles\n`;
           script += `GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_CONNECT_ROLE\n    TO ${brokers.map(u => u.name).join(', ')};\n\n`;
       }
       
       if (others.length > 0) {
           script += `GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_PARTICIPANT_ROLE\n    TO ${others.map(u => u.name).join(', ')};\n\n`;
       }
       
       if (brokers.length > 0 || others.length > 0) {
           script += `-- 4) Allocate Tablespace Quotas\n`;
           [...brokers, ...others].forEach(user => {
               script += `ALTER USER ${user.name} QUOTA 500M ON DATA;\n`;
           });
       }
   }
   
   document.getElementById('generated-script').textContent = script;
   
   const button = document.querySelector('button[onclick="generateScript()"]');
   const originalText = button.textContent;
   button.textContent = 'Generated!';
   button.style.background = '#28a745';
   setTimeout(() => {
       button.textContent = 'Generate';
       button.style.background = '#0572ce';
   }, 2000);
}

function clearUser(userKey) {
   const userField = document.getElementById(`${userKey}-user`);
   const passwordField = document.getElementById(`${userKey}-password`);
   
   if (userField) userField.value = '';
   if (passwordField) passwordField.value = '';
}

function copyScript() {
   const script = document.getElementById('generated-script').textContent;
   navigator.clipboard.writeText(script);
   
   const button = document.querySelector('button[onclick="copyScript()"]');
   const originalText = button.textContent;
   button.textContent = 'Copied!';
   button.style.background = '#28a745';
   
   setTimeout(() => {
       button.textContent = originalText;
       button.style.background = '#28a745';
   }, 2000);
}

function resetToDefaults() {
   const defaults = {
       'brokerchicago-user': 'brokerchicago', 'brokerchicago-password': 'Welcome_123#',
       'orchestratorchicago-user': 'orchestratorchicago', 'orchestratorchicago-password': 'Welcome_123#',
       'brokermex-user': 'brokermex', 'brokermex-password': 'Welcome_123#',
       'orchestratormex-user': 'orchestratormex', 'orchestratormex-password': 'Welcome_123#',
       'bankchicago-user': 'bankchicago', 'bankchicago-password': 'Welcome_123#',
       'bankmex-user': 'bankmex', 'bankmex-password': 'Welcome_123#',
       'banklondon-user': 'banklondon', 'banklondon-password': 'Welcome_123#',
       'banktokyo-user': 'banktokyo', 'banktokyo-password': 'Welcome_123#'
   };
   
   Object.keys(defaults).forEach(id => {
       const element = document.getElementById(id);
       if (element) element.value = defaults[id];
   });
   
   const button = document.querySelector('button[onclick="resetToDefaults()"]');
   const originalText = button.textContent;
   button.textContent = 'Reset!';
   button.style.background = '#28a745';
   setTimeout(() => {
       button.textContent = originalText;
       button.style.background = '#8b5a2b';
   }, 2000);
}

function saveUserConfig() {
   ['BROKERCHICAGO', 'ORCHESTRATORCHICAGO', 'BROKERMEX', 'ORCHESTRATORMEX', 'BANKCHICAGO', 'BANKMEX', 'BANKLONDON', 'BANKTOKYO'].forEach(user => {
       sessionStorage.removeItem(`cloudbank_USER_${user}`);
       sessionStorage.removeItem(`cloudbank_PASSWORD_${user}`);
   });
   
   const users = {
       brokerchicago: document.getElementById('brokerchicago-user').value.trim() || null,
       orchestratorchicago: document.getElementById('orchestratorchicago-user').value.trim() || null,
       brokermex: document.getElementById('brokermex-user').value.trim() || null,
       orchestratormex: document.getElementById('orchestratormex-user').value.trim() || null,
       bankchicago: document.getElementById('bankchicago-user').value.trim() || null,
       bankmex: document.getElementById('bankmex-user').value.trim() || null,
       banklondon: document.getElementById('banklondon-user').value.trim() || null,
       banktokyo: document.getElementById('banktokyo-user').value.trim() || null
   };
   
   const passwords = {
       brokerchicago: document.getElementById('brokerchicago-password').value.trim() || null,
       orchestratorchicago: document.getElementById('orchestratorchicago-password').value.trim() || null,
       brokermex: document.getElementById('brokermex-password').value.trim() || null,
       orchestratormex: document.getElementById('orchestratormex-password').value.trim() || null,
       bankchicago: document.getElementById('bankchicago-password').value.trim() || null,
       bankmex: document.getElementById('bankmex-password').value.trim() || null,
       banklondon: document.getElementById('banklondon-password').value.trim() || null,
       banktokyo: document.getElementById('banktokyo-password').value.trim() || null
   };
   
   let savedCount = 0;
   let nullCount = 0;
   
   Object.keys(users).forEach(key => {
       sessionStorage.setItem(`cloudbank_USER_${key.toUpperCase()}`, users[key]);
       sessionStorage.setItem(`cloudbank_PASSWORD_${key.toUpperCase()}`, passwords[key]);
       
       if (users[key] && passwords[key]) {
           savedCount++;
       } else {
           nullCount++;
       }
   });
   
   Object.keys(users).forEach(key => {
       const displayElement = document.getElementById(`saved-${key}`);
       if (displayElement) {
           if (users[key] && passwords[key]) {
               displayElement.innerHTML = `${users[key]}<br/><small>Password: ${passwords[key]}</small>`;
           } else {
               displayElement.innerHTML = `<em>-</em><br/><small>Password: <em>-</em></small>`;
           }
       }
   });
   
   const configSection = document.querySelector('.saved-config-section h4');
   if (configSection) configSection.textContent = 'Previously Saved Configuration';
   
   document.getElementById('user-save-message').innerHTML = `
       ✅ <strong>Configuration Saved Successfully!</strong><br/>
       ${savedCount} complete users saved, ${nullCount} empty fields saved as null.<br/>
       <small>Configuration will persist until you close the browser tab.</small>
   `;
   document.getElementById('user-save-status').style.display = 'block';
   document.getElementById('user-save-status').style.background = '#d4edda';
   document.getElementById('user-save-status').style.color = '#155724';
   
   const button = document.querySelector('button[onclick="saveUserConfig()"]');
   const originalText = button.textContent;
   button.textContent = 'Saved!';
   button.style.background = '#28a745';
   
   setTimeout(() => {
       button.textContent = originalText;
       button.style.background = '#0572ce';
   }, 2000);
}

function deleteUserConfig() {
   ['BROKERCHICAGO', 'ORCHESTRATORCHICAGO', 'BROKERMEX', 'ORCHESTRATORMEX', 'BANKCHICAGO', 'BANKMEX', 'BANKLONDON', 'BANKTOKYO'].forEach(user => {
       sessionStorage.removeItem(`cloudbank_USER_${user}`);
       sessionStorage.removeItem(`cloudbank_PASSWORD_${user}`);
   });

   const configSection = document.querySelector('.saved-config-section h4');
   if (configSection) configSection.textContent = 'Current Configuration (Not Yet Saved)';
   
   document.getElementById('user-save-message').innerHTML = `
       🗑️ <strong>Configuration Deleted</strong><br/>
       User configuration has been removed from browser session.
   `;
   document.getElementById('user-save-status').style.display = 'block';
   document.getElementById('user-save-status').style.background = '#f8d7da';
   document.getElementById('user-save-status').style.color = '#721c24';
}

function updateUserConfig() {
   const users = {
       brokerchicago: document.getElementById('brokerchicago-user').value.trim(),
       orchestratorchicago: document.getElementById('orchestratorchicago-user').value.trim(),
       brokermex: document.getElementById('brokermex-user').value.trim(),
       orchestratormex: document.getElementById('orchestratormex-user').value.trim(),
       bankchicago: document.getElementById('bankchicago-user').value.trim(),
       bankmex: document.getElementById('bankmex-user').value.trim(),
       banklondon: document.getElementById('banklondon-user').value.trim(),
       banktokyo: document.getElementById('banktokyo-user').value.trim()
   };
   
   const passwords = {
       brokerchicago: document.getElementById('brokerchicago-password').value.trim(),
       orchestratorchicago: document.getElementById('orchestratorchicago-password').value.trim(),
       brokermex: document.getElementById('brokermex-password').value.trim(),
       orchestratormex: document.getElementById('orchestratormex-password').value.trim(),
       bankchicago: document.getElementById('bankchicago-password').value.trim(),
       bankmex: document.getElementById('bankmex-password').value.trim(),
       banklondon: document.getElementById('banklondon-password').value.trim(),
       banktokyo: document.getElementById('banktokyo-password').value.trim()
   };

   Object.keys(users).forEach(key => {
       const displayElement = document.getElementById(`saved-${key}`);
       if (displayElement) {
           if (users[key] || passwords[key]) {
               displayElement.innerHTML = `${users[key] || '<em>-</em>'}<br/><small>Password: ${passwords[key] || '<em>-</em>'}</small>`;
           } else {
               displayElement.innerHTML = `<em>-</em><br/><small>Password: <em>-</em></small>`;
           }
       }
   });
   
   const configSection = document.querySelector('.saved-config-section h4');
   if (configSection) configSection.textContent = 'Current Configuration (Not Yet Saved)';
   
   document.getElementById('user-save-message').innerHTML = `
       🔄 <strong>Display Updated!</strong><br/>
       Showing current form values. Click "Save User Configuration" to persist these changes.<br/>
       <small>Changes are not saved until you click Save.</small>
   `;
   document.getElementById('user-save-status').style.display = 'block';
   document.getElementById('user-save-status').style.background = '#fff3cd';
   document.getElementById('user-save-status').style.color = '#856404';
   
   const button = document.querySelector('button[onclick="updateUserConfig()"]');
   const originalText = button.textContent;
   button.textContent = 'Updated!';
   button.style.background = '#28a745';
   
   setTimeout(() => {
       button.textContent = originalText;
       button.style.background = '#17a2b8';
   }, 2000);
}

// Task 3 functions
function saveComputeIP() {
   let publicIP = document.getElementById('compute-public-ip').value;
   if (publicIP) {
       sessionStorage.setItem('computePublicIP', publicIP);
       
       const task5VmIpField = document.getElementById('vm-ip');
       if (task5VmIpField) {
           task5VmIpField.value = publicIP;
           updateSshCommand();
       }
       
       document.getElementById('compute-ip-save-message').innerHTML = `
           ✅ <strong>Public IP Saved Successfully!</strong><br/>
           IP address stored and will be available for use in subsequent tasks.<br/>
           <small>Configuration will persist until you close the browser tab.</small>
       `;
       document.getElementById('compute-ip-save-status').style.display = 'block';
       document.getElementById('compute-ip-save-status').style.background = '#d4edda';
       document.getElementById('compute-ip-save-status').style.color = '#155724';
       
       const button = document.querySelector('button[onclick="saveComputeIP()"]');
       const originalText = button.textContent;
       button.textContent = 'Saved!';
       button.style.background = '#28a745';
       setTimeout(() => {
           button.textContent = originalText;
           button.style.background = '#0572ce';
       }, 2000);
   } else {
       document.getElementById('compute-ip-save-message').innerHTML = `
           ⚠️ <strong>Please Enter IP Address</strong><br/>
           Enter a valid public IP address before saving.
       `;
       document.getElementById('compute-ip-save-status').style.display = 'block';
       document.getElementById('compute-ip-save-status').style.background = '#fff3cd';
       document.getElementById('compute-ip-save-status').style.color = '#856404';
   }
}

function deleteComputeIP() {
   sessionStorage.removeItem('computePublicIP');
   
   const task5VmIpField = document.getElementById('vm-ip');
   if (task5VmIpField) {
       const legacySshVmIp = sessionStorage.getItem('sshVmIp');
       if (legacySshVmIp) {
           task5VmIpField.value = legacySshVmIp;
       } else {
           task5VmIpField.value = '';
       }
       updateSshCommand();
   }
   
   document.getElementById('compute-ip-save-message').innerHTML = `
       🗑️ <strong>Saved IP Deleted</strong><br/>
       Public IP address has been removed from browser session.
   `;
   document.getElementById('compute-ip-save-status').style.display = 'block';
   document.getElementById('compute-ip-save-status').style.background = '#f8d7da';
   document.getElementById('compute-ip-save-status').style.color = '#721c24';
}

function clearComputeIP() {
   document.getElementById('compute-public-ip').value = '';
   const statusEl = document.getElementById('compute-ip-save-status');
   if (statusEl) {
       statusEl.style.display = 'none';
   }
}

function updateWalletCommand() {
   let ocid = document.getElementById('adb-ocid').value || '<ADB_OCID>';
   let file = document.getElementById('wallet-file').value || '<WALLET_FILE>';
   let password = document.getElementById('wallet-password').value || '<WALLET_PASSWORD>';
   
   let command = `cd .. 
   oci db autonomous-database generate-wallet --autonomous-database-id ${ocid} --file oracle-saga-cloudbank/adbsSetup/adb_wallet/${file} --password ${password}`;
   const commandEl = document.getElementById('wallet-command');
   if (commandEl) commandEl.innerText = command;
   
   let unzipCmd = `cd oracle-saga-cloudbank/adbsSetup/adb_wallet && unzip ${file}
   cd ~`;
   const unzipEl = document.getElementById('unzip-command');
   if (unzipEl) unzipEl.innerText = unzipCmd;
}

function updateTnsCommand() {
   let path = document.getElementById('tns-path').value || '<CURRENT_PATH>';
   let command = `export TNS_ADMIN=${path}/oracle-saga-cloudbank/adbsSetup/adb_wallet\necho $TNS_ADMIN`;
   const commandEl = document.getElementById('tns-command');
   if (commandEl) commandEl.innerText = command;
}

function updateSshCommand() {
   let vmIp = document.getElementById('vm-ip').value || '<VM_PUBLIC_IP>';
   let sshKey = document.getElementById('ssh-key-name').value || '<SSH_KEY_FILE>';
   let command = `ssh -i ${sshKey} ubuntu@${vmIp}`;
   const commandEl = document.getElementById('ssh-command');
   if (commandEl) commandEl.innerText = command;
}

function updateTask5ConnectionCommand() {
   let connectionString = document.getElementById('adb-connection-string').value || '<CONNECTION_STRING>';
   let username = document.getElementById('task5-db-username').value || 'ADMIN';
   let password = document.getElementById('task5-db-password').value || '<PASSWORD>';
   let command = `sql ${username}/${password}@${connectionString}`;
   const commandEl = document.getElementById('task5-connection-command');
   if (commandEl) commandEl.innerText = command;
}

function saveConnectionString() {
   let connectionString = document.getElementById('adb-connection-string').value;
   if (connectionString) {
       sessionStorage.setItem('adbConnectionString', connectionString);
       
       document.getElementById('connection-string-save-message').innerHTML = `
           ✅ <strong>Connection String Saved Successfully!</strong><br/>
           Connection string stored and will be available for use in subsequent tasks.<br/>
           <small>Configuration will persist until you close the browser tab.</small>
       `;
       document.getElementById('connection-string-save-status').style.display = 'block';
       document.getElementById('connection-string-save-status').style.background = '#d4edda';
       document.getElementById('connection-string-save-status').style.color = '#155724';
       
       const button = document.querySelector('button[onclick="saveConnectionString()"]');
       if (button) {
           const originalText = button.textContent;
           button.textContent = 'Saved!';
           button.style.background = '#28a745';
           setTimeout(() => {
               button.textContent = originalText;
               button.style.background = '#0572ce';
           }, 2000);
       }
   } else {
       document.getElementById('connection-string-save-message').innerHTML = `
           ⚠️ <strong>Please Enter Connection String</strong><br/>
           Enter a valid connection string before saving.
       `;
       document.getElementById('connection-string-save-status').style.display = 'block';
       document.getElementById('connection-string-save-status').style.background = '#fff3cd';
       document.getElementById('connection-string-save-status').style.color = '#856404';
   }
}

function deleteConnectionString() {
   sessionStorage.removeItem('adbConnectionString');
   
   document.getElementById('connection-string-save-message').innerHTML = `
       🗑️ <strong>Saved Connection String Deleted</strong><br/>
       Connection string has been removed from browser session.
   `;
   document.getElementById('connection-string-save-status').style.display = 'block';
   document.getElementById('connection-string-save-status').style.background = '#f8d7da';
   document.getElementById('connection-string-save-status').style.color = '#721c24';
}

function clearConnectionString() {
document.getElementById('adb-connection-string').value = '';
   const statusEl = document.getElementById('connection-string-save-status');
   if (statusEl) {
       statusEl.style.display = 'none';
   }
   updateTask5ConnectionCommand();
}

function saveSshKeyName() {
   let sshKey = document.getElementById('ssh-key-name').value;
   
   if (sshKey) {
       sessionStorage.setItem('sshKeyName', sshKey);
       
       document.getElementById('ssh-save-message').innerHTML = `
           ✅ <strong>SSH Key Filename Saved Successfully!</strong><br/>
           SSH Key: ${sshKey}<br/>
           <small>SSH key filename will be used for connecting to your VM.</small>
       `;
       document.getElementById('ssh-save-status').style.display = 'block';
       document.getElementById('ssh-save-status').style.background = '#d4edda';
       document.getElementById('ssh-save-status').style.color = '#155724';
       
       const button = document.querySelector('button[onclick="saveSshKeyName()"]');
       if (button) {
           const originalText = button.textContent;
           button.textContent = 'Saved!';
           button.style.background = '#28a745';
           setTimeout(() => {
               button.textContent = originalText;
               button.style.background = '#0572ce';
           }, 2000);
       }
   } else {
       document.getElementById('ssh-save-message').innerHTML = `
           ⚠️ <strong>Please Enter SSH Key Filename</strong><br/>
           Enter a valid SSH key filename before saving.
       `;
       document.getElementById('ssh-save-status').style.display = 'block';
       document.getElementById('ssh-save-status').style.background = '#fff3cd';
       document.getElementById('ssh-save-status').style.color = '#856404';
   }
}

function deleteSshKeyName() {
   sessionStorage.removeItem('sshKeyName');
   
   document.getElementById('ssh-save-message').innerHTML = `
       🗑️ <strong>SSH Key Filename Deleted</strong><br/>
       SSH key filename has been removed from browser session.
   `;
   document.getElementById('ssh-save-status').style.display = 'block';
   document.getElementById('ssh-save-status').style.background = '#f8d7da';
   document.getElementById('ssh-save-status').style.color = '#721c24';
}

function clearSshKeyName() {
   document.getElementById('ssh-key-name').value = '';
   const statusEl = document.getElementById('ssh-save-status');
   if (statusEl) {
       statusEl.style.display = 'none';
   }
   updateSshCommand();
}

// Utility function to ensure save-status elements are properly hidden
function hideElement(element) {
   if (element) {
       element.style.setProperty('display', 'none', 'important');
   }
}

function loadSavedConfig() {
   try {
       const savedBrokerChicago = sessionStorage.getItem('cloudbank_USER_BROKERCHICAGO');
       const hasSavedConfig = savedBrokerChicago !== null;
       
       const defaultUsers = {
           brokerchicago: 'brokerchicago',
           orchestratorchicago: 'orchestratorchicago',
           brokermex: 'brokermex',
           orchestratormex: 'orchestratormex',
           bankchicago: 'bankchicago',
           bankmex: 'bankmex',
           banklondon: 'banklondon',
           banktokyo: 'banktokyo'
       };
       const defaultPassword = 'Welcome_123#';
       
       if (hasSavedConfig) {
           const formFields = [
               { formId: 'brokerchicago-user', sessionKey: 'cloudbank_USER_BROKERCHICAGO' },
               { formId: 'brokerchicago-password', sessionKey: 'cloudbank_PASSWORD_BROKERCHICAGO' },
               { formId: 'orchestratorchicago-user', sessionKey: 'cloudbank_USER_ORCHESTRATORCHICAGO' },
               { formId: 'orchestratorchicago-password', sessionKey: 'cloudbank_PASSWORD_ORCHESTRATORCHICAGO' },
               { formId: 'brokermex-user', sessionKey: 'cloudbank_USER_BROKERMEX' },
               { formId: 'brokermex-password', sessionKey: 'cloudbank_PASSWORD_BROKERMEX' },
               { formId: 'orchestratormex-user', sessionKey: 'cloudbank_USER_ORCHESTRATORMEX' },
               { formId: 'orchestratormex-password', sessionKey: 'cloudbank_PASSWORD_ORCHESTRATORMEX' },
               { formId: 'bankchicago-user', sessionKey: 'cloudbank_USER_BANKCHICAGO' },
               { formId: 'bankchicago-password', sessionKey: 'cloudbank_PASSWORD_BANKCHICAGO' },
               { formId: 'bankmex-user', sessionKey: 'cloudbank_USER_BANKMEX' },
               { formId: 'bankmex-password', sessionKey: 'cloudbank_PASSWORD_BANKMEX' },
               { formId: 'banklondon-user', sessionKey: 'cloudbank_USER_BANKLONDON' },
               { formId: 'banklondon-password', sessionKey: 'cloudbank_PASSWORD_BANKLONDON' },
               { formId: 'banktokyo-user', sessionKey: 'cloudbank_USER_BANKTOKYO' },
               { formId: 'banktokyo-password', sessionKey: 'cloudbank_PASSWORD_BANKTOKYO' }
           ];
           
           formFields.forEach(field => {
               const element = document.getElementById(field.formId);
               const savedValue = sessionStorage.getItem(field.sessionKey);
               if (element) {
                   element.value = savedValue === 'null' ? '' : (savedValue || '');
               }
           });
                       
           const displayMappings = [
               { displayId: 'saved-brokerchicago', userKey: 'cloudbank_USER_BROKERCHICAGO', passKey: 'cloudbank_PASSWORD_BROKERCHICAGO' },
               { displayId: 'saved-orchestratorchicago', userKey: 'cloudbank_USER_ORCHESTRATORCHICAGO', passKey: 'cloudbank_PASSWORD_ORCHESTRATORCHICAGO' },
               { displayId: 'saved-brokermex', userKey: 'cloudbank_USER_BROKERMEX', passKey: 'cloudbank_PASSWORD_BROKERMEX' },
               { displayId: 'saved-orchestratormex', userKey: 'cloudbank_USER_ORCHESTRATORMEX', passKey: 'cloudbank_PASSWORD_ORCHESTRATORMEX' },
               { displayId: 'saved-bankchicago', userKey: 'cloudbank_USER_BANKCHICAGO', passKey: 'cloudbank_PASSWORD_BANKCHICAGO' },
               { displayId: 'saved-bankmex', userKey: 'cloudbank_USER_BANKMEX', passKey: 'cloudbank_PASSWORD_BANKMEX' },
               { displayId: 'saved-banklondon', userKey: 'cloudbank_USER_BANKLONDON', passKey: 'cloudbank_PASSWORD_BANKLONDON' },
               { displayId: 'saved-banktokyo', userKey: 'cloudbank_USER_BANKTOKYO', passKey: 'cloudbank_PASSWORD_BANKTOKYO' }
           ];
           
           displayMappings.forEach(mapping => {
               const element = document.getElementById(mapping.displayId);
               if (element) {
                   const username = sessionStorage.getItem(mapping.userKey);
                   const password = sessionStorage.getItem(mapping.passKey);
                   element.innerHTML = `${username}<br/><small>Password: ${password}</small>`;
               }
           });

           const userSaveMessageEl = document.getElementById('user-save-message');
           const userSaveStatusEl = document.getElementById('user-save-status');
           
           const brokerChicagoField = document.getElementById('brokerchicago-user');
           const isTask2Visible = brokerChicagoField && brokerChicagoField.offsetParent !== null;

           if (userSaveStatusEl) {
               if (userSaveMessageEl && isTask2Visible) {
                   let message = '📋 <strong>Previously Saved Configuration Found:</strong><br/>';
                   message += 'User configurations have been loaded from browser session.<br/>';
                   message += '<small>These values will be automatically used in later labs.</small>';
                   
                   userSaveMessageEl.innerHTML = message;
                   userSaveStatusEl.style.display = 'block';
                   userSaveStatusEl.style.background = '#cce7ff';
                   userSaveStatusEl.style.color = '#004085';
               } else {
                   userSaveStatusEl.style.display = 'none';
               }
           }

           generateScript();
           
       } else {
           const displayDefaults = [
               { displayId: 'saved-brokerchicago', username: defaultUsers.brokerchicago },
               { displayId: 'saved-orchestratorchicago', username: defaultUsers.orchestratorchicago },
               { displayId: 'saved-brokermex', username: defaultUsers.brokermex },
               { displayId: 'saved-orchestratormex', username: defaultUsers.orchestratormex },
               { displayId: 'saved-bankchicago', username: defaultUsers.bankchicago },
               { displayId: 'saved-bankmex', username: defaultUsers.bankmex },
               { displayId: 'saved-banklondon', username: defaultUsers.banklondon },
               { displayId: 'saved-banktokyo', username: defaultUsers.banktokyo }
           ];
           
           displayDefaults.forEach(item => {
               const element = document.getElementById(item.displayId);
               if (element) {
                   element.innerHTML = `${item.username}<br/><small>Password: ${defaultPassword}</small>`;
               }
           });
           
           const userSaveStatusEl = document.getElementById('user-save-status');
           if (userSaveStatusEl) {
               userSaveStatusEl.style.display = 'none';
           }
       }
       
   } catch (error) {
       console.error('Error in loadSavedConfig:', error);
   }
}

function initializeCloudBank() {
   try {
       const adbStatusEl = document.getElementById('save-status');
       hideElement(adbStatusEl);

       const connectionStringStatusEl = document.getElementById('connection-string-save-status');
       hideElement(connectionStringStatusEl);
       
       const sshStatusEl = document.getElementById('ssh-save-status');
       hideElement(sshStatusEl);
       
       const userStatusEl = document.getElementById('user-save-status');
       hideElement(userStatusEl);
       
       const computeIpStatusEl = document.getElementById('compute-ip-save-status');
       hideElement(computeIpStatusEl);

       loadSavedConfig();

       const adbNameEl = document.getElementById('adb-name');
       const adbPasswordEl = document.getElementById('adb-password');
       const saveMessageEl = document.getElementById('save-message');
       const saveStatusEl = document.getElementById('save-status');
       
       let savedName = sessionStorage.getItem('cloudbank_ADB_DATABASE_NAME');
       let savedPassword = sessionStorage.getItem('cloudbank_ADB_DATABASE_PASSWORD');
       
       
       if (savedName && adbNameEl) {
           adbNameEl.value = savedName;
       }
       if (savedPassword && adbPasswordEl) {
           adbPasswordEl.value = savedPassword;
       }
       

       const isTask1Visible = adbNameEl && adbNameEl.offsetParent !== null;
       const hasAdbData = savedName || savedPassword;
       
       if (saveStatusEl) {
           if (hasAdbData && saveMessageEl && isTask1Visible) {
               let message = '📋 <strong>Previously Saved Configuration Found:</strong><br/>';
               if (savedName) message += `Database Name: ${savedName}<br/>`;
               if (savedPassword) message += `Password: ${savedPassword}<br/>`;
               message += '<small>Fields have been pre-populated. You can modify and save again if needed.</small>';
               saveMessageEl.innerHTML = message;
               saveStatusEl.style.display = 'block';
               saveStatusEl.style.background = '#cce7ff';
               saveStatusEl.style.color = '#004085';
           } else {
               saveStatusEl.style.display = 'none';
           }
       }

       const savedComputeIP = sessionStorage.getItem('computePublicIP');
       const computeIpField = document.getElementById('compute-public-ip');
       const computeIpSaveMessageEl = document.getElementById('compute-ip-save-message');
       const computeIpSaveStatusEl = document.getElementById('compute-ip-save-status');
       
       const isTask4Visible = computeIpField && computeIpField.offsetParent !== null;
       const hasComputeIpData = savedComputeIP;
       

       if (savedComputeIP && computeIpField) {
           computeIpField.value = savedComputeIP;
       }
       
       if (computeIpSaveStatusEl) {
           if (hasComputeIpData && computeIpSaveMessageEl && isTask4Visible) {
               computeIpSaveMessageEl.innerHTML = `
                   📋 <strong>Previously Saved IP Found:</strong><br/>
                   Public IP address has been loaded from browser session.<br/>
                   <small>This IP will be automatically used in subsequent tasks.</small>
               `;
               computeIpSaveStatusEl.style.display = 'block';
               computeIpSaveStatusEl.style.background = '#cce7ff';
               computeIpSaveStatusEl.style.color = '#004085';
               console.log('Loaded saved compute IP:', savedComputeIP);
           } else {
               computeIpSaveStatusEl.style.display = 'none';
               console.log('No saved compute IP found or task 3 is not visible.');
           }
       }

       const savedConnectionString = sessionStorage.getItem('adbConnectionString');
       
       const task5ConnectionField = document.getElementById('task5-connection-string');
       const task5UsernameField = document.getElementById('task5-db-username');
       const task5PasswordField = document.getElementById('task5-db-password');
       const adbConnectionField = document.getElementById('adb-connection-string');
       const connectionStringMessageEl = document.getElementById('connection-string-save-message');
       
       const isStep8Visible = adbConnectionField && adbConnectionField.offsetParent !== null;
       const hasConnectionStringData = savedConnectionString;
       
       if (connectionStringStatusEl) {
           if (hasConnectionStringData && isStep8Visible && connectionStringMessageEl) {
               connectionStringMessageEl.innerHTML = `
                   📋 <strong>Previously Saved Connection String Found:</strong><br/>
                   Connection string has been loaded from browser session.<br/>
                   <small>This will be automatically used in connection commands.</small>
               `;
               connectionStringStatusEl.style.display = 'block';
               connectionStringStatusEl.style.background = '#cce7ff';
               connectionStringStatusEl.style.color = '#004085';
           } else {
               connectionStringStatusEl.style.display = 'none';
           }
       }
       
       if (task5UsernameField) {
           task5UsernameField.value = 'ADMIN';
       }
       
       if (task5PasswordField && savedPassword) {
           task5PasswordField.value = savedPassword;
       }
       
       const savedSshKey = sessionStorage.getItem('sshKeyName');
       
       const vmIpField = document.getElementById('vm-ip');
       const sshKeyField = document.getElementById('ssh-key-name');
       const sshSaveStatusEl = document.getElementById('ssh-save-status');
       const sshSaveMessageEl = document.getElementById('ssh-save-message');
       
       const computeIpFromTask4 = sessionStorage.getItem('computePublicIP');
       const legacySshVmIp = sessionStorage.getItem('sshVmIp');
       const vmIpToUse = computeIpFromTask4 || legacySshVmIp;
    
       
       if (vmIpToUse && vmIpField) {
           vmIpField.value = vmIpToUse;
       }
       
       if (savedSshKey && sshKeyField) {
           sshKeyField.value = savedSshKey;
       }
       
       const isSSHVisible = sshKeyField && sshKeyField.offsetParent !== null;
       const hasSshKeyData = savedSshKey;
       
       if (sshSaveStatusEl) {
           if (hasSshKeyData && sshSaveMessageEl && isSSHVisible) {
               let message = '📋 <strong>Previously Saved SSH Key Found:</strong><br/>';
               message += `SSH Key: ${savedSshKey}<br/>`;
               message += '<small>SSH key filename has been pre-populated. You can modify and save again if needed.</small>';
               
               sshSaveMessageEl.innerHTML = message;
               sshSaveStatusEl.style.display = 'block';
               sshSaveStatusEl.style.background = '#cce7ff';
               sshSaveStatusEl.style.color = '#004085';
           } else {
               sshSaveStatusEl.style.display = 'none';
           }
       }


       updateWalletCommand();
       updateTnsCommand();
       updateSshCommand();
       updateTask5ConnectionCommand();

   } catch (error) {
       console.error('Error loading configuration:', error);
   }
}


if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeCloudBank);
} else {
    initializeCloudBank();
}

window.addEventListener('load', initializeCloudBank);
setTimeout(initializeCloudBank, 500);  
setTimeout(initializeCloudBank, 1500); 
setTimeout(initializeCloudBank, 3000); 

</script>

## Acknowledgements

- **Contributors** - Amit Ketkar, Pavas Navaney, Vinay Pandhariwal, 
Luis Cruz, Sebastian Gerritsen
- **Created By/Date** - Vinay Pandhariwal, April 2025
- **Last Updated By/Date** - Vinay Pandhariwal, April 2025
