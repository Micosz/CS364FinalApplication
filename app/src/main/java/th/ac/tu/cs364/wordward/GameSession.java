package th.ac.tu.cs364.wordward;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Coordinates the Bangkok day, game rules and persistence without owning views. */
final class GameSession {
    static final class GuessOutcome {
        final GameRound.GuessResult result;
        final String canonical;
        GuessOutcome(GameRound.GuessResult result, String canonical) {
            this.result = result;
            this.canonical = canonical;
        }
    }

    private final WordRanker words;
    private final GameStorage storage;
    private final Clock clock;
    private GameRound round;
    private WordRanker.Ranking ranks;
    private boolean recoveryNotice;

    GameSession(WordRanker words, GameStorage storage, Clock clock) {
        this.words = Objects.requireNonNull(words);
        this.storage = Objects.requireNonNull(storage);
        this.clock = Objects.requireNonNull(clock);
    }

    boolean refresh() {
        LocalDate today = DailyWordProvider.dateAt(clock.instant());
        if (round != null && today.equals(round.date)) return false;
        ranks = words.rankFor(DailyWordProvider.secret(today, words.daily()));
        round = storage.open(today, ranks);
        GameStorage.Recovery reason = storage.recovery();
        recoveryNotice = reason == GameStorage.Recovery.CORRUPT_SAVE
                || reason == GameStorage.Recovery.DATA_CHANGED
                || reason == GameStorage.Recovery.UNSUPPORTED_VERSION;
        return true;
    }

    GameRound round() { return round; }
    int vocabularySize() { return ranks.size(); }
    boolean hasRecoveryNotice() { return recoveryNotice; }
    void restoreRecoveryNotice(boolean pending) { recoveryNotice |= pending; }

    GuessOutcome guess(String raw) {
        refresh();
        String canonical = raw == null ? null : words.canonical(raw);
        GameRound.GuessResult result = round.guess(canonical, ranks);
        if (result == GameRound.GuessResult.ADDED) {
            storage.save(round, ranks);
            recoveryNotice = false;
        }
        return new GuessOutcome(result, canonical);
    }

    boolean hint(HintEngine engine) {
        refresh();
        String candidate = engine.next(round, ranks);
        if (candidate == null || !round.hint(candidate, ranks)) return false;
        storage.save(round, ranks);
        recoveryNotice = false;
        return true;
    }

    /** A confirmation for an old date must never erase the new day's round. */
    boolean restart(LocalDate confirmedDate) {
        refresh();
        if (!round.date.equals(confirmedDate)) return false;
        round.reset();
        storage.save(round, ranks);
        recoveryNotice = false;
        return true;
    }
}
