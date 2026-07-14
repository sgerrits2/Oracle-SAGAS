# About this Workshop

Explore **Oracle Sagas**—a native Oracle Database framework for managing sophisticated, multi-step business processes. In this hands-on session, you'll learn how to design and orchestrate **resilient, distributed workflows** using Oracle Database’s **native support for the Saga pattern**. By participating, you’ll gain practical skills for implementing and managing resilient distributed transactions within Oracle Database.

Modern applications often span multiple microservices and systems, making it challenging to maintain data consistency across long-running, distributed transactions without resorting to cumbersome locks or traditional two-phase commit. Oracle Sagas offers a powerful, database-native solution—embedding the saga pattern directly into Oracle Database. This approach allows developers to model, execute, and compensate distributed transactions natively, eliminating the need for external orchestration tools or heavy resource locking.

This LiveLab, **Oracle Sagas: Simplifying Distributed Application Development**, guides you through a step-by-step, hands-on series of six labs. You’ll cover everything from fundamental concepts and environment setup to hands-on coding, deployment, and testing—using both **PL/SQL** and **Java**. By the end of these immersive labs, you’ll have built practical know-how and templates for integrating lock-free, compensating workflows into your own modern applications.

## Flow of the LiveLab
1. **Introduction to Oracle Sagas and Distributed Transactions**
    - Understand the challenges of distributed transactions in modern applications.
    - Learn the fundamental concepts of the Saga pattern and how Oracle Sagas address transaction management needs.
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
    - Use Postman or Swagger to test APIs, simulate normal transactions, failure handling, and crash recovery.
    - Monitor and validate saga states and compensating actions in real time.

6. **Advanced and Extended Labs (Optional)**
    - Dive deeper into PL/SQL saga procedures, compensation mechanisms, and troubleshooting strategies.
    - Explore polyglot workflows combining Java initiators with PL/SQL participant logic.
    - Apply lessons learned to real-world distributed transaction requirements.

## Before you begin

### **Client Interfaces: Java vs. PL/SQL**

Oracle Sagas can be implemented via:
- **Java annotations** using the [Oracle Sagas Java Client](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core/23.7.0) – ideal for microservices.
- **PL/SQL APIs** using built-in package `DBMS_SAGA` – suitable for database-native applications.

This lab is primarily focused on setting up the infrastructure using the `DBMS_SAGA_ADM` package and using Java annotations to build a demo application, **CloudBank**. You can read more about the PL/SQL client in our extended lab (**Lab 7 Task 1**). We also test out polyglot functionality in the extended section (**Lab 7 Task 2**) where we have saga participants deployed using both Java Client and PL/SQL Client.

### **Single-PDB vs. Multi-PDB Environments**

The demo application **CloudBank** deploys multiple entities in **Coordinator**, **Broker**, and multiple **Participants** (including **Initiator**). Although they can exist in any distributed environment, for ease of use and for demo purposes, we will deploy everything in **one PDB**.

Feel free to try out deployment in a distributed environment. However, note that the **Initiator (Primary Participant)** and **Coordinator** must be deployed in the **same PDB**, regardless of the environment.

> ⚠️ Depending on your deployment options (OCI tenancy or local environment) and the selected architecture, certain features (like `DBLINKS` or `TEQ` queues) may behave differently. These will be explained clearly in the labs.

### **Permissions and Roles**

Some saga operations require **specific user privileges** (required for Core Saga Setup). These prerequisites can be explored in the package documentation and will also be addressed step-by-step later in **Labs 2 and 3**.

## What You’ll Achieve

By the end of this LiveLab, you will:
- Understand **Oracle Sagas** and its role in orchestrating distributed, long-running transactions in modern applications.
- Gain hands-on experience modeling and executing saga workflows using both **PL/SQL** and **Oracle Sagas Java Client annotations**.
- Learn how to define **compensation logic** and ensure reliable, lock-free data consistency across microservices or application modules.
- Be able to **deploy, monitor, and troubleshoot** sagas within Oracle Autonomous Database.
- Acquire practical patterns for building **resilient, distributed applications** without relying on global locks or complex external orchestrators.

Let’s dive in and start building robust, distributed business workflows with **Oracle Sagas**!

* Estimated Workshop Time: XX hour, XX minutes 

You may now [proceed to the next lab](#next).

## Learn More

- For an overview of developing applications with Sagas, see the [Oracle Database 23ai Sagas Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html).

- For technical details on PL/SQL support, refer to the [`DBMS_SAGA` Package Reference](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga.html) and [`DBMS_SAGA_ADM`](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga_adm.html).

- For integrating Sagas with Java, see the [Oracle Sagas Java Client Maven Repository](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core/23.7.0) and official API documentation.

## Acknowledgements

* **Contributors** - Amit Ketkar, Pavas Navaney, Vinay Pandhariwal,
Luis Cruz, Sebastian Gerritsen
* **Created By/Date** - Vinay Pandhariwal, August 2025
* **Last Updated By/Date** - Vinay Pandhariwal, August 2025
