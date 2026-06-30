# Core Setup: Sagas Broker, Coordinator & Participants

## **Introduction**

**Oracle Sagas** extend database transaction management with a **long-running transaction pattern**. Instead of relying on a single atomic commit across distributed services, sagas break business logic into multiple steps coordinated by a **Saga Broker** and **Saga Coordinator**, with individual **Saga Participants** executing work and compensating on failure.  

This lab focuses on setting up the **core runtime infrastructure**: the **Broker, Coordinator, and Participants**. You will also explore **roles and permissions**, as well as **monitoring views** provided by the database. These components together allow the Oracle Database to orchestrate complex multi-step workflows in a resilient, auditable, and compensatable fashion.

At their core, sagas address challenges of **distributed transactions** in microservice and multi-tenant environments, where global locks and two-phase commits are impractical. Oracle Sagas provide:
- **Atomicity with compensation** rather than strict rollback.
- **Queue-based coordination** (AQ/TEQ) between services.
- **Built-in administration** with `DBMS_SAGA_ADM`.
- **Observability** through dictionary views of saga state.

<details open>
<summary><mark>Key Characteristics of Oracle Sagas:</mark></summary>

- **Broker:** The message hub that propagates saga events between coordinators and participants.
- **Coordinator:** The central orchestrator that maintains saga state and invokes compensations.
- **Participants:** Business services that implement REQUEST, RESPONSE, COMMIT, ROLLBACK etc handlers.
- **Co-location Constraint:** The saga coordinator and initiator (orchestrator) must reside in the same schema and PDB.
- **Compensation:** When failures occur, the entire saga transaction is rolled back, and the coordinator calls each participant to execute their individual compensating actions to undo their portion of the work.
- **Queues:** Advanced Queuing (AQ) or Transaction Event Queues (TEQ) are used for reliable messaging.
- **Monitoring:** Database views and history tables provide visibility into saga progress.

</details>

In this lab, we will use **SQLcl** to configure the **Broker, Coordinator, and Participants** using the `DBMS_SAGA_ADM` package. These components establish the foundation for Java and PL/SQL saga clients in future labs.

- Estimated time: XX minutes

Watch the video below for a quick walk-through of the lab.

<!-- [Prepare your environment](videohub:1_nw8ufqzp:medium) -->

[Prepare your environment](videohub::medium)



### Objectives

In this lab, you will:

- **Create a Saga Broker** <br />
  Learn how to define and set up the message hub for saga communication.

- **Configure a Saga Coordinator** <br />
  Register the saga orchestrator responsible for maintaining saga state.

- **Register Saga Participants** <br />
  Implement business services that handles REQUEST, RESPONSE, COMMIT AND ROLLBACK etc requests.

- **Assign Roles & Permissions** <br />
  Ensure the correct privileges are available for administrators and applications.

- **Explore Saga Monitoring Views** <br />
  Query system views to inspect saga executions, incomplete sagas, and history.


### Prerequisites

- Oracle **Autonomous Database (ADB-S)** provisioned in Lab 2  
- **CloudShell** access with SQLcl installed  
- User credentials saved from Lab 2 (Broker1, Orchestrator1, Bank users)  
- Connected to your ADB instance with appropriate saga roles
- Familiar with basic SQL and PL/SQL concepts  
- Completed Lab 2: Environment Setup

> **Note**: This lab uses the user credentials configured in Lab 2. If you saved user configurations there, they will be auto-populated in the forms below. Otherwise, you can enter them manually and we'll generate the commands for you.


## Task 1: Saga Broker

The **Oracle Saga Broker** serves as the central messaging hub in saga topology, acting as a sophisticated message delivery service that orchestrates communication between saga coordinators and participants across distributed systems. Unlike traditional message brokers, the Saga Broker is specifically designed for long-running transaction patterns and does not maintain saga state itself—instead, it focuses purely on reliable message propagation and routing. The broker creates a unified Java topic that serves as a mailbox for saga participants, enabling asynchronous communication through Oracle's Advanced Queuing (AQ) infrastructure with bidirectional message propagation channels.

In Oracle's saga architecture, each saga participant or coordinator is associated with exactly one broker (either local or remote), and the broker automatically generates topic names in the format `SAGA$_<broker_name>_INOUT` to establish these communication channels. The broker's intelligent message routing ensures that messages are propagated only to their intended participants, supporting cross-database message transfer through database links when needed. This design enables microservices and distributed applications to participate in saga transactions without requiring direct point-to-point connections, making the broker an essential infrastructure component for scalable, resilient saga implementations in enterprise environments.

**Syntax:**
```
DBMS_SAGA_ADM.ADD_BROKER(
  broker_name      => '&lt;BROKER_NAME&gt;',
  broker_schema    => '&lt;BROKER_SCHEMA&gt;',     -- Default: current user
  storage_clause   => '&lt;STORAGE_CLAUSE&gt;',    -- Optional: NULL
  queue_partitions => '&lt;QUEUE_PARTITIONS&gt;',    -- Default: 1
  version          => '&lt;VERSION&gt';              -- Default: 1
);
```

<details open>
<summary><strong>📋 `DBMS_SAGA_ADM.ADD_BROKER` Parameters</strong></summary>

**Complete Parameter Reference:**

- **`broker_name`** (VARCHAR2): 
    - Unique identifier for the broker within the database
    - Case-sensitive string that will be used in topic naming
    - Example: `'CloudBankBroker'`, `'TEST'`, `'prod_broker_01'`

- **`broker_schema`** (VARCHAR2): 
    - Database schema where broker objects will be created
    - Must have appropriate SAGA roles assigned.
    - Example: `'BROKER1'`, `USER`

- **`storage_clause`** (VARCHAR2, Optional):
    - Tablespace and storage specifications for broker objects
    - NULL uses default tablespace settings
    - Example: `'TABLESPACE USERS'`, `NULL`

- **`queue_partitions`** (NUMBER, Default: 1):
    - Number of queue partitions for performance optimization
    - Higher values improve concurrency but increase complexity
    - Must match across all associated coordinators and participants
    - Example: `1`, `4` 

- **`version`** (NUMBER, Default: 1):
    - Version number for broker configuration management
    - Used for future upgrades and compatibility
    - Example: `1`
</details>
<br/>

### Step 1: Switch to CloudShell

Since we were using the Code Editor in Lab 2, we need to switch back to the CloudShell tab:

1. **Select CloudShell Tab**: Click on the **CloudShell** tab in your browser.

   > **Note**: If you don't see tabs, click on **Actions** (to the left of Developer Tools) and choose **Tabs**, then select the CloudShell tab.

![Switch to CloudShell](./images/lab3-task1-step1.png "Switch to CloudShell tab")

### Step 2: Enter Connection String 

Connect to your broker schema using the auto-populated connection details from Lab 2:

<div class="input-section">
<strong>Broker Username:</strong> 
<input type="text" id="broker1-user" placeholder="<BROKER_SCHEMA>" class="input-field" oninput="updateBrokerConnectionCommand()"><br/>
<strong>Broker Password:</strong> 
<input type="text" id="broker1-password" placeholder="<BROKER_SCHEMA_PASSWORD>" class="input-field" oninput="updateBrokerConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="broker-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateBrokerConnectionCommand()"><br/>
</div>

**Generated Connection Command:**
<pre id="broker-connection-command-container" class="interactive-command">
<span id="broker-connection-command" class="command-text">sql &lt;BROKER_USERNAME&gt;/&lt;BROKER_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('broker-connection-command', 'broker-connection-command-container')">Copy</button>
</pre>

**Expected Output:**
```text
SQLcl: Release 24.4 Production on Wed Aug 21 14:30:15 2025

Copyright (c) 1982, 2025, Oracle.  All rights reserved.

Connected to:
Oracle Database 23ai Enterprise Edition Release 23.0.0.0.0 - for Oracle Cloud and Engineered Systems
Version 23.7.0.25.03

SQL> 
```

![Connect to Broker Schema](./images/lab3-task1-step2.png "Successfully connected to broker1 schema")

### Step 3: Verify SAGA Roles

Verify that the broker user has the necessary SAGA roles:

```
<copy>
SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE LIKE '%SAGA%';
</copy>
```

**Expected Output:**
```text
ROLE
--------------------
`SAGA_ADM_ROLE`
`SAGA_PARTICIPANT_ROLE`

2 rows selected.
```

![Verify SAGA Roles](./images/lab3-task1-step3.png "SAGA roles verification for broker user")

### Step 4: Configure Broker Details

Configure your broker name and settings. The broker name is case-sensitive and will be used in topic naming:

<div class="input-section" style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<strong>Broker Database Name (case-sensitive):</strong> 
<input type="text" id="broker-db-name" placeholder="<BROKER_DB_NAME>" class="input-field" oninput="updateBrokerCommand(); saveBrokerDbName()">

<button onclick="saveBrokerConfig()" class="save-btn-small">Save</button>
<button onclick="deleteBrokerConfig()" class="delete-btn-small">Delete</button>
<button onclick="clearBrokerConfig()" class="clear-btn-small">Clear</button>
</div>

<div id="broker-save-status" style="display:none;" class="save-status">
<span id="broker-save-message"></span>
</div>

![Configure Broker Details](./images/lab3-task1-step4.png "Configure broker name and settings")

### Step 5: Generate ADD_BROKER Command

The command below is auto-generated based on your configuration:

**Generated ADD_BROKER Command:**
<pre id="broker-command-container" class="interactive-command">
<span id="broker-command" class="command-text">-- Configure broker details above to generate command</span>
<button class="copy-btn" onclick="copyToClipboard('broker-command', 'broker-command-container')">Copy</button>
</pre>

![Generate ADD_BROKER Command](./images/lab3-task1-step5.png "Auto-generated ADD_BROKER command")

### Step 6: Verify Broker Creation

After executing the ADD_BROKER command, verify the broker was created successfully:

**Check Broker Creation:**
<pre id="verify-broker-command-container" class="interactive-command">
<span id="verify-broker-command" class="command-text">-- Will auto-update based on your broker name above</span>
<button class="copy-btn" onclick="copyToClipboard('verify-broker-command', 'verify-broker-command-container')">Copy</button>
</pre>

**Expected Output:**
```text
BROKER_NAME OWNER    QUEUE_PARTITIONS VERSION CREATED_DATE
----------- -------- ---------------- ------- -------------------
TEST        BROKER1                 1       1 21-AUG-25 14:35:22

1 row selected.
```

---

## Task 2: Saga Coordinator

The **Oracle Saga Coordinator** serves as the central orchestration engine for saga transactions, acting as the state machine that manages the complete lifecycle of distributed business processes. Unlike traditional transaction coordinators that rely on two-phase commit protocols, the Saga Coordinator maintains saga state through discrete steps and manages compensation workflows when failures occur. The coordinator communicates with participants through the broker's message infrastructure, tracking progress through each saga phase and ensuring that either all operations complete successfully or the entire saga is rolled back with each participant executing their individual compensating actions.

As the control center of the saga pattern, the coordinator maintains detailed logs of each participant's state changes and orchestrates complex multi-step workflows that can span multiple databases, services, and timeframes. The coordinator's intelligent retry mechanisms and compensation logic enable resilient distributed transactions that can recover gracefully from partial failures, network issues, or service unavailability. When integrated with Oracle's Advanced Queuing infrastructure, the coordinator provides guaranteed message delivery and exactly-once processing semantics, making it suitable for mission-critical business processes that require both performance and reliability in enterprise environments.

> **Important Architectural Limitation**: In Oracle Database 23ai's initial saga implementation, the **saga coordinator and the saga initiator (orchestrator) must be co-located in the same database schema and PDB**. This is a fundamental constraint that requires the orchestrating service to also act as a saga participant. While other participants can be distributed across different schemas or databases, the coordinator-initiator pair cannot be separated. This architectural decision optimizes performance by eliminating cross-schema communication overhead during saga initialization and coordination, but limits deployment flexibility compared to fully distributed saga frameworks.

**Syntax:**
```
DBMS_SAGA_ADM.ADD_COORDINATOR(
  coordinator_name      => '&lt;COORDINATOR_NAME&gt;',
  coordinator_schema    => '&lt;COORDINATOR_SCHEMA&gt;',    -- Default: current user
  storage_clause        => '&lt;STORAGE_CLAUSE&gt;',        -- Optional: NULL
  dblink_to_broker      => '&lt;DBLINK_TO_BROKER&gt;',      -- Optional: NULL
  mailbox_schema        => '&lt;MAILBOX_SCHEMA&gt;',
  broker_name           => '&lt;BROKER_NAME&gt;',
  dblink_to_coordinator => '&lt;DBLINK_TO_COORDINATOR&gt;', -- Optional: NULL
  queue_partitions      => '&lt;QUEUE_PARTITIONS&gt;',      -- Default: 1
  listener_count        => '&lt;LISTENER_COUNT&gt;',        -- Default: -1 (AQ notification)
  version               => '&lt;VERSION&gt;'                -- Default: 1
);
```

<details open>
<summary><strong>📋 `DBMS_SAGA_ADM.ADD_COORDINATOR` Parameters</strong></summary>

**Complete Parameter Reference:**

- **`coordinator_name`** (VARCHAR2): 
    - Unique identifier for the coordinator within the saga infrastructure
    - Case-sensitive string used for participant registration
    - Example: `'CloudBankCoordinator'`, `'ORDER_COORDINATOR'`, `'payment_coord_01'`

- **`coordinator_schema`** (VARCHAR2): 
    - Database schema where coordinator objects will be created
    - Must have appropriate SAGA roles assigned
    - Example: `'ORCHESTRATOR1'`, `'COORD_USER'`

- **`broker_name`** (VARCHAR2):
    - Must reference an existing broker created in Task 1
    - Links coordinator to the messaging infrastructure
    - Example: Same broker name from Task 1 configuration

- **`mailbox_schema`** (VARCHAR2):
    - Schema containing the broker's mailbox infrastructure
    - Typically the same as broker_schema from Task 1
    - Example: `'BROKER1'`, same as broker user

- **`queue_partitions`** (NUMBER, Default: 1):
    - Must exactly match the broker's partition count
    - Inconsistent values will cause connection failures
    - Example: `1` (must match broker configuration)

- **`listener_count`** (NUMBER):
    - Controls message processing concurrency and notification method
    - `DBMS_SAGA_ADM.AQ_NTFN` (value: -2): Standard AQ notification (recommended)
    - `DBMS_SAGA_ADM.AUTO_NTFN` (value: -1): Dynamic listener scaling
    - Positive integer: Fixed number of dequeue jobs
    - Example: `DBMS_SAGA_ADM.AQ_NTFN` for most use cases

- **`version`** (NUMBER, Default: 1):
    - Version number for coordinator configuration management
    - Used for future upgrades and compatibility tracking
    - Example: `1`
</details>
<br/>

### Step 1: Switch to CloudShell

Ensure you're still in the CloudShell tab from Task 1:

1. **Verify CloudShell Tab**: Make sure you're still in the **CloudShell** tab in your browser.

   > **Note**: If you need to switch tabs, click on the **CloudShell** tab or use the Actions menu to configure tabs.

![Continue in CloudShell](./images/lab3-task2-step1.png "Continue using CloudShell tab")

### Step 2: Enter Connection String 

Connect to your orchestrator schema using the auto-populated connection details from Lab 2:

<div class="input-section">
<strong>Orchestrator Username:</strong> 
<input type="text" id="orchestrator1-user" placeholder="<ORCHESTRATOR_SCHEMA>" class="input-field" oninput="updateCoordinatorConnectionCommand()"><br/>
<strong>Orchestrator Password:</strong> 
<input type="text" id="orchestrator1-password" placeholder="<ORCHESTRATOR_SCHEMA_PASSWORD>" class="input-field" oninput="updateCoordinatorConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="coordinator-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateCoordinatorConnectionCommand()"><br/>
</div>

**Generated Connection Command:**
<pre id="coordinator-connection-command-container" class="interactive-command">
<span id="coordinator-connection-command" class="command-text">connect &lt;ORCHESTRATOR_USERNAME&gt;/&lt;ORCHESTRATOR_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('coordinator-connection-command', 'coordinator-connection-command-container')">Copy</button>
</pre>

**Expected Output:**
```text
SQLcl: Release 24.4 Production on Wed Aug 21 14:35:20 2025

Copyright (c) 1982, 2025, Oracle.  All rights reserved.

Connected to:
Oracle Database 23ai Enterprise Edition Release 23.0.0.0.0 - for Oracle Cloud and Engineered Systems
Version 23.7.0.25.03

SQL> 
```

![Connect to Orchestrator Schema](./images/lab3-task2-step2.png "Successfully connected to orchestrator1 schema")

### Step 3: Verify SAGA Roles

Verify that the orchestrator user has the necessary SAGA roles:

```
<copy>
SELECT role FROM USER_ROLE_PRIVS WHERE role LIKE '%SAGA%';
</copy>
```

**Expected Output:**
```text
ROLE
--------------------
`SAGA_ADM_ROLE`
`SAGA_PARTICIPANT_ROLE`

2 rows selected.
```

![Verify SAGA Roles](./images/lab3-task2-step3.png "SAGA roles verification for orchestrator user")

### Step 4: Configure Coordinator Details

Configure your coordinator name and settings. The coordinator will reference the broker created in Task 1:

<div class="input-section" style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<strong>Coordinator Name (case-sensitive):</strong> 
<input type="text" id="coordinator-name" placeholder="<COORDINATOR_PARTICIPANT_NAME>" class="input-field" oninput="updateCoordinatorCommand(); saveCoordinatorName()">

<button onclick="saveCoordinatorConfig()" class="save-btn-small">Save</button>
<button onclick="deleteCoordinatorConfig()" class="delete-btn-small">Delete</button>
<button onclick="clearCoordinatorConfig()" class="clear-btn-small">Clear</button>
</div>

<div id="coordinator-save-status" style="display:none;" class="save-status">
<span id="coordinator-save-message"></span>
</div>

![Configure Coordinator Details](./images/lab3-task2-step4.png "Configure coordinator name and settings")

### Step 5: Generate ADD_COORDINATOR Command

The command below is auto-generated based on your configuration and Task 1 broker settings:

**Generated ADD_COORDINATOR Command:**
<pre id="coordinator-command-container" class="interactive-command">
<span id="coordinator-command" class="command-text">-- Configure coordinator details above to generate command</span>
<button class="copy-btn" onclick="copyToClipboard('coordinator-command', 'coordinator-command-container')">Copy</button>
</pre>

![Generate ADD_COORDINATOR Command](./images/lab3-task2-step5.png "Auto-generated ADD_COORDINATOR command")

### Step 6: Verify Coordinator Creation

After executing the ADD_COORDINATOR command, verify the coordinator was created successfully:

**Check Coordinator Creation:**
<pre id="verify-coordinator-command-container" class="interactive-command">
<span id="verify-coordinator-command" class="command-text">-- Will auto-update based on your coordinator name above</span>
<button class="copy-btn" onclick="copyToClipboard('verify-coordinator-command', 'verify-coordinator-command-container')">Copy</button>
</pre>

**Expected Output:**
```text
COORDINATOR_NAME      COORDINATOR_SCHEMA BROKER_NAME QUEUE_PARTITIONS LISTENER_COUNT
--------------------- ------------------ ----------- ---------------- --------------
CLOUDBANKCOORDINATOR  ORCHESTRATOR1      TEST                       1             -2

1 row selected.
```

---

## Task 3: Saga Participants

The **Oracle Saga Participants** are the business service endpoints that implement the actual work within saga transactions, serving as the distributed components that execute specific business operations while maintaining the ability to compensate for their actions when required. Each participant follows the **Request-Response-COMMIT-Rollback** pattern, where REQUEST operations attempt to perform the business work, RESPONSE operations send status updates back to the coordinator, COMMIT operations finalize successful transactions, and ROLLBACK operations execute compensating actions to undo their specific work when the coordinator initiates saga rollback. Participants communicate asynchronously with the saga coordinator through Oracle's Advanced Queuing infrastructure, receiving operation requests and sending status responses that drive the overall saga state machine.

In Oracle's saga architecture, participants can be implemented either as database-native PL/SQL packages with callback procedures or as external Java applications that interact with the saga framework through JDBC connections and queue operations. The Java client approach offers greater flexibility for microservices architectures, allowing business logic to reside outside the database while still benefiting from Oracle's transactional guarantees and message reliability. Each participant maintains its own input queue for receiving saga instructions and automatically handles message acknowledgment, retry logic, and error reporting back to the coordinator, enabling robust distributed transaction processing across heterogeneous application environments.

**Syntax:**
```
DBMS_SAGA_ADM.ADD_PARTICIPANT(
  participant_name      => '&lt;PARTICIPANT_NAME&gt;',
  participant_schema    => '&lt;PARTICIPANT_SCHEMA&gt;',    -- Default: current user
  storage_clause        => '&lt;STORAGE_CLAUSE&gt;',        -- Optional: NULL
  coordinator_name      => '&lt;COORDINATOR_NAME&gt;',      -- Optional: NULL
  dblink_to_broker      => '&lt;DBLINK_TO_BROKER&gt;',      -- Required for distributed
  mailbox_schema        => '&lt;MAILBOX_SCHEMA&gt;',
  broker_name           => '&lt;BROKER_NAME&gt;',
  callback_schema       => '&lt;CALLBACK_SCHEMA&gt;',       -- Default: current user
  callback_package      => '&lt;CALLBACK_PACKAGE&gt;',
  dblink_to_participant => '&lt;DBLINK_TO_PARTICIPANT&gt;', -- Required for distributed
  queue_partitions      => '&lt;QUEUE_PARTITIONS&gt;',      -- Default: 1
  version               => '&lt;VERSION&gt;'                -- Default: 1
);
```

<details open>
<summary><strong>📋 `DBMS_SAGA_ADM.ADD_PARTICIPANT` Parameters</strong></summary>

**Complete Parameter Reference:**

- **`participant_name`** (VARCHAR2): 
    - Unique identifier for the participant within the broker's scope
    - Case-sensitive string used in saga workflows and monitoring
    - Example: `'CloudBank'`, `'BankA'`, `'BankB'`, `'PAYMENT_SERVICE'`

- **`participant_schema`** (VARCHAR2): 
    - Database schema where participant objects will be created
    - Must have appropriate SAGA roles assigned
    - Example: `'CLOUDBANK'`, `'BANKA'`, `'BANKB'`

- **`coordinator_name`** (VARCHAR2):
    - Must reference an existing coordinator created in Task 2
    - Links participant to the saga orchestration engine
    - Example: Same coordinator name from Task 2 configuration

- **`broker_name`** (VARCHAR2):
    - Must reference an existing broker created in Task 1
    - Links participant to the messaging infrastructure
    - Example: Same broker name from Task 1 configuration

- **`mailbox_schema`** (VARCHAR2):
    - Schema containing the broker's mailbox infrastructure
    - Typically the same as broker_schema from Task 1
    - Example: `'BROKER1'`, same as broker user

- **`callback_schema`** (VARCHAR2, Optional):
    - Schema containing PL/SQL callback package implementation
    - NULL for Java client implementations (our approach)
    - Example: `NULL` (for Java clients), `'CLOUDBANK'` (for PL/SQL callbacks)

- **`callback_package`** (VARCHAR2, Optional):
    - Name of PL/SQL package implementing TRY/CONFIRM/CANCEL methods
    - NULL for Java client implementations (our approach)
    - Example: `NULL` (for Java clients), `'PKG_CLOUDBANK_CB'` (for PL/SQL callbacks)

- **`queue_partitions`** (NUMBER, Default: 1):
    - Must exactly match broker and coordinator partition counts
    - Inconsistent values will cause connection failures
    - Example: `1` (must match broker and coordinator configuration)

- **`version`** (NUMBER, Default: 1):
    - Version number for participant configuration management
    - Used for future upgrades and compatibility tracking
    - Example: `1`
</details>
<br/>

### Step 1: Continue in CloudShell

Since we're already connected to the orchestrator schema from Task 2, we'll continue without exiting.

1. **Verify Current Connection**: You should still be connected to the orchestrator schema from Task 2.

![Continue in CloudShell](./images/lab3-task3-step1.png "Continue using current SQL session with connect commands")


### Step 2: Register CloudBank Participant in Orchestrator Schema

Since the orchestrator schema already hosts the coordinator, we'll register the "CloudBank" participant in the same orchestrator schema. This participant represents the central bank in our money transfer scenario.

> **Note**: The orchestrator schema serves dual roles - it hosts both the saga coordinator AND the CloudBank participant. This co-location is **required** in Oracle Database 23ai's saga implementation, as the coordinator and initiator must reside in the same schema and PDB. This architectural constraint ensures optimal performance but means the orchestrating service must also function as a saga participant.

<div class="input-section" style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<strong>CloudBank Participant Name (case-sensitive):</strong> 
<input type="text" id="cloudbank-participant-name" value="CloudBank" class="input-field-fixed" readonly style="background-color: #f5f5f5; cursor: not-allowed;" oninput="updateCloudbankCommand(); saveCloudbankParticipantName()">
</div>

<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>The CloudBank demo application uses this specific participant name ("CloudBank"), so we will be using this fixed name for consistency.</em>
</div>

<div id="cloudbank-save-status" style="display:none;" class="save-status">
<span id="cloudbank-save-message"></span>
</div>

**Generated CloudBank ADD_PARTICIPANT Command:**
<pre id="cloudbank-command-container" class="interactive-command">
<span id="cloudbank-command" class="command-text">-- Configure participant name above to generate command</span>
<button class="copy-btn" onclick="copyToClipboard('cloudbank-command', 'cloudbank-command-container')">Copy</button>
</pre>

Execute the generated command in the orchestrator schema (which already hosts the coordinator).

![CloudBank Participant Registration](./images/lab3-task3-cloudbank.png "CloudBank participant registration in orchestrator schema")

### Step 3: Connect to BankA Schema

Connect to the BankA participant schema:

<div class="input-section">
<strong>BankA Username:</strong> 
<input type="text" id="banka-user" placeholder="<BANKA_SCHEMA>" class="input-field" oninput="updateBankaConnectionCommand()"><br/>
<strong>BankA Password:</strong> 
<input type="text" id="banka-password" placeholder="<BANKA_SCHEMA_PASSWORD>" class="input-field" oninput="updateBankaConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="banka-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateBankaConnectionCommand()"><br/>
</div>

**Generated BankA Connection Command:**
<pre id="banka-connection-command-container" class="interactive-command">
<span id="banka-connection-command" class="command-text">connect &lt;BANKA_USERNAME&gt;/&lt;BANKA_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('banka-connection-command', 'banka-connection-command-container')">Copy</button>
</pre>

### Step 4: Verify SAGA Roles for BankA

Verify that the BankA user has the necessary SAGA roles:

```
<copy>
SELECT role FROM USER_ROLE_PRIVS WHERE role LIKE '%SAGA%';
</copy>
```

### Step 5: Configure BankA Participant Name

Configure the participant name (case-sensitive) for BankA:

<div class="input-section" style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<strong>BankA Participant Name (case-sensitive):</strong> 
<input type="text" id="banka-participant-name" value="BankA" class="input-field-fixed" readonly style="background-color: #f5f5f5; cursor: not-allowed;" oninput="updateBankaCommand(); saveBankaParticipantName()">
</div>

<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>The CloudBank demo application uses this specific participant name ("BankA"), so we will be using this fixed name for consistency.</em>
</div>

<div id="banka-save-status" style="display:none;" class="save-status">
<span id="banka-save-message"></span>
</div>

### Step 6: Generate BankA ADD_PARTICIPANT Command

**Generated BankA ADD_PARTICIPANT Command:**
<pre id="banka-command-container" class="interactive-command">
<span id="banka-command" class="command-text">-- Configure participant name above to generate command</span>
<button class="copy-btn" onclick="copyToClipboard('banka-command', 'banka-command-container')">Copy</button>
</pre>

### Step 11: Execute BankA Registration

Execute the generated command in the BankA schema.

![BankA Registration](./images/lab3-task3-banka.png "BankA participant registration")

### Step 7: Connect to BankB Schema

Connect to the BankB participant schema:

<div class="input-section">
<strong>BankB Username:</strong> 
<input type="text" id="bankb-user" placeholder="<BANKB_SCHEMA>" class="input-field" oninput="updateBankbConnectionCommand()"><br/>
<strong>BankB Password:</strong> 
<input type="text" id="bankb-password" placeholder="<BANKB_SCHEMA_PASSWORD>" class="input-field" oninput="updateBankbConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="bankb-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateBankbConnectionCommand()"><br/>
</div>

**Generated BankB Connection Command:**
<pre id="bankb-connection-command-container" class="interactive-command">
<span id="bankb-connection-command" class="command-text">connect &lt;BANKB_USERNAME&gt;/&lt;BANKB_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('bankb-connection-command', 'bankb-connection-command-container')">Copy</button>
</pre>

### Step 8: Verify SAGA Roles for BankB

Verify that the BankB user has the necessary SAGA roles:

```
<copy>
SELECT role FROM USER_ROLE_PRIVS WHERE role LIKE '%SAGA%';
</copy>
```

### Step 9: Configure BankB Participant Name

Configure the participant name (case-sensitive) for BankB:

<div class="input-section" style="display: contents; align-items: center; gap: 10px; margin: 5px 0;">
<strong>BankB Participant Name (case-sensitive):</strong> 
<input type="text" id="bankb-participant-name" placeholder="<BANKB_PARTICIPANT_NAME>" class="input-field" oninput="updateBankbCommand(); saveBankbParticipantName()">

<button onclick="saveBankbConfig()" class="save-btn-small">Save</button>
<button onclick="deleteBankbConfig()" class="delete-btn-small">Delete</button>
<button onclick="clearBankbConfig()" class="clear-btn-small">Clear</button>
</div>

<div style="font-size: 0.9em; color: #666; margin-top: 5px;">
💡 <em>Unlike CloudBank and BankA, this participant name is configurable since BankB is manipulated using PL/SQL functions in Lab 3 and doesn't require a fixed name for the demo application.</em>
</div>

<div id="bankb-save-status" style="display:none;" class="save-status">
<span id="bankb-save-message"></span>
</div>

### Step 10: Generate BankB ADD_PARTICIPANT Command

**Generated BankB ADD_PARTICIPANT Command:**
<pre id="bankb-command-container" class="interactive-command">
<span id="bankb-command" class="command-text">-- Configure participant name above to generate command</span>
<button class="copy-btn" onclick="copyToClipboard('bankb-command', 'bankb-command-container')">Copy</button>
</pre>

### Step 11: Execute BankB Registration

Execute the generated command in the BankB schema.

![BankB Registration](./images/lab3-task3-bankb.png "BankB participant registration")

### Step 17: Connect to Admin Session

Connect using the admin credentials saved from Lab 2 to verify all participant registrations:

<div class="input-section">
<strong>Admin Username:</strong> 
<input type="text" id="admin-user" placeholder="<ADMIN_SCHEMA>" value="ADMIN" class="input-field" oninput="updateAdminConnectionCommand()"><br/>
<strong>Admin Password:</strong> 
<input type="text" id="admin-password" placeholder="<DATABASE_ADMIN_PASSWORD>" class="input-field" oninput="updateAdminConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="admin-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateAdminConnectionCommand()"><br/>
</div>

**Generated Admin Connection Command:**
<pre id="admin-connection-command-container" class="interactive-command">
<span id="admin-connection-command" class="command-text">connect &lt;ADMIN_USERNAME&gt;/&lt;ADMIN_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('admin-connection-command', 'admin-connection-command-container')">Copy</button>
</pre>

### Step 12: Verify All Participants Registration

Run the verification commands from the admin session to see all participants:

<pre id="verify-all-participants-command-container" class="interactive-command">
<span id="verify-all-participants-command" class="command-text">-- Will auto-update based on your coordinator configuration</span>
<button class="copy-btn" onclick="copyToClipboard('verify-all-participants-command', 'verify-all-participants-command-container')">Copy</button>
</pre>

**Expected Output:**
```text
PARTICIPANT_NAME  PARTICIPANT_SCHEMA COORDINATOR_NAME      BROKER_NAME MAILBOX_SCHEMA
----------------- ------------------ -------------------- ----------- --------------
CLOUDBANK         ORCHESTRATOR1     CLOUDBANKCOORDINATOR  TEST        BROKER1
BANKA             BANKA             CLOUDBANKCOORDINATOR  TEST        BROKER1  
BANKB             BANKB             CLOUDBANKCOORDINATOR  TEST        BROKER1

3 rows selected.

QUEUE_NAME           QUEUE_TABLE          OWNER
-------------------- -------------------- ----------
CLOUDBANK_IN_Q       CLOUDBANK_IN_QT      ORCHESTRATOR1
BANKA_IN_Q           BANKA_IN_QT          BANKA
BANKB_IN_Q           BANKB_IN_QT          BANKB

3 rows selected.
```

![Verify All Participants](./images/lab3-task3-verify-all.png "Successful registration of all three participants")

> **Note**: All participants are registered for Java client implementation, so `CALLBACK_SCHEMA` and `CALLBACK_PACKAGE` columns will show NULL values.

## Task 4: Roles & Permissions

---

Oracle Database 23ai implements a comprehensive Role-Based Access Control (RBAC) security model for the Saga framework, ensuring that only authorized users can perform specific operations within the distributed transaction ecosystem. The Saga framework utilizes three distinct roles to provide granular control over administrative, participant, and connectivity functions. **`SAGA_ADM_ROLE`** grants full administrative privileges, enabling users to invoke the complete `DBMS_SAGA_ADM` package for initial setup, broker configuration, coordinator management, and participant registration. This role is essential for database administrators who need to establish and maintain the Saga infrastructure across multiple database schemas.

**`SAGA_PARTICIPANT_ROLE`** is designed specifically for application schemas that will act as participants in Saga transactions, providing the necessary privileges to invoke Saga primitives and interact with the coordination framework during transaction execution. This role ensures that participant services can communicate with coordinators while maintaining security boundaries between different application domains. **`SAGA_CONNECT_ROLE`** facilitates secure remote connectivity for distributed Saga participants, particularly when using database links to connect remote participants to the central Saga infrastructure. This three-tier security model ensures that each component of the Saga ecosystem operates with the minimum required privileges.

### Step 1: View Schema Roles from Lab 2

Enter the schema details from Lab 2 to examine the SAGA roles assigned to each schema type. These schemas were configured for Java client implementation in the previous lab:

<div class="input-section">
<strong>Broker Schema:</strong> 
<input type="text" id="task4-broker-schema" placeholder="<BROKER_SCHEMA>" class="input-field" oninput="updateTask4SchemaQuery()"><br/>
<strong>Orchestrator Schema:</strong> 
<input type="text" id="task4-orchestrator-schema" placeholder="<ORCHESTRATOR_SCHEMA>" class="input-field" oninput="updateTask4SchemaQuery()"><br/>
<strong>BankA Schema:</strong> 
<input type="text" id="task4-banka-schema" placeholder="<BANKA_SCHEMA>" class="input-field" oninput="updateTask4SchemaQuery()"><br/>
<strong>BankB Schema:</strong> 
<input type="text" id="task4-bankb-schema" placeholder="<BANKB_SCHEMA>" class="input-field" oninput="updateTask4SchemaQuery()"><br/>
</div>

**Generated Schema Role Query:**
<pre id="task4-schema-query-container" class="interactive-command">
<span id="task4-schema-query" class="command-text">-- Query will auto-update based on schema names above</span>
<button class="copy-btn" onclick="copyToClipboard('task4-schema-query', 'task4-schema-query-container')">Copy</button>
</pre>

**Expected Output:**
```text
GRANTEE       GRANTED_ROLE          ROLE_DESCRIPTION                              ADMIN_OPTION DEFAULT_ROLE
------------- --------------------- --------------------------------------------- ------------ ------------
BROKER1       SAGA_ADM_ROLE         Saga Administrator - Broker/Coordinator Management    NO           YES
BROKER1       SAGA_PARTICIPANT_ROLE Saga Participant - Java Client Implementation         NO           YES
ORCHESTRATOR1 SAGA_ADM_ROLE         Saga Administrator - Broker/Coordinator Management    NO           YES  
ORCHESTRATOR1 SAGA_PARTICIPANT_ROLE Saga Participant - Java Client Implementation         NO           YES
BANKA         SAGA_PARTICIPANT_ROLE Saga Participant - Java Client Implementation         NO           YES
BANKB         SAGA_PARTICIPANT_ROLE Saga Participant - Java Client Implementation         NO           YES
```

**Role Assignments Explained:**
- **Admin Schema**: Full database privileges (not shown in SAGA-specific query)
- **Broker1**: `SAGA_ADM_ROLE` + `SAGA_PARTICIPANT_ROLE` (broker creation and participation)
- **Orchestrator1**: `SAGA_ADM_ROLE` + `SAGA_PARTICIPANT_ROLE` (coordinator management, saga initiation, AND CloudBank participant)
- **BankA/BankB**: `SAGA_PARTICIPANT_ROLE` only (Java client participants)

![Schema Role Assignments](./images/lab3-task4-1.png "SAGA role assignments across Lab 2 schemas")

---

## Task 5: Dictionary Views & Monitoring

---

Oracle Database 23ai provides comprehensive dictionary views for monitoring and managing Saga transactions. These views are organized into different categories for configuration monitoring, runtime tracking, and historical analysis. The Saga framework integrates with Oracle's Advanced Queuing infrastructure, allowing DBAs to monitor message flow, participant status, and transaction lifecycle through standard Oracle dictionary views. **`DBA_SAGA_PARTICIPANTS`** provide administrative visibility into the configured Saga infrastructure, while runtime views like **`DBA_SAGAS`** and **`DBA_SAGA_DETAILS`** track active transaction execution.

### Step 2: Explore Dictionary Views from Admin Schema

Connect to your admin schema and explore the DBA_ dictionary views that provide comprehensive visibility into Saga operations:

<div class="input-section">
<strong>Admin Username:</strong> 
<input type="text" id="task5-admin-user" placeholder="<ADMIN_SCHEMA>" value="ADMIN" class="input-field" oninput="updateTask5AdminConnectionCommand()"><br/>
<strong>Admin Password:</strong> 
<input type="text" id="task5-admin-password" placeholder="<DATABASE_ADMIN_PASSWORD>" class="input-field" oninput="updateTask5AdminConnectionCommand()"><br/>
<strong>Connection String:</strong> 
<input type="text" id="task5-admin-connection-string" placeholder="<DATABASE_CONNECTION_TNS_NAME>" class="input-field" oninput="updateTask5AdminConnectionCommand()"><br/>
</div>

**Generated Connection Command:**
<pre id="task5-admin-connection-command-container" class="interactive-command">
<span id="task5-admin-connection-command" class="command-text">connect &lt;ADMIN_USERNAME&gt;/&lt;ADMIN_PASSWORD&gt;@'&lt;CONNECTION_STRING&gt;'</span>
<button class="copy-btn" onclick="copyToClipboard('task5-admin-connection-command', 'task5-admin-connection-command-container')">Copy</button>
</pre>

```sql
<copy>
-- View all Saga participants
SELECT * FROM DBA_SAGA_PARTICIPANTS;
</copy>
```

**Expected Output:**
```text
PARTICIPANT_NAME PARTICIPANT_SCHEMA COORDINATOR_NAME BROKER_NAME MAILBOX_SCHEMA CALLBACK_SCHEMA CALLBACK_PACKAGE
---------------- ------------------ ---------------- ----------- -------------- --------------- ----------------
BANKA            BANKA              CLOUDBANKCOORDINATOR BROKER1   BANKA          BANKA           BANKA_SAGA
BANKB            BANKB              CLOUDBANKCOORDINATOR BROKER1   BANKB          BANKB           BANKB_SAGA
CLOUDBANK        ORCHESTRATOR1      CLOUDBANKCOORDINATOR BROKER1   ORCHESTRATOR1  ORCHESTRATOR1   CLOUDBANK_SAGA
```

> **Important Notes about Oracle Saga Dictionary Views:**
>
> 1. **View Hierarchy Levels**: Oracle provides Saga dictionary views at different privilege levels:
>    - **`DBA_SAGA_`*** views: Database administrator level - full visibility across all schemas
>    - **`USER_SAGA_`*** views: Schema user level - limited to current user's schema objects
>    - **`CDB_SAGA_`*** views: Container database level - for multitenant environments
>
> 2. **Complete View Reference**: For comprehensive documentation of all available Saga views and their detailed descriptions, refer to the official Oracle documentation:  
>    [Oracle Database 23ai - Developing Applications with Oracle Database Saga](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html#GUID-3CA20647-5C14-4EDB-9A62-DE0894FA0338)
>
> 3. **Runtime Views**: Additional views like `DBA_SAGAS` and `DBA_SAGA_DETAILS` provide runtime monitoring capabilities. We will explore these views with actual data once we execute a complete Saga transaction flow in **Lab 5**.

![Dictionary Views Overview](./images/lab3-task5-1.png "Saga dictionary views and monitoring capabilities")

---

## Task 6: Optional Information (Not Mandatory)

---

> **Note**: This task is **optional** and not required for completing the lab successfully. It provides advanced configuration options for production deployments and distributed Saga architectures.

This section covers advanced configuration parameters and database link options for production and distributed Saga deployments.

<details>
<summary><strong>📋 i. Saga Configuration Parameters</strong></summary>

Oracle Database 23ai provides comprehensive configuration parameters for fine-tuning Saga behavior, performance, and monitoring. These parameters control timeout settings, queue management, retry logic, and historical data retention.

#### Complete Parameter Reference:

| Parameter | Default | ADB-S Modifiable | Local DB Modifiable | Description |
|-----------|---------|------------------|---------------------|-------------|
| `max_saga_duration` | 86400 seconds | NO | YES | Maximum time a saga can remain active before automatic timeout |
| `_use_saga_qtyp` | 0 (Classic AQ) | NO | YES | Queue type: 0=Classic AQ queues, 1=Transactional Event Queues (TEQ) |
| `saga_hist_retention` | 30 days | NO | YES | Retention period for completed saga transaction history |
| `_saga_afterlra_interval` | 300 seconds | NO | YES | Interval for AfterLRA method invocation after saga completion |

#### How to Check Current Values:

**For ADB-S (Autonomous Database Serverless):**
```sql
-- Template command (no copy/execute needed)
SELECT name, value, description FROM V$PARAMETER 
WHERE name IN ('max_saga_duration', '_use_saga_qtyp', 'saga_hist_retention', '_saga_afterlra_interval')
ORDER BY name;
```

**For Local Oracle Database:**
```sql  
-- Template command (no copy/execute needed)
ALTER SYSTEM SET max_saga_duration = 172800;  -- 48 hours
ALTER SYSTEM SET saga_hist_retention = 60;    -- 60 days
ALTER SYSTEM SET _use_saga_qtyp = 1;          -- Enable TEQ
ALTER SYSTEM SET _saga_afterlra_interval = 600; -- 10 minutes
```

**Configuration Impact:**
- **ADB-S Environment**: All parameters are managed by Oracle Cloud
- **Local DB Deployment**: Full administrative control over all saga parameters for production optimization
- **Performance Tuning**: Higher queue partitions and TEQ enable better concurrency but require careful coordination
</details>

<details>
<summary><strong>📋 ii. Database Links for Distributed Sagas</strong></summary>

Oracle Database 23ai Saga framework supports distributed saga transactions across multiple databases through database links. This enables saga participants to reside in different Oracle databases while maintaining ACID properties and saga transaction integrity. The `SAGA_CONNECT_ROLE` provides the necessary privileges for cross-database saga operations.

#### `SAGA_CONNECT_ROLE` Privileges and Purpose:

The `SAGA_CONNECT_ROLE` is a predefined database role that grants essential privileges for distributed saga operations:

- **Remote Table Access**: `SELECT`, `INSERT`, `UPDATE`, `DELETE` privileges on remote saga tables
- **Queue Operations**: Access to Advanced Queuing infrastructure across database boundaries  
- **Saga Metadata**: Read/write access to distributed saga coordination metadata
- **Network Connectivity**: `CONNECT` and `RESOURCE` roles for remote database authentication

#### Role Assignment for Distributed Scenarios:
```sql
-- Template commands (no copy/execute needed)
-- Grant SAGA_CONNECT_ROLE to schemas that need cross-database access
GRANT SAGA_CONNECT_ROLE TO participant_schema;
GRANT SAGA_CONNECT_ROLE TO coordinator_schema;

-- Verify role assignments
SELECT grantee, granted_role FROM DBA_ROLE_PRIVS 
WHERE granted_role = 'SAGA_CONNECT_ROLE';
```

#### Why Database Links are Required - Message Propagation:

Database links are essential for **message propagation** in distributed saga topologies. Message propagation between different pluggable databases (PDBs) requires specific database links to transfer saga messages:

- **Participant to Broker Propagation**: To propagate a participant's outbound topic to a broker's INOUT topic, the message propagation job uses the database link from the `dblink_to_broker` parameter
- **Broker to Participant Propagation**: To propagate a broker's INOUT topic to a participant's inbound topic, the message propagation job uses the database link from the `dblink_to_participant` parameter

Without these database links, participants in different database instances cannot exchange the messages required for coordinating saga transactions. The links create the communication channels that enable distributed saga coordination across multiple Oracle database instances.

#### Database Link Implementation Approaches:

For multi-database Saga deployments, two approaches are available depending on your environment:

<details close>
<summary><strong>📋 Traditional Database Links (Local/On-Premises)</strong></summary>

**For Local Oracle Database deployments:**

```sql
<copy>
-- Create traditional database link
CREATE DATABASE LINK remote_saga_db
CONNECT TO remote_user IDENTIFIED BY remote_password
USING '(DESCRIPTION=
  (ADDRESS=(PROTOCOL=TCP)(HOST=remote_host)(PORT=1521))
  (CONNECT_DATA=(SERVICE_NAME=remote_service))
)';

-- Test the database link
SELECT * FROM dual@remote_saga_db;
</copy>
```

**Parameters:**
- **`CONNECT TO`**: Remote database username
- **`IDENTIFIED BY`**: Remote database password  
- **`USING`**: TNS connection descriptor
- **`HOST`**: Remote database hostname/IP
- **`PORT`**: Database listener port (typically 1521)
- **`SERVICE_NAME`**: Target database service name
</details>

<details close>
<summary><strong>📋 DBMS_CLOUD Database Links (ADB-S Environment)</strong></summary>

**For Autonomous Database Serverless deployments:**

```sql
<copy>
-- Step 1: Create credentials for remote ADB
BEGIN
  DBMS_CLOUD.CREATE_CREDENTIAL(
    credential_name => 'REMOTE_ADB_CRED',
    username => 'REMOTE_USER',
    password => 'remote_password'
  );
END;
/

-- Step 2: Create database link using DBMS_CLOUD
BEGIN
  DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(
    db_link_name => 'REMOTE_SAGA_LINK',
    hostname => 'adb.region.oraclecloud.com',
    port => '1521',
    service_name => 'remote_adb_service.adb.region.oraclecloud.com',
    credential_name => 'REMOTE_ADB_CRED',
    directory_name => NULL  -- For TLS without wallet
  );
END;
/

-- Test the DBMS_CLOUD database link
SELECT * FROM dual@REMOTE_SAGA_LINK;
</copy>
```

**Parameters:**
- **`credential_name`**: Name for stored remote credentials
- **`hostname`**: Remote ADB hostname from connection string
- **`service_name`**: Complete service name including domain
- **`directory_name`**: NULL for TLS, directory name for mTLS with wallet
</details>
<br/>

#### Usage Guidelines and Best Practices:

- **Traditional DB Links**: Use for local Oracle Database and on-premises deployments
- **DBMS_CLOUD Links**: Required for Autonomous Database Serverless connections  
- **Cross-PDB Sagas**: Enable participants across different databases while maintaining ACID properties
- **Security**: Database links inherit the connecting user's privileges; use dedicated saga service accounts
- **Performance**: Network latency affects distributed saga performance; consider participant placement strategies
- **Failover**: Configure connection pooling and retry logic for distributed saga resilience
</details>

<details>
<summary><strong>📋 iii. Other `DBMS_SAGA_ADM` Management Commands</strong></summary>

The `DBMS_SAGA_ADM` package provides additional administrative procedures for managing saga infrastructure beyond the creation commands covered in this lab. These commands are essential for production environments where saga components need to be modified, removed, or reconfigured.

#### Removal and Cleanup Commands:

**DROP_BROKER Procedure:**
- **Purpose**: Drops a broker and the associated JMS topic from the saga framework
- **Usage**: Use when decommissioning a broker or during environment cleanup

```sql
-- Template command (no copy/execute needed)
EXEC DBMS_SAGA_ADM.DROP_BROKER(
  broker_name => '&lt;BROKER_NAME&gt;'
);
```

**DROP_COORDINATOR Procedure:**
- **Purpose**: Drops the given coordinator and disables message propagation
- **Usage**: Use when removing a coordinator configuration or during topology changes

```sql
-- Template command (no copy/execute needed)
EXEC DBMS_SAGA_ADM.DROP_COORDINATOR(
  coordinator_name => '&lt;COORDINATOR_NAME&gt;'
);
```

**DROP_PARTICIPANT Procedure:**
- **Purpose**: Drops the given participant on the local PDB and the broker
- **Usage**: Use when removing a participant from the saga topology

```sql
-- Template command (no copy/execute needed)
EXEC DBMS_SAGA_ADM.DROP_PARTICIPANT(
  participant_name => '&lt;PARTICIPANT_NAME&gt;',
  broker_name => '&lt;BROKER_NAME&gt;'
);
```

#### Callback Management Command:

**REGISTER_SAGA_CALLBACK Procedure:**
- **Purpose**: Enables users to create or modify a callback package for saga participants
- **Usage**: Use to register or update PL/SQL callback packages for database-native saga participants

```sql
-- Template command (no copy/execute needed)
EXEC DBMS_SAGA_ADM.REGISTER_SAGA_CALLBACK(
  participant_name => '&lt;PARTICIPANT_NAME&gt;',
  callback_schema => '&lt;CALLBACK_SCHEMA&gt;',
  callback_package => '&lt;CALLBACK_PACKAGE&gt;'
);
```

#### Administrative Notes:

- **Dependency Order**: Always drop participants before dropping coordinators, and drop coordinators before dropping brokers
- **Data Preservation**: Dropping saga components does not automatically clean up historical saga transaction data
- **Distributed Cleanup**: In distributed topologies, cleanup commands must be executed on each relevant database instance
- **Production Safety**: Use these commands with caution in production environments as they permanently remove saga infrastructure

</details>

<details>
<summary><strong>📋 iv. PL/SQL Callbacks for Database-Native Saga Participants</strong></summary>

Oracle Database 23ai enables saga participants to be implemented as database-native PL/SQL packages through specialized callback packages. These callback packages provide a structured way to define custom business logic for different stages of saga transaction processing while abstracting away the underlying saga infrastructure complexity.

#### Callback Package Architecture:

PL/SQL callback packages serve as the integration layer between the saga framework and your business logic, allowing developers to focus on implementing specific business operations rather than managing saga coordination details. The callback package receives saga events and executes corresponding business logic while the framework handles message routing, transaction coordination, and error management.

#### Required Callback Procedures:

Each callback package can implement several optional procedures to handle different saga transaction stages:

**Request Handler:**
```sql
-- Template procedure (no copy/execute needed)
FUNCTION request(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
) RETURN JSON;
```
- **Purpose**: Processes incoming saga messages with `REQUEST` opcode
- **Returns**: JSON payload response to the saga coordinator
- **Usage**: Implement primary business logic for the participant's operation

**Response Handler:**
```sql
-- Template procedure (no copy/execute needed)
PROCEDURE response(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
);
```
- **Purpose**: Processes saga messages with `RESPONSE` opcode
- **Usage**: Handle responses from other saga participants

**Commit Lifecycle Handlers:**
```sql
-- Template procedures (no copy/execute needed)
PROCEDURE before_commit(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
);

PROCEDURE after_commit(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
);
```
- **Purpose**: Execute logic before and after saga commit operations
- **Usage**: Implement finalization or cleanup logic for successful transactions

**Rollback Lifecycle Handlers:**
```sql
-- Template procedures (no copy/execute needed)
PROCEDURE before_rollback(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
);

PROCEDURE after_rollback(
  saga_id     IN RAW,
  saga_sender IN VARCHAR2,
  payload     IN JSON DEFAULT NULL
);
```
- **Purpose**: Execute compensating actions during saga rollback
- **Usage**: Implement compensation logic to undo participant-specific operations

#### Implementation Guidelines:

- **Transaction Control**: Callback procedures cannot invoke explicit `COMMIT` or `ROLLBACK` operations
- **Error Handling**: Use proper exception handling to ensure saga framework receives appropriate error signals
- **JSON Payloads**: Leverage JSON for structured data exchange between saga participants
- **Stateless Design**: Design callback procedures to be stateless and idempotent where possible
- **Business Logic Focus**: Keep callback procedures focused on business logic rather than saga infrastructure concerns

#### Integration with `REGISTER_SAGA_CALLBACK`:

After creating your callback package, register it with the saga participant using the `REGISTER_SAGA_CALLBACK` procedure shown in the previous section. This links your business logic implementation to the saga framework's message processing pipeline.

PL/SQL callbacks provide a powerful way to implement database-native saga participants while maintaining clean separation between business logic and saga coordination infrastructure.
</details>

---

## Summary

✅ **Congratulations!** You have successfully completed Lab 3: Core Setup. You have:

- ✅ **Created a Saga Broker** (`broker1`) for message coordination
- ✅ **Configured a Saga Coordinator** (`orchestrator1`) for saga orchestration  
- ✅ **Registered Saga Participants** (`CloudBank`, `BankA`, and `BankB`) for Java client implementation
- ✅ **Verified roles and permissions** for saga framework access
- ✅ **Explored monitoring views** and infrastructure components
- ✅ **Learned advanced configuration options** for production deployments

Your Oracle Sagas foundation is now ready for implementing actual business logic!

**Next Steps:** In **Lab 4: Developing with PL/SQL**, you'll implement the money transfer saga logic in your callback packages and execute your first saga transactions.

---

## Learn More

- [Oracle Database 23ai: Developing Applications with Saga](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)  
- [DBMS_SAGA_ADM Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga_adm.html)  

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney  
* **Created By/Date** — Vinay Pandhariwal, August 2025  
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025

<script>
function initializeCoreSetup() {
    try {
        const savedConnectionString = sessionStorage.getItem('adbConnectionString');
        if (savedConnectionString) {
            const brokerConnField = document.getElementById('broker-connection-string');
            const coordConnField = document.getElementById('coordinator-connection-string');
            const partConnField = document.getElementById('participant-connection-string');
            
            if (brokerConnField) brokerConnField.value = savedConnectionString;
            if (coordConnField) coordConnField.value = savedConnectionString;
            if (partConnField) partConnField.value = savedConnectionString;
        }
        
        const savedBroker1User = sessionStorage.getItem('cloudbank_USER_BROKER1');
        const savedBroker1Pass = sessionStorage.getItem('cloudbank_PASSWORD_BROKER1');
        const broker1UserField = document.getElementById('broker1-user');
        const broker1PassField = document.getElementById('broker1-password');
        
        if (savedBroker1User && broker1UserField) broker1UserField.value = savedBroker1User;
        if (savedBroker1Pass && broker1PassField) broker1PassField.value = savedBroker1Pass;
        
        const savedBrokerDbName = sessionStorage.getItem('saga_broker_db_name');
        const brokerDbNameField = document.getElementById('broker-db-name');
        if (savedBrokerDbName && brokerDbNameField) brokerDbNameField.value = savedBrokerDbName;
        
        const savedCoordinatorName = sessionStorage.getItem('saga_coordinator_name');
        const coordinatorNameField = document.getElementById('coordinator-name');
        if (savedCoordinatorName && coordinatorNameField) coordinatorNameField.value = savedCoordinatorName;
        
        const savedCloudbankName = sessionStorage.getItem('saga_cloudbank_participant_name');
        const savedBankaName = sessionStorage.getItem('saga_banka_participant_name');
        const savedBankbName = sessionStorage.getItem('saga_bankb_participant_name');
        
        const cloudbankNameField = document.getElementById('cloudbank-participant-name');
        const bankaNameField = document.getElementById('banka-participant-name');
        const bankbNameField = document.getElementById('bankb-participant-name');
        
        if (savedCloudbankName && cloudbankNameField) {
            cloudbankNameField.value = savedCloudbankName;
        }
        if (savedBankaName && bankaNameField) bankaNameField.value = savedBankaName;
        if (savedBankbName && bankbNameField) bankbNameField.value = savedBankbName;
        
        const brokerSaveStatusEl = document.getElementById('broker-save-status');
        const brokerSaveMessageEl = document.getElementById('broker-save-message');
        const isBrokerVisible = brokerDbNameField && brokerDbNameField.offsetParent !== null;
        const hasBrokerData = savedBrokerDbName;
        
        if (brokerSaveStatusEl) {
            if (hasBrokerData && brokerSaveMessageEl && isBrokerVisible) {
                let message = '📋 <strong>Previously Saved Broker Configuration Found:</strong><br/>';
                message += `Broker Name: ${savedBrokerDbName}<br/>`;
                message += '<small>Broker name has been pre-populated. You can modify and save again if needed.</small>';
                
                brokerSaveMessageEl.innerHTML = message;
                brokerSaveStatusEl.style.display = 'block';
                brokerSaveStatusEl.style.background = '#cce7ff';
                brokerSaveStatusEl.style.color = '#004085';
            } else {
                brokerSaveStatusEl.style.display = 'none';
            }
        }
        
        const coordinatorSaveStatusEl = document.getElementById('coordinator-save-status');
        const coordinatorSaveMessageEl = document.getElementById('coordinator-save-message');
        const isCoordinatorVisible = coordinatorNameField && coordinatorNameField.offsetParent !== null;
        const hasCoordinatorData = savedCoordinatorName;
        
        if (coordinatorSaveStatusEl) {
            if (hasCoordinatorData && coordinatorSaveMessageEl && isCoordinatorVisible) {
                let message = '📋 <strong>Previously Saved Coordinator Configuration Found:</strong><br/>';
                message += `Coordinator Name: ${savedCoordinatorName}<br/>`;
                message += '<small>Coordinator name has been pre-populated. You can modify and save again if needed.</small>';
                
                coordinatorSaveMessageEl.innerHTML = message;
                coordinatorSaveStatusEl.style.display = 'block';
                coordinatorSaveStatusEl.style.background = '#cce7ff';
                coordinatorSaveStatusEl.style.color = '#004085';
            } else {
                coordinatorSaveStatusEl.style.display = 'none';
            }
        }

        const cloudbankSaveStatusEl = document.getElementById('cloudbank-save-status');
        const cloudbankSaveMessageEl = document.getElementById('cloudbank-save-message');
        const isCloudbankVisible = cloudbankNameField && cloudbankNameField.offsetParent !== null;
        const hasCloudbankData = savedCloudbankName;
        
        if (cloudbankSaveStatusEl) {
            if (hasCloudbankData && cloudbankSaveMessageEl && isCloudbankVisible) {
                let message = '📋 <strong>Previously Saved CloudBank Configuration Found:</strong><br/>';
                message += `Participant Name: ${savedCloudbankName}<br/>`;
                message += '<small>Participant name has been pre-populated. You can modify and save again if needed.</small>';
                
                cloudbankSaveMessageEl.innerHTML = message;
                cloudbankSaveStatusEl.style.display = 'block';
                cloudbankSaveStatusEl.style.background = '#cce7ff';
                cloudbankSaveStatusEl.style.color = '#004085';
            } else {
                hideElement(cloudbankSaveStatusEl);
            }
        }
        const bankaSaveStatusEl = document.getElementById('banka-save-status');
        const bankaSaveMessageEl = document.getElementById('banka-save-message');
        const isBankaVisible = bankaNameField && bankaNameField.offsetParent !== null;
        const hasBankaData = savedBankaName;
        
        if (bankaSaveStatusEl) {
            if (hasBankaData && bankaSaveMessageEl && isBankaVisible) {
                let message = '📋 <strong>Previously Saved BankA Configuration Found:</strong><br/>';
                message += `Participant Name: ${savedBankaName}<br/>`;
                message += '<small>Participant name has been pre-populated. You can modify and save again if needed.</small>';
                
                bankaSaveMessageEl.innerHTML = message;
                bankaSaveStatusEl.style.display = 'block';
                bankaSaveStatusEl.style.background = '#cce7ff';
                bankaSaveStatusEl.style.color = '#004085';
            } else {
                hideElement(bankaSaveStatusEl);
            }
        }
        const bankbSaveStatusEl = document.getElementById('bankb-save-status');
        const bankbSaveMessageEl = document.getElementById('bankb-save-message');
        const isBankbVisible = bankbNameField && bankbNameField.offsetParent !== null;
        const hasBankbData = savedBankbName;
        
        if (bankbSaveStatusEl) {
            if (hasBankbData && bankbSaveMessageEl && isBankbVisible) {
                let message = '📋 <strong>Previously Saved BankB Configuration Found:</strong><br/>';
                message += `Participant Name: ${savedBankbName}<br/>`;
                message += '<small>Participant name has been pre-populated. You can modify and save again if needed.</small>';
                
                bankbSaveMessageEl.innerHTML = message;
                bankbSaveStatusEl.style.display = 'block';
                bankbSaveStatusEl.style.background = '#cce7ff';
                bankbSaveStatusEl.style.color = '#004085';
            } else {
                hideElement(bankbSaveStatusEl);
            }
        }
        
        const savedOrchUser = sessionStorage.getItem('cloudbank_USER_ORCHESTRATOR1');
        const savedOrchPass = sessionStorage.getItem('cloudbank_PASSWORD_ORCHESTRATOR1');
        const orchUserField = document.getElementById('orchestrator1-user');
        const orchPassField = document.getElementById('orchestrator1-password');
        
        if (savedOrchUser && orchUserField) orchUserField.value = savedOrchUser;
        if (savedOrchPass && orchPassField) orchPassField.value = savedOrchPass;
        
        const savedBankaUser = sessionStorage.getItem('cloudbank_USER_BANKA');
        const savedBankaPass = sessionStorage.getItem('cloudbank_PASSWORD_BANKA');
        const savedBankbUser = sessionStorage.getItem('cloudbank_USER_BANKB');
        const savedBankbPass = sessionStorage.getItem('cloudbank_PASSWORD_BANKB');
        
        const cloudbankUserField = document.getElementById('cloudbank-user');
        const cloudbankPassField = document.getElementById('cloudbank-password');
        const bankaUserField = document.getElementById('banka-user');
        const bankaPassField = document.getElementById('banka-password');
        const bankbUserField = document.getElementById('bankb-user');
        const bankbPassField = document.getElementById('bankb-password');
        
        const cloudbankConnField = document.getElementById('cloudbank-connection-string');
        const bankaConnField = document.getElementById('banka-connection-string');
        const bankbConnField = document.getElementById('bankb-connection-string');
        const adminConnField = document.getElementById('admin-connection-string');
        
        if (savedConnectionString && cloudbankConnField) cloudbankConnField.value = savedConnectionString;
        if (savedConnectionString && bankaConnField) bankaConnField.value = savedConnectionString;
        if (savedConnectionString && bankbConnField) bankbConnField.value = savedConnectionString;
        if (savedConnectionString && adminConnField) adminConnField.value = savedConnectionString;
        
        if (savedBankaUser && cloudbankUserField) cloudbankUserField.value = savedBankaUser;
        if (savedBankaPass && cloudbankPassField) cloudbankPassField.value = savedBankaPass;
        if (savedBankaUser && bankaUserField) bankaUserField.value = savedBankaUser;
        if (savedBankaPass && bankaPassField) bankaPassField.value = savedBankaPass;
        if (savedBankbUser && bankbUserField) bankbUserField.value = savedBankbUser;
        if (savedBankbPass && bankbPassField) bankbPassField.value = savedBankbPass;
        
        const savedAdminUser = sessionStorage.getItem('cloudbank_USER_ADMIN') || sessionStorage.getItem('admin');
        const savedAdminPass = sessionStorage.getItem('cloudbank_ADB_DATABASE_PASSWORD') || sessionStorage.getItem('adbPassword') || sessionStorage.getItem('admin_password');
        const adminUserField = document.getElementById('admin-user');
        const adminPassField = document.getElementById('admin-password');
        if (savedAdminUser && adminUserField) adminUserField.value = savedAdminUser;
        if (savedAdminPass && adminPassField) adminPassField.value = savedAdminPass;
        
        const task4AdminField = document.getElementById('task4-admin-schema');
        const task4BrokerField = document.getElementById('task4-broker-schema');
        const task4OrchestratorField = document.getElementById('task4-orchestrator-schema');
        const task4BankaField = document.getElementById('task4-banka-schema');
        const task4BankbField = document.getElementById('task4-bankb-schema');
        const task4ConnectionField = document.getElementById('task4-connection-string');
        
        if (savedAdminUser && task4AdminField) task4AdminField.value = savedAdminUser;
        if (savedBroker1User && task4BrokerField) task4BrokerField.value = savedBroker1User;
        if (savedOrchUser && task4OrchestratorField) task4OrchestratorField.value = savedOrchUser;
        if (savedBankaUser && task4BankaField) task4BankaField.value = savedBankaUser;
        if (savedBankbUser && task4BankbField) task4BankbField.value = savedBankbUser;
        if (savedConnectionString && task4ConnectionField) task4ConnectionField.value = savedConnectionString;
        
        const task5AdminUserField = document.getElementById('task5-admin-user');
        const task5AdminPassField = document.getElementById('task5-admin-password');
        const task5AdminConnField = document.getElementById('task5-admin-connection-string');
        
        if (savedAdminUser && task5AdminUserField) task5AdminUserField.value = savedAdminUser;
        if (savedAdminPass && task5AdminPassField) task5AdminPassField.value = savedAdminPass;
        if (savedConnectionString && task5AdminConnField) task5AdminConnField.value = savedConnectionString;
        
        updateBrokerConnectionCommand();
        updateBrokerCommand();
        updateVerificationCommands();
        updateCoordinatorConnectionCommand();
        updateCoordinatorCommand();
        updateCoordinatorVerificationCommands();
        updateCloudbankCommand();
        updateBankaConnectionCommand();
        updateBankaCommand();
        updateBankbConnectionCommand();
        updateBankbCommand();
        updateAdminConnectionCommand();
        updateVerifyAllParticipantsCommand();
        
        updateTask4SchemaQuery();
        updateTask5AdminConnectionCommand();
        
        setTimeout(() => {
            updateCloudbankCommand();
        }, 100);
        
    } catch (error) {
        console.error('Error loading core setup configuration:', error);
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeCoreSetup);
} else {
    initializeCoreSetup();
}

window.addEventListener('load', initializeCoreSetup);
setTimeout(initializeCoreSetup, 500);  
setTimeout(initializeCoreSetup, 1500); 
setTimeout(initializeCoreSetup, 3000);

function updateBrokerConnectionCommand() {
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const brokerPass = document.getElementById('broker1-password').value || '<BROKER_SCHEMA_PASSWORD>';
    const connectionString = document.getElementById('broker-connection-string').value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `sql ${brokerUser}/${brokerPass}@'${connectionString}'`;
    
    document.getElementById('broker-connection-command').textContent = command;
    updateBrokerCommand();
    updateCoordinatorCommand();
    updateCloudbankCommand();
    updateBankaCommand();
    updateBankbCommand();
    updateAdminConnectionCommand();
}

function updateBrokerCommand() {
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    
    let quotedBrokerName;
    if (((brokerDbName.startsWith('"') && brokerDbName.endsWith('"')) || 
        (brokerDbName.startsWith("'") && brokerDbName.endsWith("'"))) && 
        brokerDbName.length > 2) {
        quotedBrokerName = brokerDbName;
    } else {
        quotedBrokerName = `'${brokerDbName}'`;
    }
    
    const command = `-- Execute from broker1 schema
EXEC DBMS_SAGA_ADM.ADD_BROKER(
  broker_name   => ${quotedBrokerName},
  broker_schema => '${brokerUser.toUpperCase()}'
);`;
    
    document.getElementById('broker-command').textContent = command;
    updateVerificationCommands();
}

function updateVerificationCommands() {
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    
    let quotedBrokerName;
    if (((brokerDbName.startsWith('"') && brokerDbName.endsWith('"')) || 
        (brokerDbName.startsWith("'") && brokerDbName.endsWith("'"))) && 
        brokerDbName.length > 2) {
        quotedBrokerName = brokerDbName;
    } else {
        quotedBrokerName = `'${brokerDbName}'`;
    }
    
    const verifyBrokerCommand = `-- Check the new broker
SELECT broker_name, owner, queue_partitions, version, created_date
FROM ALL_SAGA_BROKERS
WHERE broker_name = ${quotedBrokerName};`;
    
    const verifyBrokerElement = document.getElementById('verify-broker-command');
    if (verifyBrokerElement) {
        verifyBrokerElement.textContent = verifyBrokerCommand;
    }
    
    const verifyQueueCommand = `-- Examine the underlying topic infrastructure
SELECT queue_name, queue_table, owner 
FROM DBA_QUEUES 
WHERE owner = USER 
AND queue_name LIKE '%SAGA$_${brokerDbName.replace(/['"]/g, '')}_%';`;
    
    const verifyQueueElement = document.getElementById('verify-queue-command');
    if (verifyQueueElement) {
        verifyQueueElement.textContent = verifyQueueCommand;
    }
}

function saveBrokerDbName() {
    const brokerDbName = document.getElementById('broker-db-name').value;
    if (brokerDbName) {
        sessionStorage.setItem('saga_broker_db_name', brokerDbName);
    }
}

function saveBrokerConfig() {
    const brokerDbName = document.getElementById('broker-db-name').value;
    
    if (brokerDbName) {
        sessionStorage.setItem('saga_broker_db_name', brokerDbName);
        
        document.getElementById('broker-save-message').innerHTML = `
            ✅ <strong>Broker Configuration Saved Successfully!</strong><br/>
            Broker Name: ${brokerDbName}<br/>
            <small>Configuration will persist until you close the browser tab.</small>
        `;
        document.getElementById('broker-save-status').style.display = 'block';
        document.getElementById('broker-save-status').style.background = '#d4edda';
        document.getElementById('broker-save-status').style.color = '#155724';
        
        const button = document.querySelector('button[onclick="saveBrokerConfig()"]');
        const originalText = button.textContent;
        button.textContent = 'Saved!';
        button.style.background = '#28a745';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
        
    } else {
        document.getElementById('broker-save-message').innerHTML = `
            ⚠️ <strong>Please Enter Broker Name</strong><br/>
            Enter a valid broker name before saving.
        `;
        document.getElementById('broker-save-status').style.display = 'block';
        document.getElementById('broker-save-status').style.background = '#fff3cd';
        document.getElementById('broker-save-status').style.color = '#856404';
    }
}

function deleteBrokerConfig() {
    sessionStorage.removeItem('saga_broker_db_name');
    document.getElementById('broker-db-name').value = '';
    
    document.getElementById('broker-save-message').innerHTML = `
        🗑️ <strong>Broker Configuration Deleted</strong><br/>
        Broker configuration has been removed from browser session.
    `;
    document.getElementById('broker-save-status').style.display = 'block';
    document.getElementById('broker-save-status').style.background = '#f8d7da';
    document.getElementById('broker-save-status').style.color = '#721c24';
    
    updateBrokerCommand();
}

function clearBrokerConfig() {
    document.getElementById('broker-db-name').value = '';
    hideElement(document.getElementById('broker-save-status'));
    updateBrokerCommand();
}

function hideElement(element) {
    if (element) {
        element.style.setProperty('display', 'none', 'important');
    }
}

function updateCoordinatorConnectionCommand() {
    const orchUser = document.getElementById('orchestrator1-user').value || '<ORCHESTRATOR_SCHEMA>';
    const orchPass = document.getElementById('orchestrator1-password').value || '<ORCHESTRATOR_SCHEMA_PASSWORD>';
    const connectionString = document.getElementById('coordinator-connection-string').value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `connect ${orchUser}/${orchPass}@'${connectionString}'`;
    
    document.getElementById('coordinator-connection-command').textContent = command;
    updateCoordinatorCommand();
}

function updateCoordinatorCommand() {
    const orchUser = document.getElementById('orchestrator1-user').value || '<ORCHESTRATOR_SCHEMA>';
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    
    let quotedBrokerName;
    if (((brokerDbName.startsWith('"') && brokerDbName.endsWith('"')) || 
        (brokerDbName.startsWith("'") && brokerDbName.endsWith("'"))) && 
        brokerDbName.length > 2) {
        quotedBrokerName = brokerDbName;
    } else {
        quotedBrokerName = `'${brokerDbName}'`;
    }

    let quotedCoordinatorName;
    if (((coordinatorName.startsWith('"') && coordinatorName.endsWith('"')) || 
        (coordinatorName.startsWith("'") && coordinatorName.endsWith("'"))) && 
        coordinatorName.length > 2) {
        quotedCoordinatorName = coordinatorName;
    } else {
        quotedCoordinatorName = `'${coordinatorName}'`;
    }
    
    const command = `-- Execute from orchestrator1 schema
EXEC DBMS_SAGA_ADM.ADD_COORDINATOR(
  coordinator_name => ${quotedCoordinatorName},
  mailbox_schema   => '${brokerUser.toUpperCase()}',
  broker_name      => ${quotedBrokerName}
);`;
    
    document.getElementById('coordinator-command').textContent = command;
    updateCoordinatorVerificationCommands();
}

function updateCoordinatorVerificationCommands() {
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    
    let quotedCoordinatorName;
    if (((coordinatorName.startsWith('"') && coordinatorName.endsWith('"')) || 
        (coordinatorName.startsWith("'") && coordinatorName.endsWith("'"))) && 
        coordinatorName.length > 2) {
        quotedCoordinatorName = coordinatorName;
    } else {
        quotedCoordinatorName = `'${coordinatorName}'`;
    }
    
    const verifyCoordinatorCommand = `-- Check the new coordinator
SELECT coordinator_name, coordinator_schema, broker_name, queue_partitions, listener_count
FROM ALL_SAGA_COORDINATORS
WHERE coordinator_name = ${quotedCoordinatorName};`;
    
    const verifyCoordinatorElement = document.getElementById('verify-coordinator-command');
    if (verifyCoordinatorElement) {
        verifyCoordinatorElement.textContent = verifyCoordinatorCommand;
    }
    const verifyCoordinatorQueueCommand = `-- Examine the coordinator queue infrastructure
SELECT queue_name, queue_table, owner 
FROM DBA_QUEUES 
WHERE owner = USER 
AND queue_name LIKE '%${coordinatorName.replace(/["']/g, '')}%';`;
    
    const verifyCoordinatorQueueElement = document.getElementById('verify-coordinator-queue-command');
    if (verifyCoordinatorQueueElement) {
        verifyCoordinatorQueueElement.textContent = verifyCoordinatorQueueCommand;
    }
}

function saveCoordinatorName() {
    const coordinatorName = document.getElementById('coordinator-name').value;
    if (coordinatorName) {
        sessionStorage.setItem('saga_coordinator_name', coordinatorName);
    }
}

function saveCoordinatorConfig() {
    const coordinatorName = document.getElementById('coordinator-name').value;
    
    if (coordinatorName) {
        sessionStorage.setItem('saga_coordinator_name', coordinatorName);
        
        document.getElementById('coordinator-save-message').innerHTML = `
            ✅ <strong>Coordinator Configuration Saved Successfully!</strong><br/>
            Coordinator Name: ${coordinatorName}<br/>
            <small>Configuration will persist until you close the browser tab.</small>
        `;
        document.getElementById('coordinator-save-status').style.display = 'block';
        document.getElementById('coordinator-save-status').style.background = '#d4edda';
        document.getElementById('coordinator-save-status').style.color = '#155724';
        
        const button = document.querySelector('button[onclick="saveCoordinatorConfig()"]');
        const originalText = button.textContent;
        button.textContent = 'Saved!';
        button.style.background = '#28a745';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
        
    } else {
        document.getElementById('coordinator-save-message').innerHTML = `
            ⚠️ <strong>Please Enter Coordinator Name</strong><br/>
            Enter a valid coordinator name before saving.
        `;
        document.getElementById('coordinator-save-status').style.display = 'block';
        document.getElementById('coordinator-save-status').style.background = '#fff3cd';
        document.getElementById('coordinator-save-status').style.color = '#856404';
    }
}

function deleteCoordinatorConfig() {
    sessionStorage.removeItem('saga_coordinator_name');
    document.getElementById('coordinator-name').value = '';
    
    document.getElementById('coordinator-save-message').innerHTML = `
        🗑️ <strong>Coordinator Configuration Deleted</strong><br/>
        Coordinator configuration has been removed from browser session.
    `;
    document.getElementById('coordinator-save-status').style.display = 'block';
    document.getElementById('coordinator-save-status').style.background = '#f8d7da';
    document.getElementById('coordinator-save-status').style.color = '#721c24';
    
    updateCoordinatorCommand();
}

function clearCoordinatorConfig() {
    document.getElementById('coordinator-name').value = '';
    hideElement(document.getElementById('coordinator-save-status'));
    updateCoordinatorCommand();
}

function updateCloudbankConnectionCommand() {
    updateCloudbankCommand();
}

function updateBankaConnectionCommand() {
    const bankaUser = document.getElementById('banka-user').value || '<BANKA_SCHEMA>';
    const bankaPass = document.getElementById('banka-password').value || '<BANKA_SCHEMA_PASSWORD>';
    const connectionString = document.getElementById('banka-connection-string').value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `connect ${bankaUser}/${bankaPass}@'${connectionString}'`;
    document.getElementById('banka-connection-command').textContent = command;
    updateBankaCommand();
}

function updateBankbConnectionCommand() {
    const bankbUser = document.getElementById('bankb-user').value || '<BANKB_SCHEMA>';
    const bankbPass = document.getElementById('bankb-password').value || '<BANKB_SCHEMA_PASSWORD>';
    const connectionString = document.getElementById('bankb-connection-string').value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `connect ${bankbUser}/${bankbPass}@'${connectionString}'`;
    document.getElementById('bankb-connection-command').textContent = command;
    updateBankbCommand();
}

function updateAdminConnectionCommand() {
    const adminUser = document.getElementById('admin-user').value || '<ADMIN_SCHEMA>';
    const adminPass = document.getElementById('admin-password').value || '<DATABASE_ADMIN_PASSWORD>';
    const connectionString = document.getElementById('admin-connection-string').value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `connect ${adminUser}/${adminPass}@'${connectionString}'`;
    document.getElementById('admin-connection-command').textContent = command;
    updateVerifyAllParticipantsCommand();
}

function updateCloudbankCommand() {
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    const participantName = 'CloudBank'; // Fixed value for CloudBank demo consistency
    
    let quotedBrokerName = getQuotedName(brokerDbName);
    let quotedCoordinatorName = getQuotedName(coordinatorName);
    let quotedParticipantName = getQuotedName(participantName);
    
    const command = `-- Execute from orchestrator schema (CloudBank participant)
EXEC DBMS_SAGA_ADM.ADD_PARTICIPANT(
  participant_name => ${quotedParticipantName},
  coordinator_name => ${quotedCoordinatorName}, 
  mailbox_schema   => '${brokerUser.toUpperCase()}',
  broker_name      => ${quotedBrokerName}
);`;
    
    document.getElementById('cloudbank-command').textContent = command;
}

function updateBankaCommand() {
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    const participantName = 'BankA'; // Fixed value for CloudBank demo consistency
    
    let quotedBrokerName = getQuotedName(brokerDbName);
    let quotedCoordinatorName = getQuotedName(coordinatorName);
    let quotedParticipantName = getQuotedName(participantName);
    
    const command = `-- Execute from banka schema
EXEC DBMS_SAGA_ADM.ADD_PARTICIPANT(
  participant_name => ${quotedParticipantName},
  coordinator_name => ${quotedCoordinatorName}, 
  mailbox_schema   => '${brokerUser.toUpperCase()}',
  broker_name      => ${quotedBrokerName}
);`;
    
    document.getElementById('banka-command').textContent = command;
}

function updateBankbCommand() {
    const brokerUser = document.getElementById('broker1-user').value || '<BROKER_SCHEMA>';
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    const brokerDbName = document.getElementById('broker-db-name').value || '<BROKER_PARTICIPANT_NAME>';
    const participantName = document.getElementById('bankb-participant-name').value || '<BANKB_PARTICIPANT_NAME>';
    
    let quotedBrokerName = getQuotedName(brokerDbName);
    let quotedCoordinatorName = getQuotedName(coordinatorName);
    let quotedParticipantName = getQuotedName(participantName);
    
    const command = `-- Execute from bankb schema
EXEC DBMS_SAGA_ADM.ADD_PARTICIPANT(
  participant_name => ${quotedParticipantName},
  coordinator_name => ${quotedCoordinatorName}, 
  mailbox_schema   => '${brokerUser.toUpperCase()}',
  broker_name      => ${quotedBrokerName}
);`;
    
    document.getElementById('bankb-command').textContent = command;
}

function getQuotedName(name) {
    if (((name.startsWith('"') && name.endsWith('"')) || 
        (name.startsWith("'") && name.endsWith("'"))) && 
        name.length > 2) {
        return name;
    } else {
        return `'${name}'`;
    }
}

function updateVerifyAllParticipantsCommand() {
    const coordinatorName = document.getElementById('coordinator-name').value || '<COORDINATOR_PARTICIPANT_NAME>';
    
    let quotedCoordinatorName = getQuotedName(coordinatorName);
    
    const verifyCommand = `-- List all participants
SELECT participant_name, participant_schema, coordinator_name, 
       broker_name, mailbox_schema, callback_schema, callback_package
FROM DBA_SAGA_PARTICIPANTS
WHERE coordinator_name = ${quotedCoordinatorName};

-- Check participant queues
SELECT queue_name, queue_table, owner 
FROM DBA_QUEUES 
WHERE owner IN ('CLOUDBANK', 'BANKA', 'BANKB')
AND queue_name LIKE '%_IN_Q';`;
    
    const verifyElement = document.getElementById('verify-all-participants-command');
    if (verifyElement) {
        verifyElement.textContent = verifyCommand;
    }
}

function saveCloudbankParticipantName() {
    const participantName = document.getElementById('cloudbank-participant-name').value;
    if (participantName) {
        sessionStorage.setItem('saga_cloudbank_participant_name', participantName);
    }
}

function saveCloudbankConfig() {
    const participantName = document.getElementById('cloudbank-participant-name').value;
    
    if (participantName) {
        sessionStorage.setItem('saga_cloudbank_participant_name', participantName);
        
        document.getElementById('cloudbank-save-message').innerHTML = `
            ✅ <strong>CloudBank Configuration Saved Successfully!</strong><br/>
            Participant Name: ${participantName}<br/>
            <small>Configuration will persist until you close the browser tab.</small>
        `;
        document.getElementById('cloudbank-save-status').style.display = 'block';
        document.getElementById('cloudbank-save-status').style.background = '#d4edda';
        document.getElementById('cloudbank-save-status').style.color = '#155724';
        
        const button = document.querySelector('button[onclick="saveCloudbankConfig()"]');
        const originalText = button.textContent;
        button.textContent = 'Saved!';
        button.style.background = '#28a745';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
        
    } else {
        document.getElementById('cloudbank-save-message').innerHTML = `
            ⚠️ <strong>Please Enter CloudBank Participant Name</strong><br/>
            Enter a valid participant name before saving.
        `;
        document.getElementById('cloudbank-save-status').style.display = 'block';
        document.getElementById('cloudbank-save-status').style.background = '#fff3cd';
        document.getElementById('cloudbank-save-status').style.color = '#856404';
    }
}

function deleteCloudbankConfig() {
    sessionStorage.removeItem('saga_cloudbank_participant_name');
    document.getElementById('cloudbank-participant-name').value = '';
    
    document.getElementById('cloudbank-save-message').innerHTML = `
        🗑️ <strong>CloudBank Configuration Deleted</strong><br/>
        Participant configuration has been removed from browser session.
    `;
    document.getElementById('cloudbank-save-status').style.display = 'block';
    document.getElementById('cloudbank-save-status').style.background = '#f8d7da';
    document.getElementById('cloudbank-save-status').style.color = '#721c24';
    
    updateCloudbankCommand();
}

function clearCloudbankConfig() {
    document.getElementById('cloudbank-participant-name').value = '';
    hideElement(document.getElementById('cloudbank-save-status'));
    updateCloudbankCommand();
}

function saveBankaParticipantName() {
    const participantName = document.getElementById('banka-participant-name').value;
    if (participantName) {
        sessionStorage.setItem('saga_banka_participant_name', participantName);
    }
}

function saveBankaConfig() {
    const participantName = document.getElementById('banka-participant-name').value;
    
    if (participantName) {
        sessionStorage.setItem('saga_banka_participant_name', participantName);
        
        document.getElementById('banka-save-message').innerHTML = `
            ✅ <strong>BankA Configuration Saved Successfully!</strong><br/>
            Participant Name: ${participantName}<br/>
            <small>Configuration will persist until you close the browser tab.</small>
        `;
        document.getElementById('banka-save-status').style.display = 'block';
        document.getElementById('banka-save-status').style.background = '#d4edda';
        document.getElementById('banka-save-status').style.color = '#155724';
        
        const button = document.querySelector('button[onclick="saveBankaConfig()"]');
        const originalText = button.textContent;
        button.textContent = 'Saved!';
        button.style.background = '#28a745';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
        
    } else {
        document.getElementById('banka-save-message').innerHTML = `
            ⚠️ <strong>Please Enter BankA Participant Name</strong><br/>
            Enter a valid participant name before saving.
        `;
        document.getElementById('banka-save-status').style.display = 'block';
        document.getElementById('banka-save-status').style.background = '#fff3cd';
        document.getElementById('banka-save-status').style.color = '#856404';
    }
}

function deleteBankaConfig() {
    sessionStorage.removeItem('saga_banka_participant_name');
    document.getElementById('banka-participant-name').value = '';
    
    document.getElementById('banka-save-message').innerHTML = `
        🗑️ <strong>BankA Configuration Deleted</strong><br/>
        Participant configuration has been removed from browser session.
    `;
    document.getElementById('banka-save-status').style.display = 'block';
    document.getElementById('banka-save-status').style.background = '#f8d7da';
    document.getElementById('banka-save-status').style.color = '#721c24';
    
    updateBankaCommand();
}

function clearBankaConfig() {
    document.getElementById('banka-participant-name').value = '';
    hideElement(document.getElementById('banka-save-status'));
    updateBankaCommand();
}

function saveBankbParticipantName() {
    const participantName = document.getElementById('bankb-participant-name').value;
    if (participantName) {
        sessionStorage.setItem('saga_bankb_participant_name', participantName);
    }
}

function saveBankbConfig() {
    const participantName = document.getElementById('bankb-participant-name').value;
    
    if (participantName) {
        sessionStorage.setItem('saga_bankb_participant_name', participantName);
        
        document.getElementById('bankb-save-message').innerHTML = `
            ✅ <strong>BankB Configuration Saved Successfully!</strong><br/>
            Participant Name: ${participantName}<br/>
            <small>Configuration will persist until you close the browser tab.</small>
        `;
        document.getElementById('bankb-save-status').style.display = 'block';
        document.getElementById('bankb-save-status').style.background = '#d4edda';
        document.getElementById('bankb-save-status').style.color = '#155724';
        
        const button = document.querySelector('button[onclick="saveBankbConfig()"]');
        const originalText = button.textContent;
        button.textContent = 'Saved!';
        button.style.background = '#28a745';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '';
        }, 2000);
        
    } else {
        document.getElementById('bankb-save-message').innerHTML = `
            ⚠️ <strong>Please Enter BankB Participant Name</strong><br/>
            Enter a valid participant name before saving.
        `;
        document.getElementById('bankb-save-status').style.display = 'block';
        document.getElementById('bankb-save-status').style.background = '#fff3cd';
        document.getElementById('bankb-save-status').style.color = '#856404';
    }
}

function deleteBankbConfig() {
    sessionStorage.removeItem('saga_bankb_participant_name');
    document.getElementById('bankb-participant-name').value = '';
    
    document.getElementById('bankb-save-message').innerHTML = `
        🗑️ <strong>BankB Configuration Deleted</strong><br/>
        Participant configuration has been removed from browser session.
    `;
    document.getElementById('bankb-save-status').style.display = 'block';
    document.getElementById('bankb-save-status').style.background = '#f8d7da';
    document.getElementById('bankb-save-status').style.color = '#721c24';
    
    updateBankbCommand();
}

function clearBankbConfig() {
    document.getElementById('bankb-participant-name').value = '';
    hideElement(document.getElementById('bankb-save-status'));
    updateBankbCommand();
}


function copyToClipboard(elementId, containerId) {
    const text = document.getElementById(elementId).textContent;
    navigator.clipboard.writeText(text).then(() => {
        const container = document.getElementById(containerId);
        container.style.opacity = "0.5";
        setTimeout(() => container.style.opacity = "1", 200);
    });
}

function updateTask4SchemaQuery() {
    const brokerSchema = document.getElementById('task4-broker-schema')?.value || '<BROKER_SCHEMA>';
    const coordinatorSchema = document.getElementById('task4-orchestrator-schema')?.value || '<COORDINATOR_SCHEMA>';
    const bankaSchema = document.getElementById('task4-banka-schema')?.value || '<BANKA_SCHEMA>';
    const bankbSchema = document.getElementById('task4-bankb-schema')?.value || '<BANKB_SCHEMA>';
    
    const query = `-- Check saga role assignments for all schemas
SELECT grantee, granted_role, admin_option, default_role
FROM DBA_ROLE_PRIVS 
WHERE grantee IN ('${brokerSchema.toUpperCase()}', '${coordinatorSchema.toUpperCase()}', '${bankaSchema.toUpperCase()}', '${bankbSchema.toUpperCase()}')
  AND granted_role LIKE 'SAGA%'
ORDER BY grantee, granted_role;

-- Check system privileges for saga schemas
SELECT grantee, privilege, admin_option
FROM DBA_SYS_PRIVS
WHERE grantee IN ('${brokerSchema.toUpperCase()}', '${coordinatorSchema.toUpperCase()}', '${bankaSchema.toUpperCase()}', '${bankbSchema.toUpperCase()}')
  AND (privilege LIKE '%SAGA%' OR privilege LIKE '%AQ%' OR privilege = 'CREATE SESSION')
ORDER BY grantee, privilege;`;

    const queryElement = document.getElementById('task4-schema-query');
    if (queryElement) {
        queryElement.textContent = query;
    }
}

function updateTask5AdminConnectionCommand() {
    const adminUser = document.getElementById('task5-admin-user')?.value || '<ADMIN_SCHEMA>';
    const adminPass = document.getElementById('task5-admin-password')?.value || '<DATABASE_ADMIN_PASSWORD>';
    const connectionString = document.getElementById('task5-admin-connection-string')?.value || '<DATABASE_CONNECTION_TNS_NAME>';
    
    const command = `connect ${adminUser}/${adminPass}@'${connectionString}'`;
    
    const commandElement = document.getElementById('task5-admin-connection-command');
    if (commandElement) {
        commandElement.textContent = command;
    }
}

document.addEventListener('DOMContentLoaded', function() {
    setTimeout(updateTask4SchemaQuery, 100);
    setTimeout(updateTask5AdminConnectionCommand, 100);
    
    // Initialize fixed participant names and update their commands
    setTimeout(function() {
        // Set fixed values for CloudBank and BankA participants
        const cloudbankInput = document.getElementById('cloudbank-participant-name');
        const bankaInput = document.getElementById('banka-participant-name');
        
        if (cloudbankInput) {
            cloudbankInput.value = 'CloudBank';
            updateCloudbankCommand();
        }
        
        if (bankaInput) {
            bankaInput.value = 'BankA';
            updateBankaCommand();
        }
    }, 200);
});
</script>

<style>
.input-section {
    background: #f9f9f9;
    border: 1px solid #ddd;
    padding: 15px;
    border-radius: 5px;
    margin: 10px 0;
}

.input-field {
    width: 300px;
    padding: 8px;
    margin: 5px 0;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 14px;
}

.input-field-fixed {
    width: 300px;
    padding: 8px;
    margin: 5px 0;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 14px;
    background-color: #f5f5f5;
    cursor: not-allowed;
    color: #666;
}

.save-btn-small, .clear-btn-small, .delete-btn-small {
    padding: 6px 12px;
    margin: 5px 5px 5px 0;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    font-weight: bold;
    transition: background 0.2s;
}

.save-btn-small {
    background: #28a745;
    color: white;
}

.save-btn-small:hover {
    background: #1e7e34;
}

.clear-btn-small {
    background: #6c757d;
    color: white;
}

.clear-btn-small:hover {
    background: #545b62;
}

.delete-btn-small {
    background: #dc3545;
    color: white;
}

.delete-btn-small:hover {
    background: #c82333;
}

.save-status {
    padding: 15px;
    border-radius: 5px;
    margin: 10px 0;
    border: 1px solid #ddd;
    min-height: 60px;
    font-size: 14px;
    background-color: #d4edda;
    color: #155724;
}

.interactive-command {
    display: flex;
    align-items: center;
    background: #f5f5f5;
    border: 1px solid #ccc;
    padding: 10px;
    border-radius: 5px;
    position: relative;
    transition: opacity 0.3s;
    margin: 10px 0;
    font-family: 'Courier New', monospace;
}

.command-text {
    white-space: pre-wrap;
    font-size: 14px;
    line-height: 1.4;
}

.copy-btn {
    background: #28a745;
    color: black;
    border: none;
    padding: 10px 20px;
    cursor: pointer;
    border-radius: 5px;
    font-weight: bold;
    font-size: 14px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
}

.copy-btn:hover {
    background: #100f0e;
    color: white;
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
    border-radius: 3px;
    transition: background 0.2s, color 0.2s;
    min-width: auto;
    height: auto;
}

.interactive-command .copy-btn:hover {
    background: #100f0e;
    color: white;
}
</style>
