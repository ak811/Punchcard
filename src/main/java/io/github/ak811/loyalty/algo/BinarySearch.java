package io.github.ak811.loyalty.algo;

import java.util.Objects;

/** Binary search over sorted {@code int} arrays. Time: O(log n). */
public final class BinarySearch {

    private BinarySearch() {
    }

    /**
     * Returns the first index {@code i} with {@code sorted[i] >= key},
     * or {@code sorted.length} if every element is smaller than {@code key}.
     *
     * @param sorted array sorted in ascending order
     */
    public static int lowerBound(int[] sorted, int key) {
        Objects.requireNonNull(sorted, "sorted");
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Returns the index of the first occurrence of {@code key}, or {@code -1} if it is absent.
     *
     * @param sorted array sorted in ascending order
     */
    public static int indexOf(int[] sorted, int key) {
        int index = lowerBound(sorted, key);
        return (index < sorted.length && sorted[index] == key) ? index : -1;
    }
}
