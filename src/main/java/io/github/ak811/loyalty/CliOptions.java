package io.github.ak811.loyalty;

import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * Command-line options for {@link App}.
 *
 * @param database    SQLite database file
 * @param topPercent  lottery pool size as a percentage of all cardholders
 * @param seed        optional seed for a reproducible draw
 * @param leaderboard number of top-ranked cardholders to print
 * @param help        whether usage information was requested
 */
public record CliOptions(Path database, int topPercent, OptionalLong seed, int leaderboard, boolean help) {

    static final String USAGE = """
            Usage: java -jar java-jdbc-loyalty-engine.jar [options]

            Options:
              --db <file>            SQLite database file (default: data.sqlite)
              --top-percent <1-100>  Lottery pool as a percentage of cardholders (default: 10)
              --seed <long>          Seed for a reproducible draw (default: SecureRandom)
              --leaderboard <n>      Number of top cardholders to print (default: 10)
              -h, --help             Show this help
            """;

    static CliOptions parse(String[] args) {
        Path database = Path.of("data.sqlite");
        int topPercent = 10;
        OptionalLong seed = OptionalLong.empty();
        int leaderboard = 10;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--db" -> database = Path.of(value(args, ++i, arg));
                case "--top-percent" -> topPercent = parseInt(value(args, ++i, arg), arg, 1, 100);
                case "--seed" -> seed = OptionalLong.of(parseLong(value(args, ++i, arg), arg));
                case "--leaderboard" -> leaderboard = parseInt(value(args, ++i, arg), arg, 0, Integer.MAX_VALUE);
                case "-h", "--help" -> help = true;
                default -> throw new IllegalArgumentException("unknown option: " + arg);
            }
        }
        return new CliOptions(database, topPercent, seed, leaderboard, help);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static int parseInt(String text, String option, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(option + " must be in [" + min + ", " + max + "]: " + text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects an integer: " + text);
        }
    }

    private static long parseLong(String text, String option) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects an integer: " + text);
        }
    }
}
