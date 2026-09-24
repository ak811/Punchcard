package io.github.ak811.loyalty.model;

/**
 * Transaction categories recognized by the scoring engine.
 *
 * <p>Each category has a stable integer code. The SQLite query maps the textual
 * {@code FinTransType} column to these codes, so no per-row {@code String}
 * objects are created while loading.
 */
public enum TransactionType {
    UNKNOWN(0),
    PURCHASE(1),
    BALANCE_INQUIRY(2),
    TOP_UP(3),
    CHARGE(4),
    BILL_PAYMENT(5);

    private static final TransactionType[] BY_CODE = new TransactionType[values().length];

    static {
        for (TransactionType type : values()) {
            BY_CODE[type.code] = type;
        }
    }

    private final int code;

    TransactionType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** Returns the type for {@code code}, or {@link #UNKNOWN} for any unrecognized code. */
    public static TransactionType fromCode(int code) {
        return (code >= 0 && code < BY_CODE.length) ? BY_CODE[code] : UNKNOWN;
    }
}
