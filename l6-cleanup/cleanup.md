# Lab 6: Terminate and/or Cleanup

## **Introduction**

After completing the CloudBank application demonstration and testing Oracle Sagas, it is important to **terminate or clean up your resources**.  
This prevents unnecessary costs and ensures your environment is ready for future exercises.  

This lab guides you through:  
- Stopping Podman services  
- Cleaning up configuration files  
- Terminating OCI compute and database resources (if applicable)  
- Verifying that your environment has been fully released  

</br>

*Estimated Time: 20–30 minutes*

---

### Objectives

In this lab, you will:  
- Stop all running **CloudBank services**.  
- Remove container images and network resources.  
- Disconnect from the database wallet and remove credentials.  
- Optionally terminate the Autonomous Database and/or VM instances.  

---

### Prerequisites

- Completion of Lab 5 (CloudBank Application).  
- Access to the OCI Console and Cloud Shell.  
- Administrative privileges on the VM and Podman environment.  

---

## Task 1: Stop Podman Services

---

1. Navigate to the CloudBank deployment directory:

VVVbash
cd ~/cloudbank
VVV

2. Stop all running services:

VVVbash
podman-compose down
VVV

3. Verify containers are stopped:

VVVbash
podman ps -a
VVV

**Expected Output:** No containers should show as `Up`.  

**Screenshot Placeholder:**  
![Podman Down](./images/lab6-task1-down.png "All CloudBank services stopped")

---

## Task 2: Remove Container Images and Volumes

---

To reclaim space on your VM, you can remove CloudBank images and volumes.

1. Remove unused containers, networks, and volumes:

VVVbash
podman system prune -a -f
VVV

2. Verify images have been deleted:

VVVbash
podman images
VVV

---

## Task 3: Cleanup Credentials and Wallets

---

1. If you stored database credentials in an **.env file**, remove it:  

VVVbash
rm ~/cloudbank/.env
VVV

2. If you downloaded an **ADB wallet**, remove it if no longer required:  

VVVbash
rm -rf ~/Wallet_*
VVV

---

## Task 4: Terminate OCI Resources (Optional)

---

If you used **Autonomous Database (ADB-S)** and a **VM instance** specifically for this workshop, you may terminate them to stop billing.  

### Step 1: Terminate the Autonomous Database
1. Log in to the **OCI Console**.  
2. Navigate to **Autonomous Database** → Select your instance.  
3. Click **Terminate**.  

**Screenshot Placeholder:**  
![Terminate ADB](./images/lab6-task4-adb.png "Terminate Autonomous Database")

---

### Step 2: Terminate the Compute VM
1. Log in to the **OCI Console**.  
2. Navigate to **Compute Instances** → Select your VM.  
3. Click **Terminate**.  

**Screenshot Placeholder:**  
![Terminate VM](./images/lab6-task4-vm.png "Terminate VM")

---

## Task 5: Verify Environment Cleanup

---

1. Check that no compute or database resources are running in your compartment:  
   - **OCI Console → Instances** = 0 running  
   - **OCI Console → Autonomous Databases** = 0 running  

2. Ensure your Cloud Shell is clear of leftover credentials and container images.  

---

✅ **End of Lab 6.**  
You have successfully stopped and cleaned up all resources used in this workshop. This ensures that you avoid unwanted charges and keeps your environment clean for future work.  

Next: **Lab 7 — Extended Labs**, where you will explore developing Oracle Sagas with PL/SQL and polyglot transactions.  

---

## Learn More

- [OCI Resource Management](https://docs.oracle.com/en-us/iaas/Content/home.htm)  
- [Podman Cleanup Reference](https://docs.podman.io/en/latest/markdown/podman-system-prune.1.html)  

## Acknowledgements

* **Contributors** — Vinay Pandhariwal, Amit Ketkar, Pavas Navaney  
* **Created By/Date** — Vinay Pandhariwal, August 2025  
* **Last Updated By/Date** — Vinay Pandhariwal, August 2025
