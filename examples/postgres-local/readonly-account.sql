-- Minimum-privilege read-only role for FaultPilot PostgreSQL analysis (design §9.2).
-- FaultPilot only SELECTs system views; it never writes or terminates backends.

CREATE ROLE faultpilot_ro LOGIN PASSWORD 'change-me';

-- pg_stat_activity / pg_locks are world-readable, but non-superusers see masked query text for
-- other users' sessions. Grant pg_monitor for full visibility (recommended, read-only).
GRANT pg_monitor TO faultpilot_ro;

-- Slow-SQL summary requires the pg_stat_statements extension (run once, as a superuser, in the DB):
--   CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
-- and shared_preload_libraries must include 'pg_stat_statements'.
-- If the extension is not enabled, FaultPilot skips slow-SQL analysis and still returns the snapshot.
