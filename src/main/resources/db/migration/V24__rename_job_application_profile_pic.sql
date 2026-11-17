DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'job_applications' AND column_name = 'profilepic') THEN
ALTER TABLE job_applications RENAME COLUMN profilepic TO profile_pic;
ELSIF EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'job_applications' AND column_name = 'profilePic') THEN
ALTER TABLE job_applications RENAME COLUMN "profilePic" TO profile_pic;
END IF;
END $$;

ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS profile_pic VARCHAR(1024);