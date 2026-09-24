package io.github.ak811.loyalty.data;

import io.github.ak811.loyalty.model.Holder;

/**
 * Cardholders read from the database.
 *
 * @param holders             holders in table order
 * @param rowsWithoutCardCode rows skipped because their card code was NULL
 */
public record LoadedHolders(Holder[] holders, long rowsWithoutCardCode) {
}
