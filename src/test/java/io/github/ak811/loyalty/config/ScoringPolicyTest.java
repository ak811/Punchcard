package io.github.ak811.loyalty.config;

import org.junit.jupiter.api.Test;

import static io.github.ak811.loyalty.model.TransactionType.BALANCE_INQUIRY;
import static io.github.ak811.loyalty.model.TransactionType.BILL_PAYMENT;
import static io.github.ak811.loyalty.model.TransactionType.CHARGE;
import static io.github.ak811.loyalty.model.TransactionType.PURCHASE;
import static io.github.ak811.loyalty.model.TransactionType.TOP_UP;
import static io.github.ak811.loyalty.model.TransactionType.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringPolicyTest {

    private static final double EPS = 1e-9;
    private final ScoringPolicy policy = ScoringPolicy.DEFAULT;

    @Test
    void purchaseEarnsPointsPerFullUnit() {
        assertEquals(0.0, policy.pointsFor(PURCHASE, 499_999), EPS);
        assertEquals(0.01, policy.pointsFor(PURCHASE, 500_000), EPS);
        assertEquals(0.01, policy.pointsFor(PURCHASE, 999_999), EPS);
        assertEquals(0.03, policy.pointsFor(PURCHASE, 1_500_000), EPS);
    }

    @Test
    void balanceInquiryEarnsFlatPoints() {
        assertEquals(0.01, policy.pointsFor(BALANCE_INQUIRY, 0), EPS);
        assertEquals(0.01, policy.pointsFor(BALANCE_INQUIRY, 9_999_999), EPS);
    }

    @Test
    void topUpAndChargeUseTheirOwnCoefficients() {
        ScoringPolicy distinct = new ScoringPolicy(500_000L, 0.01, 1.0, 0.01, 3.0, 0.02, 0.05, 5_000_000L, 2.0);
        assertEquals(2.0, distinct.pointsFor(TOP_UP, 100), EPS);
        assertEquals(5.0, distinct.pointsFor(CHARGE, 100), EPS);
    }

    @Test
    void billPaymentIsProportionalAndSaturatesAtFullAmount() {
        assertEquals(0.0, policy.pointsFor(BILL_PAYMENT, 0), EPS);
        assertEquals(1.0, policy.pointsFor(BILL_PAYMENT, 2_500_000), EPS);
        assertEquals(2.0, policy.pointsFor(BILL_PAYMENT, 5_000_000), EPS);
        assertEquals(2.0, policy.pointsFor(BILL_PAYMENT, 20_000_000), EPS);
    }

    @Test
    void capsMatchSpecification() {
        assertEquals(1.0, policy.capFor(PURCHASE));
        assertEquals(3.0, policy.capFor(BALANCE_INQUIRY));
        assertEquals(2.0, policy.capFor(BILL_PAYMENT));
        assertEquals(Double.POSITIVE_INFINITY, policy.capFor(TOP_UP));
        assertEquals(Double.POSITIVE_INFINITY, policy.capFor(CHARGE));
    }

    @Test
    void unknownTypeEarnsNothing() {
        assertEquals(0.0, policy.pointsFor(UNKNOWN, 1_000_000), EPS);
    }

    @Test
    void rejectsNegativeAmountsAndInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> policy.pointsFor(PURCHASE, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoringPolicy(0L, 0.01, 1.0, 0.01, 3.0, 0.01, 0.01, 5_000_000L, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoringPolicy(500_000L, -0.01, 1.0, 0.01, 3.0, 0.01, 0.01, 5_000_000L, 2.0));
    }
}
