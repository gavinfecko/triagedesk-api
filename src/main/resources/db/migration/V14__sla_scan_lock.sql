-- V14 (TD-43): leases for background jobs. Whoever moves locked_until into the future owns the job until then;
-- a second instance finds the lease taken and skips its run. Expired leases are simply taken over.
CREATE TABLE sla_scan_lock (
    name         varchar(40) PRIMARY KEY,
    owner        varchar(80),
    locked_until timestamptz NOT NULL
);
INSERT INTO sla_scan_lock (name, locked_until) VALUES
    ('sla-breach-scan', 'epoch'),
    ('auto-close', 'epoch');
