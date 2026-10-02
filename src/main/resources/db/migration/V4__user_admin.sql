-- V4 (TD-14): accounts an admin creates start with a temporary password the user should replace.
ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
