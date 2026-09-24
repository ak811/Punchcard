package io.github.ak811.loyalty.data;

import io.github.ak811.loyalty.model.Holder;
import io.github.ak811.loyalty.model.TransactionType;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to the loyalty database over JDBC.
 *
 * <p>Expected schema:
 * <pre>
 * company  (NationalCode INTEGER, CardAcqCode INTEGER)
 * shortend (RRN, CardAcqCode INTEGER, TerminalId, Amount INTEGER, Pan, FinTransType TEXT)
 * </pre>
 *
 * <p>Only the columns needed for scoring are read. The textual transaction type is
 * mapped to an integer code inside SQLite, case-insensitively, so the Java side
 * never allocates a {@code String} per row.
 */
public final class SqliteSource implements AutoCloseable {

    static final String HOLDERS_SQL = "SELECT NationalCode, CardAcqCode FROM company";

    static final String TRANSACTIONS_SQL = """
            SELECT CardAcqCode,
                   COALESCE(Amount, 0),
                   CASE UPPER(TRIM(FinTransType))
                       WHEN 'PURCHASE'     THEN %d
                       WHEN 'BALANCE'      THEN %d
                       WHEN 'BALLANCE'     THEN %d
                       WHEN 'TOPUP'        THEN %d
                       WHEN 'CHARGE'       THEN %d
                       WHEN 'BILL_PAYMENT' THEN %d
                       ELSE %d
                   END
            FROM shortend
            """.formatted(
            TransactionType.PURCHASE.code(),
            TransactionType.BALANCE_INQUIRY.code(),
            TransactionType.BALANCE_INQUIRY.code(),
            TransactionType.TOP_UP.code(),
            TransactionType.CHARGE.code(),
            TransactionType.BILL_PAYMENT.code(),
            TransactionType.UNKNOWN.code());

    private static final int INITIAL_TRANSACTION_CAPACITY = 1 << 20;

    private final Connection connection;

    private SqliteSource(Connection connection) {
        this.connection = connection;
    }

    /**
     * Opens {@code databaseFile} in read-only mode.
     *
     * @throws NoSuchFileException if the file does not exist (SQLite would otherwise
     *                             silently create an empty database)
     */
    public static SqliteSource open(Path databaseFile) throws IOException, SQLException {
        if (!Files.isRegularFile(databaseFile)) {
            throw new NoSuchFileException(databaseFile.toString(), null, "database file not found");
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        return new SqliteSource(DriverManager.getConnection(url, config.toProperties()));
    }

    /** Reads all cardholders. Rows with a NULL card code are skipped and counted. */
    public LoadedHolders loadHolders() throws SQLException {
        List<Holder> holders = new ArrayList<>();
        long rowsWithoutCardCode = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(HOLDERS_SQL)) {
            while (rows.next()) {
                long nationalCode = rows.getLong(1);
                int cardCode = rows.getInt(2);
                if (rows.wasNull()) {
                    rowsWithoutCardCode++;
                    continue;
                }
                holders.add(new Holder(nationalCode, cardCode));
            }
        }
        return new LoadedHolders(holders.toArray(Holder[]::new), rowsWithoutCardCode);
    }

    /** Reads all transactions. Rows with a NULL card code are skipped and counted. */
    public TransactionBatch loadTransactions() throws SQLException {
        TransactionBatch batch = new TransactionBatch(INITIAL_TRANSACTION_CAPACITY);
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(TRANSACTIONS_SQL)) {
            while (rows.next()) {
                int cardCode = rows.getInt(1);
                if (rows.wasNull()) {
                    batch.recordRowWithoutCardCode();
                    continue;
                }
                batch.add(cardCode, rows.getLong(2), TransactionType.fromCode(rows.getInt(3)));
            }
        }
        return batch;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
