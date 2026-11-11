CREATE TABLE payments (
                          id                            UUID PRIMARY KEY,
                          payment_reference             VARCHAR(30)    NOT NULL,
                          payment_type                  VARCHAR(30)    NOT NULL,
                          direction                     VARCHAR(20)    NOT NULL,
                          status                        VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
                          description                   VARCHAR(255),

                          payer_id                      UUID REFERENCES users (id),
                          payee_id                      UUID REFERENCES users (id),
                          student_id                    UUID REFERENCES student_profiles (id),

                          currency                      VARCHAR(3)     NOT NULL DEFAULT 'USD',
                          subtotal                      NUMERIC(12, 2) NOT NULL DEFAULT 0,
                          discount_amount               NUMERIC(12, 2) NOT NULL DEFAULT 0,
                          tax_amount                    NUMERIC(12, 2) NOT NULL DEFAULT 0,
                          amount                        NUMERIC(12, 2) NOT NULL,
                          amount_in_minor_units         BIGINT,
                          provider_fee                  NUMERIC(12, 2),
                          net_amount                    NUMERIC(12, 2),
                          refunded_amount               NUMERIC(12, 2) NOT NULL DEFAULT 0,

                          shopping_cart_id              UUID REFERENCES shopping_cart (id),
                          coupon_code                   VARCHAR(50),
                          coupon_discount               NUMERIC(12, 2),

                          academic_year                 VARCHAR(20),
                          term                          VARCHAR(30),
                          fee_category                  VARCHAR(50),
                          due_date                      DATE,
                          installment_number            INTEGER,
                          total_installments            INTEGER,

                          pay_period_start              DATE,
                          pay_period_end                DATE,
                          gross_amount                  NUMERIC(12, 2),
                          deductions_amount             NUMERIC(12, 2),
                          allowances_amount             NUMERIC(12, 2),
                          payslip_document_id           UUID REFERENCES stored_files (id),
                          approved_by                   UUID,
                          approved_at                   TIMESTAMP WITH TIME ZONE,

                          original_payment_id           UUID REFERENCES payments (id),
                          refund_reason                 VARCHAR(500),

                          provider                      VARCHAR(20)    NOT NULL,
                          payment_method                VARCHAR(30),
                          provider_payment_id           VARCHAR(100),
                          provider_checkout_session_id  VARCHAR(100),
                          provider_customer_id          VARCHAR(100),
                          provider_transfer_id          VARCHAR(100),
                          provider_status               VARCHAR(50),
                          external_reference            VARCHAR(100),
                          receipt_url                   VARCHAR(1024),
                          card_brand                    VARCHAR(20),
                          card_last4                    VARCHAR(4),
                          failure_code                  VARCHAR(100),
                          failure_message               VARCHAR(500),

                          receipt_number                VARCHAR(50),
                          receipt_document_id           UUID REFERENCES stored_files (id),
                          recorded_by                   UUID,
                          notes                         VARCHAR(1000),

                          paid_at                       TIMESTAMP WITH TIME ZONE,
                          failed_at                     TIMESTAMP WITH TIME ZONE,
                          cancelled_at                  TIMESTAMP WITH TIME ZONE,
                          refunded_at                   TIMESTAMP WITH TIME ZONE,

                          idempotency_key               VARCHAR(100)   NOT NULL,
                          version                       BIGINT         NOT NULL DEFAULT 0,
                          created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT uk_payment_reference           UNIQUE (payment_reference),
                          CONSTRAINT uk_payment_idempotency_key     UNIQUE (idempotency_key),
                          CONSTRAINT uk_payment_provider_payment_id UNIQUE (provider_payment_id),

                          CONSTRAINT ck_payment_type      CHECK (payment_type IN ('COURSE_PURCHASE', 'SCHOOL_FEE', 'STAFF_SALARY', 'REFUND')),
                          CONSTRAINT ck_payment_direction CHECK (direction IN ('INCOMING', 'OUTGOING')),
                          CONSTRAINT ck_payment_provider  CHECK (provider IN ('STRIPE', 'PAYPAL', 'MANUAL')),
                          CONSTRAINT ck_payment_amounts   CHECK (amount >= 0 AND subtotal >= 0 AND discount_amount >= 0
                              AND tax_amount >= 0 AND refunded_amount >= 0
                              AND refunded_amount <= amount),
                          CONSTRAINT ck_payment_purchase  CHECK (payment_type <> 'COURSE_PURCHASE'
                              OR (shopping_cart_id IS NOT NULL AND payer_id IS NOT NULL)),
                          CONSTRAINT ck_payment_fee       CHECK (payment_type <> 'SCHOOL_FEE'
                              OR (student_id IS NOT NULL AND academic_year IS NOT NULL)),
                          CONSTRAINT ck_payment_salary    CHECK (payment_type <> 'STAFF_SALARY'
                              OR (payee_id IS NOT NULL AND pay_period_start IS NOT NULL
                                  AND pay_period_end IS NOT NULL AND pay_period_end >= pay_period_start)),
                          CONSTRAINT ck_payment_refund    CHECK (payment_type <> 'REFUND' OR original_payment_id IS NOT NULL),
                          CONSTRAINT ck_payment_installments CHECK (installment_number IS NULL OR total_installments IS NULL
                              OR (installment_number >= 1 AND installment_number <= total_installments))
);

CREATE UNIQUE INDEX uk_payment_cart_succeeded
    ON payments (shopping_cart_id)
    WHERE shopping_cart_id IS NOT NULL AND status IN ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED');

CREATE UNIQUE INDEX uk_payment_salary_period
    ON payments (payee_id, pay_period_start, pay_period_end)
    WHERE payment_type = 'STAFF_SALARY' AND status NOT IN ('FAILED', 'CANCELLED');

CREATE INDEX idx_payment_type_status       ON payments (payment_type, status);
CREATE INDEX idx_payment_payer             ON payments (payer_id);
CREATE INDEX idx_payment_payee             ON payments (payee_id);
CREATE INDEX idx_payment_student           ON payments (student_id);
CREATE INDEX idx_payment_cart              ON payments (shopping_cart_id);
CREATE INDEX idx_payment_paid_at           ON payments (paid_at);
CREATE INDEX idx_payment_fee_period        ON payments (academic_year, term);
CREATE INDEX idx_payment_salary_period     ON payments (pay_period_start, pay_period_end);
CREATE INDEX idx_payment_checkout_session  ON payments (provider_checkout_session_id);