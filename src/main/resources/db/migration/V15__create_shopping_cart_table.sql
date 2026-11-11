CREATE TABLE shopping_cart (
                               id                  UUID PRIMARY KEY,
                               user_id             UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                               status              VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',

                               currency            VARCHAR(3)     NOT NULL DEFAULT 'USD',
                               item_count          INTEGER        NOT NULL DEFAULT 0,
                               subtotal            NUMERIC(12, 2) NOT NULL DEFAULT 0,
                               discount_total      NUMERIC(12, 2) NOT NULL DEFAULT 0,
                               total               NUMERIC(12, 2) NOT NULL DEFAULT 0,

                               coupon_code         VARCHAR(50),
                               coupon_discount     NUMERIC(12, 2) NOT NULL DEFAULT 0,

                               last_activity_at    TIMESTAMP WITH TIME ZONE NOT NULL,
                               checked_out_at      TIMESTAMP WITH TIME ZONE,
                               checkout_reference  VARCHAR(100),
                               abandoned_at        TIMESTAMP WITH TIME ZONE,
                               reminder_sent_at    TIMESTAMP WITH TIME ZONE,

                               version             BIGINT         NOT NULL DEFAULT 0,
                               created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
                               updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,

                               CONSTRAINT ck_shopping_cart_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED')),
                               CONSTRAINT ck_shopping_cart_totals CHECK (item_count >= 0 AND subtotal >= 0 AND discount_total >= 0
                                   AND total >= 0 AND coupon_discount >= 0)
);

CREATE UNIQUE INDEX uk_shopping_cart_one_open_per_user
    ON shopping_cart (user_id)
    WHERE status IN ('ACTIVE', 'ABANDONED');

CREATE INDEX idx_shopping_cart_user_status     ON shopping_cart (user_id, status);
CREATE INDEX idx_shopping_cart_status_activity ON shopping_cart (status, last_activity_at);


CREATE TABLE shopping_cart_items (
                                     id                UUID PRIMARY KEY,
                                     cart_id           UUID           NOT NULL REFERENCES shopping_cart (id) ON DELETE CASCADE,
                                     course_id         UUID           NOT NULL REFERENCES course (id),
                                     course_title      VARCHAR(255)   NOT NULL,
                                     course_thumbnail  VARCHAR(1024),
                                     unit_price        NUMERIC(12, 2) NOT NULL,
                                     discount_amount   NUMERIC(12, 2) NOT NULL DEFAULT 0,
                                     currency          VARCHAR(3)     NOT NULL DEFAULT 'USD',
                                     saved_for_later   BOOLEAN        NOT NULL DEFAULT FALSE,
                                     price_changed     BOOLEAN        NOT NULL DEFAULT FALSE,
                                     created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                     updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,

                                     CONSTRAINT uk_cart_item_course    UNIQUE (cart_id, course_id),
                                     CONSTRAINT ck_cart_item_price     CHECK (unit_price >= 0),
                                     CONSTRAINT ck_cart_item_discount  CHECK (discount_amount >= 0 AND discount_amount <= unit_price)
);

CREATE INDEX idx_cart_item_cart   ON shopping_cart_items (cart_id);
CREATE INDEX idx_cart_item_course ON shopping_cart_items (course_id);