package th.ac.tu.cs364.wordward;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class DailyWordProvider {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    public static final LocalDate EPOCH = LocalDate.of(2026, 11, 7);

    public static LocalDate dateAt(Instant instant) {
        return instant.atZone(BANGKOK).toLocalDate();
    }

    public static long number(LocalDate date) {
        return ChronoUnit.DAYS.between(EPOCH, date) + 1;
    }

    public static String secret(LocalDate date, List<String> daily) {
        int index = (int) Math.floorMod(number(date) - 1, (long) daily.size());
        return daily.get(index);
    }
}
