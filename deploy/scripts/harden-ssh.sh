#!/usr/bin/env bash
# Disable root and password logins over SSH. Run as root, ONLY after you have logged in as the deploy user with
# your key in a separate terminal. If that works, this is safe; if not, you could lock yourself out.
set -euo pipefail
DEPLOY_USER="${DEPLOY_USER:-deploy}"
[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
KEYS="/home/$DEPLOY_USER/.ssh/authorized_keys"
[[ -s "$KEYS" ]] || { echo "Refusing: $KEYS is missing or empty - nobody could log in afterwards." >&2; exit 1; }

# sshd uses the FIRST value it sees for a setting and reads sshd_config.d/*.conf in name order, so this file must sort
# before cloud-init's 50-cloud-init.conf (which on cloud images often re-enables password login).
cat > /etc/ssh/sshd_config.d/00-apsonic-hardening.conf <<'CONF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
X11Forwarding no
MaxAuthTries 3
LoginGraceTime 30
CONF
sshd -t
systemctl reload ssh
echo "SSH hardened. Keep this session open and verify in another terminal that:"
echo "  - ssh ${DEPLOY_USER}@<server-ip> still works"
echo "  - ssh root@<server-ip> is now refused"
echo "If anything is wrong, remove /etc/ssh/sshd_config.d/00-apsonic-hardening.conf and run: systemctl reload ssh"
