ALTER TABLE public.rule_failure_telemetry
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_id   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS entity_id   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS app_type    VARCHAR(255);

ALTER TABLE public.rule_failure_telemetry
    RENAME COLUMN close_date_time TO closed_date_time;

ALTER TABLE public.rule_failure_telemetry
    ALTER COLUMN rule_uuid DROP NOT NULL;
