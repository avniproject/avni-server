-- mid release delete, doesn't effect production
delete from dashboard_filter where 1 = 1;

alter table dashboard_filter rename column filter to filter_config;
