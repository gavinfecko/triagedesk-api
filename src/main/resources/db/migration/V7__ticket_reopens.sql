-- V7 (TD-27): how many times a resolved ticket was reopened. A reopen is the requester saying
-- "still broken"; the count feeds the reopen-rate KPI (TD-60).
ALTER TABLE tickets ADD COLUMN reopen_count integer NOT NULL DEFAULT 0;
