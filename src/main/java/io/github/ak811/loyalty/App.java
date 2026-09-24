package io.github.ak811.loyalty;

import io.github.ak811.loyalty.config.ScoringPolicy;
import io.github.ak811.loyalty.data.LoadedHolders;
import io.github.ak811.loyalty.data.SqliteSource;
import io.github.ak811.loyalty.data.TransactionBatch;
import io.github.ak811.loyalty.index.HolderIndex;
import io.github.ak811.loyalty.lottery.Lottery;
import io.github.ak811.loyalty.model.Holder;
import io.github.ak811.loyalty.model.TransactionType;
import io.github.ak811.loyalty.scoring.ScoringEngine;
import io.github.ak811.loyalty.scoring.ScoringStats;
import io.github.ak811.loyalty.util.PhaseTimer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Entry point: loads cardholders and transactions from SQLite, scores every
 * transaction, ranks cardholders by points, and draws a lottery winner from the
 * top percentage.
 */
public final class App {

    private static final String LOAD_HOLDERS = "Load cardholders (SQLite)";
    private static final String LOAD_TRANSACTIONS = "Load transactions (SQLite)";
    private static final String INDEX = "Index cardholders (merge sort)";
    private static final String SCORE = "Score transactions (binary search)";
    private static final String RANK = "Rank cardholders (merge sort)";
    private static final String DRAW = "Draw lottery winner";

    private App() {
    }

    public static void main(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.print(CliOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.print(CliOptions.USAGE);
            return;
        }
        try {
            run(options, System.out);
        } catch (NoSuchFileException e) {
            System.err.println("error: database file not found: " + e.getFile());
            System.exit(1);
        } catch (IOException | SQLException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(CliOptions options, PrintStream out) throws IOException, SQLException {
        PhaseTimer timer = new PhaseTimer();

        LoadedHolders loadedHolders;
        TransactionBatch transactions;
        try (SqliteSource source = SqliteSource.open(options.database())) {
            loadedHolders = timer.time(LOAD_HOLDERS, source::loadHolders);
            transactions = timer.time(LOAD_TRANSACTIONS, source::loadTransactions);
        }

        HolderIndex index = timer.time(INDEX, () -> HolderIndex.build(loadedHolders.holders()));

        ScoringEngine engine = new ScoringEngine(ScoringPolicy.DEFAULT);
        ScoringStats stats = timer.time(SCORE, () -> engine.score(transactions, index));

        Holder[] ranked = timer.time(RANK, () -> Lottery.rank(index.holders()));

        RandomGenerator random = options.seed().isPresent()
                ? new SplittableRandom(options.seed().getAsLong())
                : new SecureRandom();
        Draw draw = timer.time(DRAW, () -> {
            int poolSize = Lottery.poolSize(ranked, options.topPercent());
            return new Draw(poolSize, Lottery.draw(ranked, poolSize, random));
        });

        Report report = new Report(out);
        report.input(options, loadedHolders, index, transactions);
        report.scoring(stats);
        report.leaderboard(ranked, options.leaderboard());
        report.lottery(options, ranked, draw.poolSize(), draw.winner());
        report.timing(timer);
    }

    /** Lottery pool size and the index of the winner in the ranking, if any. */
    private record Draw(int poolSize, Optional<Integer> winner) {
    }

    /** Formats the run summary. */
    private static final class Report {
        private final PrintStream out;

        Report(PrintStream out) {
            this.out = out;
        }

        void input(CliOptions options, LoadedHolders loaded, HolderIndex index, TransactionBatch transactions) {
            out.println("java-jdbc-loyalty-engine");
            out.println("Database: " + options.database());
            out.println();
            out.println("Input");
            row("Cardholders loaded", loaded.holders().length);
            row("Cardholders indexed", index.size());
            row("Duplicate card codes dropped", index.duplicatesDropped());
            row("Cardholder rows without card code", loaded.rowsWithoutCardCode());
            row("Transactions loaded", transactions.size());
            row("Transaction rows without card code", transactions.rowsWithoutCardCode());
            out.println();
        }

        void scoring(ScoringStats stats) {
            out.println("Scoring");
            row("Scored", stats.scored());
            row("Unmatched card code", stats.unmatchedCard());
            row("Unknown transaction type", stats.unknownType());
            row("Negative amount", stats.negativeAmount());
            out.println();
        }

        void leaderboard(Holder[] ranked, int limit) {
            int shown = Math.min(limit, ranked.length);
            if (shown == 0) {
                return;
            }
            out.printf(Locale.US, "Top %d cardholders by points%n", shown);
            out.printf(Locale.US, "  %4s  %11s  %13s  %9s  %8s  %12s  %12s  %8s  %13s%n",
                    "Rank", "Card code", "National code", "Purchase", "Balance",
                    "Top-up", "Charge", "Bill", "Total");
            for (int i = 0; i < shown; i++) {
                Holder h = ranked[i];
                out.printf(Locale.US, "  %4d  %11d  %13d  %9.2f  %8.2f  %,12.2f  %,12.2f  %8.2f  %,13.2f%n",
                        i + 1, h.cardCode(), h.nationalCode(),
                        h.points(TransactionType.PURCHASE),
                        h.points(TransactionType.BALANCE_INQUIRY),
                        h.points(TransactionType.TOP_UP),
                        h.points(TransactionType.CHARGE),
                        h.points(TransactionType.BILL_PAYMENT),
                        h.totalPoints());
            }
            out.println();
        }

        void lottery(CliOptions options, Holder[] ranked, int poolSize, Optional<Integer> winner) {
            out.println("Lottery");
            out.printf(Locale.US, "  Pool: top %d%% of %,d cardholders = %,d, of which %,d have points%n",
                    options.topPercent(), ranked.length,
                    Lottery.topPercentSize(ranked.length, options.topPercent()), poolSize);
            out.println("  Randomness: " + (options.seed().isPresent()
                    ? "SplittableRandom, seed " + options.seed().getAsLong()
                    : "SecureRandom"));
            if (winner.isPresent()) {
                Holder h = ranked[winner.get()];
                out.printf(Locale.US, "  Winner: card %d (national code %d), rank %,d, %,.2f points%n",
                        h.cardCode(), h.nationalCode(), winner.get() + 1, h.totalPoints());
            } else {
                out.println("  Winner: none (no cardholder in the pool has points)");
            }
            out.println();
        }

        void timing(PhaseTimer timer) {
            out.println("Timing");
            long loadNanos = 0;
            for (PhaseTimer.Entry entry : timer.entries()) {
                out.printf(Locale.US, "  %-38s %9.3f s%n", entry.name(), entry.seconds());
                if (entry.name().equals(LOAD_HOLDERS) || entry.name().equals(LOAD_TRANSACTIONS)) {
                    loadNanos += entry.nanos();
                }
            }
            long totalNanos = timer.totalNanos();
            out.printf(Locale.US, "  %-38s %9.3f s%n", "I/O (SQLite loading)", loadNanos / 1e9);
            out.printf(Locale.US, "  %-38s %9.3f s%n", "Compute (index, score, rank, draw)",
                    (totalNanos - loadNanos) / 1e9);
            out.printf(Locale.US, "  %-38s %9.3f s%n", "Total", totalNanos / 1e9);
        }

        private void row(String label, long value) {
            out.printf(Locale.US, "  %-36s %,13d%n", label, value);
        }
    }
}
