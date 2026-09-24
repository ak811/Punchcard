package io.github.ak811.loyalty.scoring;

import io.github.ak811.loyalty.config.ScoringPolicy;
import io.github.ak811.loyalty.data.TransactionBatch;
import io.github.ak811.loyalty.index.HolderIndex;
import io.github.ak811.loyalty.model.Holder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.ak811.loyalty.model.TransactionType.BALANCE_INQUIRY;
import static io.github.ak811.loyalty.model.TransactionType.BILL_PAYMENT;
import static io.github.ak811.loyalty.model.TransactionType.CHARGE;
import static io.github.ak811.loyalty.model.TransactionType.PURCHASE;
import static io.github.ak811.loyalty.model.TransactionType.TOP_UP;
import static io.github.ak811.loyalty.model.TransactionType.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringEngineTest {

    private static final double EPS = 1e-9;

    private Holder alice;
    private Holder bob;
    private HolderIndex index;
    private ScoringEngine engine;

    @BeforeEach
    void setUp() {
        alice = new Holder(1L, 100);
        bob = new Holder(2L, 200);
        index = HolderIndex.build(new Holder[] {bob, alice});
        engine = new ScoringEngine(ScoringPolicy.DEFAULT);
    }

    @Test
    void topUpsAccumulateAcrossTransactions() {
        // Regression: the original code overwrote the top-up score with totalScore + result.
        TransactionBatch batch = new TransactionBatch(4);
        batch.add(100, 100, TOP_UP);
        batch.add(100, 200, TOP_UP);
        batch.add(100, 300, TOP_UP);

        engine.score(batch, index);

        assertEquals(6.0, alice.points(TOP_UP), EPS);
    }

    @Test
    void billPaymentsAreScoredInTheirOwnCategory() {
        // Regression: the original code added bill payments to the balance-inquiry score.
        TransactionBatch batch = new TransactionBatch(4);
        batch.add(100, 2_500_000, BILL_PAYMENT);

        engine.score(batch, index);

        assertEquals(1.0, alice.points(BILL_PAYMENT), EPS);
        assertEquals(0.0, alice.points(BALANCE_INQUIRY), EPS);
    }

    @Test
    void cappedCategoriesStopAtTheirLimits() {
        TransactionBatch batch = new TransactionBatch(16);
        for (int i = 0; i < 400; i++) {
            batch.add(100, 0, BALANCE_INQUIRY); // 400 * 0.01 = 4.0, capped at 3.0
        }
        batch.add(100, 50_000_000, PURCHASE);   // 1.0 points, at the cap
        batch.add(100, 50_000_000, PURCHASE);   // would exceed the cap
        batch.add(100, 5_000_000, BILL_PAYMENT);
        batch.add(100, 5_000_000, BILL_PAYMENT);

        engine.score(batch, index);

        assertEquals(3.0, alice.points(BALANCE_INQUIRY), EPS);
        assertEquals(1.0, alice.points(PURCHASE), EPS);
        assertEquals(2.0, alice.points(BILL_PAYMENT), EPS);
    }

    @Test
    void chargesAreUncapped() {
        TransactionBatch batch = new TransactionBatch(4);
        batch.add(200, 1_000_000, CHARGE);
        batch.add(200, 1_000_000, CHARGE);

        engine.score(batch, index);

        assertEquals(20_000.0, bob.points(CHARGE), EPS);
        assertEquals(20_000.0, bob.totalPoints(), EPS);
    }

    @Test
    void countsEveryTransactionInExactlyOneOutcome() {
        TransactionBatch batch = new TransactionBatch(8);
        batch.add(100, 1_000_000, PURCHASE);    // scored
        batch.add(200, 100, TOP_UP);            // scored
        batch.add(999, 1_000_000, PURCHASE);    // unmatched card
        batch.add(100, 1_000_000, UNKNOWN);     // unknown type
        batch.add(100, -5, CHARGE);             // negative amount

        ScoringStats stats = engine.score(batch, index);

        assertEquals(2, stats.scored());
        assertEquals(1, stats.unmatchedCard());
        assertEquals(1, stats.unknownType());
        assertEquals(1, stats.negativeAmount());
        assertEquals(batch.size(), stats.total());
        assertEquals(0.0, alice.points(CHARGE), EPS);
    }
}
