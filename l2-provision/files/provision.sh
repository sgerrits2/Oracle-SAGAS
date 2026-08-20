#!/usr/bin/env bash
set -euo pipefail

# CloudBank Live Lab automated provisioning with OCI CLI.
# Run this script in OCI Cloud Shell, where OCI CLI authentication is available.
# It provisions the Autonomous Database, networking, and compute instance, then
# prepares the CloudBank package, wallet, database users, and VM transfer.

# ----------------- REQUIRED INPUT -----------------
# Pass COMPARTMENT_ID when starting the script. This training environment uses
# the fixed demo password below for ADMIN and the CloudBank application schemas.
COMPARTMENT_ID="${COMPARTMENT_ID:-}"
ADMIN_PASSWORD='Welcome_123#'

if [[ -z "$COMPARTMENT_ID" ]]; then
  echo "ERROR: COMPARTMENT_ID is required."
  echo "Example: COMPARTMENT_ID='ocid1.compartment...' ./provision.sh"
  exit 1
fi

# ----------------- CONFIGURATION -----------------
DB_DISPLAY_NAME="Oracle-Saga-Demo"
DB_NAME="OracleSagaDemo"
VCN_CIDR="10.0.0.0/16"
PUBLIC_SUBNET_CIDR="10.0.0.0/24"
INSTANCE_SHAPE="VM.Standard.E2.1.Micro"
INSTANCE_NAME="oracle-saga-compute-instance"
SSH_PRIVATE_KEY_PATH="$HOME/.ssh/cloudbank_key"
SSH_PUBLIC_KEY_PATH="$HOME/.ssh/cloudbank_key.pub"
CLOUD_INIT_FILE="./cloud-init.sh"
SETUP_DIR="${SETUP_DIR:-$HOME/cloudbank-setup}"
APP_ARCHIVE_URL="${APP_ARCHIVE_URL:-https://github.com/sgerrits2/Oracle-SAGAS/raw/main/l2-provision/files/oracle-saga-cloudbank.zip}"
USER_SETUP_SQL_URL="${USER_SETUP_SQL_URL:-https://github.com/sgerrits2/Oracle-SAGAS/raw/main/l2-provision/files/create-cloudbank-users.sql}"
# -------------------------------------------------

for REQUIRED_COMMAND in curl oci scp sql ssh ssh-keygen unzip; do
  if ! command -v "$REQUIRED_COMMAND" >/dev/null 2>&1; then
    echo "ERROR: $REQUIRED_COMMAND is required. Run this script from OCI Cloud Shell."
    exit 1
  fi
done

download_file() {
  local url="$1"
  local destination="$2"
  curl --fail --location --retry 3 --output "$destination" "$url"
}

wait_for_ssh() {
  local attempt

  echo ">>> Waiting for SSH on the compute instance..."
  for attempt in {1..30}; do
    if ssh -o StrictHostKeyChecking=accept-new \
      -o ConnectTimeout=10 \
      -i "$SSH_PRIVATE_KEY_PATH" \
      "ubuntu@$PUBLIC_IP" 'exit' >/dev/null 2>&1; then
      return 0
    fi
    sleep 10
  done

  echo "ERROR: The compute instance did not accept SSH connections in time."
  exit 1
}

wait_for_cloud_init() {
  echo ">>> Waiting for compute initialization to finish..."
  ssh -o StrictHostKeyChecking=accept-new \
    -i "$SSH_PRIVATE_KEY_PATH" \
    "ubuntu@$PUBLIC_IP" 'sudo cloud-init status --wait'
}

verify_remote_environment() {
  echo ">>> Verifying Podman, Podman Compose, and the CloudBank package..."
  ssh -o StrictHostKeyChecking=accept-new \
    -i "$SSH_PRIVATE_KEY_PATH" \
    "ubuntu@$PUBLIC_IP" 'bash -s' <<'REMOTE_VERIFY'
set -euo pipefail

export PATH="$HOME/.local/bin:$PATH"

podman --version
podman-compose --version
podman system info >/dev/null
test -d "$HOME/oracle-saga-cloudbank"
test -f "$HOME/oracle-saga-cloudbank/adbsSetup/adb_wallet/tnsnames.ora"

echo "CloudBank package: READY"
echo "ADB wallet: READY"
echo "Podman environment: READY"
REMOTE_VERIFY
}

prepare_cloudbank() {
  local app_archive="$SETUP_DIR/oracle-saga-cloudbank.zip"
  local user_setup_sql="$SETUP_DIR/create-cloudbank-users.sql"
  local app_dir="$SETUP_DIR/oracle-saga-cloudbank"
  local wallet_dir="$app_dir/adbsSetup/adb_wallet"
  local wallet_archive="$wallet_dir/SagasWallet.zip"
  local tns_alias

  echo ">>> Downloading the CloudBank package and database user setup script..."
  mkdir -p "$SETUP_DIR"
  download_file "$APP_ARCHIVE_URL" "$app_archive"
  download_file "$USER_SETUP_SQL_URL" "$user_setup_sql"

  echo ">>> Extracting the CloudBank package..."
  unzip -q -o "$app_archive" -d "$SETUP_DIR"
  mkdir -p "$wallet_dir"

  echo ">>> Generating and extracting the Autonomous Database wallet..."
  oci db autonomous-database generate-wallet \
    --autonomous-database-id "$ADB_ID" \
    --file "$wallet_archive" \
    --password "$ADMIN_PASSWORD"
  unzip -q -o "$wallet_archive" -d "$wallet_dir"

  tns_alias=$(sed -n -E 's/^([[:alnum:]_]+_medium)[[:space:]]*=.*/\1/p' "$wallet_dir/tnsnames.ora" | head -n 1)
  if [[ -z "$tns_alias" ]]; then
    echo "ERROR: Could not find a _medium TNS alias in the generated wallet."
    exit 1
  fi

  echo ">>> Creating the CloudBank database users..."
  TNS_ADMIN="$wallet_dir" sql -L -s "admin/${ADMIN_PASSWORD}@${tns_alias}" "@$user_setup_sql"

  echo ">>> Transferring the prepared CloudBank package to the compute instance..."
  wait_for_ssh
  wait_for_cloud_init
  scp -o StrictHostKeyChecking=accept-new \
    -i "$SSH_PRIVATE_KEY_PATH" \
    -r "$app_dir" "ubuntu@$PUBLIC_IP:~/"

  verify_remote_environment

  TNS_ALIAS="$tns_alias"
  APP_DIR="$app_dir"
}

echo ">>> Checking region and compartment..."
REGION=$(oci iam region-subscription list --query 'data[0]."region-name"' --raw-output)
echo "Detected region: $REGION"

if [[ ! -f "$SSH_PUBLIC_KEY_PATH" ]]; then
  echo ">>> Generating SSH key pair..."
  ssh-keygen -t rsa -b 2048 -f "$SSH_PRIVATE_KEY_PATH" -N ""
fi

echo ">>> Creating VCN..."
VCN_ID=$(oci network vcn create \
  --compartment-id "$COMPARTMENT_ID" \
  --display-name "Oracle-Saga-VCN" \
  --cidr-block "$VCN_CIDR" \
  --dns-label "oraclesaga" \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "VCN created: $VCN_ID"

echo ">>> Creating Internet Gateway..."
IGW_ID=$(oci network internet-gateway create \
  --compartment-id "$COMPARTMENT_ID" \
  --vcn-id "$VCN_ID" \
  --is-enabled true \
  --display-name "Oracle-Saga-IGW" \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "Internet Gateway created: $IGW_ID"

echo ">>> Creating route table..."
ROUTE_TABLE_ID=$(oci network route-table create \
  --compartment-id "$COMPARTMENT_ID" \
  --vcn-id "$VCN_ID" \
  --display-name "Oracle-Saga-RouteTable" \
  --route-rules '[{"destination":"0.0.0.0/0","destinationType":"CIDR_BLOCK","networkEntityId":"'"$IGW_ID"'"}]' \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "Route table created: $ROUTE_TABLE_ID"

echo ">>> Creating security list with CloudBank ports..."
SECURITY_LIST_ID=$(oci network security-list create \
  --compartment-id "$COMPARTMENT_ID" \
  --vcn-id "$VCN_ID" \
  --display-name "Oracle-Saga-SecurityList" \
  --egress-security-rules '[{"destination":"0.0.0.0/0","protocol":"all","destinationType":"CIDR_BLOCK"}]' \
  --ingress-security-rules '[
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":22,"max":22}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":3000,"max":3000}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8080,"max":8080}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8081,"max":8081}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8082,"max":8082}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8083,"max":8083}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8084,"max":8084}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":8085,"max":8085}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":9411,"max":9411}}}
  ]' \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "Security list created: $SECURITY_LIST_ID"

echo ">>> Creating public subnet..."
SUBNET_ID=$(oci network subnet create \
  --compartment-id "$COMPARTMENT_ID" \
  --vcn-id "$VCN_ID" \
  --display-name "public-subnet-Oracle-Saga-VCN" \
  --cidr-block "$PUBLIC_SUBNET_CIDR" \
  --route-table-id "$ROUTE_TABLE_ID" \
  --security-list-ids '["'"$SECURITY_LIST_ID"'"]' \
  --prohibit-public-ip-on-vnic false \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "Subnet created: $SUBNET_ID"

echo ">>> Creating Autonomous Database. This takes a few minutes..."
ADB_ID=$(oci db autonomous-database create \
  --compartment-id "$COMPARTMENT_ID" \
  --db-name "$DB_NAME" \
  --display-name "$DB_DISPLAY_NAME" \
  --admin-password "$ADMIN_PASSWORD" \
  --cpu-core-count 1 \
  --data-storage-size-in-tbs 1 \
  --db-workload OLTP \
  --db-version 23ai \
  --is-free-tier true \
  --wait-for-state AVAILABLE \
  --query 'data.id' --raw-output)
echo "Autonomous Database created: $ADB_ID"

echo ">>> Preparing cloud-init..."
cat > "$CLOUD_INIT_FILE" <<'CLOUD_INIT'
#!/usr/bin/env bash
set -euo pipefail

apt-get update -y
apt-get install -y podman curl wget pipx
systemctl enable --now podman.socket

sudo -u ubuntu env PATH="/home/ubuntu/.local/bin:$PATH" bash <<'USER_SETUP'
pipx install podman-compose
pull_image() { podman pull "$1"; }
pull_image ghcr.io/oracle/oraclelinux:8 &
pull_image container-registry.oracle.com/database/sqlcl:latest &
pull_image docker.io/swaggerapi/swagger-ui:v5.20.7 &
pull_image docker.io/library/maven:3.8.6-openjdk-11 &
pull_image ghcr.io/openzipkin/zipkin:latest &
pull_image container-registry.oracle.com/database/free:latest &
wait
USER_SETUP
CLOUD_INIT

echo ">>> Looking up Ubuntu 24.04 Minimal image..."
IMAGE_ID=$(oci compute image list \
  --compartment-id "$COMPARTMENT_ID" \
  --operating-system "Canonical Ubuntu" \
  --operating-system-version "24.04 Minimal" \
  --sort-by TIMECREATED --sort-order DESC \
  --query 'data[0].id' --raw-output)

echo ">>> Looking up availability domain..."
AVAILABILITY_DOMAIN=$(oci iam availability-domain list \
  --compartment-id "$COMPARTMENT_ID" \
  --query 'data[0].name' --raw-output)

echo ">>> Launching compute instance..."
INSTANCE_ID=$(oci compute instance launch \
  --compartment-id "$COMPARTMENT_ID" \
  --availability-domain "$AVAILABILITY_DOMAIN" \
  --display-name "$INSTANCE_NAME" \
  --shape "$INSTANCE_SHAPE" \
  --image-id "$IMAGE_ID" \
  --subnet-id "$SUBNET_ID" \
  --assign-public-ip true \
  --ssh-authorized-keys-file "$SSH_PUBLIC_KEY_PATH" \
  --user-data-file "$CLOUD_INIT_FILE" \
  --wait-for-state RUNNING \
  --query 'data.id' --raw-output)
echo "Instance created: $INSTANCE_ID"

PUBLIC_IP=$(oci compute instance list-vnics \
  --instance-id "$INSTANCE_ID" \
  --query 'data[0]."public-ip"' --raw-output)

prepare_cloudbank

echo
echo "================= PROVISIONING COMPLETE ================="
echo "VCN OCID:       $VCN_ID"
echo "Subnet OCID:    $SUBNET_ID"
echo "ADB OCID:       $ADB_ID"
echo "TNS Alias:      $TNS_ALIAS"
echo "Instance OCID:  $INSTANCE_ID"
echo "Instance IP:    $PUBLIC_IP"
echo "SSH:            ssh -i $SSH_PRIVATE_KEY_PATH ubuntu@$PUBLIC_IP"
echo "CloudBank VM:   /home/ubuntu/oracle-saga-cloudbank"
echo "CloudBank local: $APP_DIR"
echo "Validation:     USERS, WALLET, SSH, PODMAN, PODMAN-COMPOSE, TRANSFER = READY"
echo "============================================================"
