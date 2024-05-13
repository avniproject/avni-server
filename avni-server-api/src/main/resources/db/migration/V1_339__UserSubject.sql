create table user_subject
(
    id                      serial primary key,
    organisation_id         int,
    uuid                    varchar(255)                not null,
    is_voided               boolean                     NOT NULL DEFAULT FALSE,
    created_date_time       timestamp(3) with time zone not null,
    last_modified_date_time timestamp(3) with time zone not null,
    created_by_id           int                         not null,
    last_modified_by_id     int                         not null,
    user_id                 int                         not null,
    subject_id              int                         not null,
    version                 int                         not null default 1,
    foreign key (user_id) references users (id),
    foreign key (subject_id) references individual (id),
    foreign key (organisation_id) references organisation (id),
    foreign key (created_by_id) references users (id),
    foreign key (last_modified_by_id) references users (id),
--     support only one user-subject relationship
    unique (user_id),
    unique (subject_id),
    unique (uuid)
);

grant all on table user_subject to public;
grant all on sequence user_subject_id_seq to public;
select enable_rls_on_tx_table('user_subject');
