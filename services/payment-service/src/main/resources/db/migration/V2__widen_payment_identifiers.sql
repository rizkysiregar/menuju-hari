-- payment-service: perlebar kolom identitas pihak ketiga.
-- Notifikasi uji dari Midtrans memakai order_id sintetis yang lebih panjang
-- dari batas transaksi normal (50 karakter), mis.
-- "payment_notif_test_G459997237_9923e226-5d80-49e6-a84a-581a776d9252" (66 char).
-- Migrasi bersifat expand-only (aman untuk rolling update).

ALTER TABLE payments       ALTER COLUMN gateway_order_id TYPE VARCHAR(128);
ALTER TABLE payment_events ALTER COLUMN gateway_order_id TYPE VARCHAR(128);
ALTER TABLE payment_events ALTER COLUMN order_number     TYPE VARCHAR(128);
