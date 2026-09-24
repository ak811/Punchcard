package io.github.ak811.loyalty.data;

import io.github.ak811.loyalty.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteSourceTest {

    @TempDir
    Path tempDir;

    private Path createDatabase(String... statements) throws SQLException {
        Path database = tempDir.resolve("test.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE company (NationalCode INTEGER, CardAcqCode INTEGER)");
            statement.execute("CREATE TABLE shortend (RRN INTEGER, CardAcqCode INTEGER, TerminalId INTEGER,"
                    + " Amount INTEGER, Pan INTEGER, FinTransType TEXT)");
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
        return database;
    }

    @Test
    void loadsHoldersIncludingNationalCodesBeyondIntRange() throws Exception {
        Path database = createDatabase(
                "INSERT INTO company VALUES (9876543210, 100)",
                "INSERT INTO company VALUES (12345, 200)",
                "INSERT INTO company VALUES (55555, NULL)");

        try (SqliteSource source = SqliteSource.open(database)) {
            LoadedHolders loaded = source.loadHolders();

            assertEquals(2, loaded.holders().length);
            assertEquals(1, loaded.rowsWithoutCardCode());
            assertEquals(9_876_543_210L, loaded.holders()[0].nationalCode());
            assertEquals(100, loaded.holders()[0].cardCode());
        }
    }

    @Test
    void mapsTransactionTypesCaseInsensitivelyAndHandlesNulls() throws Exception {
        Path database = createDatabase(
                "INSERT INTO shortend VALUES (1, 100, 1, 600000, 6000000000000000, 'PURCHASE')",
                "INSERT INTO shortend VALUES (2, 100, 1, NULL, 6000000000000000, 'Ballance')",
                "INSERT INTO shortend VALUES (3, 100, 1, 10, 6000000000000000, ' topup ')",
                "INSERT INTO shortend VALUES (4, 100, 1, 10, 6000000000000000, 'charge')",
                "INSERT INTO shortend VALUES (5, 100, 1, 10, 6000000000000000, 'Bill_Payment')",
                "INSERT INTO shortend VALUES (6, 100, 1, 10, 6000000000000000, 'REVERSAL')",
                "INSERT INTO shortend VALUES (7, 100, 1, 10, 6000000000000000, NULL)",
                "INSERT INTO shortend VALUES (8, NULL, 1, 10, 6000000000000000, 'PURCHASE')");

        try (SqliteSource source = SqliteSource.open(database)) {
            TransactionBatch batch = source.loadTransactions();

            assertEquals(7, batch.size());
            assertEquals(1, batch.rowsWithoutCardCode());
            assertEquals(TransactionType.PURCHASE, batch.type(0));
            assertEquals(600_000L, batch.amount(0));
            assertEquals(TransactionType.BALANCE_INQUIRY, batch.type(1));
            assertEquals(0L, batch.amount(1), "NULL amount is read as 0");
            assertEquals(TransactionType.TOP_UP, batch.type(2));
            assertEquals(TransactionType.CHARGE, batch.type(3));
            assertEquals(TransactionType.BILL_PAYMENT, batch.type(4));
            assertEquals(TransactionType.UNKNOWN, batch.type(5));
            assertEquals(TransactionType.UNKNOWN, batch.type(6));
        }
    }

    @Test
    void refusesToOpenAMissingFileInsteadOfCreatingOne() {
        Path missing = tempDir.resolve("missing.sqlite");

        assertThrows(NoSuchFileException.class, () -> SqliteSource.open(missing));
        assertFalse(Files.exists(missing));
    }

    @Test
    void reportsMissingTables() throws Exception {
        Path database = tempDir.resolve("empty.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unrelated (x INTEGER)");
        }

        try (SqliteSource source = SqliteSource.open(database)) {
            assertThrows(SQLException.class, source::loadHolders);
        }
    }
}
