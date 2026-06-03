-- Example schema for the JDBC log-table source (H2 syntax).
-- A project that already stores logs in a table exposes a read-only VIEW that aliases its columns
-- to the canonical names FaultPilot expects: occurred_at, level, trace_id, message, stack_trace.

CREATE TABLE IF NOT EXISTS app_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  TIMESTAMP,
    log_level   VARCHAR(10),
    trace_id    VARCHAR(64),
    message     VARCHAR(2000),
    stack_trace VARCHAR(8000)
);

CREATE VIEW IF NOT EXISTS faultpilot_log_view AS
SELECT
    created_at  AS occurred_at,
    log_level   AS level,
    trace_id,
    message,
    stack_trace
FROM app_log;

-- Sample rows: an NullPointerException spike around 10:05–10:06 (+08:00).
INSERT INTO app_log (created_at, log_level, trace_id, message, stack_trace) VALUES
 ('2026-06-01 09:58:01.102', 'INFO',  't-info', 'Received create order request', NULL),
 ('2026-06-01 10:01:12.044', 'WARN',  't-warn', 'Payment gateway slow, took 1840ms', NULL),
 ('2026-06-01 10:05:31.501', 'ERROR', 't-1', 'Create order failed',
  'java.lang.NullPointerException: order is null
	at com.example.order.OrderService.create(OrderService.java:88)
	at com.example.order.OrderController.create(OrderController.java:42)'),
 ('2026-06-01 10:05:48.778', 'ERROR', 't-2', 'Create order failed',
  'java.lang.NullPointerException: order is null
	at com.example.order.OrderService.create(OrderService.java:88)'),
 ('2026-06-01 10:06:02.115', 'ERROR', 't-3', 'Create order failed',
  'java.lang.NullPointerException: order is null
	at com.example.order.OrderService.create(OrderService.java:88)'),
 ('2026-06-01 10:06:19.640', 'ERROR', 't-4', 'Create order failed',
  'java.lang.NullPointerException: order is null
	at com.example.order.OrderService.create(OrderService.java:88)'),
 ('2026-06-01 10:06:33.992', 'ERROR', 't-5', 'Create order failed',
  'java.lang.NullPointerException: order is null
	at com.example.order.OrderService.create(OrderService.java:88)');
