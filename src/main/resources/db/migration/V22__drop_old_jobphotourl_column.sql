DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'job_listings' AND column_name = 'jobphotourl') THEN
UPDATE job_listings
SET job_photo_url = COALESCE(job_photo_url, jobphotourl);

ALTER TABLE job_listings DROP COLUMN jobphotourl;
END IF;
END $$;

ALTER TABLE job_listings ALTER COLUMN job_photo_url DROP NOT NULL;