CREATE TABLE users (
                       id                                UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       first_name                        VARCHAR(255),
                       last_name                         VARCHAR(255),
                       email                             VARCHAR(255) NOT NULL,
                       password                          VARCHAR(255),                    -- nullable: Firebase-only accounts have no password

                       role                              VARCHAR(255) NOT NULL,
                       status                            VARCHAR(255) NOT NULL DEFAULT 'UNVERIFIED',

                       failed_login_attempts             INTEGER      NOT NULL DEFAULT 0,

                       forgot_password                   VARCHAR(255),
                       forgot_password_expiration_date   TIMESTAMPTZ,
                       reset_password                    VARCHAR(255),

                       otp_code                          VARCHAR(255),
                       otp_code_expiration_code          TIMESTAMPTZ,                     -- name matches the entity's @Column (see note below)
                       otp_code_verified                 BOOLEAN      NOT NULL DEFAULT FALSE,

                       magic_link_token                  VARCHAR(255),
                       verify_magic_link_token           VARCHAR(255),
                       magic_link_token_expiration_date  TIMESTAMPTZ,

                       is_account_blocked                BOOLEAN      NOT NULL DEFAULT FALSE,
                       is_account_un_blocked             BOOLEAN      NOT NULL DEFAULT FALSE,
                       is_account_verified               BOOLEAN      NOT NULL DEFAULT FALSE,
                       is_account_deleted                BOOLEAN      NOT NULL DEFAULT FALSE,

                       firebase_uid                      VARCHAR(128),

                       access_token                      TEXT,                            -- JWTs are longer than 255 characters
                       refresh_token                     TEXT,

                       created_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT uq_users_email         UNIQUE (email),
                       CONSTRAINT uq_users_firebase_uid  UNIQUE (firebase_uid)
);

-- Blocks case-variant duplicates (Admin@x.com vs admin@x.com) even if a row is inserted outside JPA
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));