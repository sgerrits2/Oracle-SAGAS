# Lab 6: Clean Up or Terminate Resources

## Introduction

After demonstrating the CloudBank application and testing Oracle Sagas, clean up the resources created for the workshop. This helps avoid unnecessary charges and prepares your environment for future work.

*Estimated time: 20 minutes*

---

### Objectives

In this lab, you will:

- Stop CloudBank services and remove local container resources.
- Remove local database credentials and wallets that are no longer needed.
- Optionally terminate the OCI resources created for the workshop.
- Verify that the environment has been cleaned up.

---

### Prerequisites

- Completion of Lab 5 (CloudBank Application).
- Access to the OCI Console and Cloud Shell.
- Permission to manage the VM and its Podman environment.

---

## Task 1: Clean Up Local CloudBank Resources

Run the following script on the VM. It stops CloudBank, removes unused Podman resources, and deletes the local `.env` file and any wallet directories in your home directory. Run it only when you no longer need these local resources.

```bash
<copy>
#!/usr/bin/env bash
set -euo pipefail

CLOUDBANK_DIR="$HOME/cloudbank"

cd "$CLOUDBANK_DIR"
podman-compose down
podman ps -a
podman system prune -a -f
podman images
rm -f "$CLOUDBANK_DIR/.env"
find "$HOME" -maxdepth 1 -type d -name 'Wallet_*' -exec rm -rf {} +
</copy>
```

### What This Script Does

- **#!/usr/bin/env bash** runs the script with Bash.
- **set -euo pipefail** stops the script if a command fails, an unset variable is used, or a command in a pipeline fails.
- **CLOUDBANK_DIR="$HOME/cloudbank"** stores the CloudBank deployment path.
- **cd "$CLOUDBANK_DIR"** moves to the deployment directory.
- **podman-compose down** stops and removes the CloudBank containers and network.
- **podman ps -a** lists all containers so you can confirm that none are running.
- **podman system prune -a -f** removes unused containers, networks, images, and volumes without prompting.
- **podman images** lists the remaining container images.
- **rm -f "$CLOUDBANK_DIR/.env"** removes the local environment file, including any stored database credentials.
- **find "$HOME" -maxdepth 1 -type d -name 'Wallet_*' -exec rm -rf {} +** removes Autonomous Database wallet directories directly under your home directory.

---

## Task 2: Terminate OCI Resources (Optional)

If you created an Autonomous Database and a compute VM only for this workshop, terminate them to stop billing.

### Step 1: Terminate the Autonomous Database

1. Sign in to the **OCI Console**.
2. Go to **Autonomous Database** and select your database.
3. Select **Terminate** and confirm the operation.

**Screenshot Placeholder:**
![Terminate Autonomous Database](./images/lab6-task4-adb.png "Terminate Autonomous Database")

### Step 2: Terminate the Compute VM

1. In the **OCI Console**, go to **Compute** → **Instances**.
2. Select the VM created for this workshop.
3. Select **Terminate** and confirm the operation.

**Screenshot Placeholder:**
![Terminate Compute VM](./images/lab6-task4-vm.png "Terminate Compute VM")

---

## Task 3: Verify Cleanup

1. Confirm that no workshop compute instances or Autonomous Databases are running in your compartment.
2. Confirm that the local CloudBank directory no longer contains credentials, wallets, or unneeded container images.

---

✅ **End of Lab 6.** You have cleaned up the resources used for this workshop. This helps prevent unwanted charges and keeps your environment ready for future work.

Next: **Lab 7 — Extended Labs**, where you will explore Oracle Sagas with PL/SQL and polyglot transactions.

---

## Learn More

- [OCI Resource Management](https://docs.oracle.com/en-us/iaas/Content/home.htm)
- [Podman system prune reference](https://docs.podman.io/en/latest/markdown/podman-system-prune.1.html)

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney,
Luis Cruz, Sebastian Gerritsen
* **Created By/Date** — Vinay Pandhariwal, August 2025
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
