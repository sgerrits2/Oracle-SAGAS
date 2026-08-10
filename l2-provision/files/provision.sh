#!/usr/bin/env bash
set -euo pipefail

# CloudBank Live Lab automated provisioning with OCI CLI.
# Run this script in OCI Cloud Shell, where OCI CLI authentication is available.
# It provisions the Autonomous Database, networking, and compute instance.

# ----------------- CONFIGURATION -----------------
COMPARTMENT_ID="ocid1..." # Replace with your compartment OCID.
ADMIN_PASSWORD="Welcome_123#" # Replace with your ADB ADMIN password.
DB_DISPLAY_NAME="Oracle-Saga-Demo"
DB_NAME="OracleSagaDemo"
VCN_CIDR="10.0.0.0/16"
PUBLIC_SUBNET_CIDR="10.0.0.0/24"
INSTANCE_SHAPE="VM.Standard.E2.1.Micro"
INSTANCE_NAME="oracle-saga-compute-instance"
SSH_PUBLIC_KEY_PATH="$HOME/.ssh/cloudbank_key.pub"
CLOUD_INIT_FILE="./cloud-init.sh"
# -------------------------------------------------

echo ">>> Checking region and compartment..."
REGION=$(oci iam region-subscription list --query 'data[0]."region-name"' --raw-output)
echo "Detected region: $REGION"

if [[ ! -f "$SSH_PUBLIC_KEY_PATH" ]]; then
  echo ">>> Generating SSH key pair..."
  ssh-keygen -t rsa -b 2048 -f "${SSH_PUBLIC_KEY_PATH%.pub}" -N ""
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
mkdir -p /home/ubuntu/cloudbank
USER_SETUP

chown -R ubuntu:ubuntu /home/ubuntu/cloudbank
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

echo
echo "================= PROVISIONING COMPLETE ================="
echo "VCN OCID:       $VCN_ID"
echo "Subnet OCID:    $SUBNET_ID"
echo "ADB OCID:       $ADB_ID"
echo "Instance OCID:  $INSTANCE_ID"
echo "Instance IP:    $PUBLIC_IP"
echo "SSH:            ssh -i ${SSH_PUBLIC_KEY_PATH%.pub} ubuntu@$PUBLIC_IP"
echo "============================================================"
