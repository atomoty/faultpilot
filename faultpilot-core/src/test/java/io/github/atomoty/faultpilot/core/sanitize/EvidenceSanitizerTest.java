package io.github.atomoty.faultpilot.core.sanitize;

import io.github.atomoty.faultpilot.core.model.ChangeEvent;
import io.github.atomoty.faultpilot.core.model.MetricAnomaly;
import io.github.atomoty.faultpilot.core.model.SlowSqlSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceSanitizerTest {

    private final EvidenceSanitizer sanitizer = new EvidenceSanitizer();

    @Test
    void redactsSecretsAndPii() {
        String text = "login user token=abc123XYZ email=foo@bar.com phone 13800138000";
        String redacted = sanitizer.redact(text);

        assertThat(redacted).doesNotContain("abc123XYZ");
        assertThat(redacted).doesNotContain("foo@bar.com");
        assertThat(redacted).doesNotContain("13800138000");
        assertThat(redacted).contains("***");
    }

    @Test
    void redactsBearerTokenIncludingTheTokenItself() {
        assertThat(sanitizer.redact("Authorization: Bearer sk-abc123def456"))
                .doesNotContain("sk-abc123def456");
        assertThat(sanitizer.redact("sent header Bearer xyztoken now"))
                .doesNotContain("xyztoken");
    }

    @Test
    void normalizeMessageMasksVariableTokens() {
        String a = sanitizer.normalizeMessage("order 12345 failed for user 67890");
        String b = sanitizer.normalizeMessage("order 999 failed for user 111");
        assertThat(a).isEqualTo(b);
        assertThat(a).contains("{n}");
    }

    @Test
    void topStackFrameStripsLineNumbers() {
        String stack = "java.lang.NullPointerException: boom\n"
                + "\tat com.example.OrderService.create(OrderService.java:88)\n"
                + "\tat com.example.OrderController.create(OrderController.java:42)";
        assertThat(sanitizer.topStackFrame(stack))
                .isEqualTo("com.example.OrderService.create(OrderService.java)");
    }

    @Test
    void truncateAppendsEllipsisWhenCut() {
        assertThat(sanitizer.truncate("abcdef", 3)).isEqualTo("abc…");
        assertThat(sanitizer.truncate("ab", 3)).isEqualTo("ab");
    }

    @Test
    void redactsChangeEventAttributeValues() {
        ChangeEvent in = new ChangeEvent("event-1", "p", "e", "DEPLOYMENT", Instant.now(),
                Map.of("version", "v9", "password", "plain-secret"));
        ChangeEvent out = sanitizer.sanitize(in);
        assertThat(out.attributes().get("password")).isEqualTo("***");
        assertThat(out.attributes().get("version")).isEqualTo("v9");
    }

    @Test
    void redactsSlowSqlAndMetricFreeText() {
        SlowSqlSummary sql = new SlowSqlSummary("sql-1", "select * from t where token=abc123secret",
                1, 10, 10, Instant.now(), "mock");
        assertThat(sanitizer.sanitize(sql).sqlTemplate()).doesNotContain("abc123secret");

        MetricAnomaly metric = new MetricAnomaly("metric-1", "m",
                "user foo@bar.com saw spike", 1, 2, "x", Instant.now());
        assertThat(sanitizer.sanitize(metric).description()).doesNotContain("foo@bar.com");
    }

    @Test
    void limitsStackLines() {
        StringBuilder sb = new StringBuilder("java.lang.RuntimeException: boom");
        for (int i = 0; i < 30; i++) {
            sb.append("\n\tat com.example.C.m").append(i).append("(C.java:").append(i).append(')');
        }
        String limited = sanitizer.limitStackLines(sb.toString());
        // 12 kept lines + 1 ellipsis marker line
        assertThat(limited.split("\\R")).hasSize(EvidenceSanitizer.MAX_STACK_LINES + 1);
        assertThat(limited).endsWith("…");
    }

    @Test
    void sanitizeLogEventRedactsAttributeValues() {
        io.github.atomoty.faultpilot.core.model.LogEvent in =
                new io.github.atomoty.faultpilot.core.model.LogEvent("p", "e", Instant.now(),
                        "ERROR", "logger", "t", "msg", null,
                        Map.of("apiKey", "k-123456", "exceptionClass", "NPE"));
        var out = sanitizer.sanitize(in);
        assertThat(out.attributes().get("apiKey")).isEqualTo("***");
        assertThat(out.attributes().get("exceptionClass")).isEqualTo("NPE");
    }
}
