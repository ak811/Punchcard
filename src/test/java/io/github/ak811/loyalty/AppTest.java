package io.github.ak811.loyalty;

import io.github.ak811.loyalty.tools.DataGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @TempDir
    Path tempDir;

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            App.run(CliOptions.parse(args), out);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void scoresRanksAndDrawsOnAHandBuiltDatabase() throws Exception {
        Path database = tempDir.resolve("hand.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE company (NationalCode INTEGER, CardAcqCode INTEGER)");
            statement.execute("CREATE TABLE shortend (RRN INTEGER, CardAcqCode INTEGER, TerminalId INTEGER,"
                    + " Amount INTEGER, Pan INTEGER, FinTransType TEXT)");
            // Card 1 has the highest points but the lowest card code; the original
            // implementation would never have selected it.
            statement.execute("INSERT INTO company VALUES (1111111111, 1), (2222222222, 2), (3333333333, 3)");
            statement.execute("INSERT INTO shortend VALUES (1, 1, 1, 1000, 0, 'TOPUP'),"
                    + " (2, 1, 1, 1000, 0, 'TOPUP'), (3, 2, 1, 500, 0, 'CHARGE'), (4, 3, 1, 0, 0, 'BALLANCE')");
        }

        String output = run("--db", database.toString(), "--top-percent", "10", "--seed", "1");

        // 3 holders -> top 10% rounds up to 1 -> the pool is card 1 alone (20 points).
        assertTrue(output.contains("Winner: card 1 (national code 1111111111), rank 1, 20.00 points"), output);
        assertTrue(output.contains("Scored"), output);
    }

    @Test
    void runsOnAGeneratedDatabaseAndAccountsForEveryTransaction() throws Exception {
        Path database = tempDir.resolve("generated.sqlite");
        DataGenerator.generate(database, 2_000, 50_000, 0.05, 7L);

        String first = run("--db", database.toString(), "--seed", "3", "--leaderboard", "5");
        String second = run("--db", database.toString(), "--seed", "3", "--leaderboard", "5");

        assertTrue(first.matches("(?s).*Transactions loaded\\s+50,000.*"), first);
        assertTrue(first.contains("Top 5 cardholders by points"), first);
        assertTrue(first.contains("Winner: card "), first);
        assertEquals(withoutTimings(first), withoutTimings(second),
                "a seeded run must be reproducible apart from timings");
    }

    private static String withoutTimings(String output) {
        return output.replaceAll("\\d+\\.\\d{3} s", "<t>");
    }

    @Test
    void rejectsInvalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--top-percent", "0"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--seed"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--bogus"}));
    }
}
