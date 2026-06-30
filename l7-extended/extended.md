# Lab 7: Extended Labs

## **Introduction**

In this lab, you will go beyond the default CloudBank setup and explore **advanced usage of Oracle Sagas**.  
You will work with the **PL/SQL client** via the `DBMS_SAGA` package and set up **new participants** to simulate multi-bank transactions.  
Finally, you will test **polyglot transactions**, where both Java and PL/SQL clients participate in the same Saga.  

</br>

*Estimated Time: 45–60 minutes*

---

### Objectives

By the end of this lab, you will:  
- Use the **DBMS_SAGA** PL/SQL API for defining and executing Saga logic.  
- Add new users and participants (`Bank C` and `Bank D`) to extend the CloudBank demo.  
- Test **normal and failure scenarios** for transactions between Bank C and Bank D.  
- Simulate **polyglot transactions** spanning Java and PL/SQL clients.  

---

### Prerequisites

- Completion of **Lab 5: CloudBank Application**.  
- Hands-on familiarity with **Lab 3: Core Setup** (Broker, Coordinator, Participants).  
- SQLcl access to your Autonomous Database (ADB-S) using Cloud Shell.  

---

## Task 1: Develop Oracle Sagas with PL/SQL Client

---

### Step i: Overview of `DBMS_SAGA` Package

Oracle Database provides the `DBMS_SAGA` package for PL/SQL clients.  
This package enables:  
- **Saga lifecycle management** (begin, join, complete, compensate).  
- **Defining participants** and registering PL/SQL procedures as Saga actions.  
- **Error handling** for compensating or retrying transactions.  

Some important APIs include:  

- `DBMS_SAGA.BEGIN_SAGA` — Start a new Saga.  
- `DBMS_SAGA.JOIN_PARTICIPANT` — Add a participant.  
- `DBMS_SAGA.COMPLETE_SAGA` — Commit the Saga.  
- `DBMS_SAGA.ABORT_SAGA` — Abort and trigger compensations.  
- `DBMS_SAGA.REGISTER_PACKAGE` — Register a PL/SQL package as a participant.  

---

### Step ii: Create New Users (Bank C and Bank D)

1. Connect to your ADB-S with SQLcl as an admin user.  

VVVsql
-- Create Bank C
CREATE USER bankc IDENTIFIED BY "Password123";
GRANT CONNECT, RESOURCE TO bankc;

-- Create Bank D
CREATE USER bankd IDENTIFIED BY "Password123";
GRANT CONNECT, RESOURCE TO bankd;
VVV

2. Create basic schema objects for both banks (accounts table, balances).  

VVVsql
-- In BANKC schema
CREATE TABLE accounts (
  acct_id NUMBER PRIMARY KEY,
  balance NUMBER
);

INSERT INTO accounts VALUES (1, 1000);

-- In BANKD schema
CREATE TABLE accounts (
  acct_id NUMBER PRIMARY KEY,
  balance NUMBER
);

INSERT INTO accounts VALUES (1, 2000);
VVV

---

### Step iii: Register New Participants

1. Define PL/SQL procedures for debit/credit actions in both schemas.  

VVVsql
-- Example in BANKC
CREATE OR REPLACE PACKAGE bankc_pkg AS
  PROCEDURE debit(acct_id NUMBER, amt NUMBER);
  PROCEDURE credit(acct_id NUMBER, amt NUMBER);
END bankc_pkg;
/

CREATE OR REPLACE PACKAGE BODY bankc_pkg AS
  PROCEDURE debit(acct_id NUMBER, amt NUMBER) IS
  BEGIN
    UPDATE accounts SET balance = balance - amt WHERE acct_id = acct_id;
  END;

  PROCEDURE credit(acct_id NUMBER, amt NUMBER) IS
  BEGIN
    UPDATE accounts SET balance = balance + amt WHERE acct_id = acct_id;
  END;
END bankc_pkg;
/

-- Register the package with DBMS_SAGA
BEGIN
  DBMS_SAGA_ADM.REGISTER_PACKAGE(
    participant_name => 'BankC',
    package_name     => 'BANKC_PKG'
  );
END;
/
VVV

*(Repeat similar steps for BANKD with `bankd_pkg`.)*

---

### Step iv: Test Normal and Failure Cases

1. Normal case: Transfer from Bank C → Bank D.  

VVVsql
DECLARE
  l_saga_id RAW(16);
BEGIN
  l_saga_id := DBMS_SAGA.BEGIN_SAGA('Transfer C to D');

  DBMS_SAGA.JOIN_PARTICIPANT(l_saga_id, 'BankC');
  DBMS_SAGA.JOIN_PARTICIPANT(l_saga_id, 'BankD');

  bankc_pkg.debit(1, 200);
  bankd_pkg.credit(1, 200);

  DBMS_SAGA.COMPLETE_SAGA(l_saga_id);
END;
/
VVV

2. Failure case: Attempt transfer with insufficient balance.  

VVVsql
DECLARE
  l_saga_id RAW(16);
BEGIN
  l_saga_id := DBMS_SAGA.BEGIN_SAGA('Failure Scenario C to D');

  DBMS_SAGA.JOIN_PARTICIPANT(l_saga_id, 'BankC');
  DBMS_SAGA.JOIN_PARTICIPANT(l_saga_id, 'BankD');

  bankc_pkg.debit(1, 5000); -- Overdraft!
  bankd_pkg.credit(1, 5000);

  DBMS_SAGA.COMPLETE_SAGA(l_saga_id);
EXCEPTION
  WHEN OTHERS THEN
    DBMS_SAGA.ABORT_SAGA(l_saga_id);
END;
/
VVV

**Expected Output:**  
- Normal case: Balances updated.  
- Failure case: Saga aborts and compensations triggered.  

---

## Task 2: Testing a Polyglot Transaction

---

### Step i: Java + PL/SQL Clients in the Same Saga

Oracle Sagas allow **polyglot transactions**, where multiple client types (Java, PL/SQL, REST) participate in the same Saga.

For example:  
- **Bank A** handled by a **Java Client** (CloudBank microservice).  
- **Bank C** handled by a **PL/SQL Client** (registered package).  

---

### Step ii: Normal Polyglot Case

1. Start a Saga from the **Java Client** (Bank A).  
2. Join participant **Bank C** (PL/SQL).  

- Bank A debits from its account (Java logic).  
- Bank C credits into its account (PL/SQL package).  
- Saga completes successfully.  

**Expected Result:** Cross-client transaction is consistent.  

---

### Step iii: Failure Polyglot Case

1. Start a Saga from the **Java Client** (Bank A).  
2. Join participant **Bank C**.  
3. Force a failure in Bank C (e.g., debit more than balance).  

- Bank A debits first.  
- Bank C fails credit.  
- Saga aborts, compensating Bank A.  

**Expected Result:** All clients (Java + PL/SQL) roll back consistently.  

---

✅ **End of Lab 7.**  
You have now extended Oracle Sagas to include **custom PL/SQL participants** and tested **polyglot transactions**.  
This demonstrates the flexibility of Oracle Sagas in handling real-world distributed applications.  

---

## Learn More

- [DBMS_SAGA PL/SQL Reference](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)  
- [Saga Polyglot Transactions](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/introduction-to-saga.html)  

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney  
* **Created By/Date** — Vinay Pandhariwal, August 2025  
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
