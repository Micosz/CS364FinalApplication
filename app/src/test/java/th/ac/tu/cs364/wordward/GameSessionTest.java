package th.ac.tu.cs364.wordward;

import java.time.*;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameSessionTest {
    private MemoryPreferences prefs;
    private GameStorage storage;
    private WordRanker words;
    private MutableClock clock;
    private GameSession session;

    @Before public void setUp() throws Exception {
        prefs = new MemoryPreferences();
        storage = new GameStorage(prefs);
        words = new WordRanker(getClass().getResourceAsStream("/starter/puzzles.tsv"),
                getClass().getResourceAsStream("/starter/aliases.tsv"));
        clock = new MutableClock("2026-11-07T10:00:00Z");
        session = create();
    }

    @Test public void newSessionUsesBangkokDayAndActualVocabularySize() {
        assertTrue(session.refresh());
        assertEquals(LocalDate.of(2026, 11, 7), session.round().date);
        assertEquals(words.rankFor("ocean").size(), session.vocabularySize());
        assertFalse(session.refresh());
    }

    @Test public void acceptedGuessIsPersistedBeforeReopening() {
        assertEquals(GameRound.GuessResult.ADDED, session.guess(" BLUE ").result);
        GameSession reopened = create();
        reopened.refresh();
        assertEquals("blue", reopened.round().latest().word);
        assertEquals(1, reopened.round().attempts());
    }

    @Test public void repeatReturnsCanonicalForIrregularAliasWithoutWriting() {
        session.guess("child");
        String saved = prefs.getString("round", "");
        GameSession.GuessOutcome result = session.guess(" CHILDREN ");
        assertEquals(GameRound.GuessResult.REPEAT, result.result);
        assertEquals("child", result.canonical);
        assertEquals(1, session.round().attempts());
        assertEquals(saved, prefs.getString("round", ""));
    }

    @Test public void unknownAndBlankDoNotWriteOrAddAttempts() {
        session.refresh();
        assertEquals(GameRound.GuessResult.UNKNOWN, session.guess("not-in-vocab").result);
        assertEquals(GameRound.GuessResult.UNKNOWN, session.guess("  ").result);
        assertEquals(GameRound.GuessResult.UNKNOWN, session.guess(null).result);
        assertEquals(0, session.round().attempts());
        assertFalse(prefs.contains("round"));
    }

    @Test public void hintIsAcceptedThroughRulesAndPersistedOnce() {
        assertTrue(session.hint(new HintEngine(new Random(364))));
        GameSession reopened = create();
        reopened.refresh();
        assertEquals(1, reopened.round().attempts());
        assertEquals(1, reopened.round().hints());
        assertTrue(reopened.round().latest().isHint);
        assertTrue(reopened.round().latest().rank > 1);
    }

    @Test public void unsuccessfulHintNeverOverwritesSave() {
        session.guess("sea");
        String saved = prefs.getString("round", "");
        assertFalse(session.hint(new HintEngine(new Random(364))));
        assertEquals(saved, prefs.getString("round", ""));
    }

    @Test public void winSurvivesReopenAndFurtherActionsDoNotChangeSave() {
        session.guess("oceans");
        String saved = prefs.getString("round", "");
        GameSession reopened = create();
        reopened.refresh();
        assertEquals(GameRound.Status.WON, reopened.round().status);
        assertEquals(GameRound.GuessResult.CLOSED, reopened.guess("blue").result);
        assertFalse(reopened.hint(new HintEngine(new Random())));
        assertEquals(saved, prefs.getString("round", ""));
    }

    @Test public void restartClearsPlayingAndWonRoundsAndKeepsSettings() {
        storage.language("th");
        storage.theme("dark");
        session.guess("blue");
        LocalDate date = session.round().date;
        assertTrue(session.restart(date));
        assertEquals(0, session.round().attempts());
        session.guess("ocean");
        assertEquals(GameRound.Status.WON, session.round().status);
        assertTrue(session.restart(date));
        GameSession reopened = create();
        reopened.refresh();
        assertEquals(GameRound.Status.PLAYING, reopened.round().status);
        assertEquals(0, reopened.round().attempts());
        assertEquals("th", storage.language());
        assertEquals("dark", storage.theme());
        assertEquals(GameRound.GuessResult.ADDED, reopened.guess("blue").result);
    }

    @Test public void cancellingByNotConfirmingLeavesPersistedRoundUntouched() {
        session.guess("blue");
        String saved = prefs.getString("round", "");
        session.refresh();
        assertEquals(saved, prefs.getString("round", ""));
        assertEquals(1, session.round().attempts());
    }

    @Test public void staleRestartConfirmationDoesNotEraseNewDaysProgress() {
        session.guess("blue");
        LocalDate oldDate = session.round().date;
        clock.now = Instant.parse("2026-11-07T17:00:00Z");
        session.guess("pencil");
        String newSave = prefs.getString("round", "");
        assertFalse(session.restart(oldDate));
        assertEquals(1, session.round().attempts());
        assertEquals(newSave, prefs.getString("round", ""));
    }

    @Test public void midnightBetweenConfirmationAndRestartStartsNewDayWithoutDeletingIt() {
        session.guess("blue");
        LocalDate oldDate = session.round().date;
        clock.now = Instant.parse("2026-11-07T17:00:00Z");
        assertFalse(session.restart(oldDate));
        assertEquals(oldDate.plusDays(1), session.round().date);
        assertEquals(0, session.round().attempts());
    }

    @Test public void guessAndHintRecheckTheDayWithoutWaitingForActivityTimer() {
        session.guess("blue");
        clock.now = Instant.parse("2026-11-07T17:00:00Z");
        assertTrue(session.hint(new HintEngine(new Random(364))));
        assertEquals(LocalDate.of(2026, 11, 8), session.round().date);
        assertEquals(1, session.round().attempts());
        assertEquals(1, session.round().hints());
    }

    @Test public void corruptSaveNoticeSurvivesRefreshUntilSuccessfulAction() {
        prefs.edit().putString("round", "broken").apply();
        session.refresh();
        assertTrue(session.hasRecoveryNotice());
        assertFalse(session.refresh());
        session.guess("not-in-vocab");
        assertTrue(session.hasRecoveryNotice());
        session.guess("blue");
        assertFalse(session.hasRecoveryNotice());
    }

    @Test public void validLegacyMigrationDoesNotShowDataLossWarning() {
        prefs.edit().putString("day", "2026-11-07")
                .putString("entries", "[[\"blue\",7,false]]").apply();
        session.refresh();
        assertEquals(1, session.round().attempts());
        assertFalse(session.hasRecoveryNotice());
    }

    @Test public void pendingRecoveryCanBeRestoredAcrossRecreationAndClearedByRestart() {
        session.refresh();
        session.restoreRecoveryNotice(true);
        session.restoreRecoveryNotice(false);
        assertTrue(session.hasRecoveryNotice());
        assertTrue(session.restart(session.round().date));
        assertFalse(session.hasRecoveryNotice());
    }

    private GameSession create() { return new GameSession(words, storage, clock); }

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(String value) { now = Instant.parse(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
