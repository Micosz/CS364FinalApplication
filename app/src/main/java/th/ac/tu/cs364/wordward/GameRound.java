package th.ac.tu.cs364.wordward;

import java.time.LocalDate;
import java.util.*;

/** Chronological entries are the source of truth for counts and the pinned card. */
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

    GameRound(LocalDate date) { this.date = date; }

    GuessResult guess(String canonical, WordRanker.Ranking ranks) {
        if (status != Status.PLAYING) return GuessResult.CLOSED;
        if (canonical == null) return GuessResult.UNKNOWN;
        if (seen(canonical)) return GuessResult.REPEAT;
        add(new Entry(canonical, ranks.rank(canonical), false));
        return GuessResult.ADDED;
    }

    void add(Entry entry) {
        if (status != Status.PLAYING || seen(entry.word)) {
            throw new IllegalStateException("Cannot add to this round");
        }
        history.put(entry.word, entry);
        latest = entry;
        best = Math.min(best, entry.rank);
        if (entry.isHint) hints++;
        if (entry.rank == 1) status = Status.WON;
    }

    boolean seen(String word) { return history.containsKey(word); }
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
