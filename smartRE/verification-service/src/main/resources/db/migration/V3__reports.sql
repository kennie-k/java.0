CREATE TABLE IF NOT EXISTS reports (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID        NOT NULL,
    target_type     VARCHAR(20) NOT NULL,
    target_id       UUID        NOT NULL,
    reason          VARCHAR(40) NOT NULL,
    details         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    admin_notes     TEXT,
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_status   ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_reporter ON reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_reports_target   ON reports(target_type, target_id);
