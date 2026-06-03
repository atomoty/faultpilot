package io.github.atomoty.faultpilot.core.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSqlExecutorTest {

    @Test
    void runsReadOnlyQueryAndMapsRows() throws SQLException {
        String url = "jdbc:h2:mem:exec_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        try (Connection keepAlive = DriverManager.getConnection(url, "sa", "");
             Statement st = keepAlive.createStatement()) {
            st.execute("CREATE TABLE t(id INT, name VARCHAR(20))");
            st.execute("INSERT INTO t VALUES (1,'a'),(2,'b')");

            try (JdbcSqlExecutor exec = JdbcSqlExecutor.open(url, "sa", "", 2000, 3000)) {
                List<String> names = exec.query("SELECT name FROM t ORDER BY id",
                        rs -> rs.getString("name"));
                assertThat(names).containsExactly("a", "b");
            }
        }
    }

    @Test
    void connectionFailureThrowsDataSourceUnavailable() {
        assertThatThrownBy(() ->
                JdbcSqlExecutor.open("jdbc:h2:mem:nope;IFEXISTS=TRUE", "sa", "", 1000, 1000))
                .isInstanceOf(DataSourceUnavailableException.class);
    }

    @Test
    void queryLevelErrorSurfacesAsSqlException() throws SQLException {
        String url = "jdbc:h2:mem:exec2_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        try (Connection keepAlive = DriverManager.getConnection(url, "sa", "");
             JdbcSqlExecutor exec = JdbcSqlExecutor.open(url, "sa", "", 2000, 3000)) {
            // Missing table => SQLException (caller may skip this sub-query), NOT unavailability.
            assertThatThrownBy(() -> exec.query("SELECT * FROM no_such_table", rs -> rs.getString(1)))
                    .isInstanceOf(SQLException.class);
        }
    }
}
