package th.ac.tu.cs364.wordward;

import java.util.Random;

final class HintEngine {
    private final Random random;

    HintEngine(Random random) { this.random = random; }

    String next(GameRound round, WordRanker.Ranking ranks) {
        if (round.status != GameRound.Status.PLAYING || round.best() <= 2) return null;
        int best = round.best();
        int low;
        int high;
        if (round.attempts() == 0) {
            low = Math.max(2, ranks.size() / 2);
            high = Math.max(low, (ranks.size() * 3) / 4);
        } else {
            high = best - 1;
            low = Math.max(2, (int) Math.floor(best * 0.8));
        }
        if (high > ranks.size()) return null;
        return ranks.wordAt(low + random.nextInt(high - low + 1));
    }
}
