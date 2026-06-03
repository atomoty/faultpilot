package io.github.atomoty.faultpilot.core.jdbc.db;

import io.github.atomoty.faultpilot.core.jdbc.SqlExecutor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test {@link SqlExecutor} backed by H2: lets a test register canned result sets keyed by a SQL
 * substring, so dialect sources can be exercised (real column names/aliases, real ResultSet mapping)
 * without a real MySQL/PostgreSQL. A registered SQL whose value is {@code null} simulates a
 * query-level failure (e.g. missing system view) by throwing {@link SQLException}.
 */
final class FakeSqlExecutor implements SqlExecutor {

    private final Connection conn;
    private final List<Stub> stubs = new ArrayList<>();
    private int seq;

    FakeSqlExecutor() {
        try {
            conn = DriverManager.getConnection(
                    "jdbc:h2:mem:fake_" + UUID.randomUUID().toString().replace("-", ""), "sa", "");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private record Stub(String sqlContains, String tableOrNull) {
    }

    /** Register canned rows for any query containing {@code sqlContains}. Columns become a temp table. */
    FakeSqlExecutor stub(String sqlContains, List<Map<String, Object>> rows) {
        String table = "fake_t_" + (seq++);
        try (Statement st = conn.createStatement()) {
            // Quote identifiers so reserved words (e.g. MySQL's "Value" column) are valid in H2.
            String cols = String.join(", ", rows.isEmpty() ? List.of("dummy INT")
                    : rows.get(0).keySet().stream().map(k -> "\"" + k + "\" VARCHAR(4000)").toList());
            st.execute("CREATE TABLE " + table + " (" + cols + ")");
            for (Map<String, Object> row : rows) {
                String names = String.join(", ", row.keySet().stream().map(k -> "\"" + k + "\"").toList());
                String qs = String.join(", ", row.keySet().stream().map(k -> "?").toList());
                try (var ps = conn.prepareStatement("INSERT INTO " + table + "(" + names + ") VALUES (" + qs + ")")) {
                    int i = 1;
                    for (Object v : row.values()) {
                        ps.setString(i++, v == null ? null : String.valueOf(v));
                    }
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        stubs.add(new Stub(sqlContains, table));
        return this;
    }

    /** Register a query-level failure for any query containing {@code sqlContains}. */
    FakeSqlExecutor stubFailure(String sqlContains) {
        stubs.add(new Stub(sqlContains, null));
        return this;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper) throws SQLException {
        return query(sql, 0, mapper);
    }

    @Override
    public <T> List<T> query(String sql, int maxRows, RowMapper<T> mapper) throws SQLException {
        Stub stub = stubs.stream().filter(s -> sql.contains(s.sqlContains())).findFirst()
                .orElseThrow(() -> new AssertionError("No stub registered for SQL: " + sql));
        if (stub.tableOrNull() == null) {
            throw new SQLException("simulated query failure for: " + stub.sqlContains());
        }
        String select = "SELECT * FROM " + stub.tableOrNull() + (maxRows > 0 ? " LIMIT " + maxRows : "");
        List<T> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                out.add(mapper.map(rs));
            }
        }
        return out;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // best-effort
        }
    }
}
