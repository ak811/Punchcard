package io.github.ak811.loyalty.data;

import io.github.ak811.loyalty.model.TransactionType;

import java.util.Arrays;

/**
 * Transactions stored as parallel primitive arrays (structure of arrays).
 *
 * <p>Holding card codes, amounts, and type codes in three arrays uses about
 * 13 bytes per transaction, compared with tens of bytes for one object per row,
 * and lets the scoring loop stream through contiguous memory.
 */
public final class TransactionBatch {

    private int[] cardCodes;
    private long[] amounts;
    private byte[] typeCodes;
    private int size;
    private long rowsWithoutCardCode;

    public TransactionBatch(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be non-negative: " + initialCapacity);
        }
        cardCodes = new int[initialCapacity];
        amounts = new long[initialCapacity];
        typeCodes = new byte[initialCapacity];
    }

    public void add(int cardCode, long amount, TransactionType type) {
        if (size == cardCodes.length) {
            grow();
        }
        cardCodes[size] = cardCode;
        amounts[size] = amount;
        typeCodes[size] = (byte) type.code();
        size++;
    }

    /** Records a source row that was skipped because its card code was NULL. */
    public void recordRowWithoutCardCode() {
        rowsWithoutCardCode++;
    }

    public int size() {
        return size;
    }

    public int cardCode(int index) {
        checkIndex(index);
        return cardCodes[index];
    }

    public long amount(int index) {
        checkIndex(index);
        return amounts[index];
    }

    public TransactionType type(int index) {
        checkIndex(index);
        return TransactionType.fromCode(typeCodes[index]);
    }

    public long rowsWithoutCardCode() {
        return rowsWithoutCardCode;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for size " + size);
        }
    }

    private void grow() {
        int capacity = cardCodes.length;
        int newCapacity = Math.max(16, capacity + (capacity >> 1));
        if (newCapacity < 0) {
            throw new IllegalStateException("TransactionBatch capacity exceeded");
        }
        cardCodes = Arrays.copyOf(cardCodes, newCapacity);
        amounts = Arrays.copyOf(amounts, newCapacity);
        typeCodes = Arrays.copyOf(typeCodes, newCapacity);
    }
}
