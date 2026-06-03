package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.LogEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Two built-in, deterministic demo scenarios (development-plan.md §7). They are anchored to a
 * fixed base instant so reports are reproducible and assertable.
 *
 * <ul>
 *   <li>{@code order-service / staging} — deployment followed by an NPE spike → deployment-regression</li>
 *   <li>{@code order-service / local}   — slow API + slow SQL + pool waits → slow-sql-pool-contention</li>
 * </ul>
 */
public final class DemoFixtures {

    /** Anchor: end of the window used by the demo curl in the plan (2026-06-01T11:00 +08:00 == 03:00Z). */
    public static final Instant ANCHOR = Instant.parse("2026-06-01T03:00:00Z");

    private static final List<DemoScenario> SCENARIOS = List.of(deploymentRegression(), slowSqlContention());

    private DemoFixtures() {
    }

    public static List<DemoScenario> all() {
        return SCENARIOS;
    }

    public static Optional<DemoScenario> find(String projectId, String environment) {
        return SCENARIOS.stream()
                .filter(s -> s.projectId().equals(projectId) && s.environment().equals(environment))
                .findFirst();
    }

    // --- scenario 1: deployment regression --------------------------------------------------

    private static DemoScenario deploymentRegression() {
        String project = "order-service";
        String env = "staging";

        // Deploy 20 minutes before the spike, inside the window.
        Instant deployAt = ANCHOR.minusSeconds(20 * 60);
        ChangeEvent deploy = new ChangeEvent("deploy-1", project, env, "DEPLOYMENT", deployAt,
                Map.of("version", "v1.4.2", "commitId", "abc123"));

        List<LogEvent> logs = new ArrayList<>();
        // 8 NPEs in the current (last 10 min) window — a clear spike vs ~1 in baseline.
        String stack = "java.lang.NullPointerException: Cannot invoke \"Order.getId()\" because order is null\n"
                + "\tat com.example.order.OrderService.create(OrderService.java:88)\n"
                + "\tat com.example.order.OrderController.create(OrderController.java:42)";
        for (int i = 0; i < 8; i++) {
            logs.add(new LogEvent(project, env, ANCHOR.minusSeconds(60L * (1 + i)), "ERROR",
                    "com.example.order.OrderService", "trace-np-" + i,
                    "Create order failed", stack, Map.of("exceptionClass", "NullPointerException")));
        }
        // One pre-deploy baseline occurrence, 40 min before anchor.
        logs.add(new LogEvent(project, env, ANCHOR.minusSeconds(40 * 60), "ERROR",
                "com.example.order.OrderService", "trace-np-base",
                "Create order failed", stack, Map.of("exceptionClass", "NullPointerException")));

        return new DemoScenario(project, env, logs, List.of(), List.of(), List.of(deploy),
                "deployment-regression", List.of("deploy-1", "log-1"));
    }

    // --- scenario 2: slow SQL → pool contention ---------------------------------------------

    private static DemoScenario slowSqlContention() {
        String project = "order-service";
        String env = "local";

        MetricAnomaly p95 = new MetricAnomaly("metric-1", "http_p95_seconds",
                "订单创建接口 P95 从 300ms 升至 2.8s", 0.3, 2.8, "s", ANCHOR.minusSeconds(30 * 60));
        MetricAnomaly poolWait = new MetricAnomaly("metric-2", "db_pool_pending_connections",
                "数据库连接池等待数从 0 升至 12", 0, 12, "count", ANCHOR.minusSeconds(28 * 60));

        SlowSqlSummary slowSql = new SlowSqlSummary("sql-1",
                "select * from orders where customer_id = ?", 320, 2100, 2850,
                ANCHOR.minusSeconds(35 * 60), "mock");

        // A handful of slow-call WARN logs (below spike threshold; provides timeline context).
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            logs.add(new LogEvent(project, env, ANCHOR.minusSeconds(60L * (2 + i)), "WARN",
                    "com.example.order.OrderService", "trace-slow-" + i,
                    "Slow order query detected", null, Map.of()));
        }

        // The pool metric (metric-2) is the causal mechanism, so the contention candidate must
        // reference it; the P95 latency metric is supporting context on the timeline.
        return new DemoScenario(project, env, logs, List.of(p95, poolWait), List.of(slowSql), List.of(),
                "slow-sql-pool-contention", List.of("sql-1", "metric-2"));
    }
}
