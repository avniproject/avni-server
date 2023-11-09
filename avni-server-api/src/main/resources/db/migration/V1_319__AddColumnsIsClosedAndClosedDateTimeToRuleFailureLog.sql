ALTER TABLE public.rule_failure_log
    ADD COLUMN IF NOT EXISTS is_closed boolean,
    ADD COLUMN IF NOT EXISTS closed_date_time timestamp
