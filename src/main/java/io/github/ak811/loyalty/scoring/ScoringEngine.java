package io.github.ak811.loyalty.scoring;

import io.github.ak811.loyalty.config.ScoringPolicy;
import io.github.ak811.loyalty.data.TransactionBatch;
import io.github.ak811.loyalty.index.HolderIndex;
import io.github.ak811.loyalty.model.Holder;
import io.github.ak811.loyalty.model.TransactionType;

import java.util.Objects;

/**
 * Credits each transaction to its cardholder according to a {@link ScoringPolicy}.
 *
 * <p>Each transaction costs one O(log n) binary search in the {@link HolderIndex},
 * so scoring m transactions against n cardholders takes O(m log n) time.
 */
public final class ScoringEngine {

    private final ScoringPolicy policy;

    public ScoringEngine(ScoringPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Scores every transaction in {@code batch}, updating holders found in {@code index}. */
    public ScoringStats score(TransactionBatch batch, HolderIndex index) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(index, "index");

        long scored = 0;
        long unmatchedCard = 0;
        long unknownType = 0;
        long negativeAmount = 0;

        for (int i = 0, n = batch.size(); i < n; i++) {
            TransactionType type = batch.type(i);
            if (type == TransactionType.UNKNOWN) {
                unknownType++;
                continue;
            }
            long amount = batch.amount(i);
            if (amount < 0) {
                negativeAmount++;
                continue;
            }
            Holder holder = index.find(batch.cardCode(i));
            if (holder == null) {
                unmatchedCard++;
                continue;
            }
            holder.addPoints(type, policy.pointsFor(type, amount), policy.capFor(type));
            scored++;
        }
        return new ScoringStats(scored, unmatchedCard, unknownType, negativeAmount);
    }
}
