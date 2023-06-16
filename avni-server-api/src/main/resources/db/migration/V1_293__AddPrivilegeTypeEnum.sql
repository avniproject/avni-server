alter table privilege add column type varchar(100) null;
update privilege set type =  replace(initcap(name), ' ', '') where 1 = 1;
alter table privilege alter column type set not null;
