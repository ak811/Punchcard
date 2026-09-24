package io.github.ak811.loyalty.lottery;

import io.github.ak811.loyalty.algo.MergeSort;
import io.github.ak811.loyalty.model.Holder;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Ranks cardholders by points and draws a winner from the top percentage.
 *
 * <p>Ranking uses {@link MergeSort} with total points descending and card code
 * ascending as a tie-breaker, so the order is fully deterministic. The lottery pool
 * is the top {@code ceil(n * percent / 100)} cardholders, excluding anyone with zero
 * points. Every pool member has an equal chance of winning.
 */
public final class Lottery {

    /** Total points descending, then card code ascending. */
    public static final Comparator<Holder> BY_POINTS_DESCENDING =
            Comparator.comparingDouble(Holder::totalPoints).reversed()
                    .thenComparingInt(Holder::cardCode);

    private Lottery() {
    }

    /** Returns a copy of {@code holders} ranked by {@link #BY_POINTS_DESCENDING}. */
    public static Holder[] rank(Holder[] holders) {
        Objects.requireNonNull(holders, "holders");
        Holder[] ranked = holders.clone();
        MergeSort.sort(ranked, BY_POINTS_DESCENDING);
        return ranked;
    }

    /**
     * Returns the number of holders in the top {@code percent} of {@code population}, rounded up.
     *
     * @param percent integer percentage in [1, 100]
     */
    public static int topPercentSize(int population, int percent) {
        if (population < 0) {
            throw new IllegalArgumentException("population must be non-negative: " + population);
        }
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("percent must be in [1, 100]: " + percent);
        }
        return (int) (((long) population * percent + 99) / 100);
    }

    /**
     * Returns the size of the lottery pool: the top {@code percent} of {@code ranked},
     * truncated at the first holder with zero points.
     *
     * @param ranked holders ranked by {@link #BY_POINTS_DESCENDING}
     */
    public static int poolSize(Holder[] ranked, int percent) {
        Objects.requireNonNull(ranked, "ranked");
        int limit = topPercentSize(ranked.length, percent);
        int size = 0;
        while (size < limit && ranked[size].totalPoints() > 0.0) {
            size++;
        }
        return size;
    }

    /**
     * Draws one winner uniformly from {@code ranked[0, poolSize)}.
     *
     * @return the winner's index in {@code ranked}, or empty if the pool is empty
     */
    public static Optional<Integer> draw(Holder[] ranked, int poolSize, RandomGenerator random) {
        Objects.requireNonNull(ranked, "ranked");
        Objects.requireNonNull(random, "random");
        if (poolSize < 0 || poolSize > ranked.length) {
            throw new IllegalArgumentException("poolSize out of range: " + poolSize);
        }
        return poolSize == 0 ? Optional.empty() : Optional.of(random.nextInt(poolSize));
    }
}
