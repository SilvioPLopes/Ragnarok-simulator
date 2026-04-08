-- V5: Skill Enrichment
-- Adds description and img_url columns to the skills table for enriched skill information display
-- Uses IF EXISTS to ensure idempotency across environments

ALTER TABLE skills
ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE skills
ADD COLUMN IF NOT EXISTS img_url VARCHAR(500);
