-- Kupon contoh untuk pengujian checkout sandbox (ADM-002 UI menyusul).
-- TEST10 : 10% maks Rp10.000, min. transaksi Rp20.000, maks 100 penukaran.
-- HEMAT5000 : potongan tetap Rp5.000, min. transaksi Rp30.000.
INSERT INTO coupons (id, code, kind, value, max_discount_idr, min_transaction_idr, max_redemptions, max_per_user, allowed_packages)
VALUES
  ('55555555-1111-1111-1111-111111111111', 'TEST10', 'PERCENT', 10, 10000, 20000, 100, 2, '["free","silver","gold","platinum"]'::jsonb),
  ('55555555-1111-1111-1111-111111111112', 'HEMAT5000', 'FIXED', 5000, NULL, 30000, 50, 1, '["silver","gold","platinum"]'::jsonb);
