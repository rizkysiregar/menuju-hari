#!/usr/bin/env bash
# Membuat/memperbarui secret 'menujuhari-secrets' di cluster dari file .env lokal.
# Kredensial TIDAK PERNAH berada di Git. Jalankan ulang kapan pun nilainya berubah.
#
# Pemakaian: ./deploy/create-secrets.sh
# Prasyarat: kubectl ter-autentikasi ke cluster, file .env service terisi.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAMESPACE="menujuhari"

env_value() { # $1=file $2=key -> nilai (tanpa menampilkan ke log)
  grep -E "^$2=" "$1" | cut -d= -f2-
}

DB_PASSWORD="$(env_value "$REPO_ROOT/services/identity-service/.env" DB_PASSWORD)"
MAIL_USERNAME="$(env_value "$REPO_ROOT/services/identity-service/.env" MAIL_USERNAME)"
MAIL_PASSWORD="$(env_value "$REPO_ROOT/services/identity-service/.env" MAIL_PASSWORD)"
MIDTRANS_SERVER_KEY="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_SERVER_KEY)"
MIDTRANS_CLIENT_KEY="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_CLIENT_KEY)"
MIDTRANS_MERCHANT_ID="$(env_value "$REPO_ROOT/services/payment-service/.env" MIDTRANS_MERCHANT_ID)"
MINIO_ACCESS_KEY="$(env_value "$REPO_ROOT/services/media-service/.env" MINIO_ACCESS_KEY)"
MINIO_SECRET_KEY="$(env_value "$REPO_ROOT/services/media-service/.env" MINIO_SECRET_KEY)"

for pair in "DB_PASSWORD:$DB_PASSWORD" "MIDTRANS_SERVER_KEY:$MIDTRANS_SERVER_KEY" \
    "MAIL_USERNAME:$MAIL_USERNAME" "MINIO_ACCESS_KEY:$MINIO_ACCESS_KEY"; do
  KEY="${pair%%:*}"
  if [ -z "${pair#*:}" ]; then
    echo "Peringatan: $KEY kosong di .env — periksa sebelum lanjut."
  fi
done

kubectl -n "$NAMESPACE" create secret generic menujuhari-secrets \
  --from-literal="DB_PASSWORD=$DB_PASSWORD" \
  --from-literal="MAIL_USERNAME=$MAIL_USERNAME" \
  --from-literal="MAIL_PASSWORD=$MAIL_PASSWORD" \
  --from-literal="MIDTRANS_SERVER_KEY=$MIDTRANS_SERVER_KEY" \
  --from-literal="MIDTRANS_CLIENT_KEY=$MIDTRANS_CLIENT_KEY" \
  --from-literal="MIDTRANS_MERCHANT_ID=$MIDTRANS_MERCHANT_ID" \
  --from-literal="MINIO_ACCESS_KEY=$MINIO_ACCESS_KEY" \
  --from-literal="MINIO_SECRET_KEY=$MINIO_SECRET_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secret menujuhari-secrets diperbarui di namespace $NAMESPACE."
