alter table message_request_queue add column manual_broadcast_message_id bigint references manual_broadcast_message (id);

alter table message_request_queue alter column entity_id drop not null;
alter table message_request_queue alter column message_rule_id drop not null;
alter table message_receiver alter column receiver_id drop not null;

