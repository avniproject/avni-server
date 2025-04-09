create table post_etl_sync_status
(
    id              serial primary key,
    cutoff_datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    db_user         text                     not null UNIQUE
);

create policy post_etl_sync_status_rls_policy on post_etl_sync_status
    using (db_user = current_user)
    WITH CHECK (db_user = current_user);

ALTER TABLE post_etl_sync_status
    ENABLE ROW LEVEL SECURITY;

create index post_etl_sync_status_db_user_idx on post_etl_sync_status (db_user);