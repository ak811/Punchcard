package io.github.ak811.loyalty.scoring;

/**
 * Outcome counts for one scoring run. Every loaded transaction falls into exactly
 * one of the first four categories.
 *
 * @param scored          transactions credited to a cardholder
 * @param unmatchedCard   transactions whose card code matches no cardholder
 * @param unknownType     transactions with an unrecognized {@code FinTransType}
 * @param negativeAmount  transactions rejected because the amount was negative
 */
public record ScoringStats(long scored, long unmatchedCard, long unknownType, long negativeAmount) {

    /** Total number of transactions processed. */
    public long total() {
        return scored + unmatchedCard + unknownType + negativeAmount;
    }
}
