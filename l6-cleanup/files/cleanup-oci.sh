#!/usr/bin/env bash
set -eo pipefail

# Lab 2 provisions resources in the tenancy root compartment by default.
COMPARTMENT_ID="${COMPARTMENT_ID:-$(oci iam availability-domain list \
  --query 'data[0]."compartment-id"' \
  --raw-output)}"

normalize_id() {
  case "$1" in
    ""|null|None) printf '%s' "" ;;
    *) printf '%s' "$1" ;;
  esac
}

echo ">>> Locating Oracle Saga workshop resources..."

INSTANCE_ID=$(oci compute instance list \
  --compartment-id "$COMPARTMENT_ID" \
  --display-name 'oracle-saga-compute-instance' \
  --all \
  --query 'data[0].id' \
  --raw-output 2>/dev/null || true)

BOOT_VOLUME_ID=$(oci bv boot-volume list \
  --compartment-id "$COMPARTMENT_ID" \
  --all \
  --query 'data[?"display-name" == `oracle-saga-compute-instance (Boot Volume)`] | [0].id' \
  --raw-output 2>/dev/null || true)

ADB_ID=$(oci db autonomous-database list \
  --compartment-id "$COMPARTMENT_ID" \
  --display-name 'Oracle-Saga-Demo' \
  --all \
  --query 'data[0].id' \
  --raw-output 2>/dev/null || true)

VCN_ID=$(oci network vcn list \
  --compartment-id "$COMPARTMENT_ID" \
  --display-name 'Oracle-Saga-VCN' \
  --all \
  --query 'data[0].id' \
  --raw-output 2>/dev/null || true)

INSTANCE_ID=$(normalize_id "$INSTANCE_ID")
BOOT_VOLUME_ID=$(normalize_id "$BOOT_VOLUME_ID")
ADB_ID=$(normalize_id "$ADB_ID")
VCN_ID=$(normalize_id "$VCN_ID")

if [ -n "$INSTANCE_ID" ]; then
  INSTANCE_STATE=$(oci compute instance get \
    --instance-id "$INSTANCE_ID" \
    --query 'data."lifecycle-state"' \
    --raw-output 2>/dev/null || true)
  if [ "$INSTANCE_STATE" = "TERMINATED" ] || [ "$INSTANCE_STATE" = "TERMINATING" ]; then
    INSTANCE_ID=""
  fi
fi

if [ -n "$BOOT_VOLUME_ID" ]; then
  BOOT_VOLUME_STATE=$(oci bv boot-volume get \
    --boot-volume-id "$BOOT_VOLUME_ID" \
    --query 'data."lifecycle-state"' \
    --raw-output 2>/dev/null || true)
  if [ "$BOOT_VOLUME_STATE" = "TERMINATED" ] || [ "$BOOT_VOLUME_STATE" = "TERMINATING" ]; then
    BOOT_VOLUME_ID=""
  fi
fi

if [ -n "$ADB_ID" ]; then
  ADB_STATE=$(oci db autonomous-database get \
    --autonomous-database-id "$ADB_ID" \
    --query 'data."lifecycle-state"' \
    --raw-output 2>/dev/null || true)
  if [ "$ADB_STATE" = "TERMINATED" ] || [ "$ADB_STATE" = "TERMINATING" ]; then
    ADB_ID=""
  fi
fi

SUBNET_ID=""
ROUTE_TABLE_ID=""
SECURITY_LIST_ID=""
IGW_ID=""

if [ -n "$VCN_ID" ]; then
  SUBNET_ID=$(oci network subnet list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name 'public-subnet-Oracle-Saga-VCN' \
    --all \
    --query 'data[0].id' \
    --raw-output 2>/dev/null || true)

  ROUTE_TABLE_ID=$(oci network route-table list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name 'Oracle-Saga-RouteTable' \
    --all \
    --query 'data[0].id' \
    --raw-output 2>/dev/null || true)

  SECURITY_LIST_ID=$(oci network security-list list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name 'Oracle-Saga-SecurityList' \
    --all \
    --query 'data[0].id' \
    --raw-output 2>/dev/null || true)

  IGW_ID=$(oci network internet-gateway list \
    --compartment-id "$COMPARTMENT_ID" \
    --vcn-id "$VCN_ID" \
    --display-name 'Oracle-Saga-IGW' \
    --all \
    --query 'data[0].id' \
    --raw-output 2>/dev/null || true)

  SUBNET_ID=$(normalize_id "$SUBNET_ID")
  ROUTE_TABLE_ID=$(normalize_id "$ROUTE_TABLE_ID")
  SECURITY_LIST_ID=$(normalize_id "$SECURITY_LIST_ID")
  IGW_ID=$(normalize_id "$IGW_ID")
fi

echo
echo "Resources selected for permanent deletion:"
echo "Compute instance: $INSTANCE_ID"
echo "Boot volume:      $BOOT_VOLUME_ID"
echo "ADB:              $ADB_ID"
echo "VCN:              $VCN_ID"
echo "Subnet:           $SUBNET_ID"
echo "Route table:      $ROUTE_TABLE_ID"
echo "Security list:    $SECURITY_LIST_ID"
echo "Internet gateway: $IGW_ID"
echo

read -r -p "Type DELETE ORACLE SAGA to continue: " CONFIRM

if [ "$CONFIRM" != 'DELETE ORACLE SAGA' ]; then
  echo "Cleanup cancelled."
  exit 1
fi

if [ -n "$INSTANCE_ID" ]; then
  echo ">>> Terminating oracle-saga-compute-instance and its boot volume..."
  oci compute instance terminate \
    --instance-id "$INSTANCE_ID" \
    --preserve-boot-volume false \
    --force \
    --wait-for-state TERMINATED
else
  echo "Active compute instance was not found; skipping."
fi

if [ -n "$BOOT_VOLUME_ID" ]; then
  BOOT_VOLUME_STATE=$(oci bv boot-volume get \
    --boot-volume-id "$BOOT_VOLUME_ID" \
    --query 'data."lifecycle-state"' \
    --raw-output 2>/dev/null || true)

  if [ "$BOOT_VOLUME_STATE" = "AVAILABLE" ]; then
    echo ">>> Deleting retained workshop boot volume..."
    oci bv boot-volume delete \
      --boot-volume-id "$BOOT_VOLUME_ID" \
      --force \
      --wait-for-state TERMINATED
  else
    echo "Boot volume deletion is already in progress or complete; skipping."
  fi
fi

if [ -n "$ADB_ID" ]; then
  echo ">>> Terminating Oracle-Saga-Demo..."
  oci db autonomous-database delete \
    --autonomous-database-id "$ADB_ID" \
    --force
else
  echo "Autonomous Database was not found; skipping."
fi

if [ -n "$SUBNET_ID" ]; then
  echo ">>> Deleting workshop subnet..."
  oci network subnet delete \
    --subnet-id "$SUBNET_ID" \
    --force \
    --wait-for-state TERMINATED
fi

if [ -n "$ROUTE_TABLE_ID" ]; then
  echo ">>> Deleting workshop route table..."
  oci network route-table delete \
    --rt-id "$ROUTE_TABLE_ID" \
    --force \
    --wait-for-state TERMINATED
fi

if [ -n "$SECURITY_LIST_ID" ]; then
  echo ">>> Deleting workshop security list..."
  oci network security-list delete \
    --security-list-id "$SECURITY_LIST_ID" \
    --force \
    --wait-for-state TERMINATED
fi

if [ -n "$IGW_ID" ]; then
  echo ">>> Deleting workshop internet gateway..."
  oci network internet-gateway delete \
    --ig-id "$IGW_ID" \
    --force \
    --wait-for-state TERMINATED
fi

if [ -n "$VCN_ID" ]; then
  echo ">>> Deleting Oracle-Saga-VCN..."
  oci network vcn delete \
    --vcn-id "$VCN_ID" \
    --force \
    --wait-for-state TERMINATED
else
  echo "Workshop VCN was not found; skipping."
fi

if [ -n "$ADB_ID" ]; then
  echo ">>> Waiting for Oracle-Saga-Demo termination..."
  for ATTEMPT in {1..60}; do
    ADB_STATE=$(oci db autonomous-database get \
      --autonomous-database-id "$ADB_ID" \
      --query 'data."lifecycle-state"' \
      --raw-output 2>/dev/null || true)

    if [ -z "$ADB_STATE" ] || [ "$ADB_STATE" = "TERMINATED" ]; then
      break
    fi

    sleep 10
  done

  if [ -n "$ADB_STATE" ] && [ "$ADB_STATE" != "TERMINATED" ]; then
    echo "WARNING: Autonomous Database termination is still in progress."
  fi
fi

echo ">>> Removing workshop files from Cloud Shell..."
rm -f "$HOME/provision.sh"
rm -f "$HOME/cloud-init.sh"
rm -f "$HOME/cloudbank-setup/oracle-saga-cloudbank.tar.gz"
rm -f "$HOME/cloudbank-setup/oracle-saga-cloudbank.zip"
rm -f "$HOME/cloudbank-setup/create-cloudbank-users.sql"
rm -rf "$HOME/cloudbank-setup/oracle-saga-cloudbank"
rmdir "$HOME/cloudbank-setup" 2>/dev/null || true
rm -f "$HOME/.ssh/cloudbank_key" "$HOME/.ssh/cloudbank_key.pub"
rm -f "$HOME/cleanup-oci.sh"

echo
echo "Oracle Saga workshop cleanup completed."
