package io.github.ak811.loyalty.config;

import io.github.ak811.loyalty.model.TransactionType;

/**
 * Loyalty scoring rules: how many points each transaction earns, and the maximum
 * total a cardholder can accumulate in each category.
 *
 * <p>Rules applied per transaction (amounts in the currency's smallest unit):
 * <ul>
 *   <li><b>Purchase:</b> {@code pointsPerPurchaseUnit} for every full {@code purchaseUnitAmount}
 *       in the transaction; category capped at {@code maxPurchasePoints}.</li>
 *   <li><b>Balance inquiry:</b> a flat {@code pointsPerBalanceInquiry};
 *       category capped at {@code maxBalanceInquiryPoints}.</li>
 *   <li><b>Top-up:</b> {@code amount * topUpPointsPerAmountUnit}; uncapped.</li>
 *   <li><b>Charge:</b> {@code amount * chargePointsPerAmountUnit}; uncapped.</li>
 *   <li><b>Bill payment:</b> proportional to the amount, reaching {@code maxBillPaymentPoints}
 *       at {@code billPaymentFullAmount}; category capped at {@code maxBillPaymentPoints}.</li>
 * </ul>
 */
public record ScoringPolicy(
        long purchaseUnitAmount,
        double pointsPerPurchaseUnit,
        double maxPurchasePoints,
        double pointsPerBalanceInquiry,
        double maxBalanceInquiryPoints,
        double topUpPointsPerAmountUnit,
        double chargePointsPerAmountUnit,
        long billPaymentFullAmount,
        double maxBillPaymentPoints) {

    /** The rules of the original customer-club specification. */
    public static final ScoringPolicy DEFAULT = new ScoringPolicy(
            500_000L, 0.01, 1.0,
            0.01, 3.0,
            0.01,
            0.01,
            5_000_000L, 2.0);

    public ScoringPolicy {
        requirePositive(purchaseUnitAmount, "purchaseUnitAmount");
        requirePositive(billPaymentFullAmount, "billPaymentFullAmount");
        requireNonNegative(pointsPerPurchaseUnit, "pointsPerPurchaseUnit");
        requireNonNegative(maxPurchasePoints, "maxPurchasePoints");
        requireNonNegative(pointsPerBalanceInquiry, "pointsPerBalanceInquiry");
        requireNonNegative(maxBalanceInquiryPoints, "maxBalanceInquiryPoints");
        requireNonNegative(topUpPointsPerAmountUnit, "topUpPointsPerAmountUnit");
        requireNonNegative(chargePointsPerAmountUnit, "chargePointsPerAmountUnit");
        requireNonNegative(maxBillPaymentPoints, "maxBillPaymentPoints");
    }

    /**
     * Returns the points a single transaction earns before category caps are applied.
     *
     * @param amount transaction amount; must be non-negative
     */
    public double pointsFor(TransactionType type, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative: " + amount);
        }
        return switch (type) {
            case PURCHASE -> (amount / purchaseUnitAmount) * pointsPerPurchaseUnit;
            case BALANCE_INQUIRY -> pointsPerBalanceInquiry;
            case TOP_UP -> amount * topUpPointsPerAmountUnit;
            case CHARGE -> amount * chargePointsPerAmountUnit;
            case BILL_PAYMENT -> maxBillPaymentPoints
                    * Math.min(amount, billPaymentFullAmount) / (double) billPaymentFullAmount;
            case UNKNOWN -> 0.0;
        };
    }

    /** Returns the maximum total a cardholder can accumulate in a category. */
    public double capFor(TransactionType type) {
        return switch (type) {
            case PURCHASE -> maxPurchasePoints;
            case BALANCE_INQUIRY -> maxBalanceInquiryPoints;
            case BILL_PAYMENT -> maxBillPaymentPoints;
            case TOP_UP, CHARGE, UNKNOWN -> Double.POSITIVE_INFINITY;
        };
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0)) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }
}
