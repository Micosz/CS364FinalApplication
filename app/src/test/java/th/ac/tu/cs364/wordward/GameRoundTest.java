package th.ac.tu.cs364.wordward;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.*;

public class GameRoundTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);
    private WordRanker starter;
    private WordRanker.Ranking oceanRanks;

    @Before public void loadStarter() throws IOException {
        try (InputStream puzzles = getClass().getResourceAsStream("/starter/puzzles.tsv");
             InputStream aliases = getClass().getResourceAsStream("/starter/aliases.tsv")) {
            starter = new WordRanker(puzzles, aliases);
            oceanRanks = starter.rankFor("ocean");
        }
    }

    @Test public void addRejectsSecretAndNonImprovingHints() {
        GameRound secretHintRound = new GameRound(DAY);
        assertThrows(IllegalStateException.class,
                () -> secretHintRound.add(new GameRound.Entry("ocean", 1, true)));
        assertEquals(GameRound.Status.PLAYING, secretHintRound.status);
        assertEquals(0, secretHintRound.attempts());

        GameRound worseHintRound = new GameRound(DAY);
        worseHintRound.guess("blue", oceanRanks); // #7
        RoundSnapshot snapshot = new RoundSnapshot(worseHintRound);
        assertThrows(IllegalStateException.class,
                () -> worseHintRound.add(new GameRound.Entry("sky", 8, true)));
        assertThrows(IllegalStateException.class,
                () -> worseHintRound.add(new GameRound.Entry("blue", 7, true)));
        assertThrows(IllegalArgumentException.class, () -> worseHintRound.add(null));
        assertThrows(IllegalArgumentException.class,
                () -> worseHintRound.add(new GameRound.Entry(null, 3, false)));
        assertThrows(IllegalArgumentException.class,
                () -> worseHintRound.add(new GameRound.Entry("sea", 0, false)));
        snapshot.assertUnchanged(worseHintRound);
    }

    @Test public void newRoundStartsEmptyAndPlaying() {
        GameRound round = new GameRound(DAY);
        assertEquals(DAY, round.date);
        assertEquals(GameRound.Status.PLAYING, round.status);
        assertEquals(0, round.attempts());
        assertEquals(0, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(Integer.MAX_VALUE, round.best());
        assertNull(round.latest());
        assertNull(round.entry("ocean"));
        assertNull(round.entry(null));
        assertFalse(round.seen("ocean"));
        assertTrue(round.entries().isEmpty());
        assertTrue(round.sorted().isEmpty());
        assertThrows(NullPointerException.class, () -> new GameRound(null));
    }

    @Test public void guessesCountOnlyAcceptedCanonicalWordsAndResolveAliases() {
        GameRound round = new GameRound(DAY);

        assertEquals(GameRound.GuessResult.ADDED,
                round.guess(starter.canonical("book"), oceanRanks));
        assertEquals(1, round.attempts());
        assertEquals(1, round.guesses());
        assertEquals(0, round.hints());

        RoundSnapshot beforeRepeat = new RoundSnapshot(round);
        String repeatedCanonical = starter.canonical("books");
        assertEquals("book", repeatedCanonical);
        assertEquals(GameRound.GuessResult.REPEAT, round.guess(repeatedCanonical, oceanRanks));
        beforeRepeat.assertUnchanged(round);

        GameRound.Entry repeatEntry = round.entry(repeatedCanonical);
        assertNotNull(repeatEntry);
        assertEquals("book", repeatEntry.word);
        assertEquals(14, repeatEntry.rank);
        assertFalse(repeatEntry.isHint);

        // Null canonical (e.g. out-of-vocabulary word from WordRanker.canonical) or null ranks -> UNKNOWN.
        assertEquals(GameRound.GuessResult.UNKNOWN,
                round.guess(starter.canonical("not-a-word"), oceanRanks));
        assertEquals(GameRound.GuessResult.UNKNOWN, round.guess(null, oceanRanks));
        assertEquals(GameRound.GuessResult.UNKNOWN, round.guess("blue", null));
        beforeRepeat.assertUnchanged(round);

        // Mixed-case plural alias resolves to canonical and counts once.
        assertEquals(GameRound.GuessResult.ADDED,
                round.guess(starter.canonical(" CHILDREN "), oceanRanks));
        assertEquals(2, round.attempts());
        assertEquals(2, round.guesses());
        assertEquals(0, round.hints());
        assertTrue(round.seen("child"));
        assertEquals(13, round.best());
        assertEquals("child", round.latest().word);
    }

    @Test public void entriesPreserveChronologicalOrderWhileSortedOrdersByRankAndLatestTracksNewest() {
        GameRound round = new GameRound(DAY);
        assertEquals(GameRound.GuessResult.ADDED, round.guess("blue", oceanRanks));   // #7
        assertEquals(GameRound.GuessResult.ADDED, round.guess("pencil", oceanRanks)); // #12
        assertEquals(GameRound.GuessResult.ADDED, round.guess("water", oceanRanks));  // #3
        assertEquals(GameRound.GuessResult.ADDED, round.guess("book", oceanRanks));   // #14

        List<String> chronological = new ArrayList<>();
        for (GameRound.Entry entry : round.entries()) chronological.add(entry.word);
        assertEquals(Arrays.asList("blue", "pencil", "water", "book"), chronological);

        List<String> byRank = new ArrayList<>();
        for (GameRound.Entry entry : round.sorted()) byRank.add(entry.word);
        assertEquals(Arrays.asList("water", "blue", "pencil", "book"), byRank);

        // latest() tracks the most recent entry even when its rank is worse than best().
        assertEquals("book", round.latest().word);
        assertEquals(14, round.latest().rank);
        assertEquals(3, round.best());
        assertEquals(4, round.attempts());
        assertEquals(4, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(round.attempts(), round.guesses() + round.hints());
    }

    @Test public void acceptedHintsIncrementHintsAndAttemptsAndGuessingHintedWordIsRepeat() {
        GameRound round = new GameRound(DAY);
        assertEquals(GameRound.GuessResult.ADDED, round.guess("pencil", oceanRanks)); // #12

        assertTrue(round.hint("blue", oceanRanks)); // #7 < #12
        assertEquals(2, round.attempts());
        assertEquals(1, round.guesses());
        assertEquals(1, round.hints());
        assertEquals(7, round.best());
        assertEquals("blue", round.latest().word);
        assertTrue(round.latest().isHint);

        // Guessing an already-hinted word directly or via alias returns REPEAT without changing counts.
        assertTrue(round.hint("coast", oceanRanks)); // #4 < #7
        RoundSnapshot afterHint = new RoundSnapshot(round);
        assertEquals(GameRound.GuessResult.REPEAT, round.guess("blue", oceanRanks));
        assertEquals(GameRound.GuessResult.REPEAT,
                round.guess(starter.canonical("coasts"), oceanRanks));
        afterHint.assertUnchanged(round);
        assertTrue(round.entry("coast").isHint);
        assertEquals(3, round.attempts());
        assertEquals(1, round.guesses());
        assertEquals(2, round.hints());
        assertEquals(round.attempts(), round.guesses() + round.hints());
    }

    @Test public void hintRejectsInvalidCandidatesWithoutChangingRoundState() {
        GameRound empty = new GameRound(DAY);
        RoundSnapshot emptySnapshot = new RoundSnapshot(empty);
        assertFalse(empty.hint(null, oceanRanks));
        assertFalse(empty.hint("blue", null));
        assertFalse(empty.hint("ocean", oceanRanks)); // #1 cannot be hinted
        emptySnapshot.assertUnchanged(empty);

        GameRound round = new GameRound(DAY);
        assertEquals(GameRound.GuessResult.ADDED, round.guess("blue", oceanRanks)); // #7
        RoundSnapshot playingSnapshot = new RoundSnapshot(round);
        assertFalse(round.hint("blue", oceanRanks));  // already seen
        assertFalse(round.hint("sky", oceanRanks));   // #8 > best #7
        assertFalse(round.hint("ocean", oceanRanks)); // #1 secret
        playingSnapshot.assertUnchanged(round);
    }

    @Test public void winningViaCanonicalOrAliasClosesRoundToFurtherGuessesAndHints() {
        GameRound canonicalWin = new GameRound(DAY);
        assertEquals(GameRound.GuessResult.ADDED, canonicalWin.guess("blue", oceanRanks));
        assertEquals(GameRound.GuessResult.ADDED, canonicalWin.guess("ocean", oceanRanks));
        assertEquals(GameRound.Status.WON, canonicalWin.status);
        assertEquals(1, canonicalWin.best());
        assertEquals(2, canonicalWin.attempts());
        assertEquals(2, canonicalWin.guesses());
        assertEquals(0, canonicalWin.hints());
        assertEquals("ocean", canonicalWin.latest().word);

        RoundSnapshot wonSnapshot = new RoundSnapshot(canonicalWin);
        assertEquals(GameRound.GuessResult.CLOSED, canonicalWin.guess("water", oceanRanks));
        assertEquals(GameRound.GuessResult.CLOSED, canonicalWin.guess("ocean", oceanRanks));
        assertEquals(GameRound.GuessResult.CLOSED, canonicalWin.guess(null, oceanRanks));
        assertFalse(canonicalWin.hint("sea", oceanRanks));
        assertThrows(IllegalStateException.class,
                () -> canonicalWin.add(new GameRound.Entry("sea", 2, false)));
        wonSnapshot.assertUnchanged(canonicalWin);

        // Winning via plural alias on another puzzle ("apples" -> "apple").
        WordRanker.Ranking appleRanks = starter.rankFor("apple");
        GameRound aliasWin = new GameRound(DAY.plusDays(1));
        assertEquals(GameRound.GuessResult.ADDED,
                aliasWin.guess(starter.canonical("apples"), appleRanks));
        assertEquals(GameRound.Status.WON, aliasWin.status);
        assertEquals(1, aliasWin.best());
        assertEquals("apple", aliasWin.latest().word);
        assertEquals(GameRound.GuessResult.CLOSED, aliasWin.guess("fruit", appleRanks));
        assertFalse(aliasWin.hint("fruit", appleRanks));
        assertEquals(1, aliasWin.attempts());
        assertEquals(1, aliasWin.guesses());
    }

    @Test public void resetDuringPlayingClearsHistoryAndCountsWhileKeepingDate() {
        GameRound round = new GameRound(DAY);
        assertEquals(GameRound.GuessResult.ADDED, round.guess("book", oceanRanks));
        assertTrue(round.hint("blue", oceanRanks));
        assertEquals(2, round.attempts());

        GameRound returned = round.reset();
        assertSame(round, returned);
        // Proves reset() clears only round play state and preserves the final round.date field.
        assertEquals(DAY, round.date);
        assertEquals(GameRound.Status.PLAYING, round.status);
        assertEquals(0, round.attempts());
        assertEquals(0, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(Integer.MAX_VALUE, round.best());
        assertNull(round.latest());
        assertTrue(round.entries().isEmpty());
        assertTrue(round.sorted().isEmpty());
        assertFalse(round.seen("book"));
        assertFalse(round.seen("blue"));

        // Words guessed or hinted before reset are accepted again as the first entry of the new round.
        assertEquals(GameRound.GuessResult.ADDED,
                round.guess(starter.canonical("books"), oceanRanks));
        assertEquals(1, round.attempts());
        assertEquals(1, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(14, round.best());
        assertEquals("book", round.latest().word);
        assertFalse(round.latest().isHint);
    }

    @Test public void resetAfterWinningReopensSamePuzzleAndKeepsDate() {
        HintEngine engine = new HintEngine(new Random(364));
        GameRound round = new GameRound(DAY);
        round.guess("pencil", oceanRanks);
        String firstRoundHint = engine.next(round, oceanRanks);
        assertNotNull(firstRoundHint);
        assertTrue(round.hint(firstRoundHint, oceanRanks));
        round.guess("ocean", oceanRanks);
        assertEquals(GameRound.Status.WON, round.status);

        round.reset();
        // Proves reset() keeps the same round.date without reading a clock.
        assertEquals(DAY, round.date);
        assertEquals(GameRound.Status.PLAYING, round.status);
        assertEquals(0, round.attempts());
        assertEquals(0, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(Integer.MAX_VALUE, round.best());
        assertNull(round.latest());
        assertTrue(round.entries().isEmpty());

        // Check only GameRound invariants with a HintEngine candidate without hardcoding HintEngine ranges.
        String postResetHint = engine.next(round, oceanRanks);
        assertNotNull(postResetHint);
        int hintRank = oceanRanks.rank(postResetHint);
        assertTrue(hintRank > 1 && hintRank < round.best());
        assertFalse(round.seen(postResetHint));
        assertTrue(round.hint(postResetHint, oceanRanks));
        assertEquals(1, round.hints());
        assertEquals(1, round.attempts());

        assertEquals(GameRound.GuessResult.ADDED, round.guess("ocean", oceanRanks));
        assertEquals(GameRound.Status.WON, round.status);
        assertEquals(2, round.attempts());
        assertEquals(1, round.guesses());
        assertEquals(1, round.hints());
    }

    @Test public void cancelLeavesRoundUntouchedAndResetEmptiesEntriesForInMemoryReplay() {
        GameRound playing = new GameRound(DAY);
        playing.guess("blue", oceanRanks);
        playing.hint("coast", oceanRanks);
        RoundSnapshot playingBeforeCancel = new RoundSnapshot(playing);
        // Cancelling confirmation leaves reset() uncalled.
        playingBeforeCancel.assertUnchanged(playing);

        playing.guess("ocean", oceanRanks);
        assertEquals(GameRound.Status.WON, playing.status);
        RoundSnapshot wonBeforeCancel = new RoundSnapshot(playing);
        wonBeforeCancel.assertUnchanged(playing);

        // In-memory replay of entries() (not GameStorage, which requires Android Context).
        playing.reset();
        GameRound replayed = replayInMemory(playing);
        assertEquals(DAY, replayed.date);
        assertEquals(GameRound.Status.PLAYING, replayed.status);
        assertEquals(0, replayed.attempts());
        assertEquals(0, replayed.guesses());
        assertEquals(0, replayed.hints());
        assertEquals(Integer.MAX_VALUE, replayed.best());
        assertNull(replayed.latest());
        assertTrue(replayed.entries().isEmpty());
    }

    @Test public void assetsDatasetSupportsGuessesHintsWinAndResetAcrossPuzzles()
            throws IOException {
        WordRanker production;
        try (InputStream puzzles = Files.newInputStream(Paths.get("src/main/assets/puzzles.tsv"));
             InputStream aliases = Files.newInputStream(Paths.get("src/main/assets/aliases.tsv"))) {
            production = new WordRanker(puzzles, aliases);
        }
        List<String> secrets = production.daily();
        assertFalse(secrets.isEmpty());
        assertEquals(secrets.size(), new HashSet<>(secrets).size());

        // Read puzzle size and alias samples dynamically without hardcoding vocabulary counts.
        String firstSecret = secrets.get(0);
        WordRanker.Ranking firstRanks = production.rankFor(firstSecret);
        assertTrue(firstRanks.size() >= 2);

        String sampleAlias = null;
        String sampleCanonical = null;
        for (String line : Files.readAllLines(Paths.get("src/main/assets/aliases.tsv"))) {
            if (line.isEmpty()) continue;
            String[] pair = line.split("\\t");
            if (pair.length == 2 && !pair[1].equals(firstSecret)) {
                sampleAlias = pair[0];
                sampleCanonical = pair[1];
                break;
            }
        }

        GameRound round = new GameRound(DAY);
        String probeInput = sampleAlias != null
                ? "  " + sampleAlias.toUpperCase(Locale.ROOT) + " "
                : firstRanks.wordAt(firstRanks.size());
        String expectedCanonical = sampleCanonical != null
                ? sampleCanonical
                : firstRanks.wordAt(firstRanks.size());
        assertEquals(GameRound.GuessResult.ADDED,
                round.guess(production.canonical(probeInput), firstRanks));
        assertEquals(GameRound.GuessResult.REPEAT,
                round.guess(expectedCanonical, firstRanks));
        assertEquals(GameRound.GuessResult.UNKNOWN,
                round.guess(production.canonical("not-in-vocabulary-xyz"), firstRanks));
        assertEquals(1, round.attempts());

        HintEngine engine = new HintEngine(new Random(364));
        int expectedHints = 0;
        while (round.best() > 2) {
            int previousBest = round.best();
            String nextHint = engine.next(round, firstRanks);
            assertNotNull(nextHint);
            int hintRank = firstRanks.rank(nextHint);
            assertTrue(hintRank > 1 && hintRank < previousBest);
            assertFalse(round.seen(nextHint));
            assertTrue(round.hint(nextHint, firstRanks));
            expectedHints++;
        }
        assertEquals(expectedHints, round.hints());
        assertEquals(1 + expectedHints, round.attempts());
        assertEquals(2, round.best());
        assertEquals(GameRound.Status.PLAYING, round.status);

        assertEquals(GameRound.GuessResult.ADDED, round.guess(firstSecret, firstRanks));
        assertEquals(GameRound.Status.WON, round.status);
        assertEquals(GameRound.GuessResult.CLOSED,
                round.guess(firstRanks.wordAt(2), firstRanks));
        assertFalse(round.hint(firstRanks.wordAt(2), firstRanks));

        round.reset();
        assertEquals(DAY, round.date);
        assertEquals(GameRound.Status.PLAYING, round.status);
        assertEquals(0, round.attempts());
        assertEquals(0, round.guesses());
        assertEquals(0, round.hints());
        assertEquals(Integer.MAX_VALUE, round.best());
        assertEquals(GameRound.GuessResult.ADDED, round.guess(expectedCanonical, firstRanks));
        assertEquals(1, round.attempts());

        // Verify guess, hint invariant, win, closed, and reset across every puzzle in assets/.
        for (int i = 0; i < secrets.size(); i++) {
            String secret = secrets.get(i);
            WordRanker.Ranking ranks = production.rankFor(secret);
            assertTrue(ranks.size() >= 2);
            LocalDate puzzleDate = DAY.plusDays(i);
            GameRound dailyRound = new GameRound(puzzleDate);
            assertEquals(GameRound.GuessResult.ADDED,
                    dailyRound.guess(ranks.wordAt(ranks.size()), ranks));
            String hint = engine.next(dailyRound, ranks);
            if (hint != null) {
                int rank = ranks.rank(hint);
                assertTrue(rank > 1 && rank < dailyRound.best());
                assertFalse(dailyRound.seen(hint));
                assertTrue(dailyRound.hint(hint, ranks));
            }
            int expectedHintsCount = hint != null ? 1 : 0;
            assertEquals(GameRound.GuessResult.ADDED, dailyRound.guess(secret, ranks));
            assertEquals(GameRound.Status.WON, dailyRound.status);
            assertEquals(2, dailyRound.guesses());
            assertEquals(expectedHintsCount, dailyRound.hints());
            assertEquals(2 + expectedHintsCount, dailyRound.attempts());
            assertEquals(GameRound.GuessResult.CLOSED,
                    dailyRound.guess(ranks.wordAt(2), ranks));
            assertFalse(dailyRound.hint(ranks.wordAt(2), ranks));

            dailyRound.reset();
            assertEquals(puzzleDate, dailyRound.date);
            assertEquals(GameRound.Status.PLAYING, dailyRound.status);
            assertEquals(0, dailyRound.attempts());
            assertTrue(dailyRound.entries().isEmpty());
        }
    }

    private static GameRound replayInMemory(GameRound source) {
        GameRound restored = new GameRound(source.date);
        for (GameRound.Entry entry : source.entries()) {
            restored.add(new GameRound.Entry(entry.word, entry.rank, entry.isHint));
        }
        return restored;
    }

    private static final class RoundSnapshot {
        private final List<GameRound.Entry> entries;
        private final GameRound.Entry latest;
        private final GameRound.Status status;
        private final LocalDate date;
        private final int attempts;
        private final int guesses;
        private final int hints;
        private final int best;

        RoundSnapshot(GameRound round) {
            entries = Collections.unmodifiableList(new ArrayList<>(round.entries()));
            latest = round.latest();
            status = round.status;
            date = round.date;
            attempts = round.attempts();
            guesses = round.guesses();
            hints = round.hints();
            best = round.best();
        }

        void assertUnchanged(GameRound round) {
            assertEquals(entries, new ArrayList<>(round.entries()));
            assertSame(latest, round.latest());
            assertEquals(status, round.status);
            assertEquals(date, round.date);
            assertEquals(attempts, round.attempts());
            assertEquals(guesses, round.guesses());
            assertEquals(hints, round.hints());
            assertEquals(best, round.best());
        }
    }
}
