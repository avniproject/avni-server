create user :OPENCHS_DATABASE_USER with password :'OPENCHS_DATABASE_PASSWORD' createrole;
create database :OPENCHS_DATABASE with owner openchs;
\c :OPENCHS_DATABASE
create extension if not exists "uuid-ossp";
\c :OPENCHS_DATABASE
create extension if not exists "ltree";
\c :OPENCHS_DATABASE
create extension if not exists "hstore";
\c postgres
create role demo with NOINHERIT NOLOGIN;
grant demo to :OPENCHS_DATABASE_USER;
create role openchs_impl;
grant openchs_impl to :OPENCHS_DATABASE_USER;
create role organisation_user createrole admin openchs_impl;
