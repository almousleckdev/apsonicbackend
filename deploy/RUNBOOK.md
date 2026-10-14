# Apsonic — deployment runbook (single Ubuntu 24.04 server)

Everything here was written for one Alibaba Cloud ECS instance and tested locally with the same Docker images and
compose file. **Nothing in this folder has been run on your server.** Read each script before you run it.

```
Internet ──443/80──▶ Caddy (HTTPS, auto certificates)
                       └─▶ frontend (Next.js) ──▶ backend (Spring Boot) ──▶ postgres
                                                                     └──▶ redis
Only Caddy has published ports. postgres / redis / backend are on an internal network with no internet access.
```

## 0. Decide / prepare first

| Item | Why it matters |
|---|---|
| **Domain + DNS** | Create an `A` record `www.yourdomain` → the server IP. Caddy cannot get a certificate without it. |
| **Region and ICP filing** | If the ECS is in **mainland China**, a domain served on ports 80/443 must have an ICP filing or Alibaba blocks the traffic. A Hong Kong/overseas region does not need one. Check this before anything else. |
| **Security group** (Alibaba console) | Allow inbound only: 22 from **your own IP**, 80, 443. Nothing else — never 5432/6379/8080/3000. |
| **SSH key** | `ssh-keygen -t ed25519 -f ~/.ssh/apsonic_deploy` on your laptop. Send only the `.pub` file anywhere. |
| **Where images come from** | Option A — a registry (Alibaba Container Registry, recommended: layers are cached, a code-only release is a few MB). Option B — copy images over SSH (section 4). |
| **Mapbox token** | It is public by design (shipped to browsers). Restrict it by URL to your domain in the Mapbox dashboard. |
| **Docker Hub access** | From mainland China Docker Hub is blocked; set `REGISTRY_MIRROR` (your accelerator address from the Alibaba Container Registry console) when running the bootstrap script. |

## 1. Prepare the server (as root, once)

On your laptop, copy the script and the **public** key up:
```bash
scp deploy/scripts/bootstrap-server.sh ~/.ssh/apsonic_deploy.pub root@SERVER_IP:/root/
```
On the server:
```bash
less bootstrap-server.sh                     # read it
DEPLOY_PUBKEY_FILE=/root/apsonic_deploy.pub bash bootstrap-server.sh
#   mainland China:  REGISTRY_MIRROR=https://xxxx.mirror.aliyuncs.com DEPLOY_PUBKEY_FILE=... bash bootstrap-server.sh
```
It installs Docker, creates the `deploy` user (key login only), enables the firewall, fail2ban and automatic security updates.

**Do not close the root session yet.** In a *new* terminal:
```bash
ssh -i ~/.ssh/apsonic_deploy deploy@SERVER_IP
docker ps            # should print an empty table, not an error
```
Only if that works, go back to the root session and lock SSH down:
```bash
bash harden-ssh.sh   # disables root and password login
```
Then confirm in another terminal that `ssh root@SERVER_IP` is **refused** and `deploy@` still works.
(If you ever lose access: use the Alibaba console's *Remote Connection / VNC* and delete
`/etc/ssh/sshd_config.d/00-apsonic-hardening.conf`.)

## 2. Put the deployment files on the server (as `deploy`)

```bash
scp -i ~/.ssh/apsonic_deploy deploy/docker-compose.prod.yml deploy/Caddyfile deploy/.env.production.example deploy@SERVER_IP:/opt/apsonic/
scp -i ~/.ssh/apsonic_deploy deploy/scripts/*.sh deploy@SERVER_IP:/opt/apsonic/scripts/
ssh -i ~/.ssh/apsonic_deploy deploy@SERVER_IP 'chmod +x /opt/apsonic/scripts/*.sh'
```

## 3. Create the secrets file (on the server)

```bash
cd /opt/apsonic
cp .env.production.example .env.production && chmod 600 .env.production
nano .env.production
```
Fill in the domain, e-mail and the three secrets, generating **fresh** values (never reuse anything from development):
```bash
openssl rand -base64 48                      # JWT_SECRET
openssl rand -base64 24 | tr -d '/+='        # DB_PASSWORD   and again for REDIS_PASSWORD
```
Set `BOOTSTRAP_ADMIN_*` (password ≥ 12 characters, mixed). There is no default account.

## 4. Get the images onto the server

**Option A — registry (recommended).** Build in CI (the workflows in this repo build and scan them), push to your
registry, then on the server: `docker login <registry>` once, and `deploy.sh` pulls what it needs.

**Option B — no registry.** Build for the server's CPU (x86_64) on your laptop and stream the images:
```bash
TAG=$(git rev-parse --short HEAD)
docker buildx build --platform linux/amd64 -t apsonic-backend:$TAG --load backend/
docker buildx build --platform linux/amd64 -t apsonic-frontend:$TAG --load apsonicui/ \
   --build-arg NEXT_PUBLIC_MAPBOX_TOKEN=<your public token>
docker save apsonic-backend:$TAG apsonic-frontend:$TAG | gzip | ssh -i ~/.ssh/apsonic_deploy deploy@SERVER_IP 'gunzip | docker load'
```
(An Apple-silicon Mac builds amd64 under emulation: slow but works. The frontend image is ~1 GB because of the product media.)

## 5. Deploy

```bash
cd /opt/apsonic
./scripts/deploy.sh apsonic-backend:$TAG apsonic-frontend:$TAG        # or registry/image:tag names
```
The script validates the config, starts everything, waits until all services are healthy, and **rolls back automatically**
to the previous release if the new one does not become healthy. First start takes about a minute (database migrations run).

Then:
1. Open `https://your-domain` — the certificate is obtained automatically on first request.
2. Sign in at `/admin/login` with the bootstrap admin.
3. **Remove `BOOTSTRAP_ADMIN_PASSWORD` from `.env.production`** and run `docker compose -p apsonic --env-file .env.production --env-file release.env -f docker-compose.prod.yml up -d backend` to apply.
4. Set up backups (section 7) **before** adding real content.

### Moving existing development content (optional)
Your development database and `backend/uploads` are separate from this new, empty production database.
- Uploaded files: `rsync -a backend/uploads/ deploy@SERVER_IP:/tmp/uploads/` then copy into the volume:
  `docker run --rm -v apsonic_uploads:/data -v /tmp/uploads:/src:ro alpine sh -c 'cp -a /src/. /data/ && chown -R 10001:10001 /data'`
- Content tables (products, categories, news, pages): dump **only those tables** from the dev database and restore them
  on the server. Do **not** copy `users`, `roles` or `refresh_tokens` (development accounts and the retired default
  password hash). Test on a scratch database first (`restore-db.sh`).

## 6. Releasing a change / rolling back

```bash
./scripts/deploy.sh <new-backend-image> <new-frontend-image>     # previous release is remembered
./scripts/rollback.sh                                            # go back to the previous release
```
Rollback restores the previous *images*. Database migrations only move forward: if a release changed the schema and the
old code cannot run on it, restore a backup instead (section 7).

## 7. Backups (do this on day one)

```bash
crontab -e      # as deploy
30 2 * * *  OFFSITE_CMD='ossutil cp {} oss://YOUR-BUCKET/apsonic/' /opt/apsonic/scripts/backup.sh >> /var/backups/apsonic/backup.log 2>&1
```
- **A backup on the same disk is not a backup.** Set `OFFSITE_CMD` (an Alibaba OSS bucket with a write-only RAM user is ideal).
- Check it worked: `ls -lh /var/backups/apsonic`, and **test a restore into a scratch database** every month:
  `./scripts/restore-db.sh /var/backups/apsonic/db-<stamp>.dump` — compares row counts, never touches the live database.
- Restoring *over* the live database is deliberately manual: stop the backend (`docker compose ... stop backend frontend`),
  `pg_restore --clean --if-exists` into the live database from the dump, start again, and expect to lose everything since the backup.

## 8. Everyday commands

```bash
cd /opt/apsonic
alias dc='docker compose -p apsonic --env-file .env.production --env-file release.env -f docker-compose.prod.yml'
dc ps                       # status and health
dc logs -f --tail 100 backend
dc logs --since 1h caddy | grep -i error
df -h /; docker system df   # disk
docker image prune -a --filter "until=720h"   # old images, monthly
```

## 9. Secret rotation

| Secret | Effect and steps |
|---|---|
| `JWT_SECRET` | Everyone is signed out. Edit `.env.production`, `dc up -d backend`. |
| `REDIS_PASSWORD` | Edit, then `dc up -d redis backend`. Rate-limit counters and token revocations reset. |
| `DB_PASSWORD` | Change inside Postgres first (`ALTER USER ... PASSWORD ...`), then edit `.env.production`, then `dc up -d backend`. Changing only the file breaks login to the database. |

## 10. Known limits — be aware

- **One server = one point of failure.** No automatic failover; downtime during OS reboots and releases is seconds, but a
  lost server means restoring from backup. Managed databases (ApsaraDB) and OSS for uploads are the next step up.
- **Uploads live on local disk** (the `uploads` volume). Fine for one server; must move to OSS before running two.
- **Image optimiser load:** the first view of each product image is resized on the server (2 vCPU). The cache survives
  restarts (`next_cache` volume); expect slower first loads after a fresh deploy.
- Members of the `docker` group are effectively root on the host: keep `deploy` to people you trust.
- CPU/RAM/disk and uptime alerts are **not** configured; add Alibaba CloudMonitor alarms (CPU, memory, disk > 80 %, HTTP check on `/`).
- The CI workflows and these scripts have not run against your real server or a real GitHub remote yet.

## Troubleshooting

| Symptom | Look at |
|---|---|
| Site shows certificate error / no HTTPS | DNS `A` record correct? Ports 80/443 open in the **security group** and `ufw`? `dc logs caddy` |
| Backend restarts in a loop | `dc logs backend` — usually a missing/weak secret (it refuses to start on purpose) or wrong DB password |
| Login works but session drops | `PUBLIC_ORIGIN` must equal the exact site origin (`https://www.example.com`, no trailing slash) |
| 429 Too Many Requests for everyone | Rate limiting sees one client IP: check Caddy is the only thing in front and the backend is not exposed directly |
| `no space left on device` | `docker system df`, prune old images, check `/var/backups/apsonic` |
