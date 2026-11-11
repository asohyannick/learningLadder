CREATE TABLE partners (
                          id                     UUID PRIMARY KEY,
                          name                   VARCHAR(150)   NOT NULL,
                          slug                   VARCHAR(150)   NOT NULL,
                          logo                   VARCHAR(1024),
                          banner_image           VARCHAR(1024),
                          short_description      VARCHAR(300),
                          description            TEXT,
                          industry               VARCHAR(100),

                          partner_type           VARCHAR(30)    NOT NULL,
                          sponsorship_tier       VARCHAR(30),
                          status                 VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
                          benefits               TEXT,

                          website_url            VARCHAR(500),
                          linkedin_url           VARCHAR(500),
                          facebook_url           VARCHAR(500),
                          twitter_url            VARCHAR(500),
                          instagram_url          VARCHAR(500),

                          contact_person_name    VARCHAR(150),
                          contact_person_title   VARCHAR(100),
                          contact_email          VARCHAR(255),
                          contact_phone          VARCHAR(30),

                          address_line           VARCHAR(255),
                          city                   VARCHAR(100),
                          state_region           VARCHAR(100),
                          country                VARCHAR(100),

                          agreement_start_date   DATE,
                          agreement_end_date     DATE,
                          agreement_document_id  UUID REFERENCES stored_files (id),
                          sponsorship_amount     NUMERIC(14, 2),
                          sponsorship_currency   VARCHAR(3)     DEFAULT 'USD',

                          show_on_website        BOOLEAN        NOT NULL DEFAULT TRUE,
                          featured               BOOLEAN        NOT NULL DEFAULT FALSE,
                          display_order          INTEGER        NOT NULL DEFAULT 0,

                          internal_notes         VARCHAR(2000),
                          created_by             UUID,
                          version                BIGINT         NOT NULL DEFAULT 0,
                          created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT uk_partners_name UNIQUE (name),
                          CONSTRAINT uk_partners_slug UNIQUE (slug),
                          CONSTRAINT ck_partners_type   CHECK (partner_type IN
                                                               ('SPONSOR', 'ACADEMIC', 'CORPORATE', 'GOVERNMENT', 'NGO', 'COMMUNITY', 'MEDIA')),
                          CONSTRAINT ck_partners_tier   CHECK (sponsorship_tier IS NULL OR sponsorship_tier IN
                                                                                           ('PLATINUM', 'GOLD', 'SILVER', 'BRONZE', 'SUPPORTER')),
                          CONSTRAINT ck_partners_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'EXPIRED')),
                          CONSTRAINT ck_partners_dates  CHECK (agreement_end_date IS NULL OR agreement_start_date IS NULL
                              OR agreement_end_date >= agreement_start_date),
                          CONSTRAINT ck_partners_amount CHECK (sponsorship_amount IS NULL OR sponsorship_amount >= 0)
);

CREATE INDEX idx_partners_type            ON partners (partner_type);
CREATE INDEX idx_partners_status          ON partners (status);
CREATE INDEX idx_partners_tier            ON partners (sponsorship_tier);
CREATE INDEX idx_partners_public_display  ON partners (show_on_website, featured, display_order);
CREATE INDEX idx_partners_agreement_end   ON partners (agreement_end_date);