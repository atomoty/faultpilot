package io.github.atomoty.faultpilot.core.rule;

/**
 * Spike-detection thresholds. See specification.md §7.5.
 *
 * @param absoluteThreshold minimum count in the current window to even consider a spike
 * @param ratioThreshold    current must be >= baseline * ratio
 */
public record SpikeThresholds(long absoluteThreshold, double ratioThreshold) {

    public static SpikeThresholds defaults() {
        return new SpikeThresholds(5, 3.0);
    }

    /**
     * Decide whether a cluster is a spike.
     *
     * @param hasBaseline when false (insufficient baseline), only the absolute threshold applies
     *                    and the caller should flag the report (specification.md §7.5).
     */
    public boolean isSpike(long current, long baseline, boolean hasBaseline) {
        if (current < absoluteThreshold) {
            return false;
        }
        if (!hasBaseline) {
            return true;
        }
        return current >= baseline * ratioThreshold;
    }
}
