package th.ac.tu.cs364.wordward;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Persists one daily round independently from language and theme preferences. */
final class GameStorage {
    enum Recovery { NONE, MIGRATED, CORRUPT_SAVE, DATA_CHANGED, UNSUPPORTED_VERSION }

    private static final String ROUND = "round";
    private static final int SCHEMA = 1;
    private final SharedPreferences prefs;
    private final Map<WordRanker.Ranking, String> fingerprints = new IdentityHashMap<>();
    private Recovery recovery = Recovery.NONE;

    GameStorage(Context context) {
        this(context.getSharedPreferences("daily_game_v1", Context.MODE_PRIVATE));
    }

    // Allows local tests to use an in-memory preference store without an Activity.
    GameStorage(SharedPreferences prefs) { this.prefs = Objects.requireNonNull(prefs); }

    String language() {
        String code = setting("language", "");
        return validLanguage(code) ? code : "";
    }

    void language(String code) {
        if (!validLanguage(code)) throw new IllegalArgumentException("Unsupported language");
        prefs.edit().putString("language", code).apply();
    }

    // The UI owner can use this preference when the theme switch is connected.
    String theme() {
        String code = setting("theme", "system");
        return validTheme(code) ? code : "system";
    }

    void theme(String code) {
        if (!validTheme(code)) throw new IllegalArgumentException("Unsupported theme");
        prefs.edit().putString("theme", code).apply();
    }

    Recovery recovery() { return recovery; }

    /** Compatibility entry point; the app uses the overload with today's ranking. */
    GameRound open(LocalDate day) {
        return open(day, null);
    }

    GameRound open(LocalDate day, WordRanker.Ranking ranks) {
        Objects.requireNonNull(day, "day");
        recovery = Recovery.NONE;
        try {
            if (prefs.contains(ROUND)) {
                JSONObject saved = new JSONObject(string(prefs.getString(ROUND, "")));
                if (integer(saved.get("schema")) != SCHEMA) {
                    return recover(day, Recovery.UNSUPPORTED_VERSION);
                }
                LocalDate savedDay = LocalDate.parse(string(saved.get("day")));
                if (!day.equals(savedDay)) return new GameRound(day);
                String fingerprint = string(saved.get("ranking"));
                String secret = string(saved.get("secret"));
                if (fingerprint.isEmpty() != secret.isEmpty()) {
                    throw new IllegalArgumentException("Incomplete puzzle identity");
                }
                if (ranks != null && !fingerprint.isEmpty()
                        && (!fingerprint.equals(fingerprint(ranks)) || !secret.equals(ranks.wordAt(1)))) {
                    return recover(day, Recovery.DATA_CHANGED);
                }
                GameRound restored = restore(day, saved.getJSONArray("entries"), ranks);
                if (ranks != null && fingerprint.isEmpty()) {
                    save(restored, ranks);
                    recovery = Recovery.MIGRATED;
                }
                return restored;
            }

            // Legacy saves stored separate day/entries strings and no puzzle identity.
            String savedDay = string(prefs.getString("day", ""));
            if (savedDay.isEmpty()) return new GameRound(day);
            if (!day.equals(LocalDate.parse(savedDay))) return new GameRound(day);
            JSONArray rows = new JSONArray(string(prefs.getString("entries", "[]")));
            GameRound restored = restore(day, rows, ranks);
            save(restored, ranks);
            recovery = Recovery.MIGRATED;
            return restored;
        } catch (JSONException | DateTimeException | IllegalArgumentException
                 | IllegalStateException | ClassCastException error) {
            return recover(day, Recovery.CORRUPT_SAVE);
        }
    }

    /** Compatibility entry point; supply ranks to bind the save to the current puzzle. */
    void save(GameRound round) { save(round, null); }

    void save(GameRound round, WordRanker.Ranking ranks) {
        Objects.requireNonNull(round, "round");
        JSONArray rows = new JSONArray();
        for (GameRound.Entry entry : round.entries()) {
            rows.put(new JSONArray().put(entry.word).put(entry.rank).put(entry.isHint));
        }
        try {
            // Validate before overwriting a good save, including on the older GameRound.
            restore(round.date, rows, ranks);
            JSONObject saved = new JSONObject().put("schema", SCHEMA)
                    .put("day", round.date.toString())
                    .put("secret", ranks == null ? "" : ranks.wordAt(1))
                    .put("ranking", ranks == null ? "" : fingerprint(ranks))
                    .put("entries", rows);
            // Round data is one value; preferences such as language/theme are never cleared.
            prefs.edit().putString(ROUND, saved.toString()).remove("day").remove("entries").apply();
        } catch (JSONException error) {
            throw new IllegalArgumentException("Cannot encode round", error);
        }
    }

    private GameRound restore(LocalDate day, JSONArray rows, WordRanker.Ranking ranks) throws JSONException {
        GameRound restored = new GameRound(day);
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i);
            if (row.length() != 3) throw new IllegalArgumentException("Invalid entry length");
            String word = string(row.get(0));
            int rank = integer(row.get(1));
            Object hintValue = row.get(2);
            if (!word.matches("[a-z]+") || rank < 1 || !(hintValue instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid entry values");
            }
            boolean isHint = (Boolean) hintValue;
            if (ranks != null && (rank > ranks.size() || !word.equals(ranks.wordAt(rank)))) {
                throw new IllegalArgumentException("Entry does not match current ranking");
            }
            if (restored.status != GameRound.Status.PLAYING || restored.seen(word)
                    || (isHint && (rank == 1 || rank >= restored.best()))) {
                throw new IllegalStateException("Invalid chronological history");
            }
            restored.add(new GameRound.Entry(word, rank, isHint));
        }
        return restored;
    }

    private GameRound recover(LocalDate day, Recovery reason) {
        prefs.edit().remove(ROUND).remove("day").remove("entries").apply();
        recovery = reason;
        return new GameRound(day);
    }

    private String fingerprint(WordRanker.Ranking ranks) {
        String cached = fingerprints.get(ranks);
        if (cached != null) return cached;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int rank = 1; rank <= ranks.size(); rank++) {
                digest.update(ranks.wordAt(rank).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            String result = hex.toString();
            fingerprints.put(ranks, result);
            return result;
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-256 is required by the platform", error);
        }
    }

    private static String string(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected string");
        return (String) value;
    }

    private static int integer(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException("Expected integer");
        }
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer out of range");
        }
        return (int) number;
    }

    private String setting(String key, String fallback) {
        try {
            String value = prefs.getString(key, fallback);
            return value == null ? fallback : value;
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    private static boolean validLanguage(String code) {
        return "".equals(code) || "en".equals(code) || "th".equals(code);
    }

    private static boolean validTheme(String code) {
        return "system".equals(code) || "light".equals(code) || "dark".equals(code);
    }
}
