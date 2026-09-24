package io.github.ak811.loyalty.index;

import io.github.ak811.loyalty.algo.BinarySearch;
import io.github.ak811.loyalty.algo.MergeSort;
import io.github.ak811.loyalty.model.Holder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Lookup of cardholders by card code.
 *
 * <p>Holders are sorted by card code with {@link MergeSort}, and their codes are
 * copied into a contiguous {@code int[]}. Each lookup is a binary search over that
 * primitive array, which touches far less memory than probing {@code Holder}
 * objects directly.
 *
 * <p>Card codes are expected to be unique. If the input contains duplicates, the
 * first holder in input order is kept (the sort is stable), and the others are
 * counted in {@link #duplicatesDropped()}.
 */
public final class HolderIndex {

    private static final Comparator<Holder> BY_CARD_CODE = Comparator.comparingInt(Holder::cardCode);

    private final Holder[] holders;
    private final int[] cardCodes;
    private final int duplicatesDropped;

    private HolderIndex(Holder[] holders, int[] cardCodes, int duplicatesDropped) {
        this.holders = holders;
        this.cardCodes = cardCodes;
        this.duplicatesDropped = duplicatesDropped;
    }

    /** Builds an index over {@code holders}. The input array is not modified. */
    public static HolderIndex build(Holder[] holders) {
        Objects.requireNonNull(holders, "holders");
        Holder[] sorted = holders.clone();
        MergeSort.sort(sorted, BY_CARD_CODE);

        int unique = 0;
        for (Holder holder : sorted) {
            if (unique == 0 || sorted[unique - 1].cardCode() != holder.cardCode()) {
                sorted[unique++] = holder;
            }
        }
        Holder[] uniqueHolders = Arrays.copyOf(sorted, unique);
        int[] codes = new int[unique];
        for (int i = 0; i < unique; i++) {
            codes[i] = uniqueHolders[i].cardCode();
        }
        return new HolderIndex(uniqueHolders, codes, holders.length - unique);
    }

    /** Returns the holder with {@code cardCode}, or {@code null} if there is none. */
    public Holder find(int cardCode) {
        int index = BinarySearch.indexOf(cardCodes, cardCode);
        return index < 0 ? null : holders[index];
    }

    /** Returns the indexed holders, sorted by card code. The returned array is a copy. */
    public Holder[] holders() {
        return holders.clone();
    }

    public int size() {
        return holders.length;
    }

    public int duplicatesDropped() {
        return duplicatesDropped;
    }
}
