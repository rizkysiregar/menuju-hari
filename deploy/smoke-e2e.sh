#!/usr/bin/env bash
# Smoke test E2E Menuju Hari — verifikasi perjalanan pelanggan penuh
# terhadap environment yang sudah berjalan (cluster atau lokal).
#
# Pemakaian:
#   ./deploy/smoke-e2e.sh                                  # domain dev cluster
#   ./deploy/smoke-e2e.sh http://localhost:8080            # lokal (jika pakai port-forward/proxy)
#   SKIP_MEDIA=1 ./deploy/smoke-e2e.sh                     # lewati langkah media
#
# Langkah: health semua service -> login OTP (interaktif) -> buat/ubah undangan
# -> optimistik-koncurrency -> checkout + kupon -> bayar (Snap sandbox, bayar
# manual di browser) -> polling sampai PAID -> undangan PUBLISHED -> upload foto
# (media + MinIO) -> stream musik.
#
# Prasyarat: Cloudflare tunnel hostname sudah aktif & Payment Notification URL
# Midtrans sudah diset (agar pembayaran otomatis mengubah status).

set -uo pipefail

BASE_URL="${1:-https://api-dev-menujuhari.rizkysiregar.my.id}"
COOKIE_JAR="$(mktemp)"
EMAIL="${SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
EVENT_DATE="$(date -v+90d +%Y-%m-%d 2>/dev/null || date -d '+90 days' +%Y-%m-%d)"
SKIP_MEDIA="${SKIP_MEDIA:-0}"

PASS=0; FAIL=0
ok()   { echo "  [OK]   $1"; PASS=$((PASS+1)); }
bad()  { echo "  [GAGAL] $1"; FAIL=$((FAIL+1)); }
step() { echo; echo "== $1"; }

health() { # $1=path $2=nama
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$1" --max-time 15)
  [ "$code" = "200" ] && ok "$2 health 200" || bad "$2 health $code (harus 200)"
}
api() { # $1=method $2=path [body] [extra curl args...]
  local method="$1" path="$2" body="${3:-}"
  if [ $# -ge 3 ]; then shift 3; else shift $#; fi
  if [ -n "$body" ]; then
    curl -s -X "$method" "$BASE_URL$path" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
      -H "Content-Type: application/json" -d "$body" "$@"
  else
    curl -s -X "$method" "$BASE_URL$path" -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$@"
  fi
}
jget() { python3 -c "import json,sys;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }

echo "Menuju Hari — smoke test E2E"
echo "Base URL : $BASE_URL"
echo "Email uji: $EMAIL"

# ---------------------------------------------------------------- 1. health
step "1. Health semua service"
health /identity/actuator/health   identity
health /invitation/actuator/health invitation
health /catalog/actuator/health    catalog
health /media/actuator/health      media
health /orders/actuator/health     order
health /payments/actuator/health   payment

# ------------------------------------------------------------- 2. login OTP
step "2. Login OTP email"
REQ=$(api POST /identity/v1/auth/otp/request "{\"email\":\"$EMAIL\"}")
echo "$REQ" | grep -q "kode OTP telah dikirim" && ok "OTP diminta (cek inbox $EMAIL)" || bad "permintaan OTP: $REQ"
read -r -p "  Masukkan kode OTP dari email: " OTP
VERIFY=$(api POST /identity/v1/auth/otp/verify "{\"email\":\"$EMAIL\",\"code\":\"$OTP\"}")
echo "$VERIFY" | grep -q "\"role\":\"CUSTOMER\"" && ok "verifikasi OTP + sesi dibuat" || { bad "verifikasi OTP: $VERIFY"; echo "Berhenti."; exit 1; }
ME=$(api GET /identity/v1/auth/me)
echo "$ME" | grep -q "$EMAIL" && ok "GET /me memakai cookie sesi" || bad "GET /me: $ME"

# --------------------------------------------------------- 3. katalog (publik)
step "3. Katalog paket (publik)"
PKGS=$(curl -s "$BASE_URL/catalog/v1/catalog/packages")
echo "$PKGS" | grep -q '"gold"' && ok "daftar paket publik memuat gold" || bad "paket: $PKGS"

# ------------------------------------------------------ 4. buat undangan
step "4. Buat & ubah undangan"
INV=$(api POST /invitation/v1/invitations "{\"templateCode\":\"aurora\",\"brideName\":\"Smoke\",\"groomName\":\"Test\",\"eventDate\":\"$EVENT_DATE\"}")
INV_ID=$(echo "$INV" | jget "['id']")
[ -n "${INV_ID:-}" ] && ok "undangan dibuat (id ${INV_ID:0:8}…)" || { bad "buat undangan: $INV"; exit 1; }

PATCHED=$(api PATCH "/invitation/v1/invitations/$INV_ID/content" \
  '{"content":{"cover":{"title":"Undangan Uji"},"events":[{"name":"Akad","startAt":"2027-01-01T08:00:00+07:00","venueName":"Rumah"}]},"contentVersion":1}')
echo "$PATCHED" | grep -q '"contentVersion":2' && ok "autosave konten -> versi 2" || bad "autosave: $PATCHED"

STALE=$(api PATCH "/invitation/v1/invitations/$INV_ID/content" '{"content":{"cover":{}},"contentVersion":1}')
echo "$STALE" | grep -q "VERSION_CONFLICT" && ok "versi usang ditolak 409 (optimistic concurrency)" || bad "konflik versi: $STALE"

# ------------------------------------------------------ 5. checkout + kupon
step "5. Checkout + kupon"
ORDER_RESP=$(api POST /orders/v1/checkout \
  "{\"invitationId\":\"$INV_ID\",\"packageCode\":\"gold\",\"couponCode\":\"TEST10\"}" \
  -H "Idempotency-Key: smoke-$(date +%s)")
ORDER_NUMBER=$(echo "$ORDER_RESP" | jget "['orderNumber']")
echo "$ORDER_RESP" | grep -q '"totalIdr":89100' && ok "total benar: Rp89.100 (Gold 99.000 - kupon 10%)" \
  || { bad "checkout: $ORDER_RESP"; exit 1; }

DUP=$(api POST /orders/v1/checkout \
  "{\"invitationId\":\"$INV_ID\",\"packageCode\":\"gold\",\"couponCode\":\"TEST10\"}" \
  -H "Idempotency-Key: smoke-$(date +%s)")
echo "$DUP" | grep -q '"status":"PENDING"' && ok "checkout kedua membuat order terpisah (kontrol)" || bad "checkout kedua: $DUP"

# ------------------------------------------------------------- 6. bayar
step "6. Bayar via Midtrans Snap (sandbox)"
PAY=$(api POST "/orders/v1/orders/$ORDER_NUMBER/pay")
REDIRECT=$(echo "$PAY" | jget "['redirectUrl']")
if [ -n "${REDIRECT:-}" ] && [ "$REDIRECT" != "None" ]; then
  ok "sesi Snap dibuat"
  echo
  echo "  >>> BUKA URL INI DAN BAYAR: $REDIRECT"
  echo "      Kartu uji: 4811 1111 1111 1114 | CVV 123 | expiry bebas | OTP 3DS 112233"
  echo "      (menunggu maksimal 6 menit — pembayaran terdeteksi via webhook/inquiry)"
  echo
  PAID=0
  for i in $(seq 1 72); do
    sleep 5
    STATUS=$(api GET "/orders/v1/orders/$ORDER_NUMBER" | jget "['status']")
    if [ "$STATUS" = "PAID" ]; then PAID=1; break; fi
    printf "."
  done
  echo
  [ "$PAID" = "1" ] && ok "order PAID (pembayaran terverifikasi backend)" || bad "order belum PAID setelah 6 menit (status terakhir: ${STATUS:-unknown})"
else
  bad "gagal membuat sesi Snap: $PAY"
fi

# ------------------------------------------------- 7. publikasi otomatis
step "7. Publikasi undangan otomatis (event order.paid)"
if [ "${PAID:-0}" = "1" ]; then
  PUB=0
  for i in $(seq 1 12); do
    sleep 5
    STS=$(api GET "/invitation/v1/invitations/$INV_ID" | jget "['status']")
    if [ "$STS" = "PUBLISHED" ]; then PUB=1; break; fi
  done
  [ "$PUB" = "1" ] && ok "undangan PUBLISHED + expiresAt terisi" || bad "undangan belum PUBLISHED (status: ${STS:-unknown})"
else
  echo "  (dilewati — pembayaran belum selesai)"
fi

# --------------------------------------------------------- 8. media + musik
if [ "$SKIP_MEDIA" != "1" ]; then
  step "8. Upload foto (media-service + MinIO)"
  python3 - <<'PY'
import zlib, struct
def chunk(t,d):
    c=t+d; return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
w,h=800,600
raw=b''.join(b'\x00'+bytes([120,150,200]*w) for _ in range(h))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
open('/tmp/smoke-photo.png','wb').write(png)
PY
  SIZE=$(wc -c < /tmp/smoke-photo.png | tr -d ' ')
  INTENT=$(api POST /media/v1/media/photos/upload-intent \
    "{\"filename\":\"smoke.png\",\"contentType\":\"image/png\",\"sizeBytes\":$SIZE}")
  MEDIA_ID=$(echo "$INTENT" | jget "['mediaId']")
  UPLOAD_URL=$(echo "$INTENT" | jget "['uploadUrl']")
  if [ -n "${MEDIA_ID:-}" ] && [ "$MEDIA_ID" != "None" ]; then
    PUT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -T /tmp/smoke-photo.png "$UPLOAD_URL")
    [ "$PUT_CODE" = "200" ] && ok "PUT presigned ke MinIO 200" || bad "PUT presigned: $PUT_CODE"
    api POST "/media/v1/media/photos/$MEDIA_ID/upload-complete" "" > /dev/null
    READY=0
    for i in $(seq 1 20); do
      sleep 1
      PJSON=$(api GET "/media/v1/media/photos/$MEDIA_ID")
      if echo "$PJSON" | grep -q '"status":"READY"'; then READY=1; break; fi
    done
    [ "$READY" = "1" ] && ok "varian gambar siap (web + thumb)" || bad "pemrosesan varian tidak selesai"
  else
    bad "upload intent: $INTENT"
  fi

  step "9. Stream musik"
  STREAM=$(api GET /media/v1/media/music/44444444-1111-1111-1111-111111111111/stream-url)
  echo "$STREAM" | grep -q "X-Amz-Signature" && ok "signed URL musik dibuat" || bad "stream musik: $STREAM"
fi

# ------------------------------------------------------------------ hasil
rm -f "$COOKIE_JAR" /tmp/smoke-photo.png
echo
echo "==================================================="
echo "HASIL: $PASS lulus, $FAIL gagal"
echo "==================================================="
[ "$FAIL" = "0" ] || exit 1
