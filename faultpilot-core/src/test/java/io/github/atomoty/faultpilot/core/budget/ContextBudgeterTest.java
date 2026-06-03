package io.github.atomoty.faultpilot.core.budget;

import io.github.atomoty.faultpilot.core.model.LogCluster;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBudgeterTest {

    @Test
    void capsClustersAndRecordsTruncation() {
        ContextBudgeter budgeter = new ContextBudgeter(new ContextBudget(3, 15, 20, 20));

        List<LogCluster> clusters = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // increasing counts; non-spike
            clusters.add(new LogCluster("log-" + i, "k" + i, "E", "m", "f", "ERROR",
                    i, 0, false, Instant.now(), Instant.now(), null));
        }

        ContextBudgeter.Budgeted result = budgeter.apply(clusters, List.of(), List.of(), List.of());

        assertThat(result.logClusters()).hasSize(3);
        assertThat(result.report().logClustersKept()).isEqualTo(3);
        assertThat(result.report().logClustersTruncated()).isEqualTo(7);
        assertThat(result.report().anyTruncated()).isTrue();
        // highest counts retained
        assertThat(result.logClusters().get(0).count()).isEqualTo(9);
    }

    @Test
    void spikesRetainedBeforeNonSpikes() {
        ContextBudgeter budgeter = new ContextBudgeter(new ContextBudget(1, 15, 20, 20));

        LogCluster bigNonSpike = new LogCluster("log-1", "k1", "E", "m", "f", "ERROR",
                100, 0, false, Instant.now(), Instant.now(), null);
        LogCluster smallSpike = new LogCluster("log-2", "k2", "E", "m", "f", "ERROR",
                6, 1, true, Instant.now(), Instant.now(), null);

        ContextBudgeter.Budgeted result =
                budgeter.apply(List.of(bigNonSpike, smallSpike), List.of(), List.of(), List.of());

        assertThat(result.logClusters()).hasSize(1);
        assertThat(result.logClusters().get(0).evidenceId()).isEqualTo("log-2");
    }
}
