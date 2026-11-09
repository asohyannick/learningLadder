CREATE TABLE job_applications (
                                  id                       UUID                      NOT NULL DEFAULT gen_random_uuid(),

                                  job_listing_id           UUID                      NOT NULL,
                                  applicant_id             UUID                      NOT NULL,
                                  profilePic               VARCHAR(1024)             NOT NULL ,
                                  first_name               VARCHAR(100)              NOT NULL,
                                  last_name                VARCHAR(100)              NOT NULL,
                                  email                    VARCHAR(255)              NOT NULL,
                                  phone_number             VARCHAR(30),
                                  date_of_birth            DATE,
                                  nationality              VARCHAR(100),
                                  city                     VARCHAR(100),
                                  state_region             VARCHAR(100),
                                  country                  VARCHAR(100),

                                  current_job_title        VARCHAR(150),
                                  current_employer         VARCHAR(150),
                                  years_of_experience      INTEGER,
                                  highest_education        VARCHAR(100),
                                  linkedin_url             VARCHAR(500),
                                  portfolio_url            VARCHAR(500),

                                  expected_salary          NUMERIC(12, 2),
                                  expected_salary_currency VARCHAR(3)                DEFAULT 'USD',
                                  expected_salary_period   VARCHAR(30),
                                  available_from           DATE,
                                  notice_period_days       INTEGER,

                                  cover_letter             TEXT,

                                  resume_id                UUID                      NOT NULL,
                                  cover_letter_file_id     UUID,

                                  status                   VARCHAR(30)               NOT NULL DEFAULT 'SUBMITTED',
                                  submitted_at             TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
                                  reviewed_by              UUID,
                                  reviewed_at              TIMESTAMP WITH TIME ZONE,
                                  review_note              VARCHAR(1000),
                                  rejection_reason         VARCHAR(500),
                                  interview_scheduled_at   TIMESTAMP WITH TIME ZONE,
                                  withdrawn_at             TIMESTAMP WITH TIME ZONE,

                                  consent_given            BOOLEAN                   NOT NULL DEFAULT FALSE,
                                  idempotency_key          VARCHAR(100)              NOT NULL,
                                  created_at               TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
                                  updated_at               TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),

                                  CONSTRAINT pk_job_applications PRIMARY KEY (id),

                                  CONSTRAINT fk_job_application_job
                                      FOREIGN KEY (job_listing_id) REFERENCES job_listings (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_job_application_applicant
                                      FOREIGN KEY (applicant_id) REFERENCES users (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_job_application_resume
                                      FOREIGN KEY (resume_id) REFERENCES stored_files (id),
                                  CONSTRAINT fk_job_application_cover_letter_file
                                      FOREIGN KEY (cover_letter_file_id) REFERENCES stored_files (id) ON DELETE SET NULL,

                                  CONSTRAINT uk_job_application_applicant UNIQUE (applicant_id),
                                  CONSTRAINT uk_job_application_idempotency_key UNIQUE (idempotency_key),

                                  CONSTRAINT ck_job_application_status CHECK (status IN (
                                                                                         'SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW_SCHEDULED',
                                                                                         'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN')),
                                  CONSTRAINT ck_job_application_experience CHECK (years_of_experience IS NULL OR years_of_experience >= 0),
                                  CONSTRAINT ck_job_application_notice CHECK (notice_period_days IS NULL OR notice_period_days >= 0),
                                  CONSTRAINT ck_job_application_salary CHECK (expected_salary IS NULL OR expected_salary >= 0)
);

CREATE INDEX idx_job_application_job        ON job_applications (job_listing_id);
CREATE INDEX idx_job_application_status     ON job_applications (status);
CREATE INDEX idx_job_application_job_status ON job_applications (job_listing_id, status);
CREATE INDEX idx_job_application_created_at ON job_applications (created_at);

CREATE TABLE job_application_documents (
                                           job_application_id UUID NOT NULL,
                                           stored_file_id     UUID NOT NULL,

                                           CONSTRAINT pk_job_application_documents PRIMARY KEY (job_application_id, stored_file_id),
                                           CONSTRAINT fk_job_application_documents_application
                                               FOREIGN KEY (job_application_id) REFERENCES job_applications (id) ON DELETE CASCADE,
                                           CONSTRAINT fk_job_application_documents_file
                                               FOREIGN KEY (stored_file_id) REFERENCES stored_files (id) ON DELETE CASCADE
);

CREATE INDEX idx_job_application_documents_file ON job_application_documents (stored_file_id);