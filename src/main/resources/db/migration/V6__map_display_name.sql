-- V6: Map Display Name
-- Adds display_name column to the maps table for localized or customized map display labels
-- Uses IF EXISTS to ensure idempotency across environments

ALTER TABLE maps
ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
