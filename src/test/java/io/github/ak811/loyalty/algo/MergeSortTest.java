package io.github.ak811.loyalty.algo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSortTest {

    private record Item(int key, int originalPosition) {
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 31, 32, 33, 64, 65, 1000, 100_003})
    void sortsRandomInputLikeArraysSort(int size) {
        SplittableRandom random = new SplittableRandom(size);
        Integer[] actual = new Integer[size];
        for (int i = 0; i < size; i++) {
            actual[i] = random.nextInt(-1000, 1000);
        }
        Integer[] expected = actual.clone();
        Arrays.sort(expected);

        MergeSort.sort(actual, Comparator.naturalOrder());

        assertArrayEquals(expected, actual);
    }

    @Test
    void sortsAlreadySortedReverseSortedAndConstantInput() {
        Integer[] sorted = new Integer[500];
        Integer[] reversed = new Integer[500];
        Integer[] constant = new Integer[500];
        for (int i = 0; i < 500; i++) {
            sorted[i] = i;
            reversed[i] = 499 - i;
            constant[i] = 7;
        }
        Integer[] expected = sorted.clone();

        MergeSort.sort(sorted, Comparator.naturalOrder());
        MergeSort.sort(reversed, Comparator.naturalOrder());
        MergeSort.sort(constant, Comparator.naturalOrder());

        assertArrayEquals(expected, sorted);
        assertArrayEquals(expected, reversed);
        assertEquals(500, Arrays.stream(constant).filter(v -> v == 7).count());
    }

    @Test
    void isStable() {
        SplittableRandom random = new SplittableRandom(1);
        Item[] items = new Item[10_000];
        for (int i = 0; i < items.length; i++) {
            items[i] = new Item(random.nextInt(50), i); // many duplicate keys
        }

        MergeSort.sort(items, Comparator.comparingInt(Item::key));

        for (int i = 1; i < items.length; i++) {
            Item previous = items[i - 1];
            Item current = items[i];
            if (previous.key() == current.key()) {
                assertTrue(previous.originalPosition() < current.originalPosition(),
                        "equal keys must keep input order at index " + i);
            } else {
                assertTrue(previous.key() < current.key(), "keys out of order at index " + i);
            }
        }
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> MergeSort.sort(null, Comparator.<Integer>naturalOrder()));
        assertThrows(NullPointerException.class, () -> MergeSort.sort(new Integer[] {1}, null));
    }
}
