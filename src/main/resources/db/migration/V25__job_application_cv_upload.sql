ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS upload_cv VARCHAR(1024);

UPDATE job_applications SET upload_cv = 'legacy/no-cv-uploaded' WHERE upload_cv IS NULL;
ALTER TABLE job_applications ALTER COLUMN upload_cv SET NOT NULL;

ALTER TABLE job_applications ALTER COLUMN resume_id DROP NOT NULL;