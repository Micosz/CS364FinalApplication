package th.ac.tu.cs364.wordward;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/** Selects a hint without adding it to, or otherwise changing, the round. */
final class HintEngine {
    private final Random random;
    private final Predicate<String> allowedWord;

    /** Until common-word metadata is available, every word in the vocabulary is eligible. */
    HintEngine(Random random) { this(random, word -> true); }

    /** The optional filter must be stable and must not change the round. */
    HintEngine(Random random, Predicate<String> allowedWord) {
        this.random = Objects.requireNonNull(random);
        this.allowedWord = Objects.requireNonNull(allowedWord);
    }

    String next(GameRound round, WordRanker.Ranking ranks) {
        if (round.status != GameRound.Status.PLAYING || round.best() <= 2
                || ranks.size() < 2) return null;

        boolean first = round.attempts() == 0;
        int reference = first ? ranks.size() : round.best();
        int limit = first ? ranks.size() : Math.min(ranks.size(), round.best() - 1);
        if (limit < 2) return null;

        // Aim for the middle initially, then improve the best rank by about 10-20%.
        int low = first ? reference / 2 : (int) (reference * 8L / 10);
        int high = first ? (int) (reference * 3L / 4) : (int) (reference * 9L / 10);
        low = Math.max(2, Math.min(limit, low));
        high = Math.max(low, Math.min(limit, high));
        int step = Math.max(1, reference / 10);

        while (true) {
            String hint = choose(round, ranks, low, high);
            if (hint != null) return hint;
            if (low == 2 && high == limit) return null;

            // Widen gradually, keeping the secret and non-improving ranks out of range.
            low = Math.max(2, low - step);
            high = (int) Math.min(limit, (long) high + step);
        }
    }

    private String choose(GameRound round, WordRanker.Ranking ranks, int low, int high) {
        List<String> candidates = new ArrayList<>();
        for (int rank = low; rank <= high; rank++) {
            String word = ranks.wordAt(rank);
            if (!round.seen(word) && allowedWord.test(word)) candidates.add(word);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
}
