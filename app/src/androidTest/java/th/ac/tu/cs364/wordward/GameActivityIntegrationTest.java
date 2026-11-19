package th.ac.tu.cs364.wordward;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.widget.EditText;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GameActivityIntegrationTest {
    private Context context;

    @Before public void freshTestRound() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("daily_game_v1", Context.MODE_PRIVATE).edit()
                .clear().putString("language", "en").putString("theme", "light").commit();
    }

    @Test public void backCancelStaysAndBackConfirmPreservesTheRound() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            pressBackUnconditionally();
            onView(withText(R.string.exit_confirm_title)).check(matches(isDisplayed()));
            capture("01-back-confirmation");
            onView(withId(android.R.id.button2)).perform(click());
            assertEquals(1, savedRound().attempts());
            pressBackUnconditionally();
            onView(withId(android.R.id.button1)).perform(click());
            // finish() completes asynchronously after the dialog click returns.
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (scenario.getState() != Lifecycle.State.DESTROYED
                    && android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(50);
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
        try (ActivityScenario<GameActivity> reopened = ActivityScenario.launch(GameActivity.class)) {
            assertEquals(1, savedRound().attempts());
            onView(withId(R.id.totals)).check(matches(withText(context.getString(R.string.top_bar_totals, 1, 0))));
        }
    }

    @Test public void restartCancelKeepsDataAndConfirmPersistsAnEmptyRound() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            menu(R.string.restart_round);
            onView(withId(android.R.id.button2)).perform(click());
            assertEquals(1, savedRound().attempts());
            menu(R.string.restart_round);
            capture("02-restart-confirmation");
            onView(withId(android.R.id.button1)).perform(click());
            assertEquals(0, savedRound().attempts());
            scenario.recreate();
            assertEquals(0, savedRound().attempts());
            guess("child");
            assertEquals(1, savedRound().attempts());
            capture("03-restarted-round");
        }
    }

    @Test public void irregularAliasHighlightsTheCanonicalRowWithoutAddingAnAttempt() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            guess("children");
            assertEquals(1, savedRound().attempts());
            onView(withText("REPEATED")).check(matches(isDisplayed()));
            capture("04-irregular-alias-repeat");
        }
    }

    @Test public void themeChangesOnceAndDraftAndRoundSurviveRecreation() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            onView(withId(R.id.input)).perform(replaceText("unfinished"), closeSoftKeyboard());
            menu(R.string.switch_theme_night);
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
            assertEquals("dark", new GameStorage(context).theme());
            assertEquals(1, savedRound().attempts());
            scenario.onActivity(a -> assertEquals(Configuration.UI_MODE_NIGHT_YES,
                    a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK));
            scenario.recreate();
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
            assertEquals("dark", new GameStorage(context).theme());
            capture("05-dark-theme-restored");
        }
    }

    @Test public void languageSwitchKeepsThemeDraftAndTheRound() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            onView(withId(R.id.input)).perform(replaceText("unfinished"), closeSoftKeyboard());
            menu(R.string.switch_language);
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
            assertEquals("th", new GameStorage(context).language());
            assertEquals("light", new GameStorage(context).theme());
            assertEquals(1, savedRound().attempts());
            scenario.onActivity(a -> assertEquals("th",
                    a.getResources().getConfiguration().getLocales().get(0).getLanguage()));
            capture("06-thai-language-restored");
        }
    }

    @Test public void stoppingAndResumingKeepsDraftAndProgressWithoutDoubleCounting() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            onView(withId(R.id.input)).perform(replaceText("unfinished"), closeSoftKeyboard());
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
            assertEquals(1, savedRound().attempts());
        }
    }

    @Test public void corruptRoundShowsRecoveryAndWrongThemeTypeDoesNotCrash() throws Exception {
        context.getSharedPreferences("daily_game_v1", Context.MODE_PRIVATE).edit()
                .putString("round", "broken").putInt("theme", 7).commit();
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            onView(withId(R.id.notice)).check(matches(withText(R.string.recovery_notice)));
            assertEquals("system", new GameStorage(context).theme());
            capture("07-recovery-notice");
            guess("child");
            onView(withId(R.id.notice)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            assertEquals(1, savedRound().attempts());
        }
    }

    @Test public void rotationRestoresTheDraftAndSavedProgress() throws Exception {
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess("child");
            onView(withId(R.id.input)).perform(replaceText("unfinished"), closeSoftKeyboard());
            scenario.onActivity(a -> a.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
            scenario.onActivity(a -> assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                    a.getResources().getConfiguration().orientation));
            assertEquals(1, savedRound().attempts());
            capture("08-landscape-draft-restored");
            scenario.onActivity(a -> a.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            onView(withId(R.id.input)).check(matches(withText("unfinished")));
        }
    }

    @Test public void wonRoundCanBeCancelledOrRestartedUsingTheSamePuzzle() throws Exception {
        WordRanker words = new WordRanker(context.getAssets().open("puzzles.tsv"),
                context.getAssets().open("aliases.tsv"));
        String secret = DailyWordProvider.secret(DailyWordProvider.dateAt(Instant.now()), words.daily());
        try (ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class)) {
            guess(secret);
            assertEquals(GameRound.Status.WON, savedRound().status);
            capture("09-won-before-restart");
            menu(R.string.restart_round);
            onView(withId(android.R.id.button2)).perform(click());
            assertEquals(GameRound.Status.WON, savedRound().status);
            menu(R.string.restart_round);
            onView(withId(android.R.id.button1)).perform(click());
            assertEquals(GameRound.Status.PLAYING, savedRound().status);
            assertEquals(0, savedRound().attempts());
            guess(secret);
            assertEquals(GameRound.Status.WON, savedRound().status);
            assertEquals(1, savedRound().attempts());
        }
    }

    private void guess(String word) {
        onView(withId(R.id.input)).perform(replaceText(word), pressImeActionButton(), closeSoftKeyboard());
    }

    private void menu(int label) {
        onView(withId(R.id.input)).perform(closeSoftKeyboard());
        onView(withId(R.id.menu_button)).perform(click());
        onView(withText(label)).perform(click());
    }

    private GameRound savedRound() throws Exception {
        WordRanker words = new WordRanker(context.getAssets().open("puzzles.tsv"),
                context.getAssets().open("aliases.tsv"));
        java.time.LocalDate date = DailyWordProvider.dateAt(Instant.now());
        return new GameStorage(context).open(date,
                words.rankFor(DailyWordProvider.secret(date, words.daily())));
    }

    private void capture(String name) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = context.getExternalFilesDir("step2-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }
}
