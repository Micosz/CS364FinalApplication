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
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class HintEngineTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);
    private WordRanker.Ranking fixture;

    @Before public void loadFixture() throws IOException {
        try (InputStream puzzles = getClass().getResourceAsStream("/starter/puzzles.tsv");
             InputStream aliases = getClass().getResourceAsStream("/starter/aliases.tsv")) {
            fixture = new WordRanker(puzzles, aliases).rankFor("ocean");
        }
    }

    @Test public void firstHintsUseTheMiddleOfTheActualVocabulary() throws IOException {
        WordRanker production = productionWords();
        for (WordRanker.Ranking ranks : Arrays.asList(fixture, ranking(1000),
                production.rankFor("ocean"))) {
            for (int seed = 0; seed < 32; seed++) {
                GameRound round = new GameRound(DAY);
                String hint = new HintEngine(new Random(seed)).next(round, ranks);
                assertNotNull(hint);
                int rank = ranks.rank(hint);
                assertTrue(rank >= ranks.size() / 2 && rank <= ranks.size() * 3 / 4);
                assertEquals(0, round.attempts());
                assertNull(round.latest());
            }
        }
    }

    @Test public void improvesTheBestByTenToTwentyPercentWhenLatestIsWorse() throws IOException {
        WordRanker.Ranking ranks = ranking(1000);
        GameRound round = new GameRound(DAY);
        round.guess(ranks.wordAt(100), ranks);
        round.guess(ranks.wordAt(900), ranks);
        RoundSnapshot before = new RoundSnapshot(round);

        for (int seed = 0; seed < 64; seed++) {
            String hint = new HintEngine(new Random(seed)).next(round, ranks);
            int rank = ranks.rank(hint);
            assertTrue("Expected rank 80..90, got " + rank, rank >= 80 && rank <= 90);
            assertFalse(round.seen(hint));
            before.assertUnchanged(round);
        }
    }

    @Test public void eachAcceptedHintBecomesTheBasisForTheNextHint() {
        GameRound round = new GameRound(DAY);
        round.guess("blue", fixture);
        round.guess("pencil", fixture);
        HintEngine engine = new HintEngine(new Random(364));

        while (round.best() > 2) {
            int previousBest = round.best();
            String hint = engine.next(round, fixture);
            assertNotNull(hint);
            int rank = fixture.rank(hint);
            assertTrue(rank >= 2 && rank < previousBest);
            assertFalse(round.seen(hint));
            round.add(new GameRound.Entry(hint, rank, true));
        }
        assertTrue(round.hints() > 1);
        assertNull(engine.next(round, fixture));
    }

    @Test public void firstHintWidensGraduallyWhenItsMiddleRangeIsFilteredOut() throws IOException {
        WordRanker.Ranking ranks = ranking(1000);
        HintEngine engine = new HintEngine(new Random(364),
                word -> word.equals(ranks.wordAt(900)) || word.equals(ranks.wordAt(100)));
        GameRound round = new GameRound(DAY);
        RoundSnapshot before = new RoundSnapshot(round);

        // #900 enters the expanding window before #100, which would reveal much more.
        assertEquals(ranks.wordAt(900), engine.next(round, ranks));
        before.assertUnchanged(round);
    }

    @Test public void laterHintsWidenGraduallyWithoutJumpingStraightToTheClosestWord()
            throws IOException {
        WordRanker.Ranking ranks = ranking(1000);
        GameRound round = new GameRound(DAY);
        round.guess(ranks.wordAt(100), ranks);
        HintEngine engine = new HintEngine(new Random(364),
                word -> word.equals(ranks.wordAt(75)) || word.equals(ranks.wordAt(2)));

        assertEquals(ranks.wordAt(75), engine.next(round, ranks));
        assertEquals(100, round.best());
        assertEquals(1, round.attempts());
    }

    @Test public void optionalFilterCannotAllowTheSecretOrNonImprovingWords() throws IOException {
        WordRanker.Ranking ranks = ranking(1000);
        GameRound round = new GameRound(DAY);
        round.guess(ranks.wordAt(100), ranks);
        RoundSnapshot before = new RoundSnapshot(round);
        HintEngine engine = new HintEngine(new Random(364), word ->
                word.equals(ranks.wordAt(1)) || word.equals(ranks.wordAt(100))
                        || word.equals(ranks.wordAt(999)));

        assertNull(engine.next(round, ranks));
        before.assertUnchanged(round);
    }

    @Test public void noEligibleWordsLeavesTheRoundUntouched() {
        HintEngine engine = new HintEngine(new Random(364), word -> false);
        GameRound round = new GameRound(DAY);
        RoundSnapshot empty = new RoundSnapshot(round);
        assertNull(engine.next(round, fixture));
        empty.assertUnchanged(round);

        round.guess("blue", fixture);
        RoundSnapshot playing = new RoundSnapshot(round);
        assertNull(engine.next(round, fixture));
        playing.assertUnchanged(round);
    }

    @Test public void tinyVocabulariesDoNotRevealTheSecretOrExceedTheVocabulary()
            throws IOException {
        HintEngine engine = new HintEngine(new Random(364));
        assertNull(engine.next(new GameRound(DAY), ranking(0)));
        assertNull(engine.next(new GameRound(DAY), ranking(1)));
        for (int size : new int[] {2, 3}) {
            WordRanker.Ranking ranks = ranking(size);
            String hint = engine.next(new GameRound(DAY), ranks);
            assertEquals(2, ranks.rank(hint));
        }
    }

    @Test public void rankThreeCanStillReceiveRankTwoButRankTwoCannotReceiveAHint()
            throws IOException {
        WordRanker.Ranking ranks = ranking(3);
        GameRound round = new GameRound(DAY);
        round.guess(ranks.wordAt(3), ranks);
        HintEngine engine = new HintEngine(new Random(364));
        String hint = engine.next(round, ranks);
        assertEquals(2, ranks.rank(hint));
        round.add(new GameRound.Entry(hint, 2, true));

        RoundSnapshot before = new RoundSnapshot(round);
        assertNull(engine.next(round, ranks));
        before.assertUnchanged(round);
    }

    @Test public void wonRoundCannotReceiveAHint() {
        GameRound round = new GameRound(DAY);
        round.guess("ocean", fixture);
        RoundSnapshot before = new RoundSnapshot(round);

        assertNull(new HintEngine(new Random(364)).next(round, fixture));
        before.assertUnchanged(round);
    }

    @Test public void fixedSeedReplaysTheSameSequence() throws IOException {
        WordRanker.Ranking ranks = ranking(1000);
        HintEngine firstEngine = new HintEngine(new Random(364));
        HintEngine secondEngine = new HintEngine(new Random(364));
        GameRound firstRound = new GameRound(DAY);
        GameRound secondRound = new GameRound(DAY);
        while (firstRound.best() > 2) {
            String first = firstEngine.next(firstRound, ranks);
            String second = secondEngine.next(secondRound, ranks);
            assertNotNull(first);
            assertEquals(first, second);
            firstRound.add(new GameRound.Entry(first, ranks.rank(first), true));
            secondRound.add(new GameRound.Entry(second, ranks.rank(second), true));
        }
        assertNull(firstEngine.next(firstRound, ranks));
        assertNull(secondEngine.next(secondRound, ranks));
    }

    @Test public void freshRoundUsesFirstHintRulesWithTheSamePuzzleAndEngine() {
        HintEngine engine = new HintEngine(new Random(364));
        GameRound previous = new GameRound(DAY);
        previous.guess("blue", fixture);
        String previousHint = engine.next(previous, fixture);
        previous.add(new GameRound.Entry(previousHint, fixture.rank(previousHint), true));
        previous.guess("ocean", fixture);
        assertNull(engine.next(previous, fixture));

        // GameRound reset belongs to work 03; this verifies work 04 given a fresh round.
        GameRound fresh = new GameRound(previous.date);
        String hint = engine.next(fresh, fixture);
        int rank = fixture.rank(hint);
        assertTrue(rank >= fixture.size() / 2 && rank <= fixture.size() * 3 / 4);
        assertEquals("ocean", fixture.wordAt(1));
        assertEquals(0, fresh.attempts());
        assertEquals(0, fresh.hints());
        assertEquals(Integer.MAX_VALUE, fresh.best());
        assertEquals(GameRound.Status.PLAYING, fresh.status);
    }

    @Test public void all31ProductionPuzzlesHaveBoundedNonRepeatingProgressiveHints()
            throws IOException {
        WordRanker words = productionWords();
        assertEquals(31, words.daily().size());
        for (String secret : words.daily()) {
            WordRanker.Ranking ranks = words.rankFor(secret);
            assertEquals(30000, ranks.size());
            GameRound round = new GameRound(DAY);
            round.guess(ranks.wordAt(ranks.size()), ranks);
            HintEngine engine = new HintEngine(new Random(364));
            while (round.best() > 2) {
                assertTrue(round.attempts() < ranks.size());
                int previousBest = round.best();
                int previousAttempts = round.attempts();
                String hint = engine.next(round, ranks);
                assertNotNull("No hint for " + secret + " at #" + previousBest, hint);
                int rank = ranks.rank(hint);
                assertTrue(rank > 1 && rank < previousBest && rank <= ranks.size());
                assertFalse(round.seen(hint));
                assertEquals(previousAttempts, round.attempts());
                round.add(new GameRound.Entry(hint, rank, true));
            }
            assertNull(engine.next(round, ranks));
            assertEquals(GameRound.Status.PLAYING, round.status);
        }
    }

    private static WordRanker productionWords() throws IOException {
        try (InputStream puzzles = Files.newInputStream(Paths.get("src/main/assets/puzzles.tsv"));
             InputStream aliases = Files.newInputStream(Paths.get("src/main/assets/aliases.tsv"))) {
            return new WordRanker(puzzles, aliases);
        }
    }

    private static WordRanker.Ranking ranking(int size) throws IOException {
        List<String> words = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            StringBuilder word = new StringBuilder("word");
            int value = i;
            do {
                word.append((char) ('a' + value % 26));
                value /= 26;
            } while (value > 0);
            words.add(word.toString());
        }
        return new WordRanker.Ranking(words);
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
            entries = new ArrayList<>(round.entries());
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
