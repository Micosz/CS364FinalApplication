package th.ac.tu.cs364.wordward;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;

public final class GameActivity extends Activity {
    private GameStorage storage;
    private GameSession session;
    private GameScreen screen;
    private ExitConfirmation exitConfirmation;
    private String restoredDraft;
    private String restoredDraftDay;
    private boolean restoredRecoveryNotice;
    private final HintEngine hints = new HintEngine(new Random());
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable midnight = this::refreshDay;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(GameAppearance.wrap(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        storage = new GameStorage(this);
        try {
            WordRanker words = new WordRanker(getAssets().open("puzzles.tsv"), getAssets().open("aliases.tsv"));
            session = new GameSession(words, storage, Clock.systemUTC());
        } catch (IOException error) {
            throw new IllegalStateException("Invalid bundled dictionary", error);
        }
        screen = new GameScreen(this, this::guess, this::hint, this::switchLanguage,
                this::restartRound, this::switchTheme);
        exitConfirmation = new ExitConfirmation(this);
        if (state != null) {
            restoredDraft = state.getString("draft");
            restoredDraftDay = state.getString("draft_day");
            restoredRecoveryNotice = state.getBoolean("recovery_notice");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDay();
    }

    @Override protected void onPause() {
        timer.removeCallbacks(midnight);
        super.onPause();
    }

    @Override protected void onStop() {
        exitConfirmation.dismiss();
        super.onStop();
    }

    @Override protected void onDestroy() {
        timer.removeCallbacks(midnight);
        if (exitConfirmation != null) exitConfirmation.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        EditText input = findViewById(R.id.input);
        state.putString("draft", input.getText().toString());
        if (session.round() != null) state.putString("draft_day", session.round().date.toString());
        state.putBoolean("recovery_notice", session.hasRecoveryNotice());
    }

    // API 33+ uses ExitConfirmation's registered OnBackInvokedCallback.
    // Keep this override for API 24–32; it is not the modern gesture handler.
    @Override @android.annotation.SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation") public void onBackPressed() {
        exitConfirmation.show();
    }

    private void refreshDay() {
        if (session.refresh()) screen.clearInput();
        if (restoredDraft != null) {
            if (session.round().date.toString().equals(restoredDraftDay)
                    && session.round().status == GameRound.Status.PLAYING) {
                ((EditText) findViewById(R.id.input)).setText(restoredDraft);
                session.restoreRecoveryNotice(restoredRecoveryNotice);
            }
            restoredDraft = null;
        }
        render("", null);
        timer.removeCallbacks(midnight);
        Instant now = Instant.now();
        Instant next = DailyWordProvider.dateAt(now).plusDays(1)
                .atStartOfDay(DailyWordProvider.BANGKOK).toInstant();
        timer.postDelayed(midnight, Duration.between(now, next).toMillis() + 1);
    }

    private void render(String notice, String repeated) {
        screen.setVocabularySize(session.vocabularySize());
        String message = notice;
        if (message.isEmpty() && session.hasRecoveryNotice()) message = getString(R.string.recovery_notice);
        screen.show(session.round(), message, repeated);
    }

    private void guess(String raw) {
        refreshDay();
        GameSession.GuessOutcome outcome = session.guess(raw);
        String notice = "";
        String repeated = null;
        switch (outcome.result) {
            case ADDED:
                screen.clearInput();
                break;
            case UNKNOWN:
                notice = getString(R.string.unknown);
                break;
            case REPEAT:
                repeated = outcome.canonical;
                GameRound.Entry entry = session.round().entry(repeated);
                notice = getString(R.string.repeat, entry.word, entry.rank);
                screen.clearInput();
                break;
            case CLOSED:
                break;
        }
        render(notice, repeated);
    }

    private void hint() {
        refreshDay();
        render(session.hint(hints) ? "" : getString(R.string.no_hint), null);
    }

    private void restartRound() {
        LocalDate confirmedDate = session.round().date;
        boolean restarted = session.restart(confirmedDate);
        screen.clearInput();
        render(restarted ? "" : getString(R.string.restart_day_changed), null);
    }

    private void switchLanguage() {
        String current = getResources().getConfiguration().getLocales().get(0).getLanguage();
        storage.language("th".equals(current) ? "en" : "th");
        recreate();
    }

    private void switchTheme() {
        // GameScreen already saved the new preference before invoking this callback.
        recreate();
    }
}
