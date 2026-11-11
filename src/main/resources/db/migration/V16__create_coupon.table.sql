CREATE TABLE coupons (
                         id                   UUID PRIMARY KEY,
                         code                 VARCHAR(20)    NOT NULL,
                         description          VARCHAR(255),
                         discount_type        VARCHAR(20)    NOT NULL,
                         discount_value       NUMERIC(12, 2) NOT NULL,
                         max_discount_amount  NUMERIC(12, 2),
                         currency             VARCHAR(3)     NOT NULL DEFAULT 'USD',
                         minimum_cart_total   NUMERIC(12, 2),
                         course_id            UUID REFERENCES course (id),
                         assigned_user_id     UUID REFERENCES users (id),
                         starts_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                         expires_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                         max_redemptions      INTEGER,
                         redemption_count     INTEGER        NOT NULL DEFAULT 0,
                         status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
                         revoked_at           TIMESTAMP WITH TIME ZONE,
                         revoked_by           UUID,
                         revoke_reason        VARCHAR(500),
                         created_by           UUID           NOT NULL,
                         issuer_role          VARCHAR(20)    NOT NULL,
                         version              BIGINT         NOT NULL DEFAULT 0,
                         created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
                         updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,

                         CONSTRAINT uk_coupon_code           UNIQUE (code),
                         CONSTRAINT ck_coupon_type           CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
                         CONSTRAINT ck_coupon_status         CHECK (status IN ('ACTIVE', 'REVOKED')),
                         CONSTRAINT ck_coupon_value          CHECK (discount_value > 0
                             AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
                         CONSTRAINT ck_coupon_period         CHECK (expires_at > starts_at),
                         CONSTRAINT ck_coupon_redemptions    CHECK (redemption_count >= 0
                             AND (max_redemptions IS NULL OR max_redemptions >= 1)),
                         CONSTRAINT ck_coupon_amounts        CHECK ((max_discount_amount IS NULL OR max_discount_amount > 0)
                             AND (minimum_cart_total IS NULL OR minimum_cart_total >= 0))
);

CREATE INDEX idx_coupon_status_expires  ON coupons (status, expires_at);
CREATE INDEX idx_coupon_course          ON coupons (course_id);
CREATE INDEX idx_coupon_assigned_user   ON coupons (assigned_user_id);
CREATE INDEX idx_coupon_created_by      ON coupons (created_by);
CREATE INDEX idx_shopping_cart_coupon   ON shopping_cart (coupon_code);