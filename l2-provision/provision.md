# Prepare your environment

## Introduction

This lab provisions the complete OCI environment required by the CloudBank demo. A single automation script creates the infrastructure, prepares the application, configures the database users and wallet, and verifies the compute instance.

- Estimated time: 30 minutes

Watch the video below for a quick walk-through of the lab.

[Prepare your environment](videohub:1_nw8ufqzp:medium)

### Objectives

By completing this lab, you will be able to:

- Provision the Oracle Cloud infrastructure required by CloudBank.
- Create and configure the CloudBank Saga database users.
- Prepare the Autonomous Database wallet and application package.
- Verify SSH, Podman, Podman Compose, and the transferred CloudBank files.

### Prerequisites

- A Free Tier Oracle Cloud account.

## Task 1: Provision the CloudBank Environment

The provisioning script performs the complete setup. Enter your compartment OCID, download and run the script in OCI Cloud Shell, and provide an ADB `ADMIN` password when the secure terminal prompt appears.

![Automated provisioning flow](./images/lab2-automated-provisioning.svg "The provisioning script prepares and verifies the complete CloudBank environment")

### Step 1: Enter Your Compartment Information

Run the following command in OCI Cloud Shell to obtain the root compartment OCID for your tenancy:

```bash
<copy>
oci iam availability-domain list --query "data[0].\"compartment-id\"" --raw-output
</copy>
```

Paste the returned OCID below to automatically update the command used to run the downloaded provisioning script.


<div class="provision-input-section">
<div class="provision-input-grid">
<div class="provision-grid-header">Required value</div>
<div class="provision-grid-header">Your environment</div>
<div class="provision-grid-header">Example</div>
<div class="provision-grid-cell"><strong>Compartment OCID</strong></div>
<div class="provision-grid-cell"><input type="text" id="compartmentOcid" class="provision-input" placeholder="Paste your compartment OCID" oninput="updateProvisionCommand()" autocomplete="off"></div>
<div class="provision-grid-cell"><code>ocid1.tenancy.oc1..example</code></div>
</div>
</div>

### Step 2: Download and Run the Provisioning Script

Download the script below, upload it to OCI Cloud Shell, and run the command generated from the value entered in Step 1.

[**Download Provisioning Script**](files/provision.sh?download=1)

> **Timing note:** Provisioning normally takes approximately **5–20 minutes** and may take longer depending on Autonomous Database and compute capacity. Keep Cloud Shell open while the script is running.

Alternatively, run the following commands to download the provisioning script directly into OCI Cloud Shell:

```bash
<copy>
curl -fL -o provision.sh \
https://raw.githubusercontent.com/sgerrits2/Oracle-SAGAS/main/l2-provision/files/provision.sh

chmod +x provision.sh
ls -l provision.sh
</copy>
```

- These commands download the script directly into Cloud Shell, make it executable, and confirm that the file is available.

<pre id="provisionCommandContainer" class="interactive-command"><code id="provisionCommand">chmod +x provision.sh
COMPARTMENT_ID='YOUR_COMPARTMENT_OCID' ./provision.sh</code></pre>

<div class="button-center">
<button id="copyProvisionButton" onclick="copyProvisionCommand()" class="copy-btn">Copy Provisioning Command</button>
</div>

> **Note:** The generated command uses the compartment OCID entered above. Review the value before copying and running the command.

> **ADB ADMIN password:** When the provisioning script starts, enter the password you want to use for the Autonomous Database `ADMIN` account. The suggested training password is `Welcome_123#`, but you may choose another password that satisfies the displayed requirements. Your input is hidden while you type. Remember this password because Labs 3, 5, and 6 require it again.

> **Demo credentials:** The CloudBank application schemas and wallet continue to use the fixed password `Welcome_123#`. This shared password is for the training environment only and must not be used in production.

The script automatically:

- Creates the Autonomous Database, VCN, internet gateway, route table, security list, public subnet, SSH key pair, and Ubuntu compute instance.
- Opens the CloudBank application ports, including `3000`, `8080`, and `9411`.
- Installs Podman and Podman Compose and pulls the required container images.
- Downloads and extracts the CloudBank application package.
- Generates and extracts the Autonomous Database wallet.
- Executes the CloudBank user-creation SQL script and verifies all eight schemas.
- Transfers the prepared application and wallet to the compute instance.
- Waits for compute initialization and verifies SSH, Podman, Podman Compose, the wallet, and the transferred application.

Keep Cloud Shell open until the **PROVISIONING COMPLETE** summary appears.

**Expected output:**

```text
================= PROVISIONING COMPLETE =================
VCN OCID:       ocid1.vcn...
Subnet OCID:    ocid1.subnet...
ADB OCID:       ocid1.autonomousdatabase...
TNS Alias:      oraclesagademo_medium
Instance OCID:  ocid1.instance...
Instance IP:    <PUBLIC_IP>
SSH:            ssh -i <SSH_PRIVATE_KEY> ubuntu@<PUBLIC_IP>
CloudBank VM:   /home/ubuntu/oracle-saga-cloudbank
Validation:     USERS, WALLET, SSH, PODMAN, PODMAN-COMPOSE, TRANSFER = READY
============================================================
```

> **Troubleshooting:** If the script stops before this summary, use the last `ERROR` message to identify the failed operation. Correct that issue before continuing to Lab 3.

## Task 2: Review the Automated Database User Setup

The provisioning script invokes `create-cloudbank-users.sql` automatically as `ADMIN`. No SQL Worksheet, manual copy-and-paste, or separate database connection is required.

### Step 3: Review the SQL Verification

The SQL script creates the CloudBank broker, orchestrator, and participant schemas, grants the required Saga roles, assigns tablespace quotas, and finishes with this verification query:

```sql
SELECT username
FROM dba_users
WHERE username IN (
  'BROKERCHICAGO', 'ORCHESTRATORCHICAGO', 'BROKERMEX', 'ORCHESTRATORMEX',
  'BANKCHICAGO', 'BANKMEX', 'BANKLONDON', 'BANKTOKYO'
)
ORDER BY username;
```

The complete SQL executed by the provisioning script is available in [`create-cloudbank-users.sql`](files/create-cloudbank-users.sql).

**Expected output:**

```text
BANKCHICAGO
BANKLONDON
BANKMEX
BANKTOKYO
BROKERCHICAGO
BROKERMEX
ORCHESTRATORCHICAGO
ORCHESTRATORMEX

SUCCESS: All eight CloudBank database users are configured and ready.
```

The fixed schema names must remain consistent with the participant registrations and application configuration used in the following labs.

You may now [proceed to the next lab](#next).

<style>
.provision-input-section {
  margin: 16px 0;
  overflow-x: auto;
}

.provision-input-grid {
  display: grid;
  grid-template-columns: minmax(170px, 1fr) minmax(340px, 2fr) minmax(220px, 1fr);
  min-width: 760px;
  border-top: 1px solid #d5d8dc;
  border-left: 1px solid #d5d8dc;
  background: #f8f9fa;
}

.provision-grid-header,
.provision-grid-cell {
  border-right: 1px solid #d5d8dc;
  border-bottom: 1px solid #d5d8dc;
  padding: 12px;
  text-align: left;
  vertical-align: middle;
}

.provision-grid-header {
  background: #eef2f5;
  font-weight: 700;
}

.provision-input {
  box-sizing: border-box;
  width: 100%;
  min-width: 320px;
  padding: 9px;
  border: 1px solid #aeb6bf;
  border-radius: 4px;
  font-family: monospace;
}

.interactive-command {
  padding: 16px;
  overflow-x: auto;
  border: 1px solid #d5d8dc;
  border-radius: 6px;
  background: #f4f6f7;
  white-space: pre-wrap;
}

.button-center {
  margin: 18px 0;
  text-align: center;
}

.copy-btn {
  display: inline-block;
  padding: 12px 22px;
  border: 0;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff !important;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

@media (max-width: 700px) {
  .provision-input {
    min-width: 0;
  }
}
</style>

<script>
function getCompartmentOcid() {
  const field = document.getElementById('compartmentOcid');
  return field ? field.value.trim() : '';
}

function updateProvisionCommand() {
  const ocid = getCompartmentOcid();
  const command = document.getElementById('provisionCommand');

  if (!command) return;

  command.textContent = `chmod +x provision.sh\nCOMPARTMENT_ID='${ocid || 'YOUR_COMPARTMENT_OCID'}' ./provision.sh`;
}

async function copyProvisionCommand() {
  const command = document.getElementById('provisionCommand');
  const button = document.getElementById('copyProvisionButton');
  if (!command || !button) return;

  await navigator.clipboard.writeText(command.textContent);
  const originalText = button.textContent;
  button.textContent = 'Copied!';
  setTimeout(() => {
    button.textContent = originalText;
  }, 2000);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', updateProvisionCommand);
} else {
  updateProvisionCommand();
}
</script>

## Acknowledgements

- **Contributors** — Amit Ketkar, Pavas Navaney, Vinay Pandhariwal, Luis Cruz, Sebastian Gerritsen
- **Created By/Date** — Vinay Pandhariwal, April 2025
- **Last Updated By/Date** — Sebastian Gerritsen, August 2026
