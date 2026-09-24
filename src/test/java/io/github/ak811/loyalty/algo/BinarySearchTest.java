package io.github.ak811.loyalty.algo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinarySearchTest {

    @Test
    void findsEveryPresentKeyAndRejectsAbsentKeys() {
        int[] sorted = {-5, 0, 3, 8, 13, 21, 34};
        for (int i = 0; i < sorted.length; i++) {
            assertEquals(i, BinarySearch.indexOf(sorted, sorted[i]));
        }
        assertEquals(-1, BinarySearch.indexOf(sorted, -6));
        assertEquals(-1, BinarySearch.indexOf(sorted, 4));
        assertEquals(-1, BinarySearch.indexOf(sorted, 35));
    }

    @Test
    void handlesEmptyAndSingleElementArrays() {
        assertEquals(-1, BinarySearch.indexOf(new int[0], 1));
        assertEquals(0, BinarySearch.lowerBound(new int[0], 1));
        assertEquals(0, BinarySearch.indexOf(new int[] {9}, 9));
        assertEquals(-1, BinarySearch.indexOf(new int[] {9}, 8));
    }

    @Test
    void returnsFirstOccurrenceOfDuplicates() {
        int[] sorted = {1, 2, 2, 2, 3};
        assertEquals(1, BinarySearch.indexOf(sorted, 2));
        assertEquals(4, BinarySearch.lowerBound(sorted, 3));
        assertEquals(5, BinarySearch.lowerBound(sorted, 4));
    }

    @Test
    void handlesExtremeIntValues() {
        int[] sorted = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
        assertEquals(0, BinarySearch.indexOf(sorted, Integer.MIN_VALUE));
        assertEquals(2, BinarySearch.indexOf(sorted, Integer.MAX_VALUE));
    }

    @Test
    void agreesWithArraysBinarySearchOnRandomInput() {
        SplittableRandom random = new SplittableRandom(7);
        int[] values = random.ints(50_000, 0, 1_000_000).distinct().sorted().toArray();
        for (int trial = 0; trial < 100_000; trial++) {
            int key = random.nextInt(0, 1_000_000);
            int expected = Arrays.binarySearch(values, key);
            assertEquals(expected >= 0 ? expected : -1, BinarySearch.indexOf(values, key));
        }
    }
}
