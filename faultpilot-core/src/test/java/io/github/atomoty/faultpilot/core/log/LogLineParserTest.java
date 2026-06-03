package io.github.atomoty.faultpilot.core.log;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogLineParserTest {

    private final LogLineParser parser = new LogLineParser();

    @Test
    void parsesStandardLogbackHead() {
        Optional<LogLineParser.Head> head = parser.parseHead(
                "2026-06-01 10:05:31.501 ERROR [http-nio-8080-exec-5] com.example.order.OrderService - Create order failed");

        assertThat(head).isPresent();
        LogLineParser.Head h = head.get();
        assertThat(h.timestamp()).isEqualTo("2026-06-01 10:05:31.501");
        assertThat(h.level()).isEqualTo("ERROR");
        assertThat(h.thread()).isEqualTo("http-nio-8080-exec-5");
        assertThat(h.logger()).isEqualTo("com.example.order.OrderService");
        assertThat(h.message()).isEqualTo("Create order failed");
    }

    @Test
    void doesNotMatchStackTraceFrame() {
        assertThat(parser.parseHead("\tat com.example.order.OrderService.create(OrderService.java:88)"))
                .isEmpty();
        assertThat(parser.parseHead("java.lang.NullPointerException: boom")).isEmpty();
    }

    @Test
    void parsesLineWithoutThread() {
        Optional<LogLineParser.Head> head = parser.parseHead(
                "2026-06-01 10:05:31.501 WARN com.example.Foo - heads up");
        assertThat(head).isPresent();
        assertThat(head.get().thread()).isNull();
        assertThat(head.get().level()).isEqualTo("WARN");
    }

    @Test
    void parsesSpringBootDefaultConsoleLayout() {
        Optional<LogLineParser.Head> head = parser.parseHead(
                "2026-06-01T10:05:31.501+08:00 ERROR 12345 --- [http-nio-8080-exec-5] com.example.order.OrderService : Create order failed");

        assertThat(head).isPresent();
        assertThat(head.get().timestamp()).isEqualTo("2026-06-01T10:05:31.501+08:00");
        assertThat(head.get().level()).isEqualTo("ERROR");
        assertThat(head.get().thread()).isEqualTo("http-nio-8080-exec-5");
        assertThat(head.get().logger()).isEqualTo("com.example.order.OrderService");
        assertThat(head.get().message()).isEqualTo("Create order failed");
    }

    @Test
    void stripAnsiRemovesColorCodes() {
        String colored = "2026-06-03 09:55:55.313 [pool-1] [] [1;31mERROR[0;39m "
                + "[1;35mcom.example.Foo[0;39m - boom";
        String plain = LogLineParser.stripAnsi(colored);
        assertThat(plain).doesNotContain("[");
        assertThat(plain).isEqualTo("2026-06-03 09:55:55.313 [pool-1] [] ERROR com.example.Foo - boom");
    }

    @Test
    void customThreadBeforeLevelPatternWithMdcSlot() {
        // Matches the cdh-mall layout: ts [thread] [mdc] LEVEL logger - msg (after ANSI strip).
        LogLineParser p = new LogLineParser(
                "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s+"
                        + "\\[(?<thread>[^\\]]+)\\]\\s+(?:\\[[^\\]]*\\]\\s+)?"
                        + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR)\\s+"
                        + "(?<logger>[\\w$.]+)\\s*-\\s*(?<msg>.*)$");
        String line = LogLineParser.stripAnsi(
                "2026-06-03 09:55:55.313 [pool-1475-thread-1] [] [1;31mERROR[0;39m "
                        + "[1;35mc.c.h.cache.BaseGuavaCache[0;39m - cache miss");
        var head = p.parseHead(line);
        assertThat(head).isPresent();
        assertThat(head.get().level()).isEqualTo("ERROR");
        assertThat(head.get().thread()).isEqualTo("pool-1475-thread-1");
        assertThat(head.get().logger()).isEqualTo("c.c.h.cache.BaseGuavaCache");
        assertThat(head.get().message()).isEqualTo("cache miss");
    }

    @Test
    void supportsCustomPattern() {
        // Layout: "LEVEL|timestamp|logger|message"
        LogLineParser custom = new LogLineParser(
                "^(?<level>\\w+)\\|(?<ts>[^|]+)\\|(?<logger>[^|]+)\\|(?<msg>.*)$");
        Optional<LogLineParser.Head> head = custom.parseHead(
                "ERROR|2026-06-01 10:05:31.501|com.example.Foo|kaboom");
        assertThat(head).isPresent();
        assertThat(head.get().level()).isEqualTo("ERROR");
        assertThat(head.get().logger()).isEqualTo("com.example.Foo");
        assertThat(head.get().message()).isEqualTo("kaboom");
        assertThat(head.get().thread()).isNull();
    }
}
