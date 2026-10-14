#!/usr/bin/env bash
# One-time setup of a FRESH Ubuntu 24.04 server for the Apsonic stack. Run as root. READ IT FIRST.
#
#   DEPLOY_PUBKEY_FILE=/root/deploy_key.pub ./bootstrap-server.sh
#
# Does: OS updates, Docker Engine + Compose plugin, a non-root `deploy` user (key login only), firewall,
# brute-force protection, automatic security updates, log rotation for containers. It does NOT touch SSH settings
# for root: run harden-ssh.sh separately, only after you have confirmed that logging in as `deploy` works.
set -euo pipefail

: "${DEPLOY_PUBKEY_FILE:?path to the PUBLIC key (.pub) that may log in as the deploy user}"
DEPLOY_USER="${DEPLOY_USER:-deploy}"
APP_DIR="${APP_DIR:-/opt/apsonic}"
# Docker's own apt repo can be slow or blocked from some networks; Alibaba's mirror of it works on Alibaba Cloud.
DOCKER_APT_MIRROR="${DOCKER_APT_MIRROR:-https://mirrors.aliyun.com/docker-ce/linux/ubuntu}"
# In mainland China Docker Hub is unreachable without an accelerator: paste YOUR address from the Alibaba Cloud
# Container Registry console (looks like https://xxxxxxxx.mirror.aliyuncs.com). Leave empty elsewhere.
REGISTRY_MIRROR="${REGISTRY_MIRROR:-}"

[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
[[ -r "$DEPLOY_PUBKEY_FILE" ]] || { echo "Cannot read $DEPLOY_PUBKEY_FILE" >&2; exit 1; }
grep -qE '^(ssh-(ed25519|rsa)|ecdsa-sha2-)' "$DEPLOY_PUBKEY_FILE" || { echo "$DEPLOY_PUBKEY_FILE does not look like a public key (never use the private key here)." >&2; exit 1; }
. /etc/os-release
[[ "$ID" == "ubuntu" ]] || { echo "Written for Ubuntu; found $ID." >&2; exit 1; }

export DEBIAN_FRONTEND=noninteractive
echo "==> OS packages"
apt-get update -y
apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades

echo "==> Docker Engine"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL "$DOCKER_APT_MIRROR/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] $DOCKER_APT_MIRROR $VERSION_CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

echo "==> Docker daemon settings (log rotation so logs cannot fill the disk)"
mkdir -p /etc/docker
if [[ -n "$REGISTRY_MIRROR" ]]; then
  MIRRORS=", \"registry-mirrors\": [\"$REGISTRY_MIRROR\"]"
else
  MIRRORS=""
fi
cat > /etc/docker/daemon.json <<JSON
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" },
  "live-restore": true${MIRRORS}
}
JSON
systemctl enable --now docker
systemctl restart docker

echo "==> Deploy user"
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"   # NB: docker group access is effectively root on this host
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
install -m 600 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DEPLOY_PUBKEY_FILE" "/home/$DEPLOY_USER/.ssh/authorized_keys"
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/scripts"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /var/backups/apsonic

echo "==> Firewall (the Alibaba Cloud security group must ALSO allow only these ports)"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> Brute-force protection and automatic security updates"
cat > /etc/fail2ban/jail.d/sshd.local <<'JAIL'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
JAIL
systemctl enable --now fail2ban
systemctl restart fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades

cat <<DONE

Bootstrap finished. NEXT (do NOT close this root session yet):
  1. In a NEW terminal, confirm:   ssh -i <your private key> ${DEPLOY_USER}@<server-ip>   and then:   docker ps
  2. Only if that works, come back and run harden-ssh.sh to disable root and password logins.
  3. Copy the deploy files to ${APP_DIR} (see RUNBOOK.md).
DONE
