# Lab 4: Developing Oracle Sagas with the Java Client

## **Introduction**

In this lab, you will examine a Java-based saga client that provides an application-layer interface to the `DBMS_SAGA` package in PL/SQL. You will use the CloudBank demo application, a Jersey-based Spring application that demonstrates distributed transaction management with Oracle Sagas.

### What You Will Do in This Lab

1. **Review Maven dependencies** required by the Oracle Saga client.
2. **Add saga annotations** in the code editor to the supplied CloudBank files:
   - A saga initiator using `@Participant`, `@Complete`, `@Compensate`, and `@Response`.
   - A saga participant using `@Participant`, `@SagaConnection`, and `@Request`.
3. **Review the CloudBank architecture and workflows** for account creation and money transfers.

### About the CloudBank Demo Application

The CloudBank demo application runs in one Oracle Database 23ai PDB with separate schemas for its services:

- **CloudBank Orchestrator**: initiates and coordinates saga workflows.
- **CloudBank Coordinator**: manages saga completion and compensation.
- **BankA and BankB Services**: process account operations and transfers.

> **⚠️ Important:** CloudBank is a learning example. Its business rules are simplified so that the focus remains on saga patterns and compensation.

### Java Client and DBMS_SAGA Parallel

The Java client and `DBMS_SAGA` provide the same distributed-transaction capabilities through different programming models:

- **Java client actions** ↔ **`DBMS_SAGA` procedures**
- **LRA annotations** ↔ **PL/SQL saga management**
- **Jersey REST services** ↔ **database-resident coordination**

### Key Learning Objectives

- Configure the Oracle Saga Maven dependency.
- Identify the annotations used by a saga initiator and participant.
- Understand the CloudBank architecture and its compensation workflows.

### Jersey-Based Spring Application

CloudBank uses Jersey for REST endpoints, Spring for dependency injection, and Oracle Saga annotations for declarative saga behavior. The application uses `beginSaga()` for programmatic lifecycle control.

</br>

<details open>
<summary><mark>Key Characteristics of the Java Saga Client:</mark></summary>

- **Annotations simplify coding:** No manual saga orchestration logic is required for annotated callbacks.
- **Automatic compensation:** The client invokes compensation methods when a saga rolls back.
- **JDBC/TEQ integration:** Services connect securely to the Saga Broker.
- **Polyglot support:** Java services can participate alongside PL/SQL participants.

</details>

*Estimated Time: 45–60 minutes*

### Objectives

In this lab, you will:

- **Review Maven dependencies** for Oracle Saga integration.
- **Add and verify annotations** in the supplied CloudBank files.
- **Understand** the CloudBank architecture and saga workflows.

### Prerequisites

- Completion of **Lab 3** (Broker, Coordinator, and Participants configured).
- Java 11+ installed in your CloudShell or VM.
- Maven or Gradle installed for dependency management.

## Task 1: Maven Dependencies

---

The Oracle Saga Maven dependency provides the annotations and client functionality used by the CloudBank demo application.

### Step 1: Core Saga Dependency

Add the following dependency to each CloudBank service that participates in a saga:

```xml
<copy>
<dependency>
    <groupId>com.oracle.database.saga</groupId>
    <artifactId>saga-core</artifactId>
    <version>XX.X.X</version>
</dependency>
</copy>
```

**`saga-core`** provides the `@Participant`, `@Request`, `@Response`, `@Complete`, `@Compensate`, and `@SagaConnection` annotations, plus the Java client libraries.

### Step 2: CloudBank Project Structure

Review the `saga-core` dependency in these files:

1. **BankA Service:** `oracle-saga-cloudbank/Cloudbank/banka/pom.xml`
2. **BankB Service:** `oracle-saga-cloudbank/Cloudbank/bankb/pom.xml`
3. **Orchestrator Service:** `oracle-saga-cloudbank/Cloudbank/orchestrator/pom.xml`

### Step 3: Repository Information

- [Oracle Saga artifacts on Maven Repository](https://mvnrepository.com/artifact/com.oracle.database.saga)
- [`saga-core` artifact](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core)

The Maven environment is configured and verified in the next lab when you configure Podman containers.

![Maven Dependencies](./images/lab4-task1-1.png "Oracle Saga core dependency in CloudBank project structure")

## Task 2: Understanding Annotations in CloudBank Files

---

Use the code editor to examine and complete the Oracle Saga annotations in the supplied CloudBank files. Use the fixed participant names shown below; they must match the registrations from Lab 3.

### Step 1: Oracle LRA and Saga Annotations Overview

- `@Participant` identifies a saga service.
- `@SagaConnection` supplies the JDBC connection used by the saga client.
- `@Request` receives a request from an initiator.
- `@Response` receives a participant response.
- `@Complete` runs after a successful saga completion.
- `@Compensate` runs when a saga rolls back.
- `@LRA` is the alternative declarative lifecycle model; CloudBank uses `beginSaga()` instead.

### Step 2: Add the CloudBank Participant Annotation

Open `/Cloudbank/orchestrator/src/java/.../controller/CloudBankController.java`. Find the annotation placeholder above `CloudBankController` and replace it with:

```java
<copy>
@Participant(name = "CloudBank")
</copy>
```

**`@Participant`** registers the class as the fixed `CloudBank` participant name used by the saga framework.

![Participant Annotation](./images/lab4-task2-participant.png "@Participant annotation in CloudBank controller")

### Step 3: Review the Saga Connection

In the same file, review the supplied connection provider:

```java
@SagaConnection
public static Connection getCloudBankConnection() throws SQLException {
    return ConnectionPools.getCloudBankConnection();
}
```

**`@SagaConnection`** marks the static method that returns the JDBC connection for saga operations.

### Step 4: Review `beginSaga()`

Find the `beginSaga()` calls in the `newBankAccount` and `transfer` methods:

```java
Saga saga = this.beginSaga();
var sagaId = saga.getSagaId();
```

**`beginSaga()`** starts a saga and returns its `Saga` object.

**`getSagaId()`** returns the unique identifier used for logging and correlation.

CloudBank uses this programmatic approach instead of `@LRA` for explicit lifecycle control.

### Step 5: Review `sendRequest()`

Find the requests sent to `BankA` and `BankB` in the controller:

```java
saga.sendRequest(Stubs.BANK_A, payload.toString());
saga.sendRequest(Stubs.BANK_B, payload.toString());
```

**`sendRequest()`** enrolls the target participant and sends it the JSON request payload.

The participant names represented by `Stubs.BANK_A` and `Stubs.BANK_B` must match the names registered in Lab 3.

### Step 6: Add Completion and Compensation Annotations

Find the placeholders above `onPostRollback` and `onPostCommit`, then replace them with the following annotations:

```java
<copy>
@Compensate
</copy>
```

**`@Compensate`** marks the callback that runs when the saga rolls back; its logic must be idempotent.

```java
<copy>
@Complete
</copy>
```

**`@Complete`** marks the callback that runs after the saga completes successfully; its logic must be idempotent.

### Step 7: Add Response Annotations

Find the placeholders above `onResponseBankA` and `onResponseBankB`, then replace them with:

```java
<copy>
@oracle.saga.annotation.Response(sender = "BankA.*")
</copy>
```

**`@oracle.saga.annotation.Response`** routes responses from `BankA` to `onResponseBankA`.

```java
<copy>
@oracle.saga.annotation.Response(sender = "BankB.*")
</copy>
```

**`@oracle.saga.annotation.Response`** routes responses from `BankB` to `onResponseBankB`.

### Step 8: Review Saga Completion

Find the lifecycle calls in the controller:

```java
saga.commitSaga();
saga.rollbackSaga();
```

**`commitSaga()`** completes the saga and invokes `@Complete` callbacks.

**`rollbackSaga()`** compensates the saga and invokes `@Compensate` callbacks.

### Step 9: Review the BankA Participant

Open `/Cloudbank/banka/src/java/.../controller/AccountsController.java` and review these supplied annotations:

```java
@Participant(name = "BankA")
public class AccountsController extends SagaParticipant {

    @SagaConnection
    public static Connection getAccountsConnection() throws SQLException {
        return ConnectionPools.getAccountsConnection();
    }

    @Request(sender = "CloudBank")
    public String onRequest(SagaMessageContext info) {
        // Process the request and return a JSON response.
    }
}
```

**`@Participant(name = "BankA")`** registers the fixed BankA participant name.

**`@Request(sender = "CloudBank")`** accepts requests from the CloudBank initiator.

#### Key Takeaways

- CloudBank starts sagas with `beginSaga()` and controls completion explicitly.
- Participant names must match in `@Participant`, `@Request`, and `sendRequest()`.
- Completion and compensation callbacks must be safe to retry.

![Complete Analysis](./images/lab4-task2-summary.png "Oracle Saga annotation analysis in CloudBank application")

## Task 3: Understand CloudBank Demo Application

---

CloudBank is a demo application that illustrates saga coordination and compensation rather than production banking rules.

### Step 1: Schema & Architecture

CloudBank runs in a single Oracle Database 23ai PDB with separate schemas for the orchestrator, BankA, and BankB services.

[![CloudBank Architecture Screenshot](https://img.shields.io/badge/🏛️%20CloudBank-Architecture%20Demo-blue?style=for-the-badge&logo=database&logoColor=white)](images/Arch.mp4)

The schemas contain customer and saga-audit data in the orchestrator, plus account and operation-log data in each bank. Bank account balances use Oracle `RESERVABLE` columns for lock-free fund reservations.

| Component | Role |
|-----------|------|
| CloudBank Orchestrator | Starts sagas, routes requests, and records status. |
| BankA and BankB | Process account operations as saga participants. |
| Coordinator and Broker | Coordinate lifecycle events and route saga messages. |

The demo creates its own test accounts. Do not enter or configure shared credentials for this lab.

### Step 2: Saga Workflows

#### Workflow 1: New Bank Account Creation

1. The orchestrator starts a saga and determines the selected bank.
2. It sends an account-creation request to BankA or BankB.
3. The bank creates the account and returns a response.
4. The orchestrator commits on success or compensates on failure.

#### Workflow 2: Inter-Bank Money Transfer

1. The orchestrator starts a saga and sends withdrawal and deposit requests.
2. The source bank reserves funds; the target bank prepares the deposit.
3. The orchestrator commits after successful responses from both participants.
4. Any failure invokes compensation, which restores reserved funds as needed.

**`RESERVABLE` columns** support lock-free fund reservation while a saga is in progress.

**Compensation** returns the system to a consistent state when an account operation, transfer, or participant response fails.

![CloudBank Schema](./images/lab4-task3-1.png "CloudBank database schema and architecture")

![CloudBank Workflows](./images/lab4-task3-2.png "CloudBank saga workflows: account creation and money transfer")

---

## Learn More

- [Oracle Saga Client Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)
- [Java Annotations Guide](https://docs.oracle.com/javase/tutorial/java/annotations/)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney, Luis Cruz, Sebastian Gerritsen
* **Created By/Date** — Vinay Pandhariwal, August 2025
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
