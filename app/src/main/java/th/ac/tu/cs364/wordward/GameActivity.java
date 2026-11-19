package th.ac.tu.cs364.wordward;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

public final class GameActivity extends Activity {
    private GameStorage storage;
    private WordRanker words;
    private WordRanker.Ranking ranks;
    private GameRound round;
    private GameScreen screen;
    private final HintEngine hints = new HintEngine(new Random());
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable midnight = this::refreshDay;

    @Override protected void attachBaseContext(Context base) {
        String language = new GameStorage(base).language();
        if (!language.isEmpty()) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag(language));
            base = base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        storage = new GameStorage(this);
        try {
            words = new WordRanker(getAssets().open("puzzles.tsv"), getAssets().open("aliases.tsv"));
        } catch (IOException error) {
            throw new IllegalStateException("Invalid bundled dictionary", error);
        }
        screen = new GameScreen(this, this::guess, this::hint, this::switchLanguage);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDay();
    }

    @Override protected void onPause() {
        timer.removeCallbacks(midnight);
        super.onPause();
    }

    private void refreshDay() {
        Instant now = Instant.now();
        LocalDate day = DailyWordProvider.dateAt(now);
        if (round == null || !day.equals(round.date)) {
            ranks = words.rankFor(DailyWordProvider.secret(day, words.daily()));
            round = storage.open(day, ranks);
            screen.clearInput();
        }
        screen.show(round, "");
        timer.removeCallbacks(midnight);
        Instant next = day.plusDays(1).atStartOfDay(DailyWordProvider.BANGKOK).toInstant();
        timer.postDelayed(midnight, Duration.between(now, next).toMillis() + 1);
    }

    private void guess(String raw) {
        refreshDay();
        String word = words.canonical(raw);
        GameRound.GuessResult result = round.guess(word, ranks);
        String notice = "";
        switch (result) {
            case ADDED:
                storage.save(round, ranks);
                screen.clearInput();
                break;
            case UNKNOWN:
                notice = getString(R.string.unknown);
                break;
            case REPEAT:
                notice = getString(R.string.repeat, word, ranks.rank(word));
                screen.clearInput();
                break;
            case CLOSED:
                break;
        }
        screen.show(round, notice);
    }

    private void hint() {
        refreshDay();
        String word = hints.next(round, ranks);
        if (word == null || !round.hint(word, ranks)) {
            screen.show(round, getString(R.string.no_hint));
            return;
        }
        storage.save(round, ranks);
        screen.show(round, "");
    }

    private void switchLanguage() {
        String current = getResources().getConfiguration().getLocales().get(0).getLanguage();
        storage.language("th".equals(current) ? "en" : "th");
        recreate();
    }
}
