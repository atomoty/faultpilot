package io.github.atomoty.faultpilot.adapters.mock;

import io.github.atomoty.faultpilot.core.model.EvidenceQuery;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mock metric/slow-SQL adapters honour the query time range (review #3): a window that
 * does not cover the fixture's timestamps must return nothing.
 */
class MockAdapterTimeFilterTest {

    private final MockMetricSourceAdapter metricAdapter = new MockMetricSourceAdapter();
    private final MockSlowSqlSourceAdapter slowSqlAdapter = new MockSlowSqlSourceAdapter();

    private EvidenceQuery rangeAround(java.time.Instant end) {
        return new EvidenceQuery("order-service", "local",
                end.minus(Duration.ofHours(2)), end, 500);
    }

    @Test
    void returnsEvidenceWhenRangeCoversFixtures() {
        EvidenceQuery covering = rangeAround(DemoFixtures.ANCHOR);
        assertThat(metricAdapter.query(covering)).isNotEmpty();
        assertThat(slowSqlAdapter.query(covering)).isNotEmpty();
    }

    @Test
    void returnsNothingWhenRangeIsBeforeFixtures() {
        // A month earlier — fixtures (anchored to ANCHOR) fall outside this window.
        EvidenceQuery past = rangeAround(DemoFixtures.ANCHOR.minus(Duration.ofDays(30)));
        assertThat(metricAdapter.query(past)).isEmpty();
        assertThat(slowSqlAdapter.query(past)).isEmpty();
    }
}
