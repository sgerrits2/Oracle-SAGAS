# About this Workshop

Explore **Oracle Sagas**—a native Oracle Database framework for managing sophisticated, multi-step business processes. In this hands-on session, you'll learn how to design and orchestrate **resilient, distributed workflows** using Oracle Database’s **native support for the Saga pattern**. By participating, you’ll gain practical skills for implementing and managing resilient distributed transactions within Oracle Database.

Modern applications often span multiple microservices and systems, making it challenging to maintain data consistency across long-running, distributed transactions without resorting to cumbersome locks or traditional two-phase commit. Oracle Sagas offers a powerful, database-native solution—embedding the saga pattern directly into Oracle Database. This approach allows developers to model, execute, and compensate distributed transactions natively, eliminating the need for external orchestration tools or heavy resource locking.

This LiveLab, **Oracle Sagas: Simplifying Distributed Application Development**, guides you through a step-by-step series of labs followed by final resource cleanup. You’ll cover everything from fundamental concepts and environment setup to deployment, testing, and manually extending a Saga topology with PL/SQL administration APIs. By the end of these labs, you’ll have practical experience with Oracle Saga infrastructure and the CloudBank Java application.

## Flow of the LiveLab
1. **Introduction to Oracle Sagas and Distributed Transactions**
    - Understand the challenges of distributed transactions in modern applications.
    - Learn the fundamental concepts of the Saga pattern and how Oracle Sagas addresses transaction management needs.
    - Explore real-world use cases suited for Sagas.

2. **Environment Setup for Oracle Sagas**
    - Prepare your Oracle Autonomous Database environment, including user setup and required permissions.
    - Configure OCI networking and access tools such as CloudShell, SQLcl, and (optionally) UI clients.
    - Review deployment options with virtual machines and containers.

3. **Oracle Sagas Core Setup: Broker, Coordinator & Participants**
    - Set up essential Saga components: Broker, Coordinator, and Participants.
    - Create supporting tables, assign roles and permissions, and enable monitoring of saga operations.

4. **Developing Oracle Sagas with Java Client**
    - Implement multi-step workflows using PL/SQL APIs for saga orchestration.
    - Integrate Java logic and utilize Oracle’s saga annotations and artifacts for cross-language support.
    - Explore hands-on examples such as the CloudBank demo application.

5. **Oracle Sagas in Action: The CloudBank Application**
    - Deploy and interact with a complete Saga-based application.
    - Use the Flask UI or direct API calls to simulate normal transactions, failure handling, and crash recovery; Swagger UI is available as an optional tool.
    - Monitor and validate saga states and compensating actions in real time.

6. **Lab 6: Extended Lab**
    - Manually register `BankLondon` as an additional Saga participant.
    - Connect it to the existing CloudBank coordinator and broker.
    - Verify the new participant through the Saga metadata views.

7. **Cleanup**
    - Stop Podman services and remove Cloud Shell configuration files.
    - Terminate OCI compute, database, and networking resources.
    - Verify that the workshop environment has been fully released.

## Before you begin

### **Client Interfaces: Java vs. PL/SQL**

Oracle Sagas can be implemented via:
- **Java annotations** using the [Oracle Sagas Java Client](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core/23.7.0) – ideal for microservices.
- **PL/SQL APIs** using built-in package `DBMS_SAGA` – suitable for database-native applications.

This workshop uses the `DBMS_SAGA_ADM` package to configure Saga infrastructure and Java annotations in the supplied **CloudBank** application. After completing Labs 1–5, Lab 6 (Extended Lab) shows how to manually register `BankLondon` as an additional participant in the existing topology.

### **Single-PDB vs. Multi-PDB Environments**

The demo application **CloudBank** deploys multiple entities, including the **Coordinator**, **Broker**, and multiple **Participants** (including **Initiator**). Although they can exist in any distributed environment, for ease of use and for demo purposes, we will deploy everything in **one PDB**.

Feel free to try out deployment in a distributed environment. However, note that the **Initiator (Primary Participant)** and **Coordinator** must be deployed in the **same PDB**, regardless of the environment.

> ⚠️ Depending on your deployment options (OCI tenancy or local environment) and the selected architecture, certain features (like `DBLINKS` or `TEQ` queues) may behave differently. These will be explained clearly in the labs.

### **Permissions and Roles**

Some saga operations require **specific user privileges** (required for Core Saga Setup). These prerequisites can be explored in the package documentation and will also be addressed step-by-step later in **Labs 2 and 3**.

## What You’ll Achieve

By the end of this LiveLab, you will:
- Understand the **Oracle Sagas** framework and its role in orchestrating distributed, long-running transactions in modern applications.
- Gain hands-on experience administering Saga entities through **PL/SQL APIs** and understanding the supplied **Oracle Sagas Java Client annotations**.
- Learn how to define **compensation logic** and ensure reliable, lock-free data consistency across microservices or application modules.
- Be able to **deploy, monitor, and troubleshoot** sagas within Oracle Autonomous Database.
- Acquire practical patterns for building **resilient, distributed applications** without relying on global locks or complex external orchestrators.

Let’s dive in and start building robust, distributed business workflows with **Oracle Sagas**!

* Estimated Workshop Time: 4 hours, 30 minutes

You may now [proceed to the next lab](#next).

## Learn More

- For an overview of developing applications with Sagas, see the [Oracle Database 23ai Sagas Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html).

- For technical details on PL/SQL support, refer to the [`DBMS_SAGA` Package Reference](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga.html) and [`DBMS_SAGA_ADM`](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga_adm.html).

- For integrating Sagas with Java, see the [Oracle Sagas Java Client Maven Repository](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core/23.7.0) and official API documentation.

## Acknowledgements

* **Contributors** - Amit Ketkar, Pavas Navaney, Vinay Pandhariwal, Luis Cruz, Sebastian Gerritsen
* **Created By/Date** - Vinay Pandhariwal, August 2025
* **Last Updated By/Date** - Vinay Pandhariwal, August 2025
