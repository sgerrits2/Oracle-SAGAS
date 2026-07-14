# Prepare your environment

## **Introduction**

Before you can develop and run Oracle Sagas, you need a properly configured environment. In this lab you will:

1. Provision an Oracle Autonomous Database instance.
2. Create the necessary database users and grant privileges.
3. Configure your OCI network for secure connectivity.
4. Provision a Virtual Machine to host your demo application containers.
5. Connect to the Autonomous Database using Cloud Shell and SQLcl.

We'll continue to use the **CloudBank** money-transfer application as our running example. By the end of this lab, you'll have a fully ready environment to deploy and test Oracle Sagas.

- Estimated time: XX minutes

Watch the video below for a quick walk-through of the lab.

<!-- [Prepare your environment](videohub:1_nw8ufqzp:medium) -->

[Prepare your environment](videohub::medium)

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

## Task 1: Provision an Autonomous Database

---

**Note:** If you plan to use an Oracle-provided environment, you can skip the Task 1 through Task 4.

### **Step 1: Access Oracle Cloud Infrastructure Console**

Log in to the Oracle Cloud Infrastructure Console.

Once you are logged in, you are taken to the cloud services dashboard where you can see all the services available to you. Click the navigation menu in the upper left to show top level navigation choices.

![Menu](./images/lab1-task1-1.png "In the top left corner, click the 3 lines menu to expand.")

### **Step 2: Navigate to Autonomous Database Service**

The following steps apply to Autonomous Databases. So please **click the Autonomous Database**.

![Provision Autonomous Database](./images/lab1-task1-2.png "click the autonomous database.")

### **Step 3: Initiate Database Creation**

From the **Compartment** filter, select your compartment and click [**Create Autonomous Database**]

![Check Compartment](./images/lab1-task1-3.png "Make sure you are in the correct compartment.")

### **Step 4: Configure Basic Database Information**

On the **Create Autonomous Database** page, provide basic information for your database:

- **Display name** - Enter a memorable name for the database for display purposes, for this lab, use _`Oracle-Saga-Demo`_

    ```
    <copy>Oracle-Saga-Demo</copy>
    ```

- **Database Name** - Enter _`OracleSagaDemo`_, it's important to use letters and numbers only, starting with a letter (the maximum length is 14 characters and Underscores are not supported)

     ```
     <copy>OracleSagaDemo</copy>
     ```

 > **NOTE:** The database name must be unique across all Autonomous Databases and Autonomous Data Warehouses in your tenancy within the same region. If an existing database shares the same name, provisioning will fail. Use a unique name to ensure successful provisioning.

- **Compartment** - If needed, select your compartment

- **Workload Type** - Select the type of your Autonomous Database (here we select "Transaction Processing")

### **Step 5: Configure Database Settings**

Configure the database:

- **Always Free** - Select this option by moving the slider to the right
- **Database version** - Select _`23ai`_

![Basic information](./images/lab1-task1-4.png "Select the appropriate display name, database name, compartment and workload.")

### **Step 6: Set Administrator Credentials**

Create administrator credentials:

- **Password** and **Confirm Password** - Specify a password for the ADMIN database user and jot it down. The password must be between 12 and 30 characters long and must include at least one uppercase letter, one lowercase letter, and one numeric character. It cannot contain your username or the double quote (") character. For example : _`Welcome_123#`_

    ```
    <copy>Welcome_123#</copy>
    ```

### **Step 7: Configure Network Access**

Choose the network access :

- **Network Access** - Leave _`Secure access from everywhere`_ selected
- **Provide Contacts** - You can leave this blank

![password and network access types](./images/lab1-task1-5.png "choose the network type and provide contact info, if you want. ")

### **Step 8: Create the Database**

Click [**Create**]

### **Step 9: Monitor Database Provisioning**

Your instance will begin provisioning. In a few minutes, the state will turn from Provisioning to Available. At this point, your Autonomous Database is ready to use! Have a look at your instance's details here including its name, database version, OCID, Instance Type amd Mode.

![provisioning the instance will take a few minutes ](./images/lab1-task1-6.png "provisioning the instance will take a few minutes")


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
-- 1) Create Broker & Orchestrator Schemas  
CREATE USER broker1 IDENTIFIED BY Welcome_123#;  
CREATE USER orchestrator1 IDENTIFIED BY Welcome_123#;

CREATE USER broker2 IDENTIFIED BY Welcome_123#;  
CREATE USER orchestrator2 IDENTIFIED BY Welcome_123#;

-- 2) Create Bank Participant Schemas  
CREATE USER banka IDENTIFIED BY Welcome_123#;  
CREATE USER bankb IDENTIFIED BY Welcome_123#;  
CREATE USER bankc IDENTIFIED BY Welcome_123#;  
CREATE USER bankd IDENTIFIED BY Welcome_123#;

-- 3) Grant Saga Roles  
GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_CONNECT_ROLE  
TO broker1, broker2;

GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_PARTICIPANT_ROLE  
TO orchestrator1, orchestrator2,  
banka, bankb, bankc, bankd;

-- 4) Allocate Tablespace Quotas  
ALTER USER broker1 QUOTA 500M ON DATA;  
ALTER USER broker2 QUOTA 500M ON DATA;  
ALTER USER orchestrator1 QUOTA 500M ON DATA;  
ALTER USER orchestrator2 QUOTA 500M ON DATA;  
ALTER USER banka QUOTA 500M ON DATA;  
ALTER USER bankb QUOTA 500M ON DATA;  
ALTER USER bankc QUOTA 500M ON DATA;  
ALTER USER bankd QUOTA 500M ON DATA;

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

- `User BROKER1 created.`
- `Role SAGA_ADM_ROLE granted.`
- `User BROKER1 altered.`  
 …and so on.

![Script output pane with DDL/DCL messages](./images/lab2-task2-4.png "Verify each CREATE, GRANT, and ALTER statement succeeded")

### **Step 6: Verify All Users (OPTIONAL)**

Run a quick `SELECT` to ensure all eight users exist:

```
<copy>
SELECT username
   FROM dba_users
   WHERE username IN (
   'BROKER1','ORCHESTRATOR1','BROKER2','ORCHESTRATOR2',
   'BANKA','BANKB','BANKC','BANKD'
   );
</copy>
```

![Verify user list output](./images/lab2-task2-5.png "All eight schemas should appear")

</div>

## Task 3: Configure Oracle Cloud Infrastructure Networking

---

In this task, you'll establish a secure and comprehensive networking foundation for the CloudBank demo application. Oracle Cloud Infrastructure's Virtual Cloud Network (VCN) wizard will automatically provision the core networking infrastructure, including internet connectivity, subnets, and routing. Additionally, you'll configure specific security list rules to enable access to the CloudBank microservices, web interface, API documentation, and distributed tracing capabilities.

### **Step 1: Navigate to Virtual Cloud Networks**

From the OCI Console, click the **navigation menu (☰)** in the upper left, then navigate to **Networking** → **Virtual Cloud Networks**.

![Navigate to VCN](./images/lab2-task3-1.png "Click navigation menu → Networking → Virtual Cloud Networks")

### **Step 2: Start VCN Wizard**

Under the **Actions** drop down list, click **Start VCN Wizard**.

![Click Start VCN Wizard](./images/lab2-task3-2.png "Click Actions → Start VCN Wizard")

### **Step 3: Select VCN Creation Option**

Select **Create VCN with Internet Connectivity**, then click **Start VCN Wizard**.

![Select VCN Type](./images/lab2-task3-3.png "Choose VCN with Internet Connectivity option")

### **Step 4: Configure VCN Basic Information**

Fill in the following details:

- **VCN Name**: `Oracle-Saga-VCN`
- **Compartment**: Select your compartment
- **VCN IPv4 CIDR Block**: `10.0.0.0/16`
- **Use DNS Hostnames**: Keep this **checked** (enabled by default)

    ```
    <copy>Oracle-Saga-VCN</copy>
    ```

![Configure VCN Details](./images/lab2-task3-4.png "Enter VCN name, compartment, and CIDR block")

### **Step 5: Review Subnet Configuration**

The wizard will automatically configure:

- **Public Subnet CIDR**: `10.0.0.0/24`
- **Private Subnet CIDR**: `10.0.1.0/24`


![Review Subnet Configuration](./images/lab2-task3-5.png "Verify public and private subnet CIDR blocks")

### **Step 6: Create the VCN**

Click **Next** to review your configuration, then click **Create** to provision the VCN and all related networking components.

![Create VCN](./images/lab2-task3-6.png "Click Create to provision VCN and networking resources")

### **Step 7: Monitor Provisioning Progress**

The wizard automatically creates and configures:

- **VCN** with specified CIDR
- **Internet Gateway** for public internet access
- **NAT Gateway** for private subnet internet access
- **Public and Private Subnets**
- **Route Tables** with proper routing (0.0.0.0/0 → Internet Gateway for public subnet)
- **Default Security Lists** with SSH (port 22) already open

Wait until all resources show **DONE** status.

![Provisioning Complete](./images/lab2-task3-7.png "Wait for all networking components to become AVAILABLE")

### **Step 8: View Your VCN**

Click **View Virtual Cloud Network** to see your newly created VCN details.

![VCN Overview](./images/lab2-task3-8.png "Review VCN components")

### **Step 9: Navigate to Security Lists**

In the VCN details page, click on the **Security** tab.

![Navigate to Security Lists](./images/lab2-task3-9.png "Click Security Lists under Resources")

### **Step 10: Open Default Security List**

Click on **Default Security List for Oracle-Saga-VCN** to view and modify the security rules.

![Open Default Security List](./images/lab2-task3-10.png "Click on Default Security List")

### **Step 11: Add CloudBank Application Ports**

Click on Security rules tab followed by **Add Ingress Rules** button to add new rules for CloudBank application ports.

![Add Ingress Rules](./images/lab2-task3-11.png "Click Add Ingress Rules button")

### **Step 12: Configure CloudBank and Zipkin Ingress Rules**

Add the following rules one by one. For each rule, fill in the details and click **+ Another Ingress Rule** to add the next rule:

**Rule 1: Orchestrator API (Backend)**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>8081</copy>
    ```
- **Description**: 
    ```
    <copy>
    `CloudBank Orchestrator API`
    </copy>
    ```

**Rule 2: Bank A Service**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>8082</copy>
    ```
- **Description**: 
    ```
    <copy>
    `Bank A Microservice API`
    </copy>
    ```

**Rule 3: Bank B Service**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>8083</copy>
    ```
- **Description**: 
    ```
    <copy>
    `Bank B Microservice API`
    </copy>
    ```

**Rule 4: Flask Frontend UI**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>8084</copy>
    ```
- **Description**: 
    ```
    <copy>
    `CloudBank Web Frontend`
    </copy>
    ```

**Rule 5: Swagger UI**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>8085</copy>
    ```
- **Description**: 
    ```
    <copy>
    `API Documentation Interface`
    </copy>
    ```

**Rule 6: Zipkin Tracing Server**

- **Source Type**: CIDR
- **Source CIDR**:
    ```
    <copy>0.0.0.0/0</copy>
    ```
- **IP Protocol**: TCP
- **Destination Port Range**:
    ```
    <copy>9411</copy>
    ```
- **Description**: 
    ```
    <copy>
    `Zipkin Distributed Tracing UI`
    </copy>
    ```
    

![Configure Ingress Rules](./images/lab2-task3-12.png "Add all CloudBank application and Zipkin port rules")

### **Step 13: Save Ingress Rules**

After adding all six rules, click **Add Ingress Rules** to save the configuration.

![Save Ingress Rules](./images/lab2-task3-13.png "Click Add Ingress Rules to save")

**Expected Output**:

- VCN named `Oracle-Saga-VCN` with CIDR 10.0.0.0/16
- Public subnet (10.0.0.0/24) with internet access
- SSH access (port 22) automatically configured by wizard
- CloudBank application ports (8081-8085) manually added
- Zipkin distributed tracing port (9411) manually added
- Proper routing for internet connectivity

### CloudBank Application and Monitoring Port Usage

- **Port 22 (SSH)**: Automatically opened by VCN wizard for server management
- **Port 8081**: Orchestrator API backend - required for all CloudBank operations
- **Port 8082**: Bank A microservice - optional, for direct API access to Bank A
- **Port 8083**: Bank B microservice - optional, for direct API access to Bank B
- **Port 8084**: Flask Frontend UI - required for web interface access
- **Port 8085**: Swagger UI - required for API documentation and testing
- **Port 9411**: Zipkin UI - for distributed tracing and monitoring of demo application

### Zipkin Integration Benefits

- **Distributed Tracing**: Track saga execution across multiple microservices
- **Performance Monitoring**: Identify bottlenecks in saga workflows
- **Debugging Support**: Visualize transaction flows and error paths
- **Real-time Insights**: Monitor active saga transactions

> **Note**: Ports 8082 and 8083 (individual bank services) provide direct access to bank APIs for testing and debugging. Port 9411 (Zipkin) is used for demo application observability and is not a requirement of Oracle Sagas itself - it's an optional monitoring tool to enhance the demo experience and provide insights into distributed transaction flows.

## Task 4: Provision a Compute Instance for CloudBank

---

In this task, you'll create a Linux compute instance to host the CloudBank demo application. You'll select an Ubuntu image, configure the compute shape, set up networking, generate SSH keys for secure access, and use a cloud-init script to automatically install Podman and pull required container images. This instance will serve as the foundation for running the CloudBank microservices containers.

### **Step 1: Navigate to Compute Instances**

From the OCI Console, click the navigation menu (☰) in the top-left corner and navigate to **Compute** → **Instances**.

![Navigate to Compute](./images/lab2-task4-1.png "Click navigation menu → Compute → Instances")

### **Step 2: Initiate Instance Creation**

On the Instances page, click **Create Instance** to start the instance creation wizard.

![Create Instance](./images/lab2-task4-2.png "Click Create Instance button")

### **Step 3: Configure Basic Instance Information**

Enter the basic instance details:

- **Name**: `oracle-saga-compute-instance`
- **Create in Compartment**: Select your compartment from the dropdown
- **Placement Configuration**:
   - **Availability Domain**: Select any available domain (e.g., AD-1, AD-2, or AD-3)

    ```
    <copy>oracle-saga-compute-instance</copy>
    ```

![Instance Basic Details](./images/lab2-task4-3.png "Enter instance name, compartment, and placement details")

### **Step 4: Access Image Selection**

In the **Image and shape** section, click **Edit** next to the image details to change from the default Oracle Linux image to Ubuntu.

![Edit Image](./images/lab2-task4-4.png "Click Edit to change the default image")

### **Step 5: Browse Available Images**

In the Browse All Images dialog:

- Click on **Ubuntu** from the list of available operating systems
- You'll see various Ubuntu versions available

![Browse Images](./images/lab2-task4-5.png "Select Ubuntu from available operating systems")

### **Step 6: Select Ubuntu Image Version**

- Choose **Canonical Ubuntu 24.04 Minimal** (recommended for minimal resource usage)
- Review the image details including the default username (`ubuntu`)
- Click **Select Image** to confirm your choice

![Select Ubuntu Version](./images/lab2-task4-6.png "Select Ubuntu 24.04 Minimal and click Select Image")

### **Step 7: Access Shape Configuration**

Back on the main instance creation page, click **Change Shape** under the Shape to modify the compute resources.

![Edit Shape](./images/lab2-task4-7.png "Click Edit to change the instance shape")

### **Step 8: Choose Shape Series**

In the Browse All Shapes dialog:

- Click on **Specialty and previous generation** to access free-tier eligible shapes
- This section contains shapes suitable for development and testing

![Browse Shapes](./images/lab2-task4-8.png "Click Specialty and previous generation")

### **Step 9: Select Free Tier Shape**

- Select **VM.Standard.E2.1.Micro** which provides:
 - **OCPUs**: 1 OCPU (Always Free eligible)
 - **Memory**: 1 GB RAM
 - **Network Bandwidth**: 0.48 Gbps
- Click **Select Shape** to confirm

![Select Micro Shape](./images/lab2-task4-9.png "Select VM.Standard.E2.1.Micro for free tier")


### **Step 10: Configure Cloud-Init Script**

In the **Advanced options** tab under **Management**:

- In the **Cloud-init script** section, select **Paste cloud-init script**
- This script will run during the first boot to set up the environment automatically

![Access Cloud-Init](./images/lab2-task4-13.png "Select Paste cloud-init script option")

### **Step 11: Add Initialization Script**

Paste the following cloud-init script that will:

- Update package lists
- Install Podman container runtime
- Pre-pull required container images in parallel for faster deployment

```
<copy>#!/bin/bash
echo "Starting CloudBank environment setup..."
start_time=$SECONDS

# Update package lists only
apt-get update -y

# Install required packages
apt-get install -y podman curl wget pipx

# Configure podman for ubuntu user
echo "Configuring Podman for ubuntu user..."

# Add ubuntu user to necessary groups
usermod -aG podman ubuntu

# Enable and start podman socket for system-wide access
systemctl enable podman.socket
systemctl start podman.socket

# Configure podman-compose for ubuntu user
sudo -u ubuntu bash << 'EOF'
# Set up environment for ubuntu user
export PATH=/home/ubuntu/.local/bin:$PATH
pipx ensurepath
pipx install podman-compose

# Pre-pull required container images in parallel for faster setup
echo "Pulling container images in parallel as ubuntu user..."
podman pull ghcr.io/oracle/oraclelinux:8 &
podman pull container-registry.oracle.com/database/sqlcl:latest &
podman pull docker.io/swaggerapi/swagger-ui:v5.20.7 &
podman pull docker.io/library/maven:3.8.6-openjdk-11 &
podman pull ghcr.io/openzipkin/zipkin:latest &
podman pull container-registry.oracle.com/database/free:latest &

# Wait for all parallel downloads to complete
echo "Waiting for all container downloads to complete..."
wait

# Create directories for CloudBank
mkdir -p /home/ubuntu/cloudbank

EOF

# Ensure proper ownership
chown -R ubuntu:ubuntu /home/ubuntu/

echo "CloudBank environment setup complete for ubuntu user!"
end_time=$SECONDS
elapsed_seconds=$((end_time - start_time))
echo "Elapsed time: ${elapsed_seconds} seconds"</copy>
```

Click next to move on to the security section.

![Add Cloud-Init Script](./images/lab2-task4-14.png "Paste cloud-init script for automated setup")

### **Step 12: Configure Security Settings**

In the Security section, keep all default boot volume encryption settings as they are and click **Next** to continue to networking configuration.

### **Step 13: Configure Primary Network Interface**

In the **Primary VNIC Information** section, configure the networking:

- **Primary VNIC Name**: `oracle-saga-vnic`
- **Virtual Cloud Network in [Compartment]**: Under Primary network, choose Select existing Virtual cloud network floowed by appropriate compartment and Select **Oracle-Saga-VCN** fro Virtual cloud network
- **Subnet in [Compartment]**: Under Subnet, choose Select existing subnet followed by choosing appropriate compartment foolowed by choosing the **public subnet** (should show as public-subnet-Oracle-Saga-VCN)
- **Assign a public IPv4 address**: Ensure this is **checked** for internet access

    ```
    <copy>oracle-saga-vnic</copy>
    ```

![Configure Primary VNIC](./images/lab2-task4-10.png "Configure VNIC name, VCN, and subnet settings")

### **Step 14: Generate SSH Key Pair**

In the **Add SSH keys** section, set up SSH authentication for secure instance access:

**Option 1: Generate new keys (recommended)**
- Select **Generate a key pair for me**
- Click **Download Private Key** and save the `.key` file securely
- Click **Download Public Key** and save the `.pub` file
- Store both files safely - you need the private key to connect to your instance

**Option 2: Use existing public key**
- Select **Upload public key files (.pub)** if you already have SSH keys
- Browse and select your existing public key file

![Generate SSH Keys](./images/lab2-task4-11.png "Generate SSH key pair and download both keys")

### **Step 15: Configure Boot Volume Storage**

Click **Next** to access the Boot volume section. Accept the default storage settings (boot volume size and performance tier) and click **Next** to proceed to the final review.


### **Step 16: Review Instance Configuration**

Scroll up to review all your configuration settings:

- **Name**: oracle-saga-compute-instance
- **Image**: Ubuntu 24.04 Minimal
- **Shape**: VM.Standard.E2.1.Micro
- **Cloud-init**: Configured for Podman and CloudBank setup
- **VCN**: Oracle-Saga-VCN (public subnet)
- **SSH Keys**: Generated key pair

![Review Configuration](./images/lab2-task4-15.png "Review all instance configuration settings")

### **Step 17: Create the Instance**

After reviewing all settings, click **Create** to start the instance provisioning process.

![Create Instance Final](./images/lab2-task4-16.png "Click Create to provision the instance")

### **Step 18: Monitor Instance Provisioning**

You'll be redirected to the instance details page. Click the details tab and monitor the **State**:

- **Provisioning**: Instance is being created (1-2 minutes)
- **Running**: Instance is ready for use
- The **Public IP Address** will appear once the instance is running

![Instance Provisioning](./images/lab2-task4-17.png "Monitor instance state during provisioning")

### **Step 19: Record Instance Public IP**

Once the instance shows **Running** state, copy the public IP address from the instance details and enter it in the form below for use in subsequent tasks:

<div class="input-section">
<strong>VM Public IP:</strong> <input type="text" id="compute-public-ip" placeholder="xxx.xxx.xxx.xxx" class="input-field">

<button onclick="saveComputeIP()" class="save-btn">Save Public IP</button>
<button onclick="deleteComputeIP()" class="delete-btn">Delete Saved IP</button>
<button onclick="clearComputeIP()" class="clear-btn">Clear IP</button>
</div>

<div id="compute-ip-save-status" style="display:none;" class="save-status">
<span id="compute-ip-save-message"></span>
</div>

The cloud-init status verification will be covered when we connect to the compute instance in the next task.

![Instance Running](./images/lab2-task4-18.png "Instance running with IP addresses assigned")

**Expected Output**:

- ✅ Ubuntu 24.04 compute instance successfully created and running
- ✅ Instance named `oracle-saga-compute-instance` in your compartment
- ✅ Public IP address assigned for internet connectivity
- ✅ Private IP address for VCN internal communication
- ✅ SSH key pair generated and downloaded for secure access
- ✅ Podman container runtime installed and configured
- ✅ Required container images pre-pulled for CloudBank deployment
- ✅ Instance attached to Oracle-Saga-VCN public subnet

### Instance Environment Details

Your newly created instance includes:

- **Operating System**: Ubuntu 24.04 Minimal
- **Compute Resources**: 1 OCPU, 1 GB RAM (free tier eligible)
- **Container Runtime**: Podman (Docker alternative)
- **Development Tools**: curl, wget
- **Network Access**: Public internet via Internet Gateway
- **Security**: SSH key-based authentication only

### Troubleshooting Tips

- **No Public IP**: Ensure you selected the public subnet and VCN has Internet Gateway
- **SSH Connection Issues**: Verify security list allows port 22 and you're using correct private key
- **Cloud-init Status**: Check `/var/log/cloud-init-output.log` on the instance for script execution logs
- **Container Images**: If images fail to pull, they can be manually pulled after SSH login

> **Note**: The cloud-init script runs automatically during first boot and may take few minutes to complete. You can SSH into the instance and monitor progress with `tail -f /var/log/cloud-init-output.log`.

## Task 5: Connect to ADB & VM via Cloud Shell

---

In this task, you'll use Oracle Cloud Infrastructure's **Cloud Shell** - a browser-based terminal service - to establish secure connections to both your Autonomous Database and Ubuntu compute instance. You'll download and prepare the CloudBank demo application, generate database connection credentials, verify connectivity, and prepare your cloud environment for the complete application deployment using interactive command builders and automated setup tools.

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

### **Step 3: Download CloudBank Demo Application**

Download the complete CloudBank application package to your local machine. This package contains:
- Microservice source code and configuration files
- Database setup scripts and schema definitions  
- Docker compose files for container orchestration
- Configuration templates for OCI integration

Click to download: [CloudBank Demo Application Package](files/oracle-saga-cloudbank.zip?download=1)

![Download CloudBank Package](./images/lab2-task5-3.png "Download CloudBank application files")

### **Step 4: Upload Files to Cloud Shell**

Upload the CloudBank application package and your SSH keys to Cloud Shell. Note that Cloud Shell requires uploading files one at a time:

1. In Cloud Shell, click the **Settings** icon (⚙️) in the top-right corner
2. Select **Upload** from the dropdown menu
3. Upload each file individually by selecting **"Choose from your computer"** or drag-and-drop:
 - First: `oracle-saga-cloudbank.zip` (downloaded in Step 3) → Click **Upload**
 - Second: Your SSH private key file (`*.key` from Task 4) → Click **Upload**
 - Third: Your SSH public key file (`*.pub` from Task 4) → Click **Upload**

*Note: You must upload each file separately and click Upload for each one.*

![Upload Files to Cloud Shell](./images/lab2-task5-4.png "Upload files one at a time to Cloud Shell")

### **Step 5: Extract CloudBank Application**

Unzip the CloudBank application package to access the source code and configuration files:

```
<copy>unzip oracle-saga-cloudbank.zip
cd oracle-saga-cloudbank
ls -la</copy>
```

This will extract:
- **adbsSetup/**: Database setup scripts and wallet storage directory
- **CloudBank/**: Microservice source code (banka, bankb, orchestrator, Website)
- **swagger-ui-config/**: API documentation interface
- **osagaAdbsSetup.yaml**: Configuration file for the setup

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

First, get your current directory path, then set the TNS_ADMIN variable:

```
<copy>pwd</copy>
```

Then use the interactive form to set your TNS_ADMIN path:

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

First, get your database connection string from the Autonomous Database details page:
- Click **Database Connection**
- Copy the connection string for **&lt;DATABASE\_NAME&gt;_medium**

Then use the form below to generate your database connection command:

<div class="input-section">
<strong>Connection String:</strong> 
<div style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<input type="text" id="adb-connection-string" placeholder="oraclesagademo_medium" class="input-field" oninput="updateTask5ConnectionCommand()" style="flex: 1;">
<button onclick="saveConnectionString()" class="save-btn-small">Save</button>
<button onclick="deleteConnectionString()" class="delete-btn-small">Delete</button>
<button onclick="clearConnectionString()" class="clear-btn-small">Clear</button>
</div><br/>
<strong>Username:</strong> <input type="text" id="task5-db-username" value="ADMIN" class="input-field" disabled><br/>
<strong>Password:</strong> <input type="text" id="task5-db-password" value="admin123#" class="input-field" disabled>
</div>

<div id="connection-string-save-status" style="display:none;" class="save-status">
<span id="connection-string-save-message"></span>
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

### **Step 13: Set SSH Key Permissions**

Secure your uploaded SSH keys with proper permissions:

```
<copy>chmod 600 *.key
chmod 644 *.pub
ls -la *</copy>
```

![Set Key Permissions](./images/lab2-task5-14.png "Set correct SSH key permissions")

### **Step 14: SSH to VM using Interactive Builder**

Use the form below to generate your SSH command:

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

Once connected to your VM, verify the cloud-init setup completed:

```
<copy>podman --version
podman images
ls -la /home/ubuntu/cloudbank</copy>
```

![Verify VM Setup](./images/lab2-task5-16.png "Verify Podman and CloudBank environment")

### **Step 16: Exit VM and Explore CloudBank Code**

Exit the VM SSH session and explore the CloudBank application structure:

```
<copy>exit
cd ~/oracle-saga-cloudbank
ls -la</copy>
```

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

When using both CloudShell and Code Editor, it's helpful to have them in tabs for easy switching:

- If your CloudShell and Code Editor are not already in tabs mode, click on **Actions** (to the left of Developer Tools section)
- Choose **Tabs** from the dropdown menu  
- You'll now see separate tabs for **CloudShell** and **Code Editor**
- This makes it easy to switch between database operations and code editing in future labs

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

/* Task 4 SSH config section removed as it was premature */

/* Task 5 Interactive Command Styles */
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
       broker1: { 
           name: document.getElementById('broker1-user').value.trim() || null,
           password: document.getElementById('broker1-password').value.trim() || null
       },
       orchestrator1: { 
           name: document.getElementById('orchestrator1-user').value.trim() || null,
           password: document.getElementById('orchestrator1-password').value.trim() || null
       },
       broker2: { 
           name: document.getElementById('broker2-user').value.trim() || null,
           password: document.getElementById('broker2-password').value.trim() || null
       },
       orchestrator2: { 
           name: document.getElementById('orchestrator2-user').value.trim() || null,
           password: document.getElementById('orchestrator2-password').value.trim() || null
       },
       banka: { 
           name: document.getElementById('banka-user').value.trim() || null,
           password: document.getElementById('banka-password').value.trim() || null
       },
       bankb: { 
           name: document.getElementById('bankb-user').value.trim() || null,
           password: document.getElementById('bankb-password').value.trim() || null
       },
       bankc: { 
           name: document.getElementById('bankc-user').value.trim() || null,
           password: document.getElementById('bankc-password').value.trim() || null
       },
       bankd: { 
           name: document.getElementById('bankd-user').value.trim() || null,
           password: document.getElementById('bankd-password').value.trim() || null
       }
   };
   
   let script = `-- Customized Script Generated from User Configuration\n`;
   
   const brokers = [users.broker1, users.broker2].filter(u => u.name && u.password);
   const others = [users.orchestrator1, users.orchestrator2, users.banka, users.bankb, users.bankc, users.bankd].filter(u => u.name && u.password);
   
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
       'broker1-user': 'broker1', 'broker1-password': 'Welcome_123#',
       'orchestrator1-user': 'orchestrator1', 'orchestrator1-password': 'Welcome_123#',
       'broker2-user': 'broker2', 'broker2-password': 'Welcome_123#',
       'orchestrator2-user': 'orchestrator2', 'orchestrator2-password': 'Welcome_123#',
       'banka-user': 'banka', 'banka-password': 'Welcome_123#',
       'bankb-user': 'bankb', 'bankb-password': 'Welcome_123#',
       'bankc-user': 'bankc', 'bankc-password': 'Welcome_123#',
       'bankd-user': 'bankd', 'bankd-password': 'Welcome_123#'
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
   ['BROKER1', 'ORCHESTRATOR1', 'BROKER2', 'ORCHESTRATOR2', 'BANKA', 'BANKB', 'BANKC', 'BANKD'].forEach(user => {
       sessionStorage.removeItem(`cloudbank_USER_${user}`);
       sessionStorage.removeItem(`cloudbank_PASSWORD_${user}`);
   });
   
   const users = {
       broker1: document.getElementById('broker1-user').value.trim() || null,
       orchestrator1: document.getElementById('orchestrator1-user').value.trim() || null,
       broker2: document.getElementById('broker2-user').value.trim() || null,
       orchestrator2: document.getElementById('orchestrator2-user').value.trim() || null,
       banka: document.getElementById('banka-user').value.trim() || null,
       bankb: document.getElementById('bankb-user').value.trim() || null,
       bankc: document.getElementById('bankc-user').value.trim() || null,
       bankd: document.getElementById('bankd-user').value.trim() || null
   };
   
   const passwords = {
       broker1: document.getElementById('broker1-password').value.trim() || null,
       orchestrator1: document.getElementById('orchestrator1-password').value.trim() || null,
       broker2: document.getElementById('broker2-password').value.trim() || null,
       orchestrator2: document.getElementById('orchestrator2-password').value.trim() || null,
       banka: document.getElementById('banka-password').value.trim() || null,
       bankb: document.getElementById('bankb-password').value.trim() || null,
       bankc: document.getElementById('bankc-password').value.trim() || null,
       bankd: document.getElementById('bankd-password').value.trim() || null
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
   ['BROKER1', 'ORCHESTRATOR1', 'BROKER2', 'ORCHESTRATOR2', 'BANKA', 'BANKB', 'BANKC', 'BANKD'].forEach(user => {
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
       broker1: document.getElementById('broker1-user').value.trim(),
       orchestrator1: document.getElementById('orchestrator1-user').value.trim(),
       broker2: document.getElementById('broker2-user').value.trim(),
       orchestrator2: document.getElementById('orchestrator2-user').value.trim(),
       banka: document.getElementById('banka-user').value.trim(),
       bankb: document.getElementById('bankb-user').value.trim(),
       bankc: document.getElementById('bankc-user').value.trim(),
       bankd: document.getElementById('bankd-user').value.trim()
   };
   
   const passwords = {
       broker1: document.getElementById('broker1-password').value.trim(),
       orchestrator1: document.getElementById('orchestrator1-password').value.trim(),
       broker2: document.getElementById('broker2-password').value.trim(),
       orchestrator2: document.getElementById('orchestrator2-password').value.trim(),
       banka: document.getElementById('banka-password').value.trim(),
       bankb: document.getElementById('bankb-password').value.trim(),
       bankc: document.getElementById('bankc-password').value.trim(),
       bankd: document.getElementById('bankd-password').value.trim()
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

// Task 4 Functions
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
       const savedBroker1 = sessionStorage.getItem('cloudbank_USER_BROKER1');
       const hasSavedConfig = savedBroker1 !== null;
       
       const defaultUsers = {
           broker1: 'broker1',
           orchestrator1: 'orchestrator1',
           broker2: 'broker2',
           orchestrator2: 'orchestrator2',
           banka: 'banka',
           bankb: 'bankb',
           bankc: 'bankc',
           bankd: 'bankd'
       };
       const defaultPassword = 'Welcome_123#';
       
       if (hasSavedConfig) {
           const formFields = [
               { formId: 'broker1-user', sessionKey: 'cloudbank_USER_BROKER1' },
               { formId: 'broker1-password', sessionKey: 'cloudbank_PASSWORD_BROKER1' },
               { formId: 'orchestrator1-user', sessionKey: 'cloudbank_USER_ORCHESTRATOR1' },
               { formId: 'orchestrator1-password', sessionKey: 'cloudbank_PASSWORD_ORCHESTRATOR1' },
               { formId: 'broker2-user', sessionKey: 'cloudbank_USER_BROKER2' },
               { formId: 'broker2-password', sessionKey: 'cloudbank_PASSWORD_BROKER2' },
               { formId: 'orchestrator2-user', sessionKey: 'cloudbank_USER_ORCHESTRATOR2' },
               { formId: 'orchestrator2-password', sessionKey: 'cloudbank_PASSWORD_ORCHESTRATOR2' },
               { formId: 'banka-user', sessionKey: 'cloudbank_USER_BANKA' },
               { formId: 'banka-password', sessionKey: 'cloudbank_PASSWORD_BANKA' },
               { formId: 'bankb-user', sessionKey: 'cloudbank_USER_BANKB' },
               { formId: 'bankb-password', sessionKey: 'cloudbank_PASSWORD_BANKB' },
               { formId: 'bankc-user', sessionKey: 'cloudbank_USER_BANKC' },
               { formId: 'bankc-password', sessionKey: 'cloudbank_PASSWORD_BANKC' },
               { formId: 'bankd-user', sessionKey: 'cloudbank_USER_BANKD' },
               { formId: 'bankd-password', sessionKey: 'cloudbank_PASSWORD_BANKD' }
           ];
           
           formFields.forEach(field => {
               const element = document.getElementById(field.formId);
               const savedValue = sessionStorage.getItem(field.sessionKey);
               if (element) {
                   element.value = savedValue === 'null' ? '' : (savedValue || '');
               }
           });
                       
           const displayMappings = [
               { displayId: 'saved-broker1', userKey: 'cloudbank_USER_BROKER1', passKey: 'cloudbank_PASSWORD_BROKER1' },
               { displayId: 'saved-orchestrator1', userKey: 'cloudbank_USER_ORCHESTRATOR1', passKey: 'cloudbank_PASSWORD_ORCHESTRATOR1' },
               { displayId: 'saved-broker2', userKey: 'cloudbank_USER_BROKER2', passKey: 'cloudbank_PASSWORD_BROKER2' },
               { displayId: 'saved-orchestrator2', userKey: 'cloudbank_USER_ORCHESTRATOR2', passKey: 'cloudbank_PASSWORD_ORCHESTRATOR2' },
               { displayId: 'saved-banka', userKey: 'cloudbank_USER_BANKA', passKey: 'cloudbank_PASSWORD_BANKA' },
               { displayId: 'saved-bankb', userKey: 'cloudbank_USER_BANKB', passKey: 'cloudbank_PASSWORD_BANKB' },
               { displayId: 'saved-bankc', userKey: 'cloudbank_USER_BANKC', passKey: 'cloudbank_PASSWORD_BANKC' },
               { displayId: 'saved-bankd', userKey: 'cloudbank_USER_BANKD', passKey: 'cloudbank_PASSWORD_BANKD' }
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
           
           const broker1Field = document.getElementById('broker1-user');
           const isTask2Visible = broker1Field && broker1Field.offsetParent !== null;

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
               { displayId: 'saved-broker1', username: defaultUsers.broker1 },
               { displayId: 'saved-orchestrator1', username: defaultUsers.orchestrator1 },
               { displayId: 'saved-broker2', username: defaultUsers.broker2 },
               { displayId: 'saved-orchestrator2', username: defaultUsers.orchestrator2 },
               { displayId: 'saved-banka', username: defaultUsers.banka },
               { displayId: 'saved-bankb', username: defaultUsers.bankb },
               { displayId: 'saved-bankc', username: defaultUsers.bankc },
               { displayId: 'saved-bankd', username: defaultUsers.bankd }
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
               console.log('No saved compute IP found or task 4 is not visible.');
           }
       }

       const savedConnectionString = sessionStorage.getItem('adbConnectionString');
       
       const task5ConnectionField = document.getElementById('task5-connection-string');
       const task5UsernameField = document.getElementById('task5-db-username');
       const task5PasswordField = document.getElementById('task5-db-password');
       const adbConnectionField = document.getElementById('adb-connection-string');
       const connectionStringMessageEl = document.getElementById('connection-string-save-message');
       
       if (savedConnectionString) {
           if (task5ConnectionField) {
               task5ConnectionField.value = savedConnectionString;
           }
           if (adbConnectionField) {
               adbConnectionField.value = savedConnectionString;
           }
       }
       
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