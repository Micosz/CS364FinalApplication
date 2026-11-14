package th.ac.tu.cs364.wordward;

import java.time.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class StarterTest {
    private WordRanker words;
    private WordRanker.Ranking ranks;
    private final LocalDate first = LocalDate.of(2026, 11, 7);

    @Before public void load() throws Exception {
        words = new WordRanker(getClass().getResourceAsStream("/starter/puzzles.tsv"),
                getClass().getResourceAsStream("/starter/aliases.tsv"));
        ranks = words.rankFor("ocean");
    }

    @Test public void sameVocabularyHasDifferentDailyRanks() {
        assertEquals("child", words.canonical(" CHILDREN "));
        assertEquals(7, ranks.rank("blue"));
        assertEquals(9, words.rankFor("apple").rank("blue"));
        assertNull(words.canonical("unknown-word"));
    }

    @Test public void duplicatesAndUnknownDoNotAddAttempts() {
        GameRound round = new GameRound(first);
        round.guess(words.canonical("pencil"), ranks);
        assertEquals(GameRound.GuessResult.REPEAT, round.guess(words.canonical("pencils"), ranks));
        assertEquals(GameRound.GuessResult.UNKNOWN, round.guess(words.canonical("?"), ranks));
        round.guess("blue", ranks);
        assertEquals("blue", round.sorted().get(0).word);
        assertEquals(2, round.attempts());
    }

    @Test public void pluralWinsAndClosesRound() {
        GameRound round = new GameRound(first);
        round.guess(words.canonical("apples"), words.rankFor("apple"));
        assertEquals(GameRound.Status.WON, round.status);
        assertEquals(GameRound.GuessResult.CLOSED, round.guess("blue", ranks));
        assertEquals(1, round.guesses());
    }

    @Test public void hintsImproveOnBestEvenIfLatestIsWorse() {
        GameRound round = new GameRound(first);
        round.guess("blue", ranks);
        round.guess("pencil", ranks);
        HintEngine engine = new HintEngine(new Random(364));
        while (round.best() > 2) {
            String hint = engine.next(round, ranks);
            int rank = ranks.rank(hint);
            assertTrue(rank < round.best() && rank > 1 && !round.seen(hint));
            round.add(new GameRound.Entry(hint, rank, true));
        }
        assertNull(engine.next(round, ranks));
        assertEquals(round.attempts(), round.hints() + 2);
    }

    @Test public void firstHintNeverRevealsSecret() {
        for (int seed = 0; seed < 20; seed++) {
            String word = new HintEngine(new Random(seed)).next(new GameRound(first), ranks);
            assertTrue(ranks.rank(word) > 1);
        }
    }

    @Test public void bangkokMidnightSwitchesPuzzleAndEventuallyRepeats() {
        Instant midnight = Instant.parse("2026-11-07T17:00:00Z");
        assertEquals(first, DailyWordProvider.dateAt(midnight.minusSeconds(1)));
        assertEquals(first.plusDays(1), DailyWordProvider.dateAt(midnight));
        assertEquals("ocean", DailyWordProvider.secret(first, words.daily()));
        assertEquals("apple", DailyWordProvider.secret(first.plusDays(1), words.daily()));
        assertEquals("ocean", DailyWordProvider.secret(first.plusDays(2), words.daily()));
    }
}
