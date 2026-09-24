package io.github.ak811.loyalty.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Generates a synthetic SQLite database with the same schema as the production data,
 * so the engine can be built, tested, and benchmarked without access to real
 * transaction records.
 *
 * <p>Output is fully determined by the seed. Cardholders are inserted in shuffled
 * card-code order so the index build performs a real sort, and a configurable share
 * of transactions reference card codes that belong to no cardholder.
 *
 * <pre>
 * java -cp java-jdbc-loyalty-engine.jar io.github.ak811.loyalty.tools.DataGenerator \
 *     --db data.sqlite --holders 500000 --transactions 5700000 --seed 42
 * </pre>
 */
public final class DataGenerator {

    private static final int BATCH_SIZE = 50_000;
    private static final int FIRST_CARD_CODE = 10_000_000;
    private static final int UNMATCHED_CARD_CODE_BASE = 900_000_000;

    private static final String USAGE = """
            Usage: DataGenerator [options]
              --db <file>              Output SQLite file (default: data.sqlite)
              --holders <n>            Number of cardholders (default: 500000)
              --transactions <n>       Number of transactions (default: 5700000)
              --unmatched-rate <0-1>   Share of transactions with no matching cardholder (default: 0.01)
              --seed <long>            Random seed (default: 42)
              --force                  Overwrite the output file if it exists
            """;

    private DataGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Path.of("data.sqlite");
        int holders = 500_000;
        int transactions = 5_700_000;
        double unmatchedRate = 0.01;
        long seed = 42;
        boolean force = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--db" -> database = Path.of(args[++i]);
                    case "--holders" -> holders = Integer.parseInt(args[++i]);
                    case "--transactions" -> transactions = Integer.parseInt(args[++i]);
                    case "--unmatched-rate" -> unmatchedRate = Double.parseDouble(args[++i]);
                    case "--seed" -> seed = Long.parseLong(args[++i]);
                    case "--force" -> force = true;
                    case "-h", "--help" -> {
                        System.out.print(USAGE);
                        return;
                    }
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usageError("missing value for " + args[args.length - 1]);
        } catch (IllegalArgumentException e) {
            usageError(e.getMessage());
        }
        if (holders < 1 || transactions < 0 || !(unmatchedRate >= 0.0 && unmatchedRate <= 1.0)) {
            System.err.println("error: invalid arguments");
            System.err.print(USAGE);
            System.exit(2);
        }
        if (Files.exists(database)) {
            if (!force) {
                System.err.println("error: " + database + " already exists (use --force to overwrite)");
                System.exit(1);
            }
            Files.delete(database);
        }

        long start = System.nanoTime();
        generate(database, holders, transactions, unmatchedRate, seed);
        System.out.printf(Locale.US, "Wrote %,d cardholders and %,d transactions to %s in %.1f s%n",
                holders, transactions, database, (System.nanoTime() - start) / 1e9);
    }

    private static void usageError(String message) {
        System.err.println("error: " + message);
        System.err.print(USAGE);
        System.exit(2);
    }

    /** Creates {@code database} and fills it with synthetic cardholders and transactions. */
    public static void generate(Path database, int holders, int transactions, double unmatchedRate, long seed)
            throws SQLException {
        SplittableRandom random = new SplittableRandom(seed);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = OFF");
                statement.execute("PRAGMA synchronous = OFF");
                statement.execute("CREATE TABLE company (NationalCode INTEGER, CardAcqCode INTEGER)");
                statement.execute("CREATE TABLE shortend (RRN INTEGER, CardAcqCode INTEGER, TerminalId INTEGER,"
                        + " Amount INTEGER, Pan INTEGER, FinTransType TEXT)");
            }
            connection.setAutoCommit(false);

            int[] cardCodes = shuffledCardCodes(holders, random);
            insertHolders(connection, cardCodes, random);
            insertTransactions(connection, cardCodes, transactions, unmatchedRate, random);
            connection.commit();
        }
    }

    private static int[] shuffledCardCodes(int count, SplittableRandom random) {
        int[] codes = new int[count];
        for (int i = 0; i < count; i++) {
            codes[i] = FIRST_CARD_CODE + i;
        }
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = codes[i];
            codes[i] = codes[j];
            codes[j] = tmp;
        }
        return codes;
    }

    private static void insertHolders(Connection connection, int[] cardCodes, SplittableRandom random)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO company (NationalCode, CardAcqCode) VALUES (?, ?)")) {
            for (int i = 0; i < cardCodes.length; i++) {
                insert.setLong(1, random.nextLong(1_000_000_000L, 10_000_000_000L));
                insert.setInt(2, cardCodes[i]);
                insert.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private static void insertTransactions(Connection connection, int[] cardCodes, int count,
                                           double unmatchedRate, SplittableRandom random) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO shortend (RRN, CardAcqCode, TerminalId, Amount, Pan, FinTransType)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < count; i++) {
                int cardCode = random.nextDouble() < unmatchedRate
                        ? UNMATCHED_CARD_CODE_BASE + random.nextInt(100_000_000)
                        : cardCodes[random.nextInt(cardCodes.length)];

                String type;
                long amount;
                int roll = random.nextInt(100);
                if (roll < 50) {
                    type = "PURCHASE";
                    amount = random.nextLong(10_000, 5_000_001);
                } else if (roll < 65) {
                    type = "BALLANCE";
                    amount = 0;
                } else if (roll < 77) {
                    type = "TOPUP";
                    amount = random.nextLong(10_000, 1_000_001);
                } else if (roll < 90) {
                    type = "CHARGE";
                    amount = random.nextLong(10_000, 500_001);
                } else if (roll < 99) {
                    type = "BILL_PAYMENT";
                    amount = random.nextLong(50_000, 8_000_001);
                } else {
                    type = "REVERSAL"; // Not a scored type; exercises the UNKNOWN path.
                    amount = random.nextLong(10_000, 1_000_001);
                }

                insert.setLong(1, i + 1L);
                insert.setInt(2, cardCode);
                insert.setInt(3, random.nextInt(1, 100_000));
                insert.setLong(4, amount);
                insert.setLong(5, random.nextLong(6_000_000_000_000_000L, 7_000_000_000_000_000L));
                insert.setString(6, type);
                insert.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
    }
}
