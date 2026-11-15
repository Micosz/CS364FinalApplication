package th.ac.tu.cs364.wordward;

import java.time.LocalDate;
import java.util.*;

/** Chronological entries are the source of truth for counts, round status, and the pinned card. */
final class GameRound {
    enum Status { PLAYING, WON }
    enum GuessResult { ADDED, REPEAT, UNKNOWN, CLOSED }
    static final class Entry {
        final String word;
        final int rank;
        final boolean isHint;
        Entry(String word, int rank, boolean isHint) {
            this.word = word;
            this.rank = rank;
            this.isHint = isHint;
        }
    }

    final LocalDate date;
    Status status = Status.PLAYING;
    private final LinkedHashMap<String, Entry> history = new LinkedHashMap<>();
    private Entry latest;
    private int hints;
    private int best = Integer.MAX_VALUE;

    GameRound(LocalDate date) { this.date = Objects.requireNonNull(date, "date"); }

    /**
     * Evaluates and records a canonical guess when the round is open.
     * Caller must ensure {@code canonical} comes from the same vocabulary as {@code ranks} (or is {@code null}).
     */
    GuessResult guess(String canonical, WordRanker.Ranking ranks) {
        if (status != Status.PLAYING) return GuessResult.CLOSED;
        if (canonical == null || ranks == null) return GuessResult.UNKNOWN;
        if (seen(canonical)) return GuessResult.REPEAT;
        add(new Entry(canonical, ranks.rank(canonical), false));
        return GuessResult.ADDED;
    }

    /**
     * Accepts a hint only when the round is open, unseen, not #1, and strictly better than {@code best}.
     * Caller must ensure {@code canonical} comes from the same vocabulary as {@code ranks} (or is {@code null}).
     */
    boolean hint(String canonical, WordRanker.Ranking ranks) {
        if (status != Status.PLAYING || canonical == null || ranks == null || seen(canonical)) {
            return false;
        }
        int rank = ranks.rank(canonical);
        if (rank <= 1 || rank >= best) return false;
        add(new Entry(canonical, rank, true));
        return true;
    }

    /** Adds or restores one chronological entry while enforcing round and hint invariants. */
    void add(Entry entry) {
        if (entry == null || entry.word == null || entry.rank < 1) {
            throw new IllegalArgumentException("Invalid entry");
        }
        if (status != Status.PLAYING || seen(entry.word)) {
            throw new IllegalStateException("Cannot add to this round");
        }
        if (entry.isHint && (entry.rank == 1 || entry.rank >= best)) {
            throw new IllegalStateException("Invalid hint entry");
        }
        history.put(entry.word, entry);
        latest = entry;
        best = Math.min(best, entry.rank);
        if (entry.isHint) hints++;
        if (entry.rank == 1) status = Status.WON;
    }

    /** Clears guesses, hints, counts, and win state while keeping {@link #date} unchanged. */
    GameRound reset() {
        history.clear();
        latest = null;
        hints = 0;
        best = Integer.MAX_VALUE;
        status = Status.PLAYING;
        return this;
    }

    boolean seen(String word) { return history.containsKey(word); }
    Entry entry(String word) { return word == null ? null : history.get(word); }
    int attempts() { return history.size(); }
    int hints() { return hints; }
    int guesses() { return attempts() - hints; }
    int best() { return best; }
    Entry latest() { return latest; }
    Collection<Entry> entries() { return Collections.unmodifiableCollection(history.values()); }
    List<Entry> sorted() {
        List<Entry> rows = new ArrayList<>(history.values());
        rows.sort(Comparator.comparingInt(e -> e.rank));
        return rows;
    }
}
