package io.github.atomoty.faultpilot.core.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceUnavailableExceptionTest {

    @Test
    void summarizesDirectCauseAsClassNameAndMessage() {
        var ex = new DataSourceUnavailableException(
                "Database unavailable: jdbc:mysql://host/db",
                new SQLException("Access denied for user 'app'"));

        assertThat(ex.rootCauseSummary())
                .isEqualTo("SQLException: Access denied for user 'app'");
    }

    @Test
    void walksToDeepestCause() {
        var root = new java.net.ConnectException("Connection refused");
        var ex = new DataSourceUnavailableException(
                "Database unavailable: jdbc:mysql://host/db",
                new SQLException("Communications link failure", root));

        assertThat(ex.rootCauseSummary())
                .isEqualTo("ConnectException: Connection refused");
    }

    @Test
    void returnsEmptyStringWhenNoCause() {
        var ex = new DataSourceUnavailableException("down", null);

        assertThat(ex.rootCauseSummary()).isEmpty();
    }
}
