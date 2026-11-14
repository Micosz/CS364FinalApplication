package th.ac.tu.cs364.wordward;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Selects dates in Bangkok regardless of the device's default time zone.
 * The starter epoch and cyclic lookup are retained pending the team's launch/boundary policy.
 */
public final class DailyWordProvider {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    /** Changing the epoch changes existing puzzles and requires coordination with work 02/05. */
    public static final LocalDate EPOCH = LocalDate.of(2026, 11, 7);

    public static LocalDate dateAt(Instant instant) {
        return instant.atZone(BANGKOK).toLocalDate();
    }

    /** One-based day number relative to the epoch; dates before it can be zero or negative. */
    public static long number(LocalDate date) {
        return ChronoUnit.DAYS.between(EPOCH, date) + 1;
    }

    /**
     * Uses the supplied, non-empty secret order from WordRanker.daily().
     * Cycling preserves starter behavior, not an approved policy beyond the 31-day release.
     */
    public static String secret(LocalDate date, List<String> daily) {
        int index = (int) Math.floorMod(number(date) - 1, (long) daily.size());
        return daily.get(index);
    }
}
