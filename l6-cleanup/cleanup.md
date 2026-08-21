# Cleanup

## Introduction

After demonstrating the CloudBank application and testing Oracle Sagas, clean up the resources created for the workshop. This helps avoid unnecessary charges and prepares your environment for future work.

*Estimated time: 20 minutes*

---

### Objectives

In this lab, you will:

- Run one guarded Cloud Shell script to permanently delete the workshop environment.
- Remove the compute instance, boot volume, Autonomous Database, networking, credentials, wallet, and Cloud Shell files.

---

### Prerequisites

- Completion of Labs 1–5 and Lab 6 (Extended Lab).
- Access to OCI Cloud Shell.
- Permission to terminate and delete the workshop OCI resources.

---

## Task 1: Delete the Complete Workshop Environment

Run the following commands from **OCI Cloud Shell**. The downloaded script locates only the fixed resources created by Lab 2, displays their OCIDs, and requires confirmation before permanently deleting anything.

> **⚠️ Important:** Verify every resource name and compartment before confirming a termination or deletion. These operations cannot be undone.

```bash
<copy>
curl -fL -o cleanup-oci.sh \
https://raw.githubusercontent.com/sgerrits2/Oracle-SAGAS/main/l6-cleanup/files/cleanup-oci.sh

chmod +x cleanup-oci.sh
./cleanup-oci.sh
</copy>
```

Review every displayed OCID. To confirm permanent deletion of the matched workshop resources, enter:

```text
DELETE ORACLE SAGA
```

The script terminates the compute instance and boot volume first, requests termination of `Oracle-Saga-Demo`, and then deletes the workshop subnet, route table, security list, internet gateway, and VCN in dependency order. If OCI reports that the subnet is still in use, wait for the terminated instance's VNIC to disappear and run the script again.

The single script permanently deletes:

- `oracle-saga-compute-instance` and its boot volume
- `Oracle-Saga-Demo`
- `public-subnet-Oracle-Saga-VCN`
- `Oracle-Saga-RouteTable`
- `Oracle-Saga-SecurityList`
- `Oracle-Saga-IGW`
- `Oracle-Saga-VCN`
- The CloudBank archives, source, wallet, `.env`, provisioning files, and fixed SSH key pair in Cloud Shell

> **⏳ Timing note:** The script waits for the compute instance, boot volume, and networking deletions. Autonomous Database termination can continue asynchronously; the script waits up to 10 minutes and prints a warning if OCI is still finishing it.

---

✅ **Workshop complete.** You have cleaned up the resources used for this workshop. This helps prevent unwanted charges and keeps your environment ready for future work.

---

## Learn More

- [OCI Resource Management](https://docs.oracle.com/en-us/iaas/Content/home.htm)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney,
Luis Cruz, Sebastian Gerritsen
* **Created By/Date** — Vinay Pandhariwal, August 2025
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
