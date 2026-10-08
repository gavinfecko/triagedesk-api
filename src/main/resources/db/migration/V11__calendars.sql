-- V11 (TD-41): business-hours calendars. SLA clocks (TD-42) count only the hours a calendar is open.
CREATE TABLE business_calendars (
    id          uuid        PRIMARY KEY,
    name        varchar(60) NOT NULL,
    zone        varchar(60) NOT NULL,
    always_open boolean     NOT NULL DEFAULT false,
    hours       jsonb       NOT NULL,          -- {"MON": ["08:00", "18:00"], ...}; absent day = closed
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT business_calendars_name_unique UNIQUE (name)
);

CREATE TABLE calendar_holidays (
    calendar_id uuid        NOT NULL REFERENCES business_calendars (id) ON DELETE CASCADE,
    holiday     date        NOT NULL,
    name        varchar(80) NOT NULL,
    PRIMARY KEY (calendar_id, holiday)
);
