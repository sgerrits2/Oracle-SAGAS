# Lab 4: Developing Oracle Sagas with the Java Client

## **Introduction**

In this lab, you will **develop and execute a Java-based saga client** that provides a parallel programming interface to the **DBMS_SAGA package in PL/SQL**. This lab takes you through hands-on experience with the **CloudBank demo application**, a Jersey-based Spring application that demonstrates distributed transaction management using Oracle Sagas.

### What You Will Do in This Lab

This lab follows a structured approach to understanding and implementing Oracle Sagas in Java:

1. **Configure Maven Dependencies**: Set up your project with the required Oracle Saga artifacts and repositories
2. **Add Saga Annotations**: Use the **code editor** to add Oracle's LRA and Saga annotations to **preexisting CloudBank application files**, specifically:
   - **One Saga Initiator** with `@LRA`, `@Complete`, `@Compensate` annotations
   - **One Saga Participant** with `@Participant`, `@Request`, `@Response` annotations
   - Verify all annotations are correctly added and ready for deployment
3. **Understand the CloudBank Demo Application**: Dive deep into the application structure, including:
   - **Schema & Architecture**: Database tables and service components
   - **Extra Demo Tables**: Additional tables created specifically for saga demonstration
   - **Saga Workflows**: Bank Account Creation and Money Transfer workflows

### About the CloudBank Demo Application

The **CloudBank demo application** is a Jersey-based Spring application that showcases distributed banking operations using Oracle Sagas. The application operates across one Oracle Database 23ai pluggable database (PDB) with multiple service components:

- **CloudBank Orchestrator**: Saga initiator for banking workflows
- **CloudBank Coordinator**: Database-resident saga orchestrator managing commits and rollbacks  
- **BankA & BankB Services**: Saga participants handling account operations and interbank transfers
- **Transaction Processing Services**: Handle deposits, transfers, and account management

> **⚠️ Important Note**: This is a **demo application** designed for learning purposes. The business logic may vary and might not be completely comprehensive or production-ready. The focus is on understanding saga patterns rather than complete banking business rules.

### Java Client and DBMS_SAGA Parallel

The **Oracle Saga Java Client** provides application-layer functionality that **parallels the DBMS_SAGA package in PL/SQL**:

- **Java Client Actions** ↔ **PL/SQL DBMS_SAGA Procedures**
- **LRA Annotations** ↔ **PL/SQL Saga Management**
- **Jersey RESTful Services** ↔ **Database-Resident Coordination**
- **Spring Framework Integration** ↔ **PL/SQL Package Integration**

Both approaches provide the same distributed transaction capabilities, but the Java client offers object-oriented, annotation-driven development for modern microservice architectures.

### Key Learning Objectives

1. **Maven Repository Configuration**: Understanding Oracle Saga dependencies and artifacts
2. **Annotation-Driven Development**: Adding `@LRA`, `@Complete`, `@Compensate`, `@Participant`, `@Request`, `@Response` to existing code
3. **Code Editor Proficiency**: Using development tools to modify and verify saga implementations
4. **CloudBank Architecture**: Understanding real-world banking saga patterns
5. **Saga Workflow Analysis**: Bank Account Creation and Money Transfer workflows
6. **Demo Application Patterns**: Learning from simplified but representative business logic

### Jersey-Based Spring Application

This CloudBank implementation uses:
- **Jersey Framework**: For RESTful web services and API endpoints
- **Spring Framework**: For dependency injection and application context management
- **Oracle Saga Integration**: Database-resident coordination with application-layer participation
- **Annotation-Based Configuration**: Declarative saga behavior without complex orchestration code

The goal of this lab is to gain hands-on experience with Oracle Saga Java development by working with real application code, understanding the CloudBank demo architecture, and mastering the annotation-based approach to distributed transaction management.

</br>

<details open>
<summary><mark>Key Characteristics of the Java Saga Client:</mark></summary>

- **Annotations simplify coding:** No manual saga orchestration logic is needed.  
- **Automatic compensation:** The client invokes cancel methods on failures.  
- **Integration with JDBC/TEQ:** Securely connects to the Saga Broker.  
- **Polyglot support:** Allows Java services to participate alongside PL/SQL participants.  

</details>

*Estimated Time: 45–60 minutes*


### Objectives

In this lab, you will:

- **Configure Maven dependencies** for Oracle Saga framework integration  
- **Add LRA and Saga annotations** to preexisting CloudBank application files using the code editor
- **Implement one Saga Initiator** with `@LRA`, `@Complete`, `@Compensate` annotations
- **Implement one Saga Participant** with `@Participant`, `@Request`, `@Response` annotations  
- **Verify annotation setup** and prepare application for deployment
- **Understand CloudBank demo application** architecture, schema, and saga workflows


### Prerequisites

- Completion of **Lab 3** (Broker, Coordinator, and Participants configured).  
- Java 11+ installed in your CloudShell or VM.  
- Maven or Gradle installed for dependency management.  


## Task 1: Maven Dependencies

---

In this task, you will understand the **Oracle Saga Maven dependency** that provides access to all the LRA and Saga annotations used in the CloudBank demo application.

### Step 1: Core Saga Dependency

The Oracle Saga framework requires only **one primary Maven dependency** to access all the annotations and client functionality:

```xml
<copy>
<dependency>
    <groupId>com.oracle.database.saga</groupId>
    <artifactId>saga-core</artifactId>
    <version>XX.X.X</version>
</dependency>
</copy>
```

**What this dependency provides:**
- **LRA Annotations**: `@LRA`, `@Complete`, `@Compensate` for saga lifecycle management
- **Saga Annotations**: `@Participant`, `@Request`, `@Response`, `@SagaConnection` for service coordination
- **Client Libraries**: Core classes for saga message context and database connectivity

### Step 2: CloudBank Project Structure

You can see the `saga-core` dependency included in the CloudBank demo application's Maven configuration files:

**Parent Directory:** `oracle-saga-cloudbank/Cloudbank/`

**Service-Specific POM Files:**
1. **BankA Service**: `oracle-saga-cloudbank/Cloudbank/banka/pom.xml`
2. **BankB Service**: `oracle-saga-cloudbank/Cloudbank/bankb/pom.xml`  
3. **Orchestrator Service**: `oracle-saga-cloudbank/Cloudbank/orchestrator/pom.xml`

Each of these `pom.xml` files contains the `saga-core` dependency, enabling that service to use Oracle Saga annotations for distributed transaction management.

### Step 3: Repository Information

**Maven Central Repository:** [https://mvnrepository.com/artifact/com.oracle.database.saga](https://mvnrepository.com/artifact/com.oracle.database.saga)

**Specific Artifact:** [https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core)

This single dependency is all you need to start developing with Oracle Sagas in Java applications. The setup and verification of the Maven environment will be covered when we configure Podman containers in the next lab.

**Screenshot Placeholder:**  

![Maven Dependencies](./images/lab4-task1-1.png "Oracle Saga core dependency in CloudBank project structure")


## Task 2: Understanding Annotations in CloudBank Files

---

In this task, you will use the **code editor** to examine Oracle's **LRA and Saga annotations** in preexisting CloudBank application files. The CloudBank demo application already contains most annotations, and you'll analyze two key files: the **Saga Initiator (Orchestrator)** and a **Saga Participant (BankA)**.

### Step 1: Oracle LRA and Saga Annotations Overview

The following annotations are available for saga development:

**LRA (Long Running Actions) Annotations:**
- `@LRA` - Controls saga initiation behavior (asynchronous model)
- `@Complete` - Method invoked when saga is committed  
- `@Compensate` - Method invoked when saga is rolled back

**Saga-Specific Annotations:**
- `@Participant` - Maps a class to a saga participant
- `@Request` - Marks methods receiving incoming requests from saga initiators
- `@Response` - Marks methods collecting responses from enrolled participants
- `@SagaConnection` - Fetches JDBC Connection for saga producers/consumers
- `@BeforeComplete` - Invoked during saga finalization before commit (optional)
- `@BeforeCompensate` - Invoked during saga finalization before rollback (optional)
- `@InviteToJoin` - Invoked when initiator requests participant join (optional)
- `@Reject` - Invoked when participant declines saga join (optional)

### Step 2: Understanding @Participant Annotation

Open the **CloudBank Orchestrator** file in your code editor:
**File Path:** `/Cloudbank/orchestrator/src/java/.../controller/CloudBankController.java`

#### @Participant(name = "CloudBank") - Line 66

Find this annotation at **Line 66**:

```java
//Add @Participant(name = "cloudBank") annotation here
public class CloudBankController extends SagaInitiator {
```

The `@Participant` annotation maps a class to a saga participant. This annotation:

- **Identifies the participant** within the saga framework
- **Enables message routing** to this specific participant
- **Supports sender filtering** for multi-participant scenarios
- **Must match the participant name** registered in Lab 3

**Key Parameters:**
- `name` - The unique identifier for this participant

**In CloudBank:** Replace `//Add @Participant(name = "cloudBank") annotation here` with the fixed participant name:

```java
<copy>
@Participant(name = "CloudBank")
</copy>
```

**Screenshot Placeholder:**  
![Participant Annotation](./images/lab4-task2-participant.png "@Participant annotation in CloudBank controller")

### Step 3: Understanding @SagaConnection Annotation

#### @SagaConnection - Line 89

Find this annotation at **Line 89**:

```java
@SagaConnection
public static Connection getCloudBankConnection() throws SQLException {
    return ConnectionPools.getCloudBankConnection();
}
```

The `@SagaConnection` annotation provides database connections for saga operations. This method:

- **Supplies JDBC connections** to the saga framework
- **Must be static** and return `java.sql.Connection`
- **Is called automatically** by the framework when database access is needed
- **Supports connection pooling** for performance
- **Can annotate any method** that returns `Connection` but **must be present inside a @Participant-annotated class**
- **Provides flexibility** to choose the connection type and configure data sources, pools, etc., as required

**Method Signature Requirements:**
```java
@SagaConnection
public static Connection methodName() throws SQLException {
    // Return database connection - can be from any datasource/pool
    return dataSource.getConnection();
    // or return ConnectionPools.getSpecificConnection();
    // or return customDataSource.getConnection();
}
```

**Flexibility Examples:**
```java
// Using specific connection pools
@SagaConnection
public static Connection getPooledConnection() throws SQLException {
    return ConnectionPools.getAccountsConnection();
}

// Using custom datasource configuration
@SagaConnection  
public static Connection getCustomConnection() throws SQLException {
    return customDataSourceManager.getConnection();
}
```

**Screenshot Placeholder:**  
![SagaConnection Annotation](./images/lab4-task2-sagaconnection.png "@SagaConnection annotation providing database connectivity")

### Step 4: Understanding beginSaga() Method

#### Saga saga = this.beginSaga() - Lines 382, 476

Find these method calls at **Lines 382 and 476**:

```java
// Line 382 - newBankAccount method
Saga saga = this.beginSaga();
var sagaId = saga.getSagaId();

// Line 476 - transfer method  
Saga saga = this.beginSaga();
var sagaId = saga.getSagaId();
```

The `beginSaga()` method initiates a new saga and returns a `Saga` object with a unique `sagaId`. This approach:

- **Starts a new distributed transaction**
- **Provides programmatic control** over saga lifecycle
- **Returns saga ID** for tracking and correlation
- **Is parallel to @LRA annotation** (declarative approach)

<details open>
<summary><mark>**Alternative Declarative Approach:**</mark></summary>

If using `@LRA` annotation instead, you would need:

1. **Add @LRA annotation:**
    ```java
   @LRA(end = false)
   @POST
   @Path("/transfer")
   public Response transfer(AccountTransferDTO payload) {
       // @LRA automatically manages saga context
   }
   ```

2. **Configure saga filter in main application class:**
    ```java
    <copy>
    @ServletComponentScan(basePackages = {"oracle.saga.filter", "com.oracle.saga.cloudbank.orchestrator.listener"})
    </copy>
    ```

3. **Add saga-filter Maven dependency:**
    ```xml
    <copy>
    <dependency>
        <groupId>com.oracle.database.saga</groupId>
        <artifactId>saga-filter</artifactId>
        <version>XX.X.X</version>
    </dependency>
    </copy>
    ```
</details>

**Note:** CloudBank uses a **programmatic approach** with `beginSaga()` for explicit control over the saga lifecycle.

**Screenshot Placeholder:**  
![beginSaga Method](./images/lab4-task2-beginsaga.png "beginSaga() method calls in CloudBank controller")

### Step 5: Understanding saga.sendRequest() Method

#### saga.sendRequest() - Lines 405, 407, 542, 544, etc.

Find these method calls at various lines in the CloudBank controller:

```java
// Line 405 - newBankAccount method
saga.sendRequest(Stubs.BANK_A, payload.toString());

// Line 407 - newBankAccount method  
saga.sendRequest(Stubs.BANK_B, payload.toString());

// Line 542 - transfer method
saga.sendRequest(Stubs.BANK_A, jsonObjectBuildertemp.build().toString());

// Line 544 - transfer method
saga.sendRequest(Stubs.BANK_B, jsonObjectBuildertemp.build().toString());
```

The `saga.sendRequest()` method sends requests to enrolled saga participants. This method:

- **Enrolls participants** in the current saga
- **Sends request payload** to the specified participant
- **Triggers participant's @Request method** for processing
- **Supports asynchronous processing** with response collection

**Method Signature:**
```java
saga.sendRequest(String participantName, String payload)
```

**Parameters:**
- `participantName` - Must match the participant name registered in Lab 3
- `payload` - JSON string containing request data

**Screenshot Placeholder:**  
![sendRequest Method](./images/lab4-task2-sendrequest.png "saga.sendRequest() calls in CloudBank controller")

### Step 6: Understanding @Compensate Annotation

#### @Compensate - Line 601

Find this annotation at **Line 601**:

```java
//Add @Compensate annotation here
public void onPostRollback(SagaMessageContext info) {
    // Rollback logic for failed sagas
    logger.debug("After Rollback from {} for {}", info.getSender(), info.getSagaId());
    
    CloudBankSagaInfo sagaInfo = cachedSagaInfo.get(info.getSagaId());
    if (sagaInfo != null) {
        if (sagaInfo.isNewBA()) {
            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.NEW_ACCOUNT);
        }
        if (sagaInfo.isAccTransfer()) {
            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.TRANSFER);
        }
    }
}
```

The `@Compensate` annotation marks methods for saga rollback processing. According to Oracle's documentation, this annotation:

- **Invoked automatically** when a saga fails or is rolled back during distributed transaction processing
- **Receives SagaMessageContext** with complete saga details including saga ID, sender information, and payload data
- **Handles cleanup operations** and state restoration to maintain data consistency
- **Must be idempotent** to handle retry scenarios and ensure reliable compensation
- **Supports complex rollback logic**, including database operations, cache cleanup, and external service notifications
- **Can access participant-specific information** through the context for targeted compensation
- **Executes in reverse order** of participant enrollment for proper dependency handling

**Implementation Details:**
The `@Compensate` method should reverse all operations performed during the `@Try` phase. This includes:
- Reversing database transactions and state changes
- Cleaning up allocated resources and reservations
- Sending notifications about transaction failures
- Updating audit logs and operational metrics

**Replace the comment** `//Add @Compensate annotation here` **with:**

```java
<copy>
@Compensate
</copy>
```

**Method Requirements:**
```java
@Compensate
public void compensationMethod(SagaMessageContext info) {
    // Access saga information
    String sagaId = info.getSagaId();
    String sender = info.getSender();
    String payload = info.getPayload();
    
    // Implement compensation logic
    // Reverse operations, clean up resources, etc.
}
```

**Screenshot Placeholder:**  
![Compensate Annotation](./images/lab4-task2-compensate.png "@Compensate annotation for saga rollback handling")

### Step 7: Understanding @Complete Annotation

#### @Complete - Line 733

Find this annotation at **Line 733**:

```java
//Add @Complete annotation here
public void onPostCommit(SagaMessageContext info) {
    logger.debug("After Commit from {} for {}", info.getSender(), info.getSagaId());
    // Finalization logic for successful sagas
}
```

The `@Complete` annotation marks methods for successful saga completion. According to Oracle's documentation, this annotation:

- **Invoked automatically** when a saga commits successfully after all participants complete their operations
- **Performs finalization tasks** including cleanup, notifications, and post-processing operations
- **Receives SagaMessageContext** with complete saga information and execution history
- **Should be idempotent** to ensure reliable completion even with retries
- **Executes after all participants confirm** successful operation completion
- **Can perform cleanup tasks** such as removing temporary data and clearing caches
- **Enables post-transaction processing** like sending success notifications and updating metrics
- **Supports audit logging** and compliance reporting for successful transactions

**Implementation Details:**
The `@Complete` method typically handles:
- Final cleanup of temporary resources and data
- Success notifications to external systems
- Audit logging and compliance reporting
- Performance metrics and operational statistics
- Cache cleanup and optimization tasks

**Replace the comment** `//Add @Complete annotation here` **with:**

```java
<copy>
@Complete
</copy>
```

**Method Requirements:**
```java
@Complete
public void completionMethod(SagaMessageContext info) {
    // Access saga completion information
    String sagaId = info.getSagaId();
    String sender = info.getSender();
    
    // Implement completion logic
    // Send notifications, clean up, log success, etc.
}
``` 

**Screenshot Placeholder:**  
![Complete Annotation](./images/lab4-task2-complete.png "@Complete annotation for successful saga completion")

### Step 8: Understanding @Response Annotations

#### @Response - Lines 785, 824

Find these annotations at **Lines 785 and 824**:

```java
//Add @oracle.saga.annotation.Response(sender = "BankA.*") annotation here
public void onResponseBankA(SagaMessageContext info) {
    // Handle responses from BankA
    handleResponse(info);
}

//Add @oracle.saga.annotation.Response(sender = "BankB.*") annotation here
public void onResponseBankB(SagaMessageContext info) {
    // Handle responses from BankB
    handleResponse(info);
}
```

The `@Response` annotation collects responses from enrolled saga participants. These methods:

- **Filter responses by sender** using regex patterns
- **Process participant responses** asynchronously
- **Enable response aggregation** across multiple participants
- **Support saga decision making** based on responses

**Key Parameters:**
- `sender` - Regex pattern to match participant names (e.g., "BankA.*")

**Replace the comment** `//Add @oracle.saga.annotation.Response(sender = "BankA.*") annotation here` **at line 785 with:**

```java
<copy>
@oracle.saga.annotation.Response(sender = "BankA.*")
</copy>
```

**Replace the comment** `//Add @oracle.saga.annotation.Response(sender = "BankB.*") annotation here` **at line 824 with:**

```java
<copy>
@oracle.saga.annotation.Response(sender = "BankB.*")
</copy>
``` 

**Screenshot Placeholder:**  
![Response Annotations](./images/lab4-task2-response.png "@Response annotations for collecting participant responses")

### Step 9: Understanding saga.commitSaga() and saga.rollbackSaga()

#### Saga Lifecycle Methods - Lines 910, 936, 887, 994, etc.

Find these critical method calls throughout the controller:

```java
// Line 910 - Commit saga after successful account creation
saga.commitSaga();

// Line 936 - Commit saga after successful transfer
saga.commitSaga();

// Line 887 - Rollback saga on failure
saga.rollbackSaga();

// Line 994 - Rollback saga on transfer error
saga.rollbackSaga();
```

**saga.commitSaga():**
- **Commits the distributed transaction**
- **Triggers @Complete methods** on all participants
- **Finalizes successful saga execution**
- **Should be called after all participants succeed**

**saga.rollbackSaga():**
- **Rolls back the distributed transaction**
- **Triggers @Compensate methods** on all participants
- **Handles saga failure scenarios**
- **Ensures system consistency through compensation**

These methods provide explicit control over saga lifecycle and are essential for proper distributed transaction management.

**Screenshot Placeholder:**  
![Saga Lifecycle Methods](./images/lab4-task2-lifecycle.png "saga.commitSaga() and saga.rollbackSaga() method calls")

### Step 10: Examine Saga Participant Annotations (BankA)

Open the **BankA Service** file in your code editor:
**File Path:** `/Cloudbank/banka/src/java/.../controller/AccountsController.java`

The BankA participant demonstrates key participant-side annotations:

#### @Participant(name = "BankA") - Line 70
```java
@Participant(name = "BankA")
public class AccountsController extends SagaParticipant {
```
**Purpose:** Maps this class to the "BankA" saga participant. The `name` attribute must match the participant name used in `saga.sendRequest()` calls and registered in Lab 3.

#### @SagaConnection - Line 93
```java
@SagaConnection
public static Connection getAccountsConnection() throws SQLException {
    return ConnectionPools.getAccountsConnection();
}
```
**Purpose:** Provides the database connection for BankA participant operations.

#### @Request(sender = "CloudBank") - Line 481
```java
@Request(sender = "CloudBank")
public String onRequest(SagaMessageContext info) {
    // Process different account operations based on payload
    String accountsAction = parseAccountsAction(info.getPayload());
    
    switch (accountsAction) {
        case "new_bank_account":
        case "deposit":
        case "withdraw":
        case "withdrawal_check":
        case "transact":
    }
    return status; // Return JSON response
}
```
**Purpose:** Receives incoming requests from the CloudBank initiator. The `sender` attribute filters requests to only process those from "CloudBank".

**Screenshot Placeholder:**  
![BankA Annotations](./images/lab4-task2-banka.png "Complete annotation set in BankA participant")


#### Key Takeaways
- **CloudBank uses a programmatic approach** with `beginSaga()` instead of declarative `@LRA`
- **Participant names must match** between `saga.sendRequest()` calls and `@Participant` annotations
- **All files contain complete annotation sets** for their respective roles
- **Replace stub constants** with actual participant names ("BankA", "BankB", "CloudBank")

**Screenshot Placeholder:**  
![Complete Analysis](./images/lab4-task2-summary.png "Complete Oracle Saga annotation analysis in CloudBank application")

</br>

<details>
<summary><mark>🔍 Complete Oracle Saga Annotations Reference Guide</mark></summary>

This comprehensive reference provides detailed information about all available Oracle Saga annotations, their parameters, usage patterns, and implementation requirements.

### Core LRA (Long Running Actions) Annotations

#### @LRA - Saga Lifecycle Control
**Purpose:** Controls saga initiation, propagation, and termination behavior in declarative style.

**Parameters:**
- `value` - LRA.Type enum: REQUIRED, REQUIRES\_NEW, MANDATORY, SUPPORTS, NOT\_SUPPORTED, NEVER
- `end` - Boolean: true (ends LRA after method), false (keeps LRA active)
- `cancelOn` - Exception classes that trigger compensation
- `cancelOnFamily` - HTTP response families that trigger compensation
- `timeLimit` - Duration for automatic timeout
- `timeUnit` - Time unit for timeout (ChronoUnit)

**Implementation Examples:**
```java
// Basic saga initiation
@LRA(value = LRA.Type.REQUIRED)
@POST
public Response createAccount(AccountRequest request) { ... }

// Saga with timeout and specific cancellation
@LRA(value = LRA.Type.REQUIRED, 
     timeLimit = 30, 
     timeUnit = ChronoUnit.SECONDS,
     cancelOn = {ValidationException.class, AccountException.class})
@POST
public Response processTransaction(TransactionRequest request) { ... }

// Nested saga with continuation
@LRA(value = LRA.Type.REQUIRES_NEW, end = false)
@POST
public Response initiateTransfer(TransferRequest request) { ... }
```

#### @Complete - Success Completion Handler
**Purpose:** Marks methods for execution when saga commits successfully.

**Parameters:**
- None (method signature determines invocation pattern)

**Method Signature Requirements:**
```java
// Basic completion handler
@Complete
public Response completeOperation() { ... }

// With LRA context
@Complete
public Response completeOperation(
    @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) { ... }

// With full context and response
@Complete  
public Response completeOperation(
    @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
    @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId,
    @HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) String endedId) { ... }
```

**Implementation Best Practices:**
- Must be idempotent (can be called multiple times safely)
- Should perform final cleanup and notifications
- Can access saga context through headers
- Should return appropriate HTTP status codes
- Can perform database cleanup operations

#### @Compensate - Failure Compensation Handler  
**Purpose:** Marks methods for execution when saga fails or is cancelled.

**Parameters:**
- None (method signature determines invocation pattern)

**Method Signature Requirements:**
```java
// Basic compensation handler
@Compensate
public Response compensateOperation() { ... }

// With LRA context
@Compensate
public Response compensateOperation(
    @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) { ... }

// With full compensation context
@Compensate
public Response compensateOperation(
    @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
    @HeaderParam(LRA_HTTP_RECOVERY_HEADER) String recoveryUrl) { ... }
```

**Compensation Patterns:**
```java
@Compensate
public Response compensateAccountCreation(
    @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) {
    
    try {
        // Reverse account creation
        accountService.deleteAccount(lraId);
        
        // Cancel related services  
        serviceSetupService.cancelAllServices(lraId);
        
        // Send failure notifications
        notificationService.sendAccountCreationFailure(lraId);
        
        // Audit compensation
        auditService.logCompensation(lraId, "ACCOUNT_CREATION", "SUCCESS");
        
        return Response.ok().build();
        
    } catch (Exception e) {
        auditService.logCompensation(lraId, "ACCOUNT_CREATION", "FAILED", e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}
```

### Saga-Specific Annotations

#### @Participant - Participant Registration
**Purpose:** Maps a class to a saga participant for message routing and identification.

**Parameters:**
- `name` - String: Unique participant identifier (required)

**Usage Patterns:**
```java
// Basic participant registration
@Participant(name = "AccountService")
public class AccountController extends SagaParticipant { ... }

// Multi-service participant with dynamic naming
@Participant(name = "PaymentProcessor-${instance.id}")
public class PaymentController extends SagaParticipant { ... }

// Cross-region participant
@Participant(name = "RegionA-BankingService")
public class RegionalBankingController extends SagaParticipant { ... }
```

**Integration Requirements:**
- Must extend `SagaParticipant` or implement equivalent interface
- Participant name must match names used in `saga.sendRequest()` calls
- Must have exactly one `@SagaConnection` annotated method
- Should include appropriate lifecycle annotations

#### @Request - Request Message Handler
**Purpose:** Marks methods that receive and process incoming saga requests.

**Parameters:**
- `sender` - String: Regex pattern to filter request senders

**Implementation Patterns:**
```java
// Basic request handler
@Request(sender = "OrderService")
public String processPayment(SagaMessageContext context) {
    String payload = context.getPayload();
    String sagaId = context.getSagaId();
    
    // Process payment logic
    PaymentResult result = paymentService.processPayment(payload, sagaId);
    
    return result.toJson();
}

// Multi-sender request handler with pattern matching
@Request(sender = ".*Service") // Matches any sender ending with "Service"
public String handleGenericRequest(SagaMessageContext context) {
    String sender = context.getSender();
    
    switch (sender) {
        case "OrderService":
            return handleOrderRequest(context);
        case "InventoryService":
            return handleInventoryRequest(context);
        default:
            return handleDefaultRequest(context);
    }
}

// Conditional request processing
@Request(sender = "PaymentGateway.*")
public String processGatewayRequest(SagaMessageContext context) {
    String gatewayType = extractGatewayType(context.getSender());
    
    if ("CreditCard".equals(gatewayType)) {
        return creditCardService.processPayment(context.getPayload());
    } else if ("BankTransfer".equals(gatewayType)) {
        return bankTransferService.processTransfer(context.getPayload());
    }
    
    return errorResponse("Unsupported gateway type: " + gatewayType);
}
```

#### @Response - Response Collection Handler
**Purpose:** Collects and processes responses from enrolled saga participants.

**Parameters:**
- `sender` - String: Regex pattern to filter response senders

**Advanced Response Handling:**
```java
// Basic response collection
@Response(sender = "PaymentService")
public void handlePaymentResponse(SagaMessageContext context) {
    String response = context.getPayload();
    PaymentResult result = PaymentResult.fromJson(response);
    
    if (result.isSuccessful()) {
        processSuccessfulPayment(context.getSagaId(), result);
    } else {
        handlePaymentFailure(context.getSagaId(), result);
    }
}

// Multi-participant response aggregation
@Response(sender = ".*ValidationService")
public void aggregateValidationResponses(SagaMessageContext context) {
    ValidationResponse response = ValidationResponse.fromJson(context.getPayload());
    String sagaId = context.getSagaId();
    
    // Store response for aggregation
    responseAggregator.addResponse(sagaId, context.getSender(), response);
    
    // Check if all responses received
    if (responseAggregator.isComplete(sagaId)) {
        AggregatedValidation result = responseAggregator.getResult(sagaId);
        processFinalValidation(sagaId, result);
    }
}

// Conditional saga control based on responses
@Response(sender = "RiskAssessment.*")
public void handleRiskAssessmentResponse(SagaMessageContext context) {
    RiskAssessment risk = RiskAssessment.fromJson(context.getPayload());
    Saga saga = getCurrentSaga(context.getSagaId());
    
    if (risk.getRiskLevel() > RiskLevel.HIGH) {
        // Auto-reject high-risk transactions
        saga.rollbackSaga();
        notificationService.sendRiskRejection(risk);
    } else if (risk.getRiskLevel() > RiskLevel.MEDIUM) {
        // Require additional verification
        saga.sendRequest("ManualReviewService", risk.toJson());
    } else {
        // Proceed with low-risk transaction
        proceedWithTransaction(context.getSagaId());
    }
}
```

#### @SagaConnection - Database Connection Provider
**Purpose:** Provides JDBC connections for saga operations and database access.

**Parameters:**
- None (method signature and return type determine behavior)

**Implementation Requirements:**
```java
// Basic connection provider
@SagaConnection
public static Connection getDatabaseConnection() throws SQLException {
    return DriverManager.getConnection(
        "jdbc:oracle:thin:@localhost:1521:XE", 
        "username", 
        "password"
    );
}

// Connection pool integration
@SagaConnection
public static Connection getPooledConnection() throws SQLException {
    return ConnectionPools.getConnection("saga-pool");
}

// Multi-datasource connection with selection logic
@SagaConnection
public static Connection getContextualConnection() throws SQLException {
    String currentTenant = TenantContext.getCurrentTenant();
    return TenantConnectionManager.getConnection(currentTenant);
}

// Connection with transaction management
@SagaConnection
public static Connection getTransactionalConnection() throws SQLException {
    Connection conn = dataSource.getConnection();
    conn.setAutoCommit(false); // Enable transaction control
    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    return conn;
}
```

### Extended Lifecycle Annotations

#### @BeforeComplete - Pre-Completion Processing
**Purpose:** Invoked during saga finalization phase, before final commit processing.

**Usage Scenarios:**
- Final validation checks
- Resource preparation for commit
- Audit trail preparation
- Cache warming for post-commit operations

```java
@BeforeComplete
public void prepareForCompletion(SagaMessageContext context) {
    String sagaId = context.getSagaId();
    
    // Perform final validation
    ValidationResult validation = finalValidationService.validate(sagaId);
    if (!validation.isValid()) {
        throw new ValidationException("Pre-commit validation failed: " + validation.getErrors());
    }
    
    // Prepare resources for completion
    resourceManager.prepareForCommit(sagaId);
    
    // Warm caches
    cacheService.warmCachesForCommit(sagaId);
}
```

#### @BeforeCompensate - Pre-Compensation Processing  
**Purpose:** Invoked during saga failure phase, before compensation processing begins.

**Usage Scenarios:**
- Failure analysis and categorization
- Resource cleanup preparation
- Error notification preparation
- Audit trail initialization

```java
@BeforeCompensate
public void prepareForCompensation(SagaMessageContext context) {
    String sagaId = context.getSagaId();
    
    // Analyze failure cause
    FailureAnalysis analysis = failureAnalyzer.analyze(sagaId);
    
    // Prepare cleanup resources
    cleanupService.prepareCompensationResources(sagaId, analysis);
    
    // Initialize audit trail
    auditService.initializeCompensationAudit(sagaId, analysis);
    
    // Prepare notifications
    notificationService.prepareFailureNotifications(sagaId, analysis);
}
```

#### @InviteToJoin - Participant Invitation Handler
**Purpose:** Invoked when saga initiator requests participant to join the saga.

**Usage Scenarios:**
- Dynamic participant enrollment
- Conditional saga participation  
- Resource availability checking
- Participation cost evaluation

```java
@InviteToJoin
public Response handleInvitation(SagaMessageContext context) {
    String sagaId = context.getSagaId();
    String payload = context.getPayload();
    
    try {
        // Parse invitation details
        InvitationRequest invitation = InvitationRequest.fromJson(payload);
        
        // Check resource availability
        if (!resourceManager.hasAvailableCapacity(invitation.getRequiredResources())) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                          .entity("Insufficient resources")
                          .build();
        }
        
        // Evaluate participation cost
        ParticipationCost cost = costCalculator.calculateCost(invitation);
        if (cost.exceedsBudget()) {
            return Response.status(Response.Status.PAYMENT_REQUIRED)
                          .entity("Cost exceeds budget")
                          .build();
        }
        
        // Accept invitation
        participationService.acceptInvitation(sagaId, invitation);
        return Response.ok("Invitation accepted").build();
        
    } catch (Exception e) {
        logger.error("Failed to process invitation for saga: " + sagaId, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}
```

#### @Reject - Participation Rejection Handler
**Purpose:** Invoked when participant declines to join the saga or participation fails.

**Usage Scenarios:**
- Resource cleanup after rejection
- Alternative participant suggestion
- Rejection reason logging
- Cascade rejection handling

```java
@Reject
public void handleRejection(SagaMessageContext context) {
    String sagaId = context.getSagaId();
    String rejectionReason = context.getPayload();
    
    // Log rejection
    auditService.logParticipationRejection(sagaId, context.getSender(), rejectionReason);
    
    // Clean up pre-allocated resources
    resourceManager.releaseReservedResources(sagaId);
    
    // Suggest alternative participants
    List<String> alternatives = participantRegistry.findAlternatives(
        context.getSender(), 
        context.getSagaType()
    );
    
    if (!alternatives.isEmpty()) {
        alternativeService.suggestAlternatives(sagaId, alternatives);
    }
    
    // Notify coordinator of rejection
    notificationService.notifyRejection(sagaId, context.getSender(), rejectionReason);
}
```

#### @AfterLRA - Post-Saga Completion Callback
**Purpose:** Called after a saga has completed to notify participants that the saga is complete.

**Parameters:**
- None (method signature determines invocation pattern)

**Method Signature Requirements:**
```java
@AfterLRA
public void callbackMethod(SagaMessageContext context, LRAStatus status)
```

**Parameters:**
- `context` - SagaMessageContext object containing saga information
- `status` - LRAStatus enum indicating final saga state (as defined by Eclipse MicroProfile)

**Usage Scenarios:**
- Post-completion cleanup and notifications
- Audit logging of saga completion
- Metrics collection and reporting
- Resource deallocation after saga finalization

**Implementation Examples:**
```java
// Basic AfterLRA callback
@AfterLRA
public void onSagaComplete(SagaMessageContext context, LRAStatus status) {
    String sagaId = context.getSagaId();
    logger.info("Saga {} completed with status: {}", sagaId, status);
    
    // Perform post-completion tasks
    switch (status) {
        case Closed:
            handleSuccessfulCompletion(context);
            break;
        case Cancelled:
            handleSagaCancellation(context);
            break;
        case FailedToClose:
        case FailedToCancel:
            handleSagaFailure(context, status);
            break;
        default:
            logger.warn("Unexpected saga status: {}", status);
    }
}

// Advanced AfterLRA with comprehensive handling
@AfterLRA
public void handleSagaCompletion(SagaMessageContext context, LRAStatus status) {
    String sagaId = context.getSagaId();
    String sender = context.getSender();
    
    try {
        // Log saga completion
        auditService.logSagaCompletion(sagaId, status, sender);
        
        // Update metrics
        metricsService.recordSagaCompletion(sagaId, status);
        
        // Handle status-specific logic
        if (status == LRAStatus.Closed) {
            // Successful completion
            cleanupSuccessfulSaga(sagaId);
            sendSuccessNotifications(context);
            releaseReservedResources(sagaId);
            
        } else if (status == LRAStatus.Cancelled) {
            // Saga was cancelled/compensated
            cleanupCancelledSaga(sagaId);
            sendCancellationNotifications(context);
            releaseReservedResources(sagaId);
            
        } else {
            // Failed states
            handleFailedSagaCompletion(context, status);
            alertService.sendSagaFailureAlert(sagaId, status);
        }
        
        // Common cleanup regardless of status
        removeTemporaryData(sagaId);
        updateSagaRegistry(sagaId, status);
        
    } catch (Exception e) {
        logger.error("Error in AfterLRA callback for saga: " + sagaId, e);
        // Don't rethrow - this is a callback method
    }
}

// Participant-level AfterLRA callback
@AfterLRA
public void participantSagaComplete(SagaMessageContext context, LRAStatus status) {
    String sagaId = context.getSagaId();
    
    // Participant-specific cleanup
    participantResourceManager.cleanupSagaResources(sagaId);
    
    // Update participant state
    participantStateService.markSagaComplete(sagaId, status);
    
    // Participant metrics
    participantMetrics.recordSagaParticipation(sagaId, status);
    
    logger.info("Participant completed saga {} with status: {}", sagaId, status);
}
```

**Important Notes:**
- **Initiator callback**: Invoked by default for saga initiators
- **Participant callback**: Only invoked when event 10855 is set at level 512
- **Invocation timing**: Called after all participants enter a final state
- **Error handling**: Should not throw exceptions as this is a notification callback
- **LRAStatus values**: Closed (success), Cancelled (compensated), FailedToClose, FailedToCancel
- **Final notification**: This is the last callback in the saga lifecycle

**Integration with Other Annotations:**
```java
@Participant(name = "OrderProcessor")
public class OrderController extends SagaParticipant {
    
    @Request(sender = "OrderOrchestrator")
    public String processOrder(SagaMessageContext context) {
        // Process order request
        return orderResult.toJson();
    }
    
    @Complete
    public void completeOrder(SagaMessageContext context) {
        // Handle successful order completion
        finalizeOrderProcessing(context.getSagaId());
    }
    
    @Compensate  
    public void compensateOrder(SagaMessageContext context) {
        // Handle order compensation
        reverseOrderProcessing(context.getSagaId());
    }
    
    @AfterLRA
    public void afterOrderSaga(SagaMessageContext context, LRAStatus status) {
        // Final cleanup after saga completion
        cleanupOrderResources(context.getSagaId());
        updateOrderMetrics(context.getSagaId(), status);
        
        // Send final notifications to external systems
        externalNotificationService.notifySagaComplete(context.getSagaId(), status);
    }
}
```

### Annotation Combination Patterns

#### Complete Saga Initiator Pattern
```java
@Participant(name = "OrderOrchestrator")
public class OrderController extends SagaInitiator {
    
    @SagaConnection
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @LRA(value = LRA.Type.REQUIRED, timeLimit = 300, timeUnit = ChronoUnit.SECONDS)
    @POST
    @Path("/orders")
    public Response createOrder(OrderRequest request) {
        // Initiate order saga
        return processOrder(request);
    }
    
    @Response(sender = "InventoryService")
    public void handleInventoryResponse(SagaMessageContext context) {
        // Handle inventory allocation response
    }
    
    @Response(sender = "PaymentService")
    public void handlePaymentResponse(SagaMessageContext context) {
        // Handle payment processing response
    }
    
    @Complete
    public Response completeOrder(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) {
        // Finalize successful order
        return Response.ok().build();
    }
    
    @Compensate
    public Response compensateOrder(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) {
        // Handle order failure compensation
        return Response.ok().build();
    }
}
```

#### Complete Saga Participant Pattern
```java
@Participant(name = "PaymentProcessor")
public class PaymentController extends SagaParticipant {
    
    @SagaConnection
    public static Connection getPaymentConnection() throws SQLException {
        return paymentDataSource.getConnection();
    }
    
    @Request(sender = "OrderOrchestrator")
    public String processPayment(SagaMessageContext context) {
        // Process payment request
        return processPaymentLogic(context);
    }
    
    @BeforeComplete
    public void preparePaymentCompletion(SagaMessageContext context) {
        // Prepare for payment confirmation
    }
    
    @Complete
    public void completePayment(SagaMessageContext context) {
        // Confirm payment success
    }
    
    @BeforeCompensate
    public void preparePaymentCompensation(SagaMessageContext context) {
        // Prepare for payment reversal
    }
    
    @Compensate
    public void compensatePayment(SagaMessageContext context) {
        // Reverse payment transaction
    }
    
    @InviteToJoin
    public Response handlePaymentInvitation(SagaMessageContext context) {
        // Handle dynamic participation request
        return Response.ok().build();
    }
}
```

This comprehensive guide covers all Oracle Saga annotations with practical implementation patterns and real-world usage scenarios. Each annotation serves a specific purpose in the distributed transaction lifecycle and can be combined to create robust saga implementations.

</details>

</br>

<details>
<summary><mark>⚙️ Complete Oracle Saga Methods Reference Guide</mark></summary>

This comprehensive reference provides detailed information about all Oracle Saga methods, their usage patterns, parameters, and implementation requirements for both initiators and participants.

> **⚠️ Important:** This guide covers Oracle Sagas Version 1. **Nested saga patterns are NOT supported** in the current version.

### Saga Interface Methods

The Saga interface provides the means to participate in, complete, and request metadata for a database Saga.

#### sendRequest() Methods - Participant Enrollment

<details>
<summary><mark>sendRequest(String recipient, String payload)</mark></summary>

**Purpose:** Enrolls participants in the saga and sends request payloads for processing.

**Method Signature:**
```java
public void sendRequest(String recipient, String payload)
```

**Parameters:**
- `recipient` - Name of the participant to enroll (must match the `@Participant` annotation)
- `payload` - Saga payload data

**Usage Examples:**
```java
// Basic participant enrollment
saga.sendRequest("InventoryService", jsonPayload);

// Multiple participant enrollment
saga.sendRequest("BankA", bankAPayload);
saga.sendRequest("BankB", bankBPayload);
```

**Important Notes:**
- Automatically commits the transaction by default when used outside Saga-annotated methods
- If participant joins the saga, initiator invokes `@Response` method
- If participant rejects, initiator invokes `@Reject` method

</details>

<details>
<summary><mark>sendRequest(AQjmsSession session, TopicPublisher publisher, String recipient, String payload) - DEPRECATED</mark></summary>

**Status:** ⚠️ **DEPRECATED** - Use `sendRequest(java.sql.Connection, String, String, String)` instead

**Method Signature:**
```java
public void sendRequest(AQjmsSession session, TopicPublisher publisher, String recipient, String payload)
```

**Usage:** Multiple sendRequest() calls can be made in a single database transaction with identical session and publisher parameters.

</details>

<details>
<summary><mark>sendRequest(java.sql.Connection connection, String publisher, String recipient, String payload)</mark></summary>

**Purpose:** Fine-grained transaction control for participant enrollment.

**Method Signature:**
```java
public void sendRequest(java.sql.Connection connection, String publisher, String recipient, String payload)
```

**Parameters:**
- `connection` - User-supplied JDBC connection for transaction control
- `publisher` - Topic publisher name
- `recipient` - Name of the participant to enroll
- `payload` - Saga payload data

**Usage Examples:**
```java
// Transaction-controlled enrollment
Connection conn = dataSource.getConnection();
saga.sendRequest(conn, "OrderPublisher", "InventoryService", payload);
saga.sendRequest(conn, "OrderPublisher", "PaymentService", paymentPayload);
conn.commit(); // Commit all enrollments together
```

**Transaction Control:**
- Multiple sendRequest() calls can use the same connection for atomic enrollment
- Other DML operations can be part of the same transaction
- Use `connection.commit()` or `connection.rollback()` for explicit transaction control

</details>

#### getSagaId() Method - Saga Identifier Access

**Purpose:** Returns the saga identifier associated with the saga object.

**Method Signature:**
```java
public String getSagaId()
```

**Usage Examples:**
```java
Saga saga = this.beginSaga();
String sagaId = saga.getSagaId();
logger.info("Started saga with ID: {}", sagaId);
```

#### commitSaga() Methods - Success Completion

<details>
<summary><mark>commitSaga()</mark></summary>

**Purpose:** Commits the saga, triggering `@BeforeComplete` and `@Complete` methods.

**Method Signature:**
```java
public void commitSaga()
```

**Usage Examples:**
```java
// Basic saga commit
saga.commitSaga();
```

**Execution Flow:**
1. `@BeforeComplete` methods invoked at initiator and participant levels
2. Reservable column operations finalized
3. `@Complete` methods invoked at initiator and participant levels

**Important:** Auto-commits the transaction by default when used outside Saga-annotated methods.

</details>

<details>
<summary><mark>commitSaga(AQjmsSession session) - DEPRECATED</mark></summary>

**Status:** ⚠️ **DEPRECATED** - Use `commitSaga(java.sql.Connection)` instead

**Method Signature:**
```java
public void commitSaga(AQjmsSession session)
```

</details>

<details>
<summary><mark>commitSaga(java.sql.Connection connection)</mark></summary>

**Purpose:** Fine-grained transaction control for saga commit.

**Method Signature:**
```java
public void commitSaga(java.sql.Connection connection)
```

**Usage Examples:**
```java
Connection conn = dataSource.getConnection();
// Perform additional DML operations
updateOrderStatus(conn, orderId, "PROCESSING");
saga.commitSaga(conn);
conn.commit(); // Commit saga and DML together
```

</details>

<details>
<summary><mark>commitSaga(boolean force)</mark></summary>

**Purpose:** Forces the saga to commit locally without waiting for initiator finalization.

**Method Signature:**
```java
public void commitSaga(boolean force)
```

**Usage Examples:**
```java
// Force commit in special situations
saga.commitSaga(true);
```

**Use Case:** Can be used by saga participants in special situations to commit locally and inform the coordinator.

</details>

<details>
<summary><mark>commitSaga(java.sql.Connection connection, boolean force)</mark></summary>

**Purpose:** Combines transaction control with force commit functionality.

**Method Signature:**
```java
public void commitSaga(java.sql.Connection connection, boolean force)
```

**Important:** Can only be used outside the scope of Saga-annotated methods.

</details>

#### rollbackSaga() Methods - Failure Compensation

<details>
<summary><mark>rollbackSaga()</mark></summary>

**Purpose:** Rolls back the saga, triggering `@BeforeCompensate` and `@Compensate` methods.

**Method Signature:**
```java
public void rollbackSaga()
```

**Usage Examples:**
```java
// Basic saga rollback
try {
    processPayment(order);
} catch (PaymentException e) {
    saga.rollbackSaga();
    throw e;
}
```

**Execution Flow:**
1. `@BeforeCompensate` methods invoked at initiator and participant levels
2. Reservable column operations finalized
3. `@Compensate` methods invoked at initiator and participant levels

</details>

<details>
<summary><mark>rollbackSaga(AQjmsSession session) - DEPRECATED</mark></summary>

**Status:** ⚠️ **DEPRECATED** - Use `rollbackSaga(java.sql.Connection)` instead

**Method Signature:**
```java
public void rollbackSaga(AQjmsSession session)
```

</details>

<details>
<summary><mark>rollbackSaga(java.sql.Connection connection)</mark></summary>

**Purpose:** Fine-grained transaction control for saga rollback.

**Method Signature:**
```java
public void rollbackSaga(java.sql.Connection connection)
```

**Usage Examples:**
```java
Connection conn = dataSource.getConnection();
try {
    // Attempt operations
    processOrder(conn, order);
    saga.commitSaga(conn);
    conn.commit();
} catch (Exception e) {
    saga.rollbackSaga(conn);
    conn.rollback();
}
```

</details>

<details>
<summary><mark>rollbackSaga(boolean force)</mark></summary>

**Purpose:** Forces the saga to roll back locally without waiting for initiator finalization.

**Method Signature:**
```java
public void rollbackSaga(boolean force)
```

**Use Case:** Can be used by saga participants in special situations to roll back locally and inform the coordinator.

</details>

<details>
<summary><mark>rollbackSaga(java.sql.Connection connection, boolean force)</mark></summary>

**Purpose:** Combines transaction control with force rollback functionality.

**Method Signature:**
```java
public void rollbackSaga(java.sql.Connection connection, boolean force)
```

**Important:** Can only be used outside the scope of Saga-annotated methods.

</details>

#### Additional Saga Interface Methods

<details>
<summary><mark>isSagaFinalized()</mark></summary>

**Purpose:** Checks whether the saga has reached one of the finalization states.

**Method Signature:**
```java
public boolean isSagaFinalized()
```

**Return Values:**
- `false` - If the saga is in JOINING, JOINED, or TIMEDOUT state
- `true` - Otherwise (finalized states)

**Usage Examples:**
```java
if (!saga.isSagaFinalized()) {
    // Saga still in progress
    processNextStep();
} else {
    // Saga completed
    cleanupResources();
}
```

</details>

<details>
<summary><mark>beginSagaTransaction() / endSagaTransaction()</mark></summary>

**Purpose:** Starts/ends saga transactions outside Saga-annotated methods (e.g., under `@LRA`).

**Method Signatures:**
```java
// Deprecated
public void beginSagaTransaction(AQjmsSession session, TopicPublisher publisher)

// Current
public void beginSagaTransaction(java.sql.Connection connection, String publisher)
public void endSagaTransaction()
```

**Usage Examples:**
```java
@LRA
public Response processOrder(OrderRequest request) {
    Connection conn = dataSource.getConnection();
    saga.beginSagaTransaction(conn, "OrderPublisher");
    
    try {
        // Perform operations
        saga.sendRequest(conn, "OrderPublisher", "InventoryService", payload);
        conn.commit();
    } finally {
        saga.endSagaTransaction();
    }
}
```

**Important Notes:**
- Can only be invoked at initiator level
- Requires corresponding `endSagaTransaction()` call
- Serialized with other saga methods like `commitSaga()` or `rollbackSaga()`

</details>

### SagaInitiator Class Methods

The SagaInitiator class inherits all SagaParticipant methods and adds initiator-specific functionality.

#### beginSaga() Methods - Saga Initiation

<details>
<summary><mark>beginSaga()</mark></summary>

**Purpose:** Starts a saga and returns a saga identifier.

**Method Signature:**
```java
public Saga beginSaga()
```

**Usage Examples:**
```java
Saga saga = this.beginSaga();
String sagaId = saga.getSagaId();
```

**Default Behavior:** Starts saga with default timeout of 84600 seconds.

</details>

<details>
<summary><mark>beginSaga(int timeout)</mark></summary>

**Purpose:** Starts a saga with user-specified timeout.

**Method Signature:**
```java
public Saga beginSaga(int timeout)
```

**Parameters:**
- `timeout` - Saga timeout in seconds

**Usage Examples:**
```java
// 5-minute timeout for payment processing
Saga paymentSaga = this.beginSaga(300);

// 1-minute timeout for high-priority orders
Saga urgentSaga = this.beginSaga(60);
```

**Timeout Behavior:** If saga is not finalized before timeout, it automatically finalizes based on `_saga_timeout_operation_type` parameter (default=rollback).

</details>

#### Publisher Management Methods

<details>
<summary><mark>Publisher Thread Management</mark></summary>

**Methods for managing saga message publisher threads:**

```java
// Add publishers
public void addSagaMessagePublishers(int numPublishers)

// Remove publishers
public void removeSagaMessagePublishers(int numPublishers)
public void removeAllSagaMessagePublishers()

// Query publisher count
public int getSagaMessagePublisherCount()

// Get topic publisher
public TopicPublisher getSagaOutTopicPublisher(AQjmsSession session, int partition)
```

**Usage Examples:**
```java
// Scale up for high load
sagaInitiator.addSagaMessagePublishers(5);

// Scale down for low load
sagaInitiator.removeSagaMessagePublishers(3);

// Check current capacity
int currentPublishers = sagaInitiator.getSagaMessagePublisherCount();
```

</details>

### SagaParticipant Class Methods

The SagaParticipant class provides the means to create and manage a saga participant instance.

<details>
<summary><mark>Message Listener Management</mark></summary>

**Methods for managing saga message listener threads:**

```java
// Add listeners
public void addSagaMessageListener()
public void addSagaMessageListener(int numListeners)

// Remove listeners  
public void removeSagaMessageListener()
public void removeSagaMessageListener(int numListeners)
public void removeAllSagaMessageListeners()

// Query listener count
public int getSagaMessageListenerCount()

// Get saga by ID
public Saga getSaga(String sagaId)

// Close participant
public void close()
```

**Usage Examples:**
```java
// Scale up for high load
sagaParticipant.addSagaMessageListener(5);

// Scale down for low load  
sagaParticipant.removeSagaMessageListener(3);

// Check current capacity
int currentListeners = sagaParticipant.getSagaMessageListenerCount();

// Get specific saga
Saga targetSaga = sagaParticipant.getSaga("saga-12345");

// Shutdown participant
sagaParticipant.close();
```

**Important Notes:**
- `getSagaMessageListenerCount()` returns threads for single partition in AQ setup
- Total threads = `getSagaMessageListenerCount() * numPartitions`
- Number of partitions can be queried using `DBA_SAGA_PARTICIPANTS` view
- `close()` retains pending messages for future consumption

</details>

### SagaMessageContext Class Methods  

The SagaMessageContext object is passed to methods annotated with Saga annotations upon Saga-related events.

<details>
<summary><mark>Context Access Methods</mark></summary>

**All available SagaMessageContext methods:**

```java
// Get saga identifier
public String getSagaId()

// Get message sender name  
public String getSender()

// Get message payload
public String getPayload()

// Get database connection
public java.sql.Connection getConnection()

// Get associated saga object
public Saga getSaga()
```

**Method Descriptions:**

**getSagaId()** - Returns the identifier of the saga
```java
@Request(sender = "OrderService")
public String processOrder(SagaMessageContext context) {
    String sagaId = context.getSagaId();
    logger.info("Processing order for saga: {}", sagaId);
    return processOrderLogic(sagaId);
}
```

**getSender()** - Returns the name of the message sender
```java
@Response(sender = ".*ValidationService") 
public void handleResponse(SagaMessageContext context) {
    String sender = context.getSender();
    if (sender.equals("CreditValidationService")) {
        handleCreditResponse(context);
    }
}
```

**getPayload()** - Returns the payload associated with the saga message
```java
@Request(sender = "PaymentGateway")
public String processPayment(SagaMessageContext context) {
    String payload = context.getPayload();
    PaymentRequest request = PaymentRequest.fromJson(payload);
    return processPayment(request);
}
```

**getConnection()** - Returns database connection of the saga message listener thread
```java
@Request(sender = "InventoryService")
public String checkInventory(SagaMessageContext context) {
    Connection conn = context.getConnection();
    // Use connection for database operations
    // Note: Explicit commit()/rollback() not supported
    return checkInventoryAvailability(conn, context.getPayload());
}
```

**getSaga()** - Returns the Saga object associated with the current saga
```java
@Request(sender = "OrderOrchestrator")
public String processRequest(SagaMessageContext context) {
    Saga saga = context.getSaga();
    String sagaId = saga.getSagaId();
    
    // Can use saga object for finalization or metadata requests
    if (someCondition) {
        saga.commitSaga();
    }
    
    return processRequestLogic(context.getPayload());
}
```

**Important Notes:**
- Connections from `getConnection()` cannot use explicit `commit()` or `rollback()`
- Can be used at initiator or participant level
- `getSaga()` allows access to saga finalization methods
- All methods provide access to current saga context

</details>

This comprehensive methods guide covers the Oracle Saga API methods with their actual signatures and usage patterns as documented in Oracle Database 23ai. These methods provide the foundation for building distributed transaction systems using Oracle Sagas Version 1.

</details>

---

## Task 3: Understand CloudBank Demo Application

---

> **⚠️ Disclaimer:** This is a **demo application** designed for learning purposes. The business logic might feel somewhat vague or simplified compared to production banking systems. The focus is on understanding saga patterns and compensation workflows rather than comprehensive banking business rules.

In this task, you will explore the **CloudBank demo application**, a sample banking system that demonstrates real-world saga patterns using the Oracle Saga framework. This application showcases how distributed transactions work across multiple microservices with proper compensation handling.

### Step 1: Schema & Architecture

#### Deployment Architecture Overview
The CloudBank application is deployed within a **single Oracle Database 23ai PDB** but uses **separate database schemas** for each microservice component. This demonstrates how saga participants can be logically separated while sharing the same database infrastructure.

**Single PDB, Multi-Schema Deployment:**

**📹 CloudBank Architecture Demo:**

[![CloudBank Architecture Screenshot](https://img.shields.io/badge/🏛️%20CloudBank-Architecture%20Demo-blue?style=for-the-badge&logo=database&logoColor=white)](images/Arch.mp4)

*Click above to download and view the CloudBank database schema and microservice architecture demonstration.*

> **📋 Reference**: For detailed deployment information, refer to **Lab 2** which covers the specific setup of brokers, coordinators, and participants within the database environment.

#### Database Schema Details

**Orchestrator Schema Tables:**

```sql
-- Customer master data table
CREATE TABLE cloudbank_customer (
  customer_id VARCHAR2(50) UNIQUE,
  password VARCHAR2(50),
  full_name VARCHAR2(100),
  address VARCHAR2(255),
  phone VARCHAR2(20),
  email VARCHAR2(100) UNIQUE,
  ossn VARCHAR2(10) NOT NULL,
  bank VARCHAR2(10) NOT NULL CHECK (bank IN ('BankA', 'BankB')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP,
  PRIMARY KEY(email,ossn)
);

-- Saga operation audit and status tracking
CREATE TABLE cloudbank_book (
  log_id NUMBER PRIMARY KEY,
  saga_id VARCHAR2(50),
  ucid VARCHAR2(50) REFERENCES cloudbank_customer(customer_id),
  operationType VARCHAR2(18) CHECK (operationType IN ('VIEW', 'TRANSFER', 'NEW_ACCOUNT','NEW_CREDIT_CARD', 'WITHDRAWAL_CHECK')),
  transfer_type VARCHAR2(20) CHECK (transfer_type IN ('INTER-BANK', 'INTRA-BANK', 'null')),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10) DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);
```

**BankA Schema Tables:**

```sql
-- BankA account and balance management
CREATE TABLE bankA (
  ucid VARCHAR2(50),
  account_number NUMBER(20) PRIMARY KEY,
  account_type VARCHAR2(15) CHECK (account_type IN ('CHECKING', 'SAVING')),
  balance_amount DECIMAL(10,2) RESERVABLE CONSTRAINT balance_conA CHECK(balance_amount >= 0),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- BankA saga operations tracking
CREATE TABLE bankA_book (
  log_id NUMBER PRIMARY KEY,
  saga_id VARCHAR2(100),
  ucid VARCHAR2(50),
  operationType VARCHAR2(30) CHECK (operationType IN ('VIEW_BALANCE_BA', 'WITHDRAW', 'DEPOSIT', 'NEW_BANK_ACCOUNT', 'TRANSACT', 'WITHDRAWAL_CHECK')),
  transactionType VARCHAR2(10) CHECK (transactionType IN ('CREDIT', 'DEBIT','null')),
  transaction_amount DECIMAL(10,2),
  account_number VARCHAR2(100),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10) DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);
```

**BankB Schema Tables:**

```sql
-- BankB account and balance management  
CREATE TABLE bankB (
  ucid VARCHAR2(50),
  account_number NUMBER(20) PRIMARY KEY,
  account_type VARCHAR2(15) CHECK (account_type IN ('CHECKING', 'SAVING')),
  balance_amount DECIMAL(10,2) RESERVABLE CONSTRAINT balance_conB CHECK(balance_amount >= 0),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- BankB saga operations tracking
CREATE TABLE bankB_book (
  log_id NUMBER PRIMARY KEY,
  saga_id VARCHAR2(100),
  ucid VARCHAR2(50),
  operationType VARCHAR2(30) CHECK (operationType IN ('VIEW_BALANCE_BA', 'WITHDRAW', 'DEPOSIT', 'NEW_BANK_ACCOUNT', 'TRANSACT', 'WITHDRAWAL_CHECK')),
  transactionType VARCHAR2(10) CHECK (transactionType IN ('CREDIT', 'DEBIT','null')),
  transaction_amount DECIMAL(10,2),
  account_number VARCHAR2(100),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10) DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);
```

#### Key Architectural Features

**🔹 Reservable Columns:**
Both `bankA.balance_amount` and `bankB.balance_amount` use Oracle's **RESERVABLE** column feature, enabling lock-free fund reservations for distributed transactions.

**🔹 SpringBoot-Jersey Applications:**
- **Orchestrator Service**: Saga initiator handling customer requests and coordinating transactions
- **BankA Service**: Saga participant managing BankA account operations  
- **BankB Service**: Saga participant managing BankB account operations
- All services expose **REST APIs** for transaction processing.

**🔹 Schema Separation:**
- **Orchestrator + Coordinator**: Share the same schema for centralized coordination
- **BankA Participant**: Dedicated schema for BankA operations
- **BankB Participant**: Dedicated schema for BankB operations  
- **Broker**: System schema managing saga message routing

#### Demo Login Credentials

The CloudBank application includes **pre-configured demo accounts** for testing purposes:

| USERNAME  | PASSWORD | BANK   | OSSN    | NAME       | ACCOUNT TYPE |
|-----------|----------|--------|---------|------------|-------------|
| ORACLE001 | cb1      | BankA  | OSSN001 | CUSTOMER 1 | CHECKING    |
| ORACLE002 | cb2      | BankB  | OSSN002 | CUSTOMER 2 | SAVING      |
| ORACLE003 | cb3      | BankB  | OSSN003 | CUSTOMER 3 | CHECKING    |
| ORACLE004 | cb4      | BankA  | OSSN004 | CUSTOMER 4 | SAVING      |

**Account Details:**
- **Initial Balance**: Each account starts with $2,000.00
- **Account Numbers**: Auto-generated using sequences (BankA: 1234560001+, BankB: 1234560301+)
- **Usage**: These credentials can be used for frontend login in subsequent labs


### Step 2: Saga Workflows

The CloudBank application demonstrates distributed transaction management through real banking workflows. These workflows showcase both successful execution and failure compensation scenarios using Oracle Saga annotations and programmatic control.

#### Workflow 1: New Bank Account Creation

The CloudBank application supports **new bank account creation** through a **multi-participant saga** that coordinates between the orchestrator and the appropriate bank participant.

**Saga Flow Overview:**
1. **Orchestrator** receives account creation request
2. **Orchestrator** determines target bank (BankA or BankB) 
3. **Orchestrator** sends account creation request to participant
4. **Bank Participant** creates account with initial balance
5. **Orchestrator** logs successful completion or handles compensation

**Implementation Details:**

```java
// Orchestrator - Saga Initiator
@POST  
@Path("/newBankAccount")
public Response newBankAccount(NewBankAccountDTO payload) {
    Saga saga = this.beginSaga();
    String sagaId = saga.getSagaId();
    
    // Log operation start
    logBookUpdateCloudBank(payload, "PENDING", "NEW_ACCOUNT");
    
    // Determine target bank and send request
    if ("BankA".equals(payload.getBank())) {
        saga.sendRequest("BankA", payload.toString());
    } else if ("BankB".equals(payload.getBank())) {
        saga.sendRequest("BankB", payload.toString());
    }
    
    return Response.ok("Account creation initiated").build();
}

@Compensate
public void onPostRollback(SagaMessageContext info) {
    // Handle account creation failure
    logger.debug("Account creation failed for saga: {}", info.getSagaId());
    logBookUpdateCloudBank(info, "FAILED", "NEW_ACCOUNT");
}

@Complete
public void onPostCommit(SagaMessageContext info) {
    // Handle successful account creation
    logger.debug("Account creation completed for saga: {}", info.getSagaId());
    logBookUpdateCloudBank(info, "COMPLETED", "NEW_ACCOUNT");
}
```

```java
// Bank Participant - Account Creation Handler
@Request(sender = "CloudBank")
public String onRequest(SagaMessageContext info) {
    String payload = info.getPayload();
    String accountsAction = parseAccountsAction(payload);
    
    if ("new_bank_account".equals(accountsAction)) {
        return processNewBankAccount(info);
    }
    // Handle other operations...
}

private String processNewBankAccount(SagaMessageContext info) {
    try {
        // Parse request data
        NewBankAccountRequest request = parseRequest(info.getPayload());
        
        // Create new account record
        String accountNumber = generateAccountNumber();
        insertBankAccount(request.getUcid(), accountNumber, 
                         request.getAccountType(), request.getInitialBalance());
        
        // Log successful creation
        logBankOperation(info.getSagaId(), "NEW_BANK_ACCOUNT", "COMPLETED");
        
        return createSuccessResponse(accountNumber);
        
    } catch (Exception e) {
        logger.error("Failed to create account", e);
        logBankOperation(info.getSagaId(), "NEW_BANK_ACCOUNT", "FAILED");
        return createErrorResponse(e.getMessage());
    }
}
```

#### Workflow 2: Inter-Bank Money Transfer

The CloudBank application's **money transfer saga** demonstrates complex multi-participant coordination using Oracle's **RESERVABLE columns** for lock-free fund management.

**Saga Flow Overview:**
1. **Orchestrator** receives transfer request
2. **Orchestrator** validates transfer details and participants
3. **Source Bank** reserves funds using RESERVABLE column
4. **Target Bank** prepares to receive funds
5. **Both participants** complete or compensate based on saga outcome

**Implementation Details:**

```java
// Orchestrator - Money Transfer Initiator
@POST
@Path("/transfer")  
public Response transfer(AccountTransferDTO payload) {
    Saga saga = this.beginSaga();
    String sagaId = saga.getSagaId();
    
    // Log transfer initiation
    logBookUpdateCloudBank(payload, "PENDING", "TRANSFER");
    
    // Build requests for both banks
    JsonObjectBuilder sourceRequest = Json.createObjectBuilder()
        .add("accounts_action", "withdraw")
        .add("ucid", payload.getFromAccount())
        .add("amount", payload.getAmount())
        .add("saga_id", sagaId);
        
    JsonObjectBuilder targetRequest = Json.createObjectBuilder()
        .add("accounts_action", "deposit") 
        .add("ucid", payload.getToAccount())
        .add("amount", payload.getAmount())
        .add("saga_id", sagaId);
    
    // Send requests to both banks
    saga.sendRequest("BankA", sourceRequest.build().toString());
    saga.sendRequest("BankB", targetRequest.build().toString());
    
    return Response.ok("Transfer initiated").build();
}

@Response(sender = "BankA.*")
public void onResponseBankA(SagaMessageContext info) {
    handleTransferResponse(info, "BankA");
}

@Response(sender = "BankB.*") 
public void onResponseBankB(SagaMessageContext info) {
    handleTransferResponse(info, "BankB");
}

private void handleTransferResponse(SagaMessageContext info, String bank) {
    TransferResponse response = parseResponse(info.getPayload());
    
    if (response.isSuccessful()) {
        // Check if all participants responded successfully
        if (allParticipantsSuccessful(info.getSagaId())) {
            getCurrentSaga(info.getSagaId()).commitSaga();
        }
    } else {
        // Any failure triggers rollback
        getCurrentSaga(info.getSagaId()).rollbackSaga();
    }
}
```

```java
// Bank Participant - Transfer Processing with RESERVABLE columns
@Request(sender = "CloudBank")
public String processTransferRequest(SagaMessageContext info) {
    String action = parseAccountsAction(info.getPayload());
    
    if ("withdraw".equals(action)) {
        return processWithdrawal(info);
    } else if ("deposit".equals(action)) {
        return processDeposit(info);
    }
}

private String processWithdrawal(SagaMessageContext info) {
    try {
        WithdrawRequest request = parseWithdrawRequest(info.getPayload());
        
        // Use RESERVABLE column for lock-free fund reservation
        String sql = "UPDATE bankA SET balance_amount = balance_amount - ? " +
                    "WHERE ucid = ? AND balance_amount >= ?";
                    
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setDouble(1, request.getAmount());
        stmt.setString(2, request.getUcid());
        stmt.setDouble(3, request.getAmount());
        
        int rowsUpdated = stmt.executeUpdate();
        
        if (rowsUpdated > 0) {
            logBankOperation(info.getSagaId(), "WITHDRAW", "COMPLETED");
            return createSuccessResponse("Funds reserved successfully");
        } else {
            logBankOperation(info.getSagaId(), "WITHDRAW", "FAILED");
            return createErrorResponse("Insufficient funds");
        }
        
    } catch (Exception e) {
        logger.error("Withdrawal failed", e);
        return createErrorResponse("Withdrawal processing failed");
    }
}

@Compensate
public void compensateTransfer(SagaMessageContext info) {
    // Reverse withdrawal - restore reserved funds
    String sql = "UPDATE bankA SET balance_amount = balance_amount + ? " +
                "WHERE ucid = ?";
    // Execute fund restoration
    logBankOperation(info.getSagaId(), "COMPENSATE_WITHDRAW", "COMPLETED");
}
```

#### Key Architectural Features of CloudBank Workflows

**🔹 RESERVABLE Columns Implementation:**
- Oracle's RESERVABLE column feature enables **lock-free fund reservations**
- Prevents traditional row-level locking during concurrent transactions
- Automatic rollback of reserved amounts on saga failure

**🔹 Programmatic Saga Control:**
- CloudBank uses `beginSaga()` method for explicit saga initiation
- Manual participant enrollment via `saga.sendRequest()`
- Explicit commit/rollback control based on participant responses

**🔹 Multi-Schema Coordination:**
- Orchestrator coordinates between separate BankA and BankB schemas
- Each participant maintains independent transaction logs (`bankA_book`, `bankB_book`)
- Centralized saga status tracking in `cloudbank_book` table

**🔹 Compensation Patterns:**
```
Account Creation:
Success: Request → Create → Log → Complete
Failure: Request → Create [FAILS] → Compensate → Cleanup

Money Transfer:  
Success: Withdrawal Check (Source Bank) → [If Sufficient Funds] → 
         Withdrawal Request & Deposit Request → Complete → Finalize
         
Failure Scenarios:
• Insufficient Funds: Withdrawal Check [FAILS] → Compensate → No Action Needed
• Withdrawal Fails: Withdrawal Check [PASSES] → Withdrawal [FAILS] → 
                   Compensate → Reverse Fund Reservation  
• Deposit Fails: Withdrawal [PASSES] → Deposit [FAILS] → 
                Compensate → Reverse Withdrawal & Restore Funds
```

**Failure Scenarios Handled:**
- **Insufficient funds**: Automatic rollback with fund restoration
- **Account not found**: Transaction rejection with proper error handling
- **Network failures**: Timeout-based compensation with retry logic
- **Database constraints**: RESERVABLE column violations trigger compensation

**Screenshot Placeholder:**  

![CloudBank Schema](./images/lab4-task3-1.png "CloudBank database schema and architecture")

**Screenshot Placeholder:**  

![CloudBank Workflows](./images/lab4-task3-2.png "CloudBank saga workflows: Account Creation and Money Transfer")

---

## Learn More

- [Oracle Saga Client Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html)  
- [Java Annotations Guide](https://docs.oracle.com/javase/tutorial/java/annotations/)  

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney, Luis Cruz, Sebastian Gerritsen  
* **Created By/Date** — Vinay Pandhariwal, August 2025  
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
