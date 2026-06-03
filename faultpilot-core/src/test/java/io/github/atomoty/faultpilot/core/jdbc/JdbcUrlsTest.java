package io.github.atomoty.faultpilot.core.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcUrlsTest {

    @Test
    void stripsQueryStringWithCredentials() {
        assertThat(JdbcUrls.safe("jdbc:mysql://h:3306/db?user=ro&password=secret"))
                .isEqualTo("jdbc:mysql://h:3306/db")
                .doesNotContain("secret");
    }

    @Test
    void keepsPlainUrl() {
        assertThat(JdbcUrls.safe("jdbc:postgresql://h:5432/db")).isEqualTo("jdbc:postgresql://h:5432/db");
    }

    @Test
    void handlesNull() {
        assertThat(JdbcUrls.safe(null)).isEqualTo("(none)");
    }
}
