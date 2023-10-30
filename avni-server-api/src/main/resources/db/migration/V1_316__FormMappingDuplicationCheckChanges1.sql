alter table form_mapping
    add column if not exists impl_version int not null default 1;
update form_mapping
set impl_version = 0
where is_voided = true;
