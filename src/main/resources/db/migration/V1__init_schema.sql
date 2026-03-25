-- BabyShopHub — PostgreSQL Schema V1
-- Flyway migration V1__init_schema.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ENUM types
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'SELLER', 'ADMIN');
CREATE TYPE order_status AS ENUM ('CONFIRMED','PROCESSING','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED');
CREATE TYPE delivery_type AS ENUM ('STANDARD','EXPRESS');
CREATE TYPE product_status AS ENUM ('LIVE','DRAFT','DEACTIVATED');
CREATE TYPE discount_type AS ENUM ('PERCENT','FIXED');
CREATE TYPE payout_status AS ENUM ('PENDING','PROCESSED');
CREATE TYPE support_status AS ENUM ('OPEN','IN_PROGRESS','CLOSED');
CREATE TYPE platform_type AS ENUM ('IOS','ANDROID','WEB');

-- ─────────────────── USERS ───────────────────
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    avatar_url      TEXT,
    role            user_role NOT NULL DEFAULT 'CUSTOMER',
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    is_suspended    BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── SELLER PROFILES ───────────────────
CREATE TABLE seller_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    store_name      VARCHAR(255) NOT NULL,
    store_logo_url  TEXT,
    description     TEXT,
    is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    rating_avg      DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    review_count    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── ADDRESSES ───────────────────
CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label           VARCHAR(50) NOT NULL DEFAULT 'Home',
    recipient_name  VARCHAR(200) NOT NULL,
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100),
    postal_code     VARCHAR(20) NOT NULL,
    country         VARCHAR(100) NOT NULL DEFAULT 'US',
    is_default      BOOLEAN NOT NULL DEFAULT FALSE
);

-- ─────────────────── CATEGORIES ───────────────────
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    icon_name   VARCHAR(100),
    image_url   TEXT,
    parent_id   BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

-- ─────────────────── PRODUCTS ───────────────────
CREATE TABLE products (
    id                    BIGSERIAL PRIMARY KEY,
    seller_id             BIGINT NOT NULL REFERENCES seller_profiles(id) ON DELETE CASCADE,
    category_id           BIGINT NOT NULL REFERENCES categories(id),
    name                  VARCHAR(255) NOT NULL,
    slug                  VARCHAR(255) NOT NULL UNIQUE,
    description           TEXT,
    price                 DECIMAL(10,2) NOT NULL,
    original_price        DECIMAL(10,2),
    sku                   VARCHAR(100) UNIQUE,
    stock_qty             INTEGER NOT NULL DEFAULT 0,
    low_stock_threshold   INTEGER NOT NULL DEFAULT 5,
    age_suitability       VARCHAR(100),
    images                JSONB NOT NULL DEFAULT '[]',
    is_featured           BOOLEAN NOT NULL DEFAULT FALSE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    status                product_status NOT NULL DEFAULT 'LIVE',
    rating_avg            DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    review_count          INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_seller ON products(seller_id);
CREATE INDEX idx_products_featured ON products(is_featured) WHERE is_active = TRUE;
CREATE INDEX idx_products_status ON products(status);

-- Full-text search
ALTER TABLE products ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(name,'') || ' ' || coalesce(description,''))) STORED;
CREATE INDEX idx_products_fts ON products USING gin(search_vector);

-- ─────────────────── PRODUCT VARIANTS ───────────────────
CREATE TABLE product_variants (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    value           VARCHAR(100) NOT NULL,
    price_modifier  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    stock_qty       INTEGER NOT NULL DEFAULT 0
);

-- ─────────────────── CARTS ───────────────────
CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,
    session_id  VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT cart_owner CHECK (user_id IS NOT NULL OR session_id IS NOT NULL)
);

CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    cart_id     BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id  BIGINT REFERENCES product_variants(id) ON DELETE SET NULL,
    quantity    INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price  DECIMAL(10,2) NOT NULL,
    UNIQUE(cart_id, product_id, variant_id)
);

-- ─────────────────── PROMO CODES ───────────────────
CREATE TABLE promo_codes (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    discount_type   discount_type NOT NULL,
    discount_value  DECIMAL(10,2) NOT NULL,
    min_order       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    max_uses        INTEGER,
    uses_count      INTEGER NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

-- ─────────────────── ORDERS ───────────────────
CREATE TABLE orders (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id),
    address_id              BIGINT NOT NULL REFERENCES addresses(id),
    promo_id                BIGINT REFERENCES promo_codes(id),
    subtotal                DECIMAL(10,2) NOT NULL,
    discount                DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    delivery_fee            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total                   DECIMAL(10,2) NOT NULL,
    delivery_type           delivery_type NOT NULL DEFAULT 'STANDARD',
    status                  order_status NOT NULL DEFAULT 'CONFIRMED',
    estimated_delivery_date DATE,
    notes                   TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at DESC);

CREATE TABLE order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    BIGINT REFERENCES products(id) ON DELETE SET NULL,
    variant_id    BIGINT REFERENCES product_variants(id) ON DELETE SET NULL,
    product_name  VARCHAR(255) NOT NULL,
    quantity      INTEGER NOT NULL,
    unit_price    DECIMAL(10,2) NOT NULL
);

CREATE TABLE order_status_history (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status        order_status NOT NULL,
    changed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    changed_by    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note          TEXT
);

-- ─────────────────── REVIEWS ───────────────────
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    order_id        BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title           VARCHAR(255),
    text            TEXT,
    images          JSONB NOT NULL DEFAULT '[]',
    helpful_votes   INTEGER NOT NULL DEFAULT 0,
    is_hidden       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, product_id, order_id)
);

CREATE INDEX idx_reviews_product ON reviews(product_id) WHERE is_hidden = FALSE;

CREATE TABLE review_helpful_votes (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    review_id   BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, review_id)
);

CREATE TABLE review_responses (
    id          BIGSERIAL PRIMARY KEY,
    review_id   BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    seller_id   BIGINT NOT NULL REFERENCES seller_profiles(id) ON DELETE CASCADE,
    text        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── WISHLIST ───────────────────
CREATE TABLE wishlists (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    added_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, product_id)
);

-- ─────────────────── SEARCH HISTORY ───────────────────
CREATE TABLE search_history (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query       VARCHAR(255) NOT NULL,
    searched_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, query)
);

-- ─────────────────── SUPPORT ───────────────────
CREATE TABLE support_tickets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id    BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    type        VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    status      support_status NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE support_messages (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── NOTIFICATIONS ───────────────────
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    body            TEXT NOT NULL,
    type            VARCHAR(100) NOT NULL,
    reference_id    BIGINT,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);

CREATE TABLE notification_preferences (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    push_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    order_updates   BOOLEAN NOT NULL DEFAULT TRUE,
    promotions      BOOLEAN NOT NULL DEFAULT TRUE,
    price_alerts    BOOLEAN NOT NULL DEFAULT TRUE
);

-- ─────────────────── PAYOUTS ───────────────────
CREATE TABLE payouts (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL REFERENCES seller_profiles(id) ON DELETE CASCADE,
    amount          DECIMAL(10,2) NOT NULL,
    status          payout_status NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP
);

-- ─────────────────── FCM TOKENS ───────────────────
CREATE TABLE fcm_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL UNIQUE,
    platform    platform_type NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── APP FEEDBACK ───────────────────
CREATE TABLE app_feedback (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─────────────────── FAQ ───────────────────
CREATE TABLE faqs (
    id          BIGSERIAL PRIMARY KEY,
    question    TEXT NOT NULL,
    answer      TEXT NOT NULL,
    category    VARCHAR(100) NOT NULL DEFAULT 'General',
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
