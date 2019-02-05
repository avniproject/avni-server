create table subject_type (
  id              serial primary key,
  uuid            varchar(255),
  name           varchar(255) not null,
  core_fields     jsonb,
  search_fields   jsonb,
  organisation_id bigint       not null,
  is_voided       boolean      not null default false,
  audit_id        bigint       not null,
  version         integer               default 1
);

alter table only subject_type
  add constraint subject_type_audit foreign key (audit_id) references audit (id);
ALTER TABLE ONLY subject_type
  ADD CONSTRAINT subject_type_organisation FOREIGN KEY (organisation_id) REFERENCES organisation (id);

ALTER TABLE subject_type ENABLE ROW LEVEL SECURITY;

CREATE POLICY subject_type_orgs
  ON subject_type USING (organisation_id IN
                  (WITH RECURSIVE list_of_orgs(id, parent_organisation_id) AS (SELECT id, parent_organisation_id
                                                                               FROM organisation
                                                                               WHERE db_user = current_user
                                                                               UNION ALL SELECT o.id,
                                                                                                o.parent_organisation_id
                                                                                         FROM organisation o,
                                                                                              list_of_orgs log
                                                                                         WHERE o.id = log.parent_organisation_id) SELECT id
                                                                                                                                  FROM list_of_orgs))
WITH CHECK ((organisation_id = (select id
                                from organisation
                                where db_user = current_user)));

create table operational_subject_type (
  id              serial primary key,
  uuid            varchar(255),
  name           varchar(255) not null,
  subject_type_id       INTEGER REFERENCES subject_type (id) NOT NULL,
  organisation_id bigint       not null,
  is_voided       boolean      not null default false,
  audit_id        bigint       not null,
  version         integer               default 1

);

alter table only operational_subject_type
  add constraint operational_subject_type_audit foreign key (audit_id) references audit (id);
ALTER TABLE ONLY operational_subject_type
  ADD CONSTRAINT operational_subject_type_organisation FOREIGN KEY (organisation_id) REFERENCES organisation (id);

ALTER TABLE operational_subject_type ENABLE ROW LEVEL SECURITY;

CREATE POLICY operational_subject_type_orgs
  ON operational_subject_type USING (organisation_id IN
                  (WITH RECURSIVE list_of_orgs(id, parent_organisation_id) AS (SELECT id, parent_organisation_id
                                                                               FROM organisation
                                                                               WHERE db_user = current_user
                                                                               UNION ALL SELECT o.id,
                                                                                                o.parent_organisation_id
                                                                                         FROM organisation o,
                                                                                              list_of_orgs log
                                                                                         WHERE o.id = log.parent_organisation_id) SELECT id
                                                                                                                                  FROM list_of_orgs))
WITH CHECK ((organisation_id = (select id
                                from organisation
                                where db_user = current_user)));





  INSERT INTO concept (name, data_type, uuid, version, audit_id, organisation_id)
  VALUES ('First Name', 'Text', '098d688a-63ed-44ac-88d3-95eb36d0cce6', 1, create_audit(), 1);
  INSERT INTO concept (name, data_type, uuid, version, audit_id, organisation_id)
  VALUES ('Last Name', 'Text', 'f7198793-aa42-436c-ae9f-733d091a651e', 1, create_audit(), 1);
  INSERT INTO concept (name, data_type, uuid, version, audit_id, organisation_id)
  VALUES ('Date of Birth', 'Date', '57489b4b-4da9-4b12-8558-0ae61807bfce', 1, create_audit(), 1);
  INSERT INTO concept (name, data_type, uuid, version, audit_id, organisation_id)
  VALUES ('Date of birth verified', 'Coded', 'f03ce02a-6852-4fbf-95b1-238ce7d0b2c7', 1, create_audit(), 1);


--insert individual subject type
insert into subject_type(uuid, name, core_fields, search_fields, organisation_id, audit_id) VALUES ('9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3', 'Individual', '{"coreFields": [
  {"comment": "First Name", "conceptUuid": "098d688a-63ed-44ac-88d3-95eb36d0cce6"},
  {"comment": "Last Name", "conceptUuid": "f7198793-aa42-436c-ae9f-733d091a651e"},
  {"comment": "Gender", "conceptUuid": "483be0b2-b6ba-40e0-8bf7-91cb33c6e284", "type": "SingleSelect"},
  {"comment": "Date of Birth", "conceptUuid": "57489b4b-4da9-4b12-8558-0ae61807bfce", "type": "DateOfBirthAndAge", "extra": {"verificationConcept": "f03ce02a-6852-4fbf-95b1-238ce7d0b2c7"}}
]}'::jsonb, '{}'::jsonb, 1, create_audit() );