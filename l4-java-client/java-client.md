# Lab 4: Developing Oracle Sagas with the Java Client

## Introduction

In this lab, you will examine a Java-based saga client that provides an application-layer interface to the `DBMS_SAGA` package in PL/SQL. You will use the CloudBank demo application, a Jersey-based Spring application that demonstrates distributed transaction management with Oracle Sagas.

### About the CloudBank Demo Application

CloudBank runs in one Oracle Database 23ai PDB with separate schemas for these services:

- **CloudBank Orchestrator**: starts and coordinates sagas.
- **CloudBank Coordinator**: manages completion and compensation.
- **BankChicago and BankMex Services**: process account operations and transfers.

> **⚠️ Important:** CloudBank is a demo application designed for learning. Its business logic is simplified and may not cover all production cases; focus on the saga patterns and compensation workflow.

- **Estimated time:** 10 minutes

### Objectives

By completing this lab, you will be able to:

- **Review Maven dependencies** for Oracle Saga integration.
- **Verify Saga annotations** in the CloudBank services.
- **Understand the Saga workflow** from request through completion or compensation.

> **👀 Note:** Lab 4 is review-only. The CloudBank source, Maven dependencies, and annotations are already complete. You do not need to edit, build, or run the application in this lab.

## Task 1: Maven Dependency

The Oracle Saga Maven dependency provides the annotations and client functionality used by the CloudBank demo application.

### Step 1: Verify the Core Saga Dependency

The supplied CloudBank project already includes the following dependency in each service that participates in a saga. Review the corresponding `pom.xml` files and verify that the dependency and version match:

<pre><code>&lt;dependency&gt;
    &lt;groupId&gt;com.oracle.database.saga&lt;/groupId&gt;
    &lt;artifactId&gt;saga-core&lt;/artifactId&gt;
    &lt;version&gt;23.7.0&lt;/version&gt;
&lt;/dependency&gt;</code></pre>

**`saga-core` version `23.7.0`** provides the Java client, `Saga` API, and the annotations used in this lab. No dependency changes are required when the supplied POM files already contain this entry.

### Step 2: CloudBank Project Structure

<details>
<summary><strong>📋 CloudBank service POM files</strong></summary>

- `oracle-saga-cloudbank/CloudBank/orchestrator/pom.xml`
- `oracle-saga-cloudbank/CloudBank/banka/pom.xml` — BankChicago source directory
- `oracle-saga-cloudbank/CloudBank/bankb/pom.xml` — BankMex source directory

</details>

### Step 3: Repository Information

- [Oracle Saga artifacts on Maven Repository](https://mvnrepository.com/artifact/com.oracle.database.saga)
- [saga-core artifact](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core)

The Maven environment is configured and verified in the next lab when you configure Podman containers.

![Maven Dependencies](./images/lab4-task1-1.png "Oracle Saga core dependency in CloudBank project structure")

## Task 2: Understanding Annotations in CloudBank Files

Use the code editor to inspect the supplied CloudBank implementation. All annotations are already present; do not change the source files. Verify where each annotation is used and review how it participates in the Saga lifecycle. The participant names are fixed: `CloudBank`, `BankChicago`, and `BankMex`.

### Step 1: Annotation Map

| Annotation or method | Where it is used | Purpose |
|---|---|---|
| `@Participant` | Initiator and participant classes | Registers a fixed saga participant name. |
| `@SagaConnection` | Participant class | Supplies the JDBC connection. |
| `beginSaga()` | Initiator endpoint | Starts a saga. |
| `sendRequest()` | Initiator endpoint | Enrolls a participant and sends a JSON payload. |
| `@Request` | Participant method | Receives an initiator request. |
| `@Response` | Initiator method | Receives a participant response. |
| `@Complete` / `@Compensate` | Initiator callback | Finalizes or compensates the saga. |

<details>
<summary><strong>💡 Optional: Declarative alternative</strong></summary>

`@LRA` provides a declarative lifecycle model. CloudBank uses `beginSaga()` instead so that the controller explicitly decides when to commit or roll back.

</details>

### Step 2: Verify the CloudBank Initiator Annotations

Open `oracle-saga-cloudbank/CloudBank/orchestrator/src/main/java/com/oracle/saga/cloudbank/orchestrator/controller/CloudBankController.java`. Verify that the following annotation is already present above `CloudBankController`:

```java
@Participant(name = "CloudBank")
```

**`@Participant`** registers the initiator with the fixed `CloudBank` name.

![Participant Annotation](./images/lab4-task2-participant.png "@Participant annotation in CloudBank controller")

In the same class, review the supplied method:

```java
@SagaConnection
public static Connection getCloudBankConnection() throws SQLException {
    return ConnectionPools.getCloudBankConnection();
}
```

**`@SagaConnection`** marks the method that supplies the JDBC connection for saga operations.

![SagaConnection Annotation](./images/lab4-task2-sagaconnection.png "@SagaConnection annotation providing database connectivity")

Find this code in the `newBankAccount` and `transfer` methods:

```java
Saga saga = this.beginSaga();
var sagaId = saga.getSagaId();
```

**`beginSaga()`** starts the saga.

**`getSagaId()`** returns the identifier used for logging and correlation.

![beginSaga Method](./images/lab4-task2-beginsaga.png "beginSaga() method calls in CloudBank controller")

Find the requests sent from CloudBank:

```java
saga.sendRequest(Stubs.BANK_A, payload.toString());
saga.sendRequest(Stubs.BANK_B, payload.toString());
```

**`sendRequest()`** enrolls the participant and sends its JSON request.

**`Stubs.BANK_A` and `Stubs.BANK_B`** resolve to the fixed participant names `BankChicago` and `BankMex`.

![sendRequest Method](./images/lab4-task2-sendrequest.png "saga.sendRequest() calls in CloudBank controller")

### Step 3: Review Participant Requests and Responses

Open `oracle-saga-cloudbank/CloudBank/banka/src/main/java/com/oracle/saga/cloudbank/banka/controller/AccountsController.java`. Review the supplied BankChicago participant; no changes are required in this file:

```java
@Participant(name = "BankChicago")
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

**`@Request(sender = "CloudBank")`** accepts requests sent by the CloudBank initiator.

![BankChicago Annotations](./images/lab4-task2-banka.png "Annotation set in the BankChicago participant")

<details>
<summary><strong>📋 BankMex equivalent</strong></summary>

The supplied BankMex implementation is in `oracle-saga-cloudbank/CloudBank/bankb/src/main/java/com/oracle/saga/cloudbank/bankb/controller/AccountsController.java` and uses `@Participant(name = "BankMex")` with the same `@Request(sender = "CloudBank")` pattern. No changes are required in this file.

</details>

Return to `CloudBankController.java` and review the supplied response handlers:

```java
@oracle.saga.annotation.Response(sender = "BankChicago.*")
public void onResponseBankChicago(SagaMessageContext info) {
    handleResponse(info);
}

@oracle.saga.annotation.Response(sender = "BankMex.*")
public void onResponseBankMex(SagaMessageContext info) {
    handleResponse(info);
}
```

**`@Response`** routes each participant response to the matching handler.

![Response Annotations](./images/lab4-task2-response.png "@Response annotations for participant responses")

### Step 4: Verify Completion and Compensation

In the supplied `CloudBankController.java`, verify that `@Compensate` is present above `onPostRollback`:

```java
@Compensate
```

**`@Compensate`** marks the idempotent callback that runs when the saga rolls back.

![Compensate Annotation](./images/lab4-task2-compensate.png "@Compensate annotation for saga rollback handling")

Verify that `@Complete` is present above `onPostCommit`:

```java
@Complete
```

**`@Complete`** marks the idempotent callback that runs after the saga completes successfully.

![Complete Annotation](./images/lab4-task2-complete.png "@Complete annotation for successful saga completion")

Together, these are the three key orchestrator annotations reviewed in this lab:

- `@Participant(name = "CloudBank")` above `CloudBankController`
- `@Compensate` above `onPostRollback`
- `@Complete` above `onPostCommit`

The annotations and remaining implementation are supplied by the project. No edits or save operation are required.

> **✅ Review outcome:** The CloudBank initiator and bank participants already contain the required Saga annotations. No source changes are required.

Find the lifecycle calls in the controller:

```java
saga.commitSaga();
saga.rollbackSaga();
```

**`commitSaga()`** completes a successful saga and invokes `@Complete` callbacks.

**`rollbackSaga()`** compensates a failed saga and invokes `@Compensate` callbacks.

![Saga Lifecycle Methods](./images/lab4-task2-lifecycle.png "saga.commitSaga() and saga.rollbackSaga() method calls")

| Stage | CloudBank action | Participant action |
|---|---|---|
| Start | Calls `beginSaga()` | — |
| Request | Calls `sendRequest()` | `@Request` processes the payload. |
| Response | `@Response` evaluates each result. | Returns a JSON response. |
| Success | Calls `commitSaga()` | `@Complete` finalizes work. |
| Failure | Calls `rollbackSaga()` | `@Compensate` restores consistency. |

- `BankChicago` and `BankMex` must match in Lab 3 registration, `@Participant`, `sendRequest()`, and `@Response`.
- `beginSaga()` starts the flow; `sendRequest()` involves participants; responses determine the outcome.
- `@Request` processes work, `@Complete` finalizes it, and `@Compensate` reverses it safely.

<details>
<summary><strong>📖 CloudBank architecture and workflow reference</strong></summary>

CloudBank coordinates account creation and inter-bank transfers. Bank balances use Oracle `RESERVABLE` columns to support lock-free fund reservations while a saga is in progress.

![CloudBank Schema](./images/lab4-task3-1.png "CloudBank database schema and architecture")

![CloudBank Workflows](./images/lab4-task3-2.png "CloudBank saga workflows: account creation and money transfer")

</details>

**➡️ Next step: Continue to Lab 5**

After reviewing the supplied annotations and Saga lifecycle, continue to **Lab 5: Oracle Sagas in Action — The CloudBank Application** to build, start, and test the application.

## Learn More

- [Oracle Saga Client Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)
- [Java Annotations Guide](https://docs.oracle.com/javase/tutorial/java/annotations/)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney, Luis Cruz, Sebastian Gerritsen
* **Created By/Date** — Vinay Pandhariwal, August 2025
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
