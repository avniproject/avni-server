-- cannot drop column or program_outcome table because it is used in reporting views
alter table program_enrolment drop CONSTRAINT program_enrolment_program_outcome;
