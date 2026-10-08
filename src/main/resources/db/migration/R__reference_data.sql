-- Repeatable (TD-15 / TD-20): the queues and categories every environment needs. Fixed ids so
-- clients and seed data can refer to them; re-running never duplicates or renames anything.
INSERT INTO queues (id, name) VALUES
    ('00000000-0000-4000-8000-000000000101', 'Front Desk'),
    ('00000000-0000-4000-8000-000000000102', 'Clinical Systems'),
    ('00000000-0000-4000-8000-000000000103', 'Network'),
    ('00000000-0000-4000-8000-000000000104', 'Hardware')
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, default_queue_id) VALUES
    ('00000000-0000-4000-8000-000000000201', 'EHR',               '00000000-0000-4000-8000-000000000102'),
    ('00000000-0000-4000-8000-000000000202', 'Printer',           '00000000-0000-4000-8000-000000000104'),
    ('00000000-0000-4000-8000-000000000203', 'Network/VPN',       '00000000-0000-4000-8000-000000000103'),
    ('00000000-0000-4000-8000-000000000204', 'Email/M365',        '00000000-0000-4000-8000-000000000101'),
    ('00000000-0000-4000-8000-000000000205', 'Hardware',          '00000000-0000-4000-8000-000000000104'),
    ('00000000-0000-4000-8000-000000000206', 'Access request',    '00000000-0000-4000-8000-000000000101'),
    ('00000000-0000-4000-8000-000000000207', 'Security incident', '00000000-0000-4000-8000-000000000103'),
    ('00000000-0000-4000-8000-000000000208', 'Other',             '00000000-0000-4000-8000-000000000101')
ON CONFLICT (id) DO NOTHING;

-- Calendars (TD-41). The clinic calendar is the default for SLA policies; 24x7 is for critical tickets.
INSERT INTO business_calendars (id, name, zone, always_open, hours) VALUES
    ('00000000-0000-4000-8000-000000000301', 'Clinic hours', 'America/New_York', false,
     '{"MON": ["08:00", "18:00"], "TUE": ["08:00", "18:00"], "WED": ["08:00", "18:00"], "THU": ["08:00", "18:00"], "FRI": ["08:00", "18:00"]}'::jsonb),
    ('00000000-0000-4000-8000-000000000302', '24x7', 'UTC', true, '{}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- US federal holidays observed in 2026 and the first of 2027, for the clinic calendar.
INSERT INTO calendar_holidays (calendar_id, holiday, name) VALUES
    ('00000000-0000-4000-8000-000000000301', '2026-01-01', 'New Year''s Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-01-19', 'Martin Luther King Jr. Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-02-16', 'Presidents'' Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-05-25', 'Memorial Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-06-19', 'Juneteenth'),
    ('00000000-0000-4000-8000-000000000301', '2026-07-03', 'Independence Day (observed)'),
    ('00000000-0000-4000-8000-000000000301', '2026-09-07', 'Labor Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-10-12', 'Columbus Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-11-11', 'Veterans Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-11-26', 'Thanksgiving Day'),
    ('00000000-0000-4000-8000-000000000301', '2026-12-25', 'Christmas Day'),
    ('00000000-0000-4000-8000-000000000301', '2027-01-01', 'New Year''s Day')
ON CONFLICT (calendar_id, holiday) DO NOTHING;
