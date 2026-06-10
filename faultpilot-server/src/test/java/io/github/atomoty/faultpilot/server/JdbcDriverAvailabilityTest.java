package io.github.atomoty.faultpilot.server;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the runtime JDBC drivers the server ships for per-project database evidence sources.
 *
 * <p>Background: the MySQL/PostgreSQL evidence sources build a {@code jdbc:mysql:} / {@code jdbc:postgresql:}
 * URL and open it through {@link DriverManager}. Every other database test uses an in-memory H2 URL, so a
 * missing real driver dependency produces a green build yet fails in production with
 * {@code No suitable driver found}. This test exercises driver resolution for those URLs directly, so
 * removing the {@code mysql-connector-j} or {@code postgresql} dependency from the server POM turns the
 * build red immediately. It resolves the driver only — it does not open a connection.
 */
class JdbcDriverAvailabilityTest {

    @Test
    void mysqlDriverIsOnTheRuntimeClasspath() {
        assertDriverResolves("jdbc:mysql://localhost:3306/db");
    }

    @Test
    void postgresDriverIsOnTheRuntimeClasspath() {
        assertDriverResolves("jdbc:postgresql://localhost:5432/db");
    }

    /** {@link DriverManager#getDriver} throws {@code SQLException} when no driver matches the URL. */
    private static void assertDriverResolves(String url) {
        assertThatCode(() -> DriverManager.getDriver(url))
                .as("a JDBC driver must be registered for %s", url)
                .doesNotThrowAnyException();
    }
}
