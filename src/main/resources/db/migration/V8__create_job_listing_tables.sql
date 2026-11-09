CREATE TABLE job_listings (
                              id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              title                      VARCHAR(255)  NOT NULL,
                              jobPhotoUrl                VARCHAR(1024) NOT NULL,
                              slug                       VARCHAR(255)  NOT NULL,
                              reference_code             VARCHAR(30),
                              short_description          VARCHAR(500),
                              description                TEXT,
                              responsibilities           TEXT,
                              requirements               TEXT,

                              faculty                    VARCHAR(150),
                              department                 VARCHAR(150),
                              position_title             VARCHAR(150),
                              reports_to                 VARCHAR(150),

                              employment_type            VARCHAR(30)   NOT NULL DEFAULT 'FULL_TIME',
                              work_mode                  VARCHAR(30)   NOT NULL DEFAULT 'ON_SITE',
                              experience_level           VARCHAR(30),
                              minimum_years_experience   INTEGER,
                              minimum_education          VARCHAR(100),
                              number_of_vacancies        INTEGER       NOT NULL DEFAULT 1,

                              city                       VARCHAR(100),
                              state_region               VARCHAR(100),
                              country                    VARCHAR(100),

                              salary_min                 NUMERIC(12,2),
                              salary_max                 NUMERIC(12,2),
                              currency                   VARCHAR(3)    NOT NULL DEFAULT 'USD',
                              salary_period              VARCHAR(30),
                              salary_visible             BOOLEAN       NOT NULL DEFAULT TRUE,

                              application_deadline       DATE,
                              expected_start_date        DATE,
                              contact_email              VARCHAR(255),
                              application_instructions   TEXT,

                              status                     VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
                              published_at               TIMESTAMPTZ,
                              closed_at                  TIMESTAMPTZ,

                              posted_by                  UUID,
                              created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),

                              CONSTRAINT uq_job_listings_slug           UNIQUE (slug),
                              CONSTRAINT uq_job_listings_reference_code UNIQUE (reference_code),
                              CONSTRAINT chk_job_listings_salary_range  CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max),
                              CONSTRAINT chk_job_listings_vacancies     CHECK (number_of_vacancies > 0)
);

CREATE INDEX idx_job_listings_status      ON job_listings (status);
CREATE INDEX idx_job_listings_deadline    ON job_listings (application_deadline);
CREATE INDEX idx_job_listings_department  ON job_listings (faculty, department);

CREATE TABLE job_listing_certifications (
                                            job_listing_id UUID         NOT NULL REFERENCES job_listings (id) ON DELETE CASCADE,
                                            certification  VARCHAR(150) NOT NULL
);
CREATE INDEX idx_job_listing_certifications ON job_listing_certifications (job_listing_id);

CREATE TABLE job_listing_skills (
                                    job_listing_id UUID         NOT NULL REFERENCES job_listings (id) ON DELETE CASCADE,
                                    skill          VARCHAR(100) NOT NULL
);
CREATE INDEX idx_job_listing_skills ON job_listing_skills (job_listing_id);

CREATE TABLE job_listing_languages (
                                       job_listing_id UUID        NOT NULL REFERENCES job_listings (id) ON DELETE CASCADE,
                                       language       VARCHAR(50) NOT NULL
);
CREATE INDEX idx_job_listing_languages ON job_listing_languages (job_listing_id);

CREATE TABLE job_listing_benefits (
                                      job_listing_id UUID         NOT NULL REFERENCES job_listings (id) ON DELETE CASCADE,
                                      benefit        VARCHAR(150) NOT NULL
);
CREATE INDEX idx_job_listing_benefits ON job_listing_benefits (job_listing_id);