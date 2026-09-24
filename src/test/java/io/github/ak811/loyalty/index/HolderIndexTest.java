package io.github.ak811.loyalty.index;

import io.github.ak811.loyalty.model.Holder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HolderIndexTest {

    @Test
    void findsHoldersByCardCodeRegardlessOfInputOrder() {
        Holder a = new Holder(1L, 300);
        Holder b = new Holder(2L, 100);
        Holder c = new Holder(3L, 200);
        Holder[] input = {a, b, c};

        HolderIndex index = HolderIndex.build(input);

        assertSame(a, index.find(300));
        assertSame(b, index.find(100));
        assertSame(c, index.find(200));
        assertNull(index.find(150));
        assertEquals(3, index.size());
        assertSame(a, input[0], "input array must not be modified");
    }

    @Test
    void keepsFirstHolderForDuplicateCardCodes() {
        Holder first = new Holder(1L, 500);
        Holder duplicate = new Holder(2L, 500);
        Holder other = new Holder(3L, 400);

        HolderIndex index = HolderIndex.build(new Holder[] {first, other, duplicate});

        assertSame(first, index.find(500));
        assertEquals(2, index.size());
        assertEquals(1, index.duplicatesDropped());
    }

    @Test
    void handlesEmptyInput() {
        HolderIndex index = HolderIndex.build(new Holder[0]);
        assertEquals(0, index.size());
        assertNull(index.find(1));
    }

    @Test
    void holdersAreReturnedSortedByCardCode() {
        HolderIndex index = HolderIndex.build(new Holder[] {
                new Holder(1L, 9), new Holder(2L, -3), new Holder(3L, 4)});
        Holder[] holders = index.holders();
        assertEquals(-3, holders[0].cardCode());
        assertEquals(4, holders[1].cardCode());
        assertEquals(9, holders[2].cardCode());
    }
}
