#!/usr/bin/env bash
# Kemas secret menjadi file manifest (untuk dikirim via SFTP ke master node).
# Jalankan di LAPTOP:  ./deploy/pack-secrets.sh
# Lalu ikuti instruksi yang tercetak untuk scp + kubectl apply di master.
#
# File hasil: deploy/menujuhari-secrets.yaml — berisi nilai base64 (BUKAN
# enkripsi). Jangan di-commit (sudah di-gitignore) dan hapus dari server
# setelah apply.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/deploy/menujuhari-secrets.yaml"
NAMESPACE="menujuhari"

env_value() { grep -E "^$2=" "$1" | cut -d= -f2-; }

DB_PASSWORD="$(env_value "$REPO_ROOT/services/identity-service/.env" DB_PASSWORD)"
MAIL_USERNAME="$(env_value "$REPO_ROOT/services/identity-service/.env" MAIL_USERNAME)"
MAIL_PASSWORD="$(env_value "$REPO_ROOT/services/identity-service/.env" MAIL_PASSWORD)"
MIDTRANS_SERVER_KEY="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_SERVER_KEY)"
MIDTRANS_CLIENT_KEY="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_CLIENT_KEY)"
MIDTRANS_MERCHANT_ID="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_MERCHANT_ID)"
MINIO_ACCESS_KEY="$(env_value "$REPO_ROOT/services/media-service/.env" MINIO_ACCESS_KEY)"
MINIO_SECRET_KEY="$(env_value "$REPO_ROOT/services/media-service/.env" MINIO_SECRET_KEY)"

MISSING=0
check() { # nama nilai
  if [ -z "${1:-}" ]; then echo "  PERINGATAN: $2 kosong di .env"; MISSING=1; fi
}
check "$DB_PASSWORD" DB_PASSWORD
check "$MAIL_USERNAME" MAIL_USERNAME
check "$MAIL_PASSWORD" MAIL_PASSWORD
check "$MIDTRANS_SERVER_KEY" MIDTRANS_SERVER_KEY
check "$MINIO_ACCESS_KEY" MINIO_ACCESS_KEY
[ "$MISSING" = "1" ] && echo "-> Lanjutkan tetap? (nilai kosong akan membuat service gagal start)" || true

b64() { printf '%s' "$1" | base64; }

cat > "$OUT" <<EOF
# Hasil generate deploy/pack-secrets.sh — SIFATNYA RAHASIA.
# Hapus dari server setelah apply. Jangan pernah di-commit.
apiVersion: v1
kind: Namespace
metadata:
  name: $NAMESPACE
---
apiVersion: v1
kind: Secret
metadata:
  name: menujuhari-secrets
  namespace: $NAMESPACE
type: Opaque
data:
  DB_PASSWORD: $(b64 "$DB_PASSWORD")
  MAIL_USERNAME: $(b64 "$MAIL_USERNAME")
  MAIL_PASSWORD: $(b64 "$MAIL_PASSWORD")
  MIDTRANS_SERVER_KEY: $(b64 "$MIDTRANS_SERVER_KEY")
  MIDTRANS_CLIENT_KEY: $(b64 "$MIDTRANS_CLIENT_KEY")
  MIDTRANS_MERCHANT_ID: $(b64 "$MIDTRANS_MERCHANT_ID")
  MINIO_ACCESS_KEY: $(b64 "$MINIO_ACCESS_KEY")
  MINIO_SECRET_KEY: $(b64 "$MINIO_SECRET_KEY")
EOF

chmod 600 "$OUT"
echo "File terkemas: $OUT"
echo ""
echo "Langkah berikutnya:"
echo "  1) scp $OUT rizky@192.168.1.70:/tmp/"
echo "  2) ssh rizky@192.168.1.70"
echo "  3) sudo kubectl apply -f /tmp/menujuhari-secrets.yaml"
echo "  4) rm /tmp/menujuhari-secrets.yaml   # hapus dari server"
echo "  5) sudo kubectl -n $NAMESPACE get secret"
