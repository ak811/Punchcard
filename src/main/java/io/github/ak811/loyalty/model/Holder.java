package io.github.ak811.loyalty.model;

/**
 * A loyalty-club cardholder and the points accumulated in each transaction category.
 *
 * <p>Category totals are clamped at a caller-supplied cap on every update, so a
 * category can never exceed its limit regardless of transaction order.
 */
public final class Holder {

    private final long nationalCode;
    private final int cardCode;

    private double purchasePoints;
    private double balanceInquiryPoints;
    private double topUpPoints;
    private double chargePoints;
    private double billPaymentPoints;

    public Holder(long nationalCode, int cardCode) {
        this.nationalCode = nationalCode;
        this.cardCode = cardCode;
    }

    public long nationalCode() {
        return nationalCode;
    }

    public int cardCode() {
        return cardCode;
    }

    /** Returns the points accumulated in one category. */
    public double points(TransactionType type) {
        return switch (type) {
            case PURCHASE -> purchasePoints;
            case BALANCE_INQUIRY -> balanceInquiryPoints;
            case TOP_UP -> topUpPoints;
            case CHARGE -> chargePoints;
            case BILL_PAYMENT -> billPaymentPoints;
            case UNKNOWN -> 0.0;
        };
    }

    /**
     * Adds {@code points} to a category, clamping the category total at {@code cap}.
     *
     * @throws IllegalArgumentException if {@code points} is negative or NaN, or {@code type} is UNKNOWN
     */
    public void addPoints(TransactionType type, double points, double cap) {
        if (!(points >= 0.0)) {
            throw new IllegalArgumentException("points must be non-negative: " + points);
        }
        switch (type) {
            case PURCHASE -> purchasePoints = Math.min(cap, purchasePoints + points);
            case BALANCE_INQUIRY -> balanceInquiryPoints = Math.min(cap, balanceInquiryPoints + points);
            case TOP_UP -> topUpPoints = Math.min(cap, topUpPoints + points);
            case CHARGE -> chargePoints = Math.min(cap, chargePoints + points);
            case BILL_PAYMENT -> billPaymentPoints = Math.min(cap, billPaymentPoints + points);
            case UNKNOWN -> throw new IllegalArgumentException("UNKNOWN transactions cannot be scored");
        }
    }

    /** Returns the sum of all category totals. */
    public double totalPoints() {
        return purchasePoints + balanceInquiryPoints + topUpPoints + chargePoints + billPaymentPoints;
    }

    @Override
    public String toString() {
        return "Holder{cardCode=" + cardCode + ", nationalCode=" + nationalCode
                + ", totalPoints=" + totalPoints() + '}';
    }
}
