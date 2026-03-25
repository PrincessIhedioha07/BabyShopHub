-- BabyShopHub — Seed Data V2
-- V2__seed_data.sql

-- ─────────────────── CATEGORIES ───────────────────
INSERT INTO categories (name, slug, icon_name, sort_order) VALUES
('All Products',   'all',         'grid',          0),
('Diapers',        'diapers',     'baby',          1),
('Baby Food',      'baby-food',   'apple',         2),
('Clothing',       'clothing',    'tshirt',        3),
('Toys',           'toys',        'puzzle',        4),
('Feeding',        'feeding',     'bottle',        5),
('Health & Safety','health',      'shield',        6),
('Nursery',        'nursery',     'moon',          7),
('Accessories',    'accessories', 'star',          8),
('Bath Time',      'bath',        'droplets',      9);

-- ─────────────────── USERS (BCrypt cost 12 hashes) ───────────────────
-- Passwords: Admin123! / Seller123! / User123!
-- Hashes generated with Python bcrypt (cost=12) and verified via bcrypt.checkpw().
-- Spring Security BCryptPasswordEncoder supports $2a$, $2b$, and $2y$ prefixes.
INSERT INTO users (email, password_hash, first_name, last_name, role, email_verified) VALUES
('admin@babyshophub.com',
 '$2b$12$xY7Sqh9Y7.zcrwXXSU.Fde3pSlGwB2GYa08ZfR983nlb9Y5RVM3uC',
 'Admin', 'BabyShopHub', 'ADMIN', TRUE),

('seller@babyshophub.com',
 '$2b$12$91HW.pvwDJmNliJtpimdTucECvFvkPQ9ABlgOvPMI4PbTn3XcX6U2',
 'Tiny', 'Treasures', 'SELLER', TRUE),

('user@babyshophub.com',
 '$2b$12$CeDaiuXeT0vzRmJs6gjLYu7EGY9QE2dcVBrFShyyN5gWUWe81gcbq',
 'Sarah', 'Johnson', 'CUSTOMER', TRUE);

-- ─────────────────── SELLER PROFILE ───────────────────
INSERT INTO seller_profiles (user_id, store_name, description, is_verified, rating_avg, review_count)
SELECT id, 'TinyTreasures', 'Premium baby products for your little one. Safe, certified, and lovingly curated.', TRUE, 4.80, 127
FROM users WHERE email = 'seller@babyshophub.com';

-- ─────────────────── SAMPLE ADDRESSES ───────────────────
INSERT INTO addresses (user_id, label, recipient_name, address_line1, city, state, postal_code, country, is_default)
SELECT id, 'Home', 'Sarah Johnson', '123 Maple Street', 'Austin', 'TX', '78701', 'US', TRUE
FROM users WHERE email = 'user@babyshophub.com';

INSERT INTO addresses (user_id, label, recipient_name, address_line1, city, state, postal_code, country, is_default)
SELECT id, 'Work', 'Sarah Johnson', '456 Oak Avenue, Suite 200', 'Austin', 'TX', '78702', 'US', FALSE
FROM users WHERE email = 'user@babyshophub.com';

-- ─────────────────── PROMO CODES ───────────────────
INSERT INTO promo_codes (code, discount_type, discount_value, min_order, max_uses, expires_at, is_active) VALUES
('BABY10',   'PERCENT', 10.00, 30.00, 1000, NOW() + INTERVAL '90 days', TRUE),
('FREESHIP', 'FIXED',    9.99, 25.00, 500,  NOW() + INTERVAL '30 days', TRUE),
('WELCOME20','PERCENT', 20.00, 50.00, NULL, NOW() + INTERVAL '180 days', TRUE);

-- ─────────────────── PRODUCTS ───────────────────
-- Using seller_id from seller_profiles
DO $$
DECLARE
  v_seller_id BIGINT;
  v_cat_feeding BIGINT;
  v_cat_toys BIGINT;
  v_cat_clothing BIGINT;
  v_cat_bath BIGINT;
  v_cat_health BIGINT;
  v_cat_nursery BIGINT;
  v_cat_diapers BIGINT;
  v_cat_accessories BIGINT;
BEGIN
  SELECT id INTO v_seller_id FROM seller_profiles LIMIT 1;
  SELECT id INTO v_cat_feeding FROM categories WHERE slug = 'feeding';
  SELECT id INTO v_cat_toys FROM categories WHERE slug = 'toys';
  SELECT id INTO v_cat_clothing FROM categories WHERE slug = 'clothing';
  SELECT id INTO v_cat_bath FROM categories WHERE slug = 'bath';
  SELECT id INTO v_cat_health FROM categories WHERE slug = 'health';
  SELECT id INTO v_cat_nursery FROM categories WHERE slug = 'nursery';
  SELECT id INTO v_cat_diapers FROM categories WHERE slug = 'diapers';
  SELECT id INTO v_cat_accessories FROM categories WHERE slug = 'accessories';

  INSERT INTO products (seller_id, category_id, name, slug, description, price, original_price, sku, stock_qty, age_suitability, images, is_featured, status, rating_avg, review_count) VALUES
  (v_seller_id, v_cat_feeding, 'Anti-Colic Bottle Set (3pk)', 'anti-colic-bottle-set',
   'Patented venting mechanism reduces air ingestion, colic, and reflux. BPA-free, dishwasher-safe. Available in multiple nipple flow rates.',
   18.99, 24.99, 'SKU-FEED-001', 80, 'Newborn+',
   '[{"url":"https://placehold.co/400x400/a8d4e6/5aadcc?text=Bottle+Set","alt":"Anti-Colic Bottle Set"}]',
   TRUE, 'LIVE', 4.80, 312),

  (v_seller_id, v_cat_toys, 'Organic Plush Bear', 'organic-plush-bear',
   'Hand-crafted from 100% organic cotton. Hypoallergenic, ASTM-certified safe for newborns. Machine washable at 40°C.',
   24.99, 32.99, 'SKU-TOY-001', 45, '0–12 months',
   '[{"url":"https://placehold.co/400x400/fde8e0/e8896a?text=Plush+Bear","alt":"Organic Plush Bear"}]',
   TRUE, 'LIVE', 4.90, 289),

  (v_seller_id, v_cat_clothing, 'Cotton Onesie Bundle (3pk)', 'cotton-onesie-bundle',
   'GOTS-certified ultra-soft breathable organic cotton onesies. Easy snap closures, tagless, relaxed fit.',
   29.99, 39.99, 'SKU-CLO-001', 120, 'Newborn–2Y',
   '[{"url":"https://placehold.co/400x400/e2ede2/6aad8a?text=Onesie+Bundle","alt":"Cotton Onesie Bundle"}]',
   TRUE, 'LIVE', 4.70, 198),

  (v_seller_id, v_cat_bath, 'Gentle Baby Wash Kit', 'gentle-baby-wash-kit',
   'Pediatrician-tested. Tear-free formula, fragrance-free, hypoallergenic. Includes body wash, shampoo, and lotion.',
   15.99, NULL, 'SKU-BATH-001', 60, 'Newborn+',
   '[{"url":"https://placehold.co/400x400/ede0f5/9a7ac4?text=Wash+Kit","alt":"Baby Wash Kit"}]',
   FALSE, 'LIVE', 4.60, 145),

  (v_seller_id, v_cat_health, 'Digital Thermometer Pro', 'digital-thermometer-pro',
   'AAP-recommended. Rectal, oral, and axillary modes. 10-second reading. Memory stores last 10 readings.',
   22.99, 29.99, 'SKU-HLT-001', 35, 'All ages',
   '[{"url":"https://placehold.co/400x400/fef9e0/c9a020?text=Thermometer","alt":"Digital Thermometer"}]',
   FALSE, 'LIVE', 4.85, 421),

  (v_seller_id, v_cat_nursery, 'Merino Swaddle Blanket', 'merino-swaddle-blanket',
   '100% ethically-sourced merino wool. Temperature-regulating, naturally breathable. Machine washable.',
   38.99, 49.99, 'SKU-NUR-001', 28, 'Newborn–6 months',
   '[{"url":"https://placehold.co/400x400/feeee8/c46a4a?text=Swaddle","alt":"Merino Swaddle"}]',
   TRUE, 'LIVE', 4.75, 167),

  (v_seller_id, v_cat_diapers, 'Ultra-Dry Diapers — Size 1 (50pk)', 'ultra-dry-diapers-size-1',
   'Up to 12-hour dryness. Soft cotton-feel outer, wetness indicator strip, flexible waistband.',
   19.99, 24.99, 'SKU-DIA-001', 200, '2–5 kg',
   '[{"url":"https://placehold.co/400x400/daeef7/5aadcc?text=Diapers","alt":"Ultra-Dry Diapers"}]',
   TRUE, 'LIVE', 4.65, 556),

  (v_seller_id, v_cat_accessories, 'Baby Monitor — 1080p WiFi', 'baby-monitor-wifi',
   '1080p HD camera, night vision, two-way audio, temperature sensor, lullaby player. Works with iOS & Android.',
   89.99, 119.99, 'SKU-ACC-001', 18, 'All ages',
   '[{"url":"https://placehold.co/400x400/b8cbb8/5a7a5a?text=Monitor","alt":"Baby Monitor"}]',
   TRUE, 'LIVE', 4.90, 234),

  (v_seller_id, v_cat_feeding, 'Silicone Bib (2pk)', 'silicone-bib-2pk',
   'Food-grade silicone. Deep pocket catches crumbs. Adjustable neck snaps. Dishwasher safe.',
   12.99, NULL, 'SKU-FEED-002', 90, '4 months+',
   '[{"url":"https://placehold.co/400x400/a8d4e6/5aadcc?text=Bib","alt":"Silicone Bib"}]',
   FALSE, 'LIVE', 4.55, 88),

  (v_seller_id, v_cat_toys, 'Sensory Play Mat', 'sensory-play-mat',
   'Foam-padded, non-toxic, double-sided design. Includes 5 hanging toys, mirror panel, crinkle textures.',
   54.99, 69.99, 'SKU-TOY-002', 22, '0–18 months',
   '[{"url":"https://placehold.co/400x400/fde8e0/e8896a?text=Play+Mat","alt":"Sensory Play Mat"}]',
   TRUE, 'LIVE', 4.80, 312),

  (v_seller_id, v_cat_clothing, 'Merino Wool Beanie (2pk)', 'merino-beanie-2pk',
   'Ultra-soft, itch-free. Naturally temperature-regulating. One-size-fits-most newborn.',
   9.99, 14.99, 'SKU-CLO-002', 75, 'Newborn',
   '[{"url":"https://placehold.co/400x400/e2ede2/6aad8a?text=Beanie","alt":"Merino Beanie"}]',
   FALSE, 'LIVE', 4.40, 62),

  (v_seller_id, v_cat_nursery, 'White Noise Machine', 'white-noise-machine',
   '30 soothing sounds. Auto-timer, USB-powered, compact nightlight. Helps babies sleep longer.',
   34.99, 44.99, 'SKU-NUR-002', 40, 'All ages',
   '[{"url":"https://placehold.co/400x400/feeee8/c46a4a?text=White+Noise","alt":"White Noise Machine"}]',
   FALSE, 'LIVE', 4.70, 203);
END $$;

-- ─────────────────── PRODUCT VARIANTS (for clothing) ───────────────────
INSERT INTO product_variants (product_id, name, value, price_modifier, stock_qty)
SELECT p.id, 'Size', 'Newborn', 0.00, 30 FROM products p WHERE p.slug = 'cotton-onesie-bundle'
UNION ALL
SELECT p.id, 'Size', '0–3M',   0.00, 40 FROM products p WHERE p.slug = 'cotton-onesie-bundle'
UNION ALL
SELECT p.id, 'Size', '3–6M',   0.00, 30 FROM products p WHERE p.slug = 'cotton-onesie-bundle'
UNION ALL
SELECT p.id, 'Size', '6–12M',  2.00, 20 FROM products p WHERE p.slug = 'cotton-onesie-bundle';

-- ─────────────────── SAMPLE REVIEWS ───────────────────
DO $$
DECLARE
  v_customer_id BIGINT;
  v_bear_id BIGINT;
  v_bottle_id BIGINT;
BEGIN
  SELECT id INTO v_customer_id FROM users WHERE email = 'user@babyshophub.com';
  SELECT id INTO v_bear_id FROM products WHERE slug = 'organic-plush-bear';
  SELECT id INTO v_bottle_id FROM products WHERE slug = 'anti-colic-bottle-set';

  INSERT INTO reviews (user_id, product_id, rating, title, text, helpful_votes) VALUES
  (v_customer_id, v_bear_id, 5, 'So soft and adorable!',
   'My baby absolutely loves this! It''s incredibly soft and I love that it''s organic. Washes perfectly too. Highly recommend for any new parent.', 24),
  (v_customer_id, v_bottle_id, 5, 'Completely solved our colic issues',
   'We struggled with colic for weeks. These bottles were a game changer. Baby went from 2 hours of crying to almost none. Worth every penny.', 31);
END $$;

-- ─────────────────── NOTIFICATION PREFS ───────────────────
INSERT INTO notification_preferences (user_id)
SELECT id FROM users;

-- ─────────────────── FAQ ───────────────────
INSERT INTO faqs (question, answer, category, sort_order) VALUES
('What is your return policy?', 'We offer 30-day hassle-free returns on all items in original condition. Simply initiate a return from the Orders screen.', 'Returns', 1),
('Is free delivery available?', 'Yes! Free 2-day standard delivery on orders over $40. Express delivery (1–2 days) is available for $9.99.', 'Shipping', 2),
('Are all products safety certified?', 'Absolutely. Every product on BabyShopHub is tested and certified to meet ASTM, CPSC, or EN71 safety standards.', 'Safety', 3),
('How do I track my order?', 'Open the Orders section in the app and tap on your order to see real-time tracking updates.', 'Orders', 4),
('Can I cancel my order?', 'Orders can be cancelled before they are shipped. Once shipped, please wait for delivery and then initiate a return.', 'Orders', 5),
('How do I become a seller?', 'Sign up with the Seller role, complete your store profile, and submit for verification. Our team reviews within 2 business days.', 'Sellers', 6),
('What payment methods do you accept?', 'We accept Visa, Mastercard, American Express, and PayPal. Apple Pay and Google Pay coming soon.', 'Payments', 7),
('How is my data protected?', 'We use bank-grade encryption (TLS 1.3) and never sell your personal data. See our Privacy Policy for full details.', 'Privacy', 8);
