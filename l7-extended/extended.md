# Extended Lab: Add a Saga Participant

## Introduction

In Labs 1–5, you prepared the Oracle Cloud environment, configured the Saga broker and coordinator, reviewed the Java client, and ran the CloudBank application with the original `CloudBank`, `BankChicago`, and `BankMex` participants.

In this lab, you will extend that existing topology manually. You will connect to the `banklondon` schema created in Lab 2, register `BankLondon` with the broker and coordinator created in Lab 3, and verify the new participant from the database metadata.

*Estimated time: 10 minutes*

### Objectives

By completing this lab, you will be able to:

- Register an additional Saga participant manually with `DBMS_SAGA_ADM.ADD_PARTICIPANT`.
- Associate the participant with the existing CloudBank coordinator and broker.
- Verify the participant name, owner, coordinator, broker, and type.
- Understand the difference between registering a participant and implementing its business logic.

### Prerequisites

- Complete Labs 1–5 before starting this lab.
- Keep the Autonomous Database and CloudBank environment from the previous labs available.

## Task 1: Register BankLondon as an Additional Participant

The `banklondon` schema, password, roles, wallet, broker, and coordinator are already configured by the previous labs. You only need to connect with SQLcl and run the complete registration script once.

### Step 1: Open SQLcl

Run the following commands in OCI Cloud Shell. The first command points SQLcl to the Autonomous Database wallet created in Lab 2, and the second command opens SQLcl without connecting to a schema yet.

```bash
<copy>
export TNS_ADMIN="$HOME/cloudbank-setup/oracle-saga-cloudbank/adbsSetup/adb_wallet"
sql /nolog
</copy>
```

Wait for the `SQL>` prompt before continuing.

### Step 2: Add and Verify BankLondon

At the `SQL>` prompt, copy and paste the complete block below once. Participant names must be unique within a broker, so do not run the registration block a second time after it succeeds.

```sql
<copy>
-- =========================================================
-- ADD BANKLONDON AS AN EXTRA SAGA PARTICIPANT
-- =========================================================
SET VERIFY OFF
SET SERVEROUTPUT ON

DEFINE DATABASE_CONNECTION_TNS_NAME = 'oraclesagademo_medium'
DEFINE CLOUDBANK_PASSWORD           = 'Welcome_123#'
DEFINE PARTICIPANT_SCHEMA           = 'banklondon'
DEFINE PARTICIPANT_NAME             = 'BankLondon'
DEFINE COORDINATOR_NAME             = 'CloudBankCoordinator'
DEFINE MAILBOX_SCHEMA               = 'brokerhub'
DEFINE BROKER_NAME                  = 'CloudBankBroker'

-- ADD_PARTICIPANT must run from the participant's own schema.
CONNECT &PARTICIPANT_SCHEMA/&CLOUDBANK_PASSWORD@'&DATABASE_CONNECTION_TNS_NAME'

BEGIN
  DBMS_SAGA_ADM.ADD_PARTICIPANT(
    participant_name => '&PARTICIPANT_NAME',
    coordinator_name => '&COORDINATOR_NAME',
    mailbox_schema   => '&MAILBOX_SCHEMA',
    broker_name      => '&BROKER_NAME'
  );
END;
/

-- Verify the new participant using the current schema metadata view.
COLUMN participant_name FORMAT A20
COLUMN participant_schema FORMAT A20
COLUMN coordinator_name FORMAT A25
COLUMN broker_name FORMAT A15
COLUMN type FORMAT A15

SELECT name        AS participant_name,
       owner       AS participant_schema,
       coordinator AS coordinator_name,
       broker_name,
       type
FROM   user_saga_participants
WHERE  UPPER(name) = UPPER('&PARTICIPANT_NAME')
AND    UPPER(owner) = UPPER('&PARTICIPANT_SCHEMA');

DECLARE
  participant_count PLS_INTEGER;
BEGIN
  SELECT COUNT(*)
  INTO   participant_count
  FROM   user_saga_participants
  WHERE  UPPER(name) = UPPER('&PARTICIPANT_NAME')
  AND    UPPER(owner) = UPPER('&PARTICIPANT_SCHEMA')
  AND    UPPER(coordinator) = UPPER('&COORDINATOR_NAME')
  AND    UPPER(broker_name) = UPPER('&BROKER_NAME');

  IF participant_count != 1 THEN
    RAISE_APPLICATION_ERROR(
      -20001,
      'BankLondon participant verification failed.'
    );
  END IF;

  DBMS_OUTPUT.PUT_LINE(
    'SUCCESS: BankLondon is registered as an additional Saga participant.'
  );
END;
/

UNDEFINE DATABASE_CONNECTION_TNS_NAME
UNDEFINE CLOUDBANK_PASSWORD
UNDEFINE PARTICIPANT_SCHEMA
UNDEFINE PARTICIPANT_NAME
UNDEFINE COORDINATOR_NAME
UNDEFINE MAILBOX_SCHEMA
UNDEFINE BROKER_NAME
</copy>
```

**✅ Expected output:**

```text
Connected.

PL/SQL procedure successfully completed.

PARTICIPANT_NAME    PARTICIPANT_SCHEMA    COORDINATOR_NAME         BROKER_NAME    TYPE
------------------- --------------------- ------------------------ -------------- -----------
BANKLONDON          BANKLONDON            CLOUDBANKCOORDINATOR     CLOUDBANKBROKER Participant

SUCCESS: BankLondon is registered as an additional Saga participant.

PL/SQL procedure successfully completed.
```

The exact spacing and capitalization displayed by SQLcl can vary. The registration is successful when the query returns one `BANKLONDON` row with coordinator `CLOUDBANKCOORDINATOR`, broker `CLOUDBANKBROKER`, and the final `SUCCESS` message appears.

### Step 3: Understand Why the Script Works

- **`CONNECT banklondon...`:** Connects to the existing participant schema. Oracle requires participant administration to run from the participant's own schema.

- **`ADD_PARTICIPANT`:** Registers `BankLondon` in the Saga framework, creating the participant metadata and messaging relationships.

- **`coordinator_name`:** Associates the participant with `CloudBankCoordinator`, which manages Saga completion and compensation.

- **`mailbox_schema` and `broker_name`:** Connect the participant to broker `CloudBankBroker`, which routes messages between Saga entities.

- **`USER_SAGA_PARTICIPANTS` query:** Reads the participant metadata visible to the current schema and confirms that the participant has the expected owner and topology.

- **Validation block:** Requires exactly one matching participant, preventing the lab from displaying a misleading success message.

The resulting topology is:

```text
CloudBankBroker
└── CloudBankCoordinator
    ├── CloudBank
    ├── BankChicago
    ├── BankMex
    └── BankLondon
```

> **⚠️ Important:** This lab registers `BankLondon` as a Saga participant, but the supplied CloudBank Java application does not send business requests to it. Processing requests would additionally require a Java implementation with `@Participant(name = "BankLondon")` or a PL/SQL callback package registered with `DBMS_SAGA_ADM.REGISTER_SAGA_CALLBACK`.

## Learn More

- [DBMS_SAGA_ADM PL/SQL Reference](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/dbms_saga_adm.html)
- [Developing Applications with Sagas](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/developing-applications-saga.html)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney
* **Created By/Date** — Vinay Pandhariwal, August 2025
* **Last Updated By/Date** — Sebastian Gerritsen, August 2026
