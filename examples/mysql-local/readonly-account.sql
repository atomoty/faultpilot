-- Minimum-privilege read-only account for FaultPilot MySQL analysis (design §9.1).
-- Grant only what the built-in queries need; FaultPilot never writes, KILLs, or runs EXPLAIN.

CREATE USER IF NOT EXISTS 'faultpilot_ro'@'%' IDENTIFIED BY 'change-me';

-- Connection/thread counts: SHOW GLOBAL STATUS
GRANT PROCESS ON *.* TO 'faultpilot_ro'@'%';

-- SQL digest summary + lock waits: performance_schema
GRANT SELECT ON performance_schema.events_statements_summary_by_digest TO 'faultpilot_ro'@'%';
GRANT SELECT ON performance_schema.data_lock_waits TO 'faultpilot_ro'@'%';

-- Long transactions: information_schema.innodb_trx is visible with PROCESS.

FLUSH PRIVILEGES;

-- If data_lock_waits cannot be granted, FaultPilot skips lock-wait analysis and still returns the
-- rest of the snapshot.
