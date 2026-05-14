ALTER TABLE form ADD COLUMN IF NOT EXISTS share_rule text;
ALTER TABLE form ADD COLUMN IF NOT EXISTS share_template_s3_key text;
ALTER TABLE form ADD COLUMN IF NOT EXISTS share_translations jsonb;
