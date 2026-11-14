package th.ac.tu.cs364.wordward;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class DailyWordProviderTest {
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 11, 7);
    private WordRanker words;
    private List<String> daily;
    private List<String> pinned;

    @Before public void loadProduction() throws IOException {
        try (InputStream puzzles = Files.newInputStream(Paths.get("src/main/assets/puzzles.tsv"));
             InputStream aliases = Files.newInputStream(Paths.get("src/main/assets/aliases.tsv"))) {
            words = new WordRanker(puzzles, aliases);
        }
        daily = words.daily();
        pinned = Files.readAllLines(Paths.get("../tools/wordward/secrets.txt"));
    }

    @Test public void bangkokMidnightSwitchesTheDateAndPuzzle() {
        Instant before = Instant.parse("2026-11-07T16:59:59Z");
        Instant midnight = Instant.parse("2026-11-07T17:00:00Z");
        assertEquals(FIRST_DAY, DailyWordProvider.dateAt(before));
        assertEquals(FIRST_DAY.plusDays(1), DailyWordProvider.dateAt(midnight));
        assertEquals("ocean", secretAt(before));
        assertEquals("apple", secretAt(midnight));
        assertEquals(1L, DailyWordProvider.number(DailyWordProvider.dateAt(before)));
        assertEquals(2L, DailyWordProvider.number(DailyWordProvider.dateAt(midnight)));
    }

    @Test public void deviceTimeZoneDoesNotChangeTheBangkokPuzzle() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[] {"UTC", "America/Los_Angeles", "Asia/Tokyo",
                    "Pacific/Kiritimati", "Pacific/Pago_Pago", "Asia/Bangkok"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                Instant before = Instant.parse("2026-11-07T16:59:59Z");
                Instant after = Instant.parse("2026-11-07T17:00:00Z");
                assertEquals(zone, FIRST_DAY, DailyWordProvider.dateAt(before));
                assertEquals(zone, FIRST_DAY.plusDays(1), DailyWordProvider.dateAt(after));
                assertEquals(zone, "ocean", secretAt(before));
                assertEquals(zone, "apple", secretAt(after));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void all31DaysFollowThePinnedOrderWithoutRepeating() {
        assertEquals(31, pinned.size());
        assertEquals(pinned, daily);
        Set<String> found = new HashSet<>();
        for (int offset = 0; offset < 31; offset++) {
            LocalDate date = FIRST_DAY.plusDays(offset);
            String secret = DailyWordProvider.secret(date, daily);
            assertEquals(offset + 1L, DailyWordProvider.number(date));
            assertEquals(pinned.get(offset), secret);
            assertTrue("Repeated secret on " + date, found.add(secret));
            assertEquals(secret, words.canonical(secret));
            assertEquals(secret, words.rankFor(secret).wordAt(1));
        }
        assertEquals(31, found.size());
        assertEquals(pinned, daily);
    }

    @Test public void firstMiddleAndLastDaysSelectTheExpectedSecrets() {
        assertEquals("ocean", DailyWordProvider.secret(FIRST_DAY, daily));
        assertEquals("river", DailyWordProvider.secret(LocalDate.of(2026, 11, 22), daily));
        assertEquals("rainbow", DailyWordProvider.secret(LocalDate.of(2026, 12, 7), daily));
        assertEquals(1L, DailyWordProvider.number(FIRST_DAY));
        assertEquals(16L, DailyWordProvider.number(LocalDate.of(2026, 11, 22)));
        assertEquals(31L, DailyWordProvider.number(LocalDate.of(2026, 12, 7)));
    }

    @Test public void allTransitionsWithinThe31DaysHappenAt1700Utc() {
        for (int offset = 1; offset < 31; offset++) {
            LocalDate nextDay = FIRST_DAY.plusDays(offset);
            // Independent oracle: 00:00 Bangkok is 17:00 UTC on the preceding date.
            Instant midnight = nextDay.minusDays(1).atTime(17, 0).toInstant(ZoneOffset.UTC);
            assertEquals(nextDay.minusDays(1), DailyWordProvider.dateAt(midnight.minusSeconds(1)));
            assertEquals(nextDay, DailyWordProvider.dateAt(midnight));
            assertEquals(pinned.get(offset - 1), secretAt(midnight.minusSeconds(1)));
            assertEquals(pinned.get(offset), secretAt(midnight));
        }
    }

    @Test public void aBangkokDayKeepsTheSamePuzzleUntilItsLastSecond() {
        for (int offset : new int[] {0, 15, 30}) {
            LocalDate date = FIRST_DAY.plusDays(offset);
            Instant start = date.minusDays(1).atTime(17, 0).toInstant(ZoneOffset.UTC);
            for (long seconds : new long[] {0, 6 * 3600, 12 * 3600, 24 * 3600 - 1}) {
                assertEquals(date, DailyWordProvider.dateAt(start.plusSeconds(seconds)));
                assertEquals(pinned.get(offset), secretAt(start.plusSeconds(seconds)));
            }
        }
    }

    private String secretAt(Instant instant) {
        return DailyWordProvider.secret(DailyWordProvider.dateAt(instant), daily);
    }
}
