package io.github.ak811.loyalty.lottery;

import io.github.ak811.loyalty.model.Holder;
import io.github.ak811.loyalty.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotteryTest {

    private static Holder holder(int cardCode, double points) {
        Holder holder = new Holder(cardCode * 10L, cardCode);
        if (points > 0) {
            holder.addPoints(TransactionType.TOP_UP, points, Double.POSITIVE_INFINITY);
        }
        return holder;
    }

    @Test
    void ranksByPointsNotByCardCode() {
        // Regression: the original code ranked by card code, so the highest card codes won.
        Holder[] holders = {holder(1, 50), holder(2, 10), holder(3, 99), holder(4, 0)};

        Holder[] ranked = Lottery.rank(holders);

        assertEquals(3, ranked[0].cardCode());
        assertEquals(1, ranked[1].cardCode());
        assertEquals(2, ranked[2].cardCode());
        assertEquals(4, ranked[3].cardCode());
    }

    @Test
    void breaksTiesByCardCode() {
        Holder[] ranked = Lottery.rank(new Holder[] {holder(9, 5), holder(2, 5), holder(5, 5)});
        assertEquals(2, ranked[0].cardCode());
        assertEquals(5, ranked[1].cardCode());
        assertEquals(9, ranked[2].cardCode());
    }

    @Test
    void topPercentSizeRoundsUp() {
        assertEquals(0, Lottery.topPercentSize(0, 10));
        assertEquals(1, Lottery.topPercentSize(1, 10));
        assertEquals(1, Lottery.topPercentSize(9, 10));
        assertEquals(1, Lottery.topPercentSize(10, 10));
        assertEquals(2, Lottery.topPercentSize(11, 10));
        assertEquals(570_000, Lottery.topPercentSize(5_700_000, 10));
        assertEquals(5_700_000, Lottery.topPercentSize(5_700_000, 100));
        assertThrows(IllegalArgumentException.class, () -> Lottery.topPercentSize(10, 0));
        assertThrows(IllegalArgumentException.class, () -> Lottery.topPercentSize(10, 101));
    }

    @Test
    void poolExcludesHoldersWithoutPoints() {
        Holder[] ranked = Lottery.rank(new Holder[] {
                holder(1, 7), holder(2, 0), holder(3, 0), holder(4, 0), holder(5, 0)});

        assertEquals(1, Lottery.poolSize(ranked, 100));
        assertEquals(1, Lottery.poolSize(ranked, 10));
    }

    @Test
    void smallPopulationsStillProduceAWinner() {
        // Regression: the original code built an empty pool for fewer than 10 holders
        // and then crashed in Random.nextInt(0).
        Holder[] ranked = Lottery.rank(new Holder[] {holder(1, 3), holder(2, 4)});

        int poolSize = Lottery.poolSize(ranked, 10);
        Optional<Integer> winner = Lottery.draw(ranked, poolSize, new SplittableRandom(1));

        assertEquals(1, poolSize);
        assertEquals(Optional.of(0), winner);
    }

    @Test
    void emptyPoolHasNoWinner() {
        assertEquals(Optional.empty(), Lottery.draw(new Holder[0], 0, new SplittableRandom(1)));
        Holder[] noPoints = {holder(1, 0)};
        assertEquals(0, Lottery.poolSize(noPoints, 100));
    }

    @Test
    void drawIsUniformOverThePoolAndReproducibleWithASeed() {
        Holder[] ranked = new Holder[100];
        for (int i = 0; i < ranked.length; i++) {
            ranked[i] = holder(i + 1, 1000 - i);
        }
        ranked = Lottery.rank(ranked);
        int poolSize = Lottery.poolSize(ranked, 10);
        assertEquals(10, poolSize);

        int[] counts = new int[poolSize];
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < 100_000; i++) {
            int index = Lottery.draw(ranked, poolSize, random).orElseThrow();
            assertTrue(index >= 0 && index < poolSize);
            counts[index]++;
        }
        for (int count : counts) {
            assertTrue(count > 9_000 && count < 11_000, "expected about 10,000 wins, got " + count);
        }

        assertEquals(Lottery.draw(ranked, poolSize, new SplittableRandom(123)),
                Lottery.draw(ranked, poolSize, new SplittableRandom(123)));
    }
}
