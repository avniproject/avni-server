-- Migration to add mediaUrl and mediaType columns to concept table
ALTER TABLE concept ADD COLUMN media_url VARCHAR(255);
ALTER TABLE concept ADD COLUMN media_type VARCHAR(255);
