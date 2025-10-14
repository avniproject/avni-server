-- Add new jsonb column for media array
ALTER TABLE concept ADD COLUMN IF NOT EXISTS media jsonb;

-- Migrate existing data from media_url and media_type to the new media jsonb column
UPDATE concept 
SET media = jsonb_build_array(
    jsonb_build_object(
        'url', media_url::text,
        'type', media_type::text
    )
)
WHERE media_url IS NOT NULL OR media_type IS NOT NULL;

ALTER TABLE concept DROP COLUMN media_url;
ALTER TABLE concept DROP COLUMN media_type;
