package io.github.ak811.loyalty.algo;

import java.util.Comparator;
import java.util.Objects;

/**
 * Stable top-down merge sort.
 *
 * <p>A single auxiliary array is allocated for the whole sort, and the roles of the
 * two arrays alternate at each recursion level, so no copying is needed between
 * merges. Small subarrays are sorted with insertion sort, and the merge step is
 * skipped when the two halves are already in order.
 *
 * <p>Time: O(n log n) worst case. Extra space: O(n).
 */
public final class MergeSort {

    /** Subarrays at or below this length are sorted with insertion sort. */
    static final int INSERTION_SORT_THRESHOLD = 32;

    private MergeSort() {
    }

    /** Sorts {@code array} in place. Equal elements keep their relative order. */
    public static <T> void sort(T[] array, Comparator<? super T> comparator) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(comparator, "comparator");
        if (array.length < 2) {
            return;
        }
        T[] aux = array.clone();
        sort(aux, array, 0, array.length, comparator);
    }

    /**
     * Sorts the elements of {@code src[lo, hi)} into {@code dst[lo, hi)}.
     * On entry, both arrays must hold the same elements in that range.
     */
    private static <T> void sort(T[] src, T[] dst, int lo, int hi, Comparator<? super T> comparator) {
        if (hi - lo <= INSERTION_SORT_THRESHOLD) {
            insertionSort(dst, lo, hi, comparator);
            return;
        }
        int mid = (lo + hi) >>> 1;
        // Swap roles: sort each half into src, then merge src back into dst.
        sort(dst, src, lo, mid, comparator);
        sort(dst, src, mid, hi, comparator);

        if (comparator.compare(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, lo, dst, lo, hi - lo);
            return;
        }
        merge(src, dst, lo, mid, hi, comparator);
    }

    private static <T> void merge(T[] src, T[] dst, int lo, int mid, int hi, Comparator<? super T> comparator) {
        int left = lo;
        int right = mid;
        for (int k = lo; k < hi; k++) {
            // Taking from the left run on ties ("<= 0") keeps the sort stable.
            if (right >= hi || (left < mid && comparator.compare(src[left], src[right]) <= 0)) {
                dst[k] = src[left++];
            } else {
                dst[k] = src[right++];
            }
        }
    }

    private static <T> void insertionSort(T[] array, int lo, int hi, Comparator<? super T> comparator) {
        for (int i = lo + 1; i < hi; i++) {
            T value = array[i];
            int j = i - 1;
            while (j >= lo && comparator.compare(array[j], value) > 0) {
                array[j + 1] = array[j];
                j--;
            }
            array[j + 1] = value;
        }
    }
}
