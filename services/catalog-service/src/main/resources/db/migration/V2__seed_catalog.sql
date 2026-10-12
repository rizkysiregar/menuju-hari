-- Seed data katalog (TPL-005): dikelola sebagai migrasi agar
-- environment dev/staging/produksi konsisten. Perubahan harga/fitur
-- = migrasi baru, bukan edit manual.

-- ===== Template =====
INSERT INTO templates (id, code, name, description) VALUES
  ('11111111-1111-1111-1111-111111111111', 'aurora', 'Aurora',
   'Minimalis modern: tipografi besar, latar putih bersih, aksen lembut.'),
  ('11111111-1111-1111-1111-111111111112', 'lentera', 'Lentera',
   'Klasik nusantara: nuansa emas, ornamen halus, kesan formal dan hangat.');

-- Skema konten v1 (dipakai kedua template; per-template dapat menimpa di versi berikut)
INSERT INTO template_versions (id, template_id, version, status, content_schema, theme, capability_flags) VALUES
  ('22222222-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', '1.0.0', 'ACTIVE',
  '{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "cover": { "type": "object", "properties": {
          "title": { "type": "string", "maxLength": 200 },
          "quote": { "type": "string", "maxLength": 500 }
        }, "additionalProperties": false },
      "couple": { "type": "object", "properties": {
          "bridePhotoMediaId": { "type": "string" },
          "groomPhotoMediaId": { "type": "string" },
          "brideProfile": { "type": "string", "maxLength": 300 },
          "groomProfile": { "type": "string", "maxLength": 300 }
        }, "additionalProperties": false },
      "events": { "type": "array", "maxItems": 2, "items": { "type": "object", "properties": {
          "name": { "type": "string", "maxLength": 60 },
          "startAt": { "type": "string", "format": "date-time" },
          "venueName": { "type": "string", "maxLength": 120 },
          "address": { "type": "string", "maxLength": 300 },
          "mapsUrl": { "type": "string", "maxLength": 500 }
        }, "required": ["name", "startAt", "venueName"], "additionalProperties": false } },
      "greetings": { "type": "object", "properties": {
          "opening": { "type": "string", "maxLength": 600 },
          "parentsLine": { "type": "string", "maxLength": 300 },
          "closing": { "type": "string", "maxLength": 600 }
        }, "additionalProperties": false },
      "story": { "type": "array", "maxItems": 10, "items": { "type": "object", "properties": {
          "title": { "type": "string", "maxLength": 80 },
          "body": { "type": "string", "maxLength": 800 }
        }, "required": ["title", "body"], "additionalProperties": false } },
      "gallery": { "type": "array", "maxItems": 30, "items": { "type": "string" } },
      "gift": { "type": "object", "properties": {
          "accounts": { "type": "array", "maxItems": 3, "items": { "type": "object", "properties": {
              "bank": { "type": "string", "maxLength": 50 },
              "number": { "type": "string", "maxLength": 32 },
              "holder": { "type": "string", "maxLength": 100 }
            }, "required": ["bank", "number", "holder"], "additionalProperties": false } },
          "qrisMediaId": { "type": "string" },
          "note": { "type": "string", "maxLength": 300 }
        }, "additionalProperties": false },
      "closing": { "type": "object", "properties": {
          "hashtag": { "type": "string", "maxLength": 60 },
          "message": { "type": "string", "maxLength": 600 }
        }, "additionalProperties": false },
      "musicTrackId": { "type": "string" }
    },
    "additionalProperties": false
  }'::jsonb,
  '{
    "palette": { "primary": "#6366f1", "accent": "#ec4899", "background": "#ffffff", "text": "#1f2937" },
    "font": { "heading": "Playfair Display", "body": "Inter" }
  }'::jsonb,
  '{
    "gallery": true, "music": true, "story": true, "gift": true,
    "rsvp": true, "guestbook": true, "countdown": true, "maps": true, "videoEmbed": true
  }'::jsonb),
  ('22222222-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111112', '1.0.0', 'ACTIVE',
  '{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "cover": { "type": "object", "properties": {
          "title": { "type": "string", "maxLength": 200 },
          "quote": { "type": "string", "maxLength": 500 }
        }, "additionalProperties": false },
      "couple": { "type": "object", "properties": {
          "bridePhotoMediaId": { "type": "string" },
          "groomPhotoMediaId": { "type": "string" },
          "brideProfile": { "type": "string", "maxLength": 300 },
          "groomProfile": { "type": "string", "maxLength": 300 }
        }, "additionalProperties": false },
      "events": { "type": "array", "maxItems": 2, "items": { "type": "object", "properties": {
          "name": { "type": "string", "maxLength": 60 },
          "startAt": { "type": "string", "format": "date-time" },
          "venueName": { "type": "string", "maxLength": 120 },
          "address": { "type": "string", "maxLength": 300 },
          "mapsUrl": { "type": "string", "maxLength": 500 }
        }, "required": ["name", "startAt", "venueName"], "additionalProperties": false } },
      "greetings": { "type": "object", "properties": {
          "opening": { "type": "string", "maxLength": 600 },
          "parentsLine": { "type": "string", "maxLength": 300 },
          "closing": { "type": "string", "maxLength": 600 }
        }, "additionalProperties": false },
      "story": { "type": "array", "maxItems": 10, "items": { "type": "object", "properties": {
          "title": { "type": "string", "maxLength": 80 },
          "body": { "type": "string", "maxLength": 800 }
        }, "required": ["title", "body"], "additionalProperties": false } },
      "gallery": { "type": "array", "maxItems": 30, "items": { "type": "string" } },
      "gift": { "type": "object", "properties": {
          "accounts": { "type": "array", "maxItems": 3, "items": { "type": "object", "properties": {
              "bank": { "type": "string", "maxLength": 50 },
              "number": { "type": "string", "maxLength": 32 },
              "holder": { "type": "string", "maxLength": 100 }
            }, "required": ["bank", "number", "holder"], "additionalProperties": false } },
          "qrisMediaId": { "type": "string" },
          "note": { "type": "string", "maxLength": 300 }
        }, "additionalProperties": false },
      "closing": { "type": "object", "properties": {
          "hashtag": { "type": "string", "maxLength": 60 },
          "message": { "type": "string", "maxLength": 600 }
        }, "additionalProperties": false },
      "musicTrackId": { "type": "string" }
    },
    "additionalProperties": false
  }'::jsonb,
  '{
    "palette": { "primary": "#b45309", "accent": "#d4af37", "background": "#fffaf3", "text": "#3f2d1d" },
    "font": { "heading": "Cormorant Garamond", "body": "Source Serif 4" }
  }'::jsonb,
  '{
    "gallery": true, "music": true, "story": true, "gift": true,
    "rsvp": true, "guestbook": true, "countdown": true, "maps": true, "videoEmbed": true
  }'::jsonb);

-- ===== Paket (K6; harga placeholder final oleh product owner) =====
INSERT INTO packages (id, code, name, price_idr, feature_flags, sort_order, active) VALUES
  ('33333333-1111-1111-1111-111111111111', 'free', 'Gratis',
   0, '{"watermark":true,"maxGalleryPhotos":3,"music":false,"giftAccounts":0,"rsvp":false,"guestbook":false,"customSlug":false,"videoEmbed":false,"story":false,"analytics":false}'::jsonb, 0, TRUE),
  ('33333333-1111-1111-1111-111111111112', 'silver', 'Silver',
   49000, '{"watermark":false,"maxGalleryPhotos":10,"music":true,"giftAccounts":1,"rsvp":false,"guestbook":false,"customSlug":false,"videoEmbed":false,"story":false,"analytics":false}'::jsonb, 1, TRUE),
  ('33333333-1111-1111-1111-111111111113', 'gold', 'Gold',
   99000, '{"watermark":false,"maxGalleryPhotos":30,"music":true,"giftAccounts":3,"rsvp":true,"guestbook":true,"customSlug":true,"videoEmbed":true,"story":true,"analytics":false}'::jsonb, 2, TRUE),
  ('33333333-1111-1111-1111-111111111114', 'platinum', 'Platinum',
   179000, '{"watermark":false,"maxGalleryPhotos":50,"music":true,"giftAccounts":5,"rsvp":true,"guestbook":true,"customSlug":true,"videoEmbed":true,"story":true,"analytics":true,"qrCheckIn":true,"liveStream":true}'::jsonb, 3, FALSE);

-- ===== Musik (K13: hanya katalog berlisensi; karya domain publik via Musopen) =====
INSERT INTO music_tracks (id, title, artist, object_key, license_note, duration_seconds) VALUES
  ('44444444-1111-1111-1111-111111111111', 'Canon in D', 'Johann Pachelbel', 'music/canon-in-d.mp3',
   'Domain publik (Musopen) — bebas digunakan komersial; verifikasi ulang sebelum produksi.', 180),
  ('44444444-1111-1111-1111-111111111112', 'Gymnopedie No. 1', 'Erik Satie', 'music/gymnopedie-no-1.mp3',
   'Domain publik (Musopen) — bebas digunakan komersial; verifikasi ulang sebelum produksi.', 200),
  ('44444444-1111-1111-1111-111111111113', 'Clair de Lune', 'Claude Debussy', 'music/clair-de-lune.mp3',
   'Domain publik (Musopen) — bebas digunakan komersial; verifikasi ulang sebelum produksi.', 240),
  ('44444444-1111-1111-1111-111111111114', 'Air on the G String', 'J.S. Bach', 'music/air-on-the-g-string.mp3',
   'Domain publik (Musopen) — bebas digunakan komersial; verifikasi ulang sebelum produksi.', 190),
  ('44444444-1111-1111-1111-111111111115', 'Moonlight Sonata (1st Movement)', 'L.v. Beethoven', 'music/moonlight-sonata-1.mp3',
   'Domain publik (Musopen) — bebas digunakan komersial; verifikasi ulang sebelum produksi.', 270);
