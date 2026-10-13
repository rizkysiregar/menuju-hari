# Panduan Deployment — Menuju Hari ke RKE2 (GitOps + Jenkins + Argo CD)

> Lingkungan Anda:
> - **Infra VM**: `192.168.1.50` — PostgreSQL (`:5433`), Redis (`:6379`), MinIO (`:9000`), (mungkin juga Jenkins & cloudflared)
> - **Master node RKE2**: `192.168.1.70`
> - **Worker node RKE2**: `192.168.1.71`
> - **Domain**: `rizkysiregar.my.id` di Cloudflare (tunnel aktif untuk `api-dev-feedzmore.rizkysiregar.my.id`)
> - **Repositori**: `menuju-hari` (monorepo aplikasi) & `homelabs-gitops` (manifest GitOps, di-watch Argo CD)

---

## 0. Gambaran alur (apa yang terjadi secara otomatis)

```
git push (monorepo)
   └─> Jenkins (webhook)
         ├─ mvn test (67 test, Testcontainers)
         ├─ docker build per service yang berubah
         ├─ docker push -> Docker Hub (rizkysiregar/menujuhari-<svc>:<sha12>)
         └─ yq update tag di homelabs-gitops -> git push
               └─> Argo CD mendeteksi commit -> sync ke cluster RKE2
                     └─> 6 pod berjalan di namespace "menujuhari"
                           └─> Cloudflare Tunnel -> bisa diakses internet
```

**Prinsip penting:** manifest Kubernetes TIDAK di-apply manual ke master node. Argo CD yang menarik dari GitHub. Satu-satunya langkah `kubectl` manual adalah membuat **Secret** (karena kredensial tidak pernah berada di Git).

---

## 1. Akses cluster — semua `kubectl` via SSH ke master node

Laptop **tidak perlu** kubectl/kubeconfig. Semua perintah cluster dijalankan langsung di master:

```bash
ssh rizky@192.168.1.70
sudo kubectl get nodes        # master 192.168.1.70 + worker 192.168.1.71
sudo kubectl get pods -n feedzmore   # bukti cluster + Argo CD aktif
```

> RKE2 menyediakan `kubectl` di `/usr/local/bin/kubectl` dengan kubeconfig `/etc/rancher/rke2/rke2.yaml` (hanya bisa dibaca root) — makanya pakai `sudo`. Berkas yang dibutuhkan di server dikirim via `scp`/SFTP (tahap 3).

---

## 2. Commit & push DUA repo (tidak perlu upload manifest ke master!)

### 2a. Monorepo `menuju-hari` → GitHub

```bash
cd ~/Documents/work/personal-project/menuju-hari
git status            # pastikan .env TIDAK muncul (gitignore sudah menutup)
git add -A
git commit -m "feat: backend microservices + pipeline"
git push origin main   # sesuaikan remote
```

### 2b. GitOps `homelabs-gitops` → GitHub

```bash
cd ~/Documents/work/personal-project/homelabs-gitops
git add apps/menujuhari
git commit -m "feat: manifests menujuhari (6 service + ingress)"
git push origin main
```

**Mengapa sekalian push GitOps sekarang?** Setelah Argo CD Application dibuat (tahap 4), ia langsung sync. Pod sementara akan `ImagePullBackOff` karena tag `000000000000` belum ada di Docker Hub — itu **normal** dan selesai sendiri setelah Jenkins build pertama (tahap 3) mem-push tag nyata dan meng-update manifest.

### Jawaban atas pertanyaan Anda
> "apakah saya harus upload dulu file gitops ke master node lalu apply manual?"

**Tidak.** File GitOps cukup berada di GitHub — Argo CD yang menarik dan menerapkan ke cluster. Satu-satunya berkas yang dikirim ke server adalah **file Secret** (tahap 3) — dan itu dihapus dari server setelah apply.

---

## 3. Buat Secret (SATU-SATUNYA langkah manual, via SFTP + SSH)

Kredensial tidak pernah berada di Git. Kemas dari `.env` lokal, kirim ke master, apply, hapus:

```bash
# 1) Di laptop — kemas .env menjadi manifest secret:
cd ~/Documents/work/personal-project/menuju-hari
./deploy/pack-secrets.sh
#    hasil: deploy/menujuhari-secrets.yaml (base64; tidak ter-track Git)

# 2) Kirim ke master node:
scp deploy/menujuhari-secrets.yaml rizky@192.168.1.70:/tmp/

# 3) Di master node:
ssh rizky@192.168.1.70
sudo kubectl apply -f /tmp/menujuhari-secrets.yaml   # namespace + secret sekaligus
sudo kubectl -n menujuhari get secret menujuhari-secrets
rm /tmp/menujuhari-secrets.yaml                      # penting: hapus dari server
```

Script `pack-secrets.sh` mengambil: `DB_PASSWORD` (identity), `MIDTRANS_*` (payment), `MAIL_USERNAME/PASSWORD` (identity), `MINIO_*` (media) — semuanya dari file `.env` yang sudah Anda isi. Jalankan ulang + ulangi kirim-apply kapan pun kredensial berubah (mis. saat pindah ke Midtrans produksi).

> Upgrade keamanan berikutnya (opsional): Sealed Secrets atau SOPS agar secret juga bisa berada di Git terenkripsi. Untuk sekarang, "secret di cluster + tidak di Git + file sementara dihapus" sudah memadai dan jauh lebih baik daripada plaintext di repo.

---

## 4. Jenkins job (build pertama)

1. Jenkins → **New Item** → pilih **Multibranch Pipeline** (atau Pipeline) → nama `menujuhari`
2. Source: GitHub repo `menuju-hari` + webhook dari GitHub ke Jenkins (sama seperti feedzmore)
3. Script Path: `Jenkinsfile` (di root repo — sudah ada)
4. Credentials `dockerhub` dan `github-all-repo-pat` **dipakai ulang** dari feedzmore (tidak perlu buat baru)
5. Klik **Build Now** untuk build pertama (webhook baru bekerja untuk push berikutnya)

Yang terjadi saat build:
- Test: 67 test via Testcontainers (butuh docker.sock bisa diakses — sudah diatasi dengan `--network=host` + `--group-add`)
- Build & push 6 image: `rizkysiregar/menujuhari-{identity,invitation,catalog,media,order,payment}:<sha12>`
- Commit otomatis di `homelabs-gitops`: `deploy(menujuhari): <sha12> [identity,...]`

**Cek:** Docker Hub menampilkan 6 repo baru; `homelabs-gitops` berkomitmen tag baru.

---

## 5. Argo CD Application

UI Argo CD → **New App**:
- Application Name: `menujuhari`
- Project: `default`
- Sync Policy: **Automatic** + **Self Heal** (Prune boleh diaktifkan)
- Source Repo: `https://github.com/rizkysiregar/homelabs-gitops` — Path: `apps/menujuhari`
- Destination: Cluster `in-cluster` — Namespace: `menujuhari`

Setelah sync:

```bash
sudo kubectl -n menujuhari get pods -w   # di master node (via SSH)
# 6 pod: identity, invitation, catalog, media, order, payment — semua Running 1/1
sudo kubectl -n menujuhari get svc,ingress
```

Jika semua Running: **Flyway sudah migrasi ke DB VM** (cek log pod mana pun: `Successfully applied ... migrations`).

---

## 6. Cloudflare Tunnel → Ingress

Lihat dulu rule feedzmore (acuan):
**Zero Trust → Networks → Tunnels → tunnel Anda → Public Hostname** → temukan `api-dev-feedzmore.rizkysiregar.my.id` → catat **Service** yang dituju (biasanya `http://192.168.1.70:80` atau `http://192.168.1.71:80` — port 80 host node tempat Ingress NGINX RKE2 menempel).

Tambahkan rule baru dengan **pola yang sama**:
- Hostname: `api-dev-menujuhari.rizkysiregar.my.id`
- Service: `http://192.168.1.70:80` (ikutkan IP yang sama dengan feedzmore)

(Aturan tunnel juga bisa diubah lewat file `config.yml` di VM tempat cloudflared berjalan + restart `cloudflared` — pilih cara yang sama seperti yang Anda pakai feedzmore.)

Verifikasi dari luar:

```bash
curl https://api-dev-menujuhari.rizkysiregar.my.id/payments/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
curl https://api-dev-menujuhari.rizkysiregar.my.id/catalog/v1/catalog/packages | head
# daftar paket dari DB VM
```

Swagger dari cluster: `https://api-dev-menujuhari.rizkysiregar.my.id/payments/docs`, `.../orders/docs`, dst.

---

## 7. Midtrans Webhook

Dashboard Midtrans (sandbox) → **Settings → Configuration**:

| Kolom | Nilai |
|---|---|
| Payment Notification URL | `https://api-dev-menujuhari.rizkysiregar.my.id/payments/v1/webhooks/payments/midtrans` |
| Finish/Unfinish/Error (opsional, untuk nanti FE) | `https://menujuhari.rizkysiregar.my.id/checkout/finish` dsb. |

Klik **Test URL** → harus sukses. Payload uji punya signature dummy — payment-service tetap menjawab 2xx (event tercatat untuk audit, tidak diproses), jadi test Midtrans lolos.

Sejak titik ini: setiap pembayaran sandbox → webhook otomatis → order PAID → undangan PUBLISHED tanpa intervensi.

---

## 8. Uji End-to-End di cluster (alur pelanggan sejati)

```bash
BASE=https://api-dev-menujuhari.rizkysiregar.my.id

# 1. Login OTP
curl -s -X POST $BASE/identity/v1/auth/otp/request -H "Content-Type: application/json" \
  -d '{"email":"uji@example.com"}'
# buka email -> kode 6 digit
curl -s -c /tmp/ck.txt -X POST $BASE/identity/v1/auth/otp/verify -H "Content-Type: application/json" \
  -d '{"email":"uji@example.com","code":"<KODE>"}'

# 2. Buat undangan + checkout + bayar
INV=$(curl -s -b /tmp/ck.txt -X POST $BASE/invitation/v1/invitations -H "Content-Type: application/json" \
  -d '{"templateCode":"aurora","brideName":"Ayu","groomName":"Fajar","eventDate":"2027-03-20"}')
INV_ID=$(echo $INV | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

ORD=$(curl -s -b /tmp/ck.txt -H "Idempotency-Key: uji-1" -X POST $BASE/orders/v1/checkout \
  -H "Content-Type: application/json" \
  -d "{\"invitationId\":\"$INV_ID\",\"packageCode\":\"gold\",\"couponCode\":\"TEST10\"}")
ORDER=$(echo $ORD | python3 -c "import json,sys;print(json.load(sys.stdin)['orderNumber'])")
echo "order: $ORDER (Gold Rp89.100 setelah kupon)"

PAY=$(curl -s -b /tmp/ck.txt -X POST $BASE/orders/v1/orders/$ORDER/pay)
echo $PAY | python3 -c "import json,sys;print(json.load(sys.stdin)['redirectUrl'])"
# 3. Buka redirectUrl di browser -> bayar dengan kartu uji:
#    4811 1111 1111 1114, CVV 123, expiry bebas, OTP 3DS sandbox 112233
# 4. Webhook masuk otomatis -> cek:
curl -s -b /tmp/ck.txt $BASE/orders/v1/orders/$ORDER          # status: PAID
curl -s -b /tmp/ck.txt $BASE/invitation/v1/invitations/$INV_ID  # status: PUBLISHED + expiresAt
```

---

## 9. Troubleshooting cepat

| Gejala | Penyebab umum | Periksa |
|---|---|---|
| `CreateContainerConfigError` | Secret belum dibuat | Tahap 3: `./deploy/pack-secrets.sh` lalu apply di master |
| `InvalidAccessKeyId` (MinIO) / kredensial DB salah | Secret berisi nilai lama (mis. kredensial lokal) atau pod belum di-restart | Cek `sudo kubectl -n menujuhari logs deploy/media-service` baris `MinIO: endpoint=… accessKeyPrefix=…`; bandingkan dengan kredensial VM; perbaiki `.env` → pack → apply → **`sudo kubectl -n menujuhari rollout restart deployment/<svc>`** (env Secret hanya dibaca saat container dibuat — `apply` saja tidak menyegarkan pod lama) |
| `ImagePullBackOff` | Tag belum ada di Docker Hub | Jalankan Jenkins build dulu; cek nama image di Docker Hub |
| `CrashLoopBackOff` saat start | DB tak reachable dari node, Flyway gagal | `sudo kubectl -n menujuhari logs deploy/identity-service` (via SSH master); pastikan PostgreSQL VM menerima koneksi dari `192.168.1.70/.71` (firewall VM + `pg_hba.conf`) |
| Readiness 0/1 | Redis/DB tidak terjangkau | `kubectl -n menujuhari exec -it deploy/identity-service -- sh -c 'command -v nc && nc -zv 192.168.1.50 6379'` |
| Ingress 404 | Host/path tidak match | `kubectl -n menujuhari get ingress`; samakan hostname di Cloudflare & curl `-H "Host: ..."` |
| Testcontainers gagal di Jenkins | docker.sock tidak terbaca | `docker exec <jenkins-container> ls -la /var/run/docker.sock`; pastikan `--network=host` + `--group-add` di stage Test |
| Webhook Midtrans gagal | URL salah / tunnel mati | Cek `payment_events` di `payment_db` (tabel audit) + dashboard Midtrans log |

---

## 10. Peta referensi cepat

| Service | Port pod | Path ingress | DB (VM :5433) | Image Docker Hub |
|---|---|---|---|---|
| identity | 8081 | `/identity` | identity_db | rizkysiregar/menujuhari-identity |
| invitation | 8082 | `/invitation` | invitation_db | rizkysiregar/menujuhari-invitation |
| catalog | 8083 | `/catalog` | catalog_db | rizkysiregar/menujuhari-catalog |
| media | 8084 | `/media` | media_db | rizkysiregar/menujuhari-media |
| order | 8085 | `/orders` | order_db | rizkysiregar/menujuhari-order |
| payment | 8086 | `/payments` | payment_db | rizkysiregar/menujuhari-payment |

Semua service: PostgreSQL & Redis & MinIO di `192.168.1.50`; event stream via Redis `mh:stream:*`; secret `menujuhari-secrets` (namespace `menujuhari`).

---

*Setelah tahap 7 sukses: deployment beres. Lanjut: notification-service (email struk + relokasi OTP), API publik tamu, lalu hardening pra-launch.*
