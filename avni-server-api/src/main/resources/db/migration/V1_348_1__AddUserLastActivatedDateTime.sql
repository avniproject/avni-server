alter table users
    add column last_activated_date_time timestamp(3) with time zone default null;