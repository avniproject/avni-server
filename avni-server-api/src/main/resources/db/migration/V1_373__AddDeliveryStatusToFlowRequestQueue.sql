ALTER TABLE flow_request_queue ADD COLUMN delivery_status VARCHAR(255) NOT NULL DEFAULT 'NotSent';
