CREATE TABLE library (
                         id                 UUID PRIMARY KEY,
                         title              VARCHAR(255)   NOT NULL,
                         subtitle           VARCHAR(255),
                         author             VARCHAR(255)   NOT NULL,
                         isbn               VARCHAR(13),
                         publisher          VARCHAR(150),
                         publication_year   INTEGER,
                         edition            VARCHAR(50),
                         language           VARCHAR(50),
                         category           VARCHAR(100),
                         description        TEXT,
                         page_count         INTEGER,
                         cover_image        VARCHAR(1024),
                         course_id          UUID REFERENCES course (id),

                         shelf_location     VARCHAR(50),
                         total_copies       INTEGER        NOT NULL DEFAULT 1,
                         available_copies   INTEGER        NOT NULL DEFAULT 1,

                         borrowable         BOOLEAN        NOT NULL DEFAULT TRUE,
                         loan_period_days   INTEGER        NOT NULL DEFAULT 14,
                         max_renewals       INTEGER        NOT NULL DEFAULT 2,
                         daily_late_fee     NUMERIC(10, 2) NOT NULL DEFAULT 0,
                         currency           VARCHAR(3)     NOT NULL DEFAULT 'USD',
                         active             BOOLEAN        NOT NULL DEFAULT TRUE,

                         version            BIGINT         NOT NULL DEFAULT 0,
                         created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
                         updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,

                         CONSTRAINT uk_library_isbn         UNIQUE (isbn),
                         CONSTRAINT ck_library_copies       CHECK (total_copies >= 0 AND available_copies >= 0
                             AND available_copies <= total_copies),
                         CONSTRAINT ck_library_loan_rules   CHECK (loan_period_days > 0 AND max_renewals >= 0
                             AND daily_late_fee >= 0),
                         CONSTRAINT ck_library_pub_year     CHECK (publication_year IS NULL OR publication_year BETWEEN 1400 AND 2100)
);

CREATE INDEX idx_library_title            ON library (title);
CREATE INDEX idx_library_author           ON library (author);
CREATE INDEX idx_library_category         ON library (category);
CREATE INDEX idx_library_course           ON library (course_id);
CREATE INDEX idx_library_active_available ON library (active, available_copies);


CREATE TABLE library_loans (
                               id                      UUID PRIMARY KEY,
                               loan_reference          VARCHAR(30)    NOT NULL,
                               book_id                 UUID           NOT NULL REFERENCES library (id),
                               student_id              UUID           NOT NULL REFERENCES student_profiles (id),
                               status                  VARCHAR(30)    NOT NULL DEFAULT 'RESERVED',

                               reserved_at             TIMESTAMP WITH TIME ZONE,
                               reservation_expires_at  TIMESTAMP WITH TIME ZONE,
                               borrowed_at             TIMESTAMP WITH TIME ZONE,
                               due_at                  TIMESTAMP WITH TIME ZONE,
                               returned_at             TIMESTAMP WITH TIME ZONE,
                               renewal_count           INTEGER        NOT NULL DEFAULT 0,
                               last_renewed_at         TIMESTAMP WITH TIME ZONE,

                               return_requested_at     TIMESTAMP WITH TIME ZONE,
                               return_request_note     VARCHAR(1000),

                               issued_by               UUID,
                               received_by             UUID,
                               condition_on_borrow     VARCHAR(255),
                               condition_on_return     VARCHAR(255),
                               notes                   VARCHAR(1000),

                               late_fee                NUMERIC(12, 2) NOT NULL DEFAULT 0,
                               fee_currency            VARCHAR(3)     NOT NULL DEFAULT 'USD',
                               fee_paid                BOOLEAN        NOT NULL DEFAULT FALSE,
                               fee_paid_at             TIMESTAMP WITH TIME ZONE,

                               due_reminder_sent       BOOLEAN        NOT NULL DEFAULT FALSE,
                               due_reminder_sent_at    TIMESTAMP WITH TIME ZONE,

                               idempotency_key         VARCHAR(100)   NOT NULL,
                               version                 BIGINT         NOT NULL DEFAULT 0,
                               created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                               updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,

                               CONSTRAINT uk_library_loan_reference       UNIQUE (loan_reference),
                               CONSTRAINT uk_library_loan_idempotency_key UNIQUE (idempotency_key),
                               CONSTRAINT ck_library_loan_status          CHECK (status IN
                                                                                 ('RESERVED', 'BORROWED', 'OVERDUE', 'RETURNED', 'LOST', 'CANCELLED')),
                               CONSTRAINT ck_library_loan_dates           CHECK (due_at IS NULL OR borrowed_at IS NULL OR due_at > borrowed_at),
                               CONSTRAINT ck_library_loan_fee             CHECK (late_fee >= 0 AND renewal_count >= 0)
);

CREATE UNIQUE INDEX uk_library_loan_active_student_book
    ON library_loans (student_id, book_id)
    WHERE status IN ('RESERVED', 'BORROWED', 'OVERDUE');

CREATE INDEX idx_library_loan_book           ON library_loans (book_id);
CREATE INDEX idx_library_loan_student_status ON library_loans (student_id, status);
CREATE INDEX idx_library_loan_status_due     ON library_loans (status, due_at);