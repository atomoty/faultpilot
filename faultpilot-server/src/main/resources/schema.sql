-- FaultPilot internal store (H2). Holds generated diagnosis reports and ingested
-- change events so they survive restarts. The nested report/attributes are stored
-- as JSON text; queries filter by project/environment/time.

CREATE TABLE IF NOT EXISTS diagnosis_report (
    id          VARCHAR(64) PRIMARY KEY,
    project_id  VARCHAR(128) NOT NULL,
    environment VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    report_json CLOB         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_project_env_time
    ON diagnosis_report (project_id, environment, created_at);

CREATE SEQUENCE IF NOT EXISTS change_event_seq START WITH 1;

CREATE TABLE IF NOT EXISTS change_event (
    evidence_id     VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(128) NOT NULL,
    environment     VARCHAR(64)  NOT NULL,
    type            VARCHAR(64),
    occurred_at     TIMESTAMP,
    attributes_json CLOB         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_project_env_time
    ON change_event (project_id, environment, occurred_at);
