package th.ac.tu.cs364.wordward;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

/** Storage logic tests; the preference fake does not prove Android disk/lifecycle behavior. */
public class GameStorageTest {
    private final LocalDate day = LocalDate.of(2026, 11, 18);
    private MemoryPreferences prefs;
    private GameStorage storage;
    private WordRanker.Ranking ranks;

    @Before public void setUp() throws Exception {
        prefs = new MemoryPreferences();
        storage = new GameStorage(prefs);
        ranks = new WordRanker.Ranking(Arrays.asList("ocean", "sea", "water", "blue", "book", "pencil"));
    }

    @Test public void newInstallStartsEmptyWithDefaultSettings() {
        assertEmpty(storage.open(day, ranks));
        assertEquals(GameStorage.Recovery.NONE, storage.recovery());
        assertEquals("", storage.language());
        assertEquals("system", storage.theme());
        assertTrue(prefs.getAll().isEmpty());
    }

    @Test public void restoresChronologyCountsAndBestSeparatelyFromLatest() {
        GameRound round = new GameRound(day);
        round.guess("blue", ranks);
        round.add(new GameRound.Entry("water", 3, true));
        round.guess("pencil", ranks);
        storage.save(round, ranks);

        GameRound restored = new GameStorage(prefs).open(day, ranks);
        assertEquals(Arrays.asList("blue", "water", "pencil"), words(restored.entries()));
        assertEquals(Arrays.asList("water", "blue", "pencil"), words(restored.sorted()));
        assertEquals(3, restored.attempts());
        assertEquals(2, restored.guesses());
        assertEquals(1, restored.hints());
        assertEquals(3, restored.best());
        assertEquals("pencil", restored.latest().word);
        assertEquals(GameRound.Status.PLAYING, restored.status);
    }

    @Test public void restoresWonStateFromTheFinalAcceptedGuess() {
        GameRound round = new GameRound(day);
        round.guess("book", ranks);
        round.guess("ocean", ranks);
        storage.save(round, ranks);
        GameRound restored = storage.open(day, ranks);
        assertEquals(GameRound.Status.WON, restored.status);
        assertEquals(2, restored.attempts());
        assertEquals(GameRound.GuessResult.CLOSED, restored.guess("blue", ranks));
    }

    @Test public void writesOneVersionedRoundValueAndKeepsSettings() throws Exception {
        storage.language("th");
        storage.theme("dark");
        storage.save(new GameRound(day), ranks);
        JSONObject saved = saved();
        assertEquals(1, saved.getInt("schema"));
        assertEquals(day.toString(), saved.getString("day"));
        assertEquals("ocean", saved.getString("secret"));
        assertTrue(saved.getString("ranking").matches("[0-9a-f]{64}"));
        assertEquals(0, saved.getJSONArray("entries").length());
        assertFalse(prefs.contains("day"));
        assertFalse(prefs.contains("entries"));
        assertEquals("th", storage.language());
        assertEquals("dark", storage.theme());
    }

    @Test public void migratesValidLegacyRoundWithoutLosingLanguageOrTheme() {
        storage.language("en");
        storage.theme("light");
        legacy("[[\"blue\",4,false],[\"water\",3,true]]");
        GameRound restored = storage.open(day, ranks);
        assertEquals(2, restored.attempts());
        assertEquals(1, restored.hints());
        assertEquals(GameStorage.Recovery.MIGRATED, storage.recovery());
        assertTrue(prefs.contains("round"));
        assertFalse(prefs.contains("entries"));
        assertEquals("en", storage.language());
        assertEquals("light", storage.theme());
        assertEquals(2, new GameStorage(prefs).open(day, ranks).attempts());
    }

    @Test public void malformedJsonDiscardsTheWholeRoundButKeepsSettings() {
        storage.language("th");
        storage.theme("dark");
        legacy("[[\"blue\",4,false],broken");
        assertCorrupt();
        assertEquals("th", storage.language());
        assertEquals("dark", storage.theme());
        assertEmpty(new GameStorage(prefs).open(day, ranks));
    }

    @Test public void invalidEntryDoesNotRestoreEvenAValidPrefix() {
        String[] invalidRows = {
                "[null,2,false]", "[\"sea\",0,false]", "[\"sea\",2.5,false]",
                "[\"sea\",\"2\",false]", "[\"sea\",2,\"false\"]",
                "[\"sea\",2]", "[\"sea\",2,false,\"extra\"]",
                "[\"sea\",9999999999999,false]", "[\"outside\",2,false]",
                "[\"sea\",99,false]", "[\"sea\",3,false]", "[\"SEA\",2,false]",
                "[\"blue\",4,false]", "[\"book\",5,true]", "[\"ocean\",1,true]"
        };
        for (String invalid : invalidRows) {
            legacy("[[\"blue\",4,false]," + invalid + "]");
            assertCorrupt();
        }
    }

    @Test public void entriesAfterAWinningGuessInvalidateTheSave() {
        legacy("[[\"ocean\",1,false],[\"blue\",4,false]]");
        assertCorrupt();
    }

    @Test public void invalidModernSaveNeverFallsBackToStaleLegacyEntries() {
        legacy("[[\"blue\",4,false]]");
        prefs.edit().putString("round", "not JSON").apply();
        assertCorrupt();
        assertFalse(prefs.contains("round"));
        assertFalse(prefs.contains("day"));
        assertFalse(prefs.contains("entries"));
    }

    @Test public void rejectsWrongPreferenceTypesAndInvalidDatesWithoutCrashing() {
        prefs.edit().putInt("round", 7).apply();
        assertCorrupt();
        prefs.edit().putInt("day", 7).apply();
        assertCorrupt();
        prefs.edit().putString("day", "not-a-date").apply();
        assertCorrupt();
        prefs.edit().putString("day", day.toString()).putInt("entries", 7).apply();
        assertCorrupt();
    }

    @Test public void rejectsUnsupportedSchemaAndWrongMetadataTypes() throws Exception {
        storage.save(new GameRound(day), ranks);
        putSaved(saved().put("schema", 2));
        assertEmpty(storage.open(day, ranks));
        assertEquals(GameStorage.Recovery.UNSUPPORTED_VERSION, storage.recovery());
        String[] keys = {"schema", "day", "secret", "ranking", "entries"};
        for (String key : keys) {
            storage.save(new GameRound(day), ranks);
            putSaved(saved().put(key, true));
            assertCorrupt();
        }
    }

    @Test public void rejectsCorruptEntriesInModernFormatAsWellAsLegacy() throws Exception {
        storage.save(new GameRound(day), ranks);
        putSaved(saved().put("entries", new JSONArray("[[\"blue\",4,false],[\"blue\",4,false]]")));
        assertCorrupt();
    }

    @Test public void detectsRankingChangesEvenWhenTheSavedWordsKeepTheirRanks() throws Exception {
        GameRound round = new GameRound(day);
        round.guess("blue", ranks);
        storage.save(round, ranks);
        WordRanker.Ranking changed = new WordRanker.Ranking(
                Arrays.asList("ocean", "water", "sea", "blue", "book", "pencil"));
        assertEmpty(storage.open(day, changed));
        assertEquals(GameStorage.Recovery.DATA_CHANGED, storage.recovery());
        assertFalse(prefs.contains("round"));
    }

    @Test public void detectsChangedSecretAndVocabularySize() throws Exception {
        storage.save(new GameRound(day), ranks);
        WordRanker.Ranking changed = new WordRanker.Ranking(
                Arrays.asList("sea", "ocean", "water", "blue", "book", "pencil"));
        assertEmpty(storage.open(day, changed));
        assertEquals(GameStorage.Recovery.DATA_CHANGED, storage.recovery());
        storage.save(new GameRound(day), ranks);
        WordRanker.Ranking expanded = new WordRanker.Ranking(
                Arrays.asList("ocean", "sea", "water", "blue", "book", "pencil", "tree"));
        assertEmpty(storage.open(day, expanded));
        assertEquals(GameStorage.Recovery.DATA_CHANGED, storage.recovery());
    }

    @Test public void nextDayStartsEmptyWithoutChangingPreferencesOrThePreviousSave() {
        GameRound round = new GameRound(day);
        round.guess("blue", ranks);
        storage.language("th");
        storage.theme("dark");
        storage.save(round, ranks);
        String previous = prefs.getString("round", "");
        GameRound next = storage.open(day.plusDays(1), ranks);
        assertEmpty(next);
        assertEquals(day.plusDays(1), next.date);
        assertEquals(previous, prefs.getString("round", ""));
        assertEquals(GameStorage.Recovery.NONE, storage.recovery());
        assertEquals("th", storage.language());
        assertEquals("dark", storage.theme());
    }

    @Test public void savingAnEmptyRoundOverwritesProgressAndWinWithoutClearingSettings() {
        GameRound won = new GameRound(day);
        won.guess("blue", ranks);
        won.guess("ocean", ranks);
        storage.language("th");
        storage.theme("dark");
        storage.save(won, ranks);
        // Persistence half of restart; GameRound.reset and the dialog belong to peers.
        storage.save(new GameRound(day), ranks);
        GameStorage reopened = new GameStorage(prefs);
        GameRound restored = reopened.open(day, ranks);
        assertEmpty(restored);
        assertEquals(day, restored.date);
        assertEquals("th", reopened.language());
        assertEquals("dark", reopened.theme());
        assertEquals(GameRound.GuessResult.ADDED, restored.guess("blue", ranks));
        assertEquals(1, restored.attempts());
    }

    @Test public void rejectsInvalidRoundBeforeOverwritingTheLastGoodSave() {
        GameRound good = new GameRound(day);
        good.guess("blue", ranks);
        storage.save(good, ranks);
        String previous = prefs.getString("round", "");
        GameRound invalid = new GameRound(day);
        invalid.add(new GameRound.Entry("outside", 3, false));
        assertThrows(IllegalArgumentException.class, () -> storage.save(invalid, ranks));
        assertEquals(previous, prefs.getString("round", ""));
    }

    @Test public void compatibilityMethodsStillMigrateToRankingAwareSaves() {
        GameRound round = new GameRound(day);
        round.guess("blue", ranks);
        storage.save(round);
        assertEquals(1, storage.open(day).attempts());
        assertEquals(1, storage.open(day, ranks).attempts());
        assertEquals(GameStorage.Recovery.MIGRATED, storage.recovery());
        assertEquals(1, new GameStorage(prefs).open(day, ranks).attempts());
    }

    @Test public void settingsValidateValuesAndFallBackWhenStoredTypesAreWrong() {
        prefs.edit().putInt("language", 1).putBoolean("theme", true).apply();
        assertEquals("", storage.language());
        assertEquals("system", storage.theme());
        prefs.edit().putString("language", "unknown").putString("theme", "unknown").apply();
        assertEquals("", storage.language());
        assertEquals("system", storage.theme());
        assertThrows(IllegalArgumentException.class, () -> storage.language("fr"));
        assertThrows(IllegalArgumentException.class, () -> storage.theme("unknown"));
    }

    @Test public void restoresUsingTheActualMergedProductionDataset() throws Exception {
        WordRanker words;
        try (FileInputStream puzzles = new FileInputStream("src/main/assets/puzzles.tsv");
             FileInputStream aliases = new FileInputStream("src/main/assets/aliases.tsv")) {
            words = new WordRanker(puzzles, aliases);
        }
        for (String secret : words.daily()) {
            WordRanker.Ranking actual = words.rankFor(secret);
            GameRound round = new GameRound(day);
            round.guess(actual.wordAt(actual.size()), actual);
            round.add(new GameRound.Entry(actual.wordAt(2), 2, true));
            round.guess(secret, actual);
            storage.save(round, actual);
            GameRound restored = new GameStorage(prefs).open(day, actual);
            assertEquals(GameRound.Status.WON, restored.status);
            assertEquals(3, restored.attempts());
            assertEquals(1, restored.hints());
        }
    }

    private void legacy(String entries) {
        prefs.edit().remove("round").putString("day", day.toString()).putString("entries", entries).apply();
    }

    private JSONObject saved() throws Exception { return new JSONObject(prefs.getString("round", "")); }
    private void putSaved(JSONObject saved) { prefs.edit().putString("round", saved.toString()).apply(); }

    private void assertCorrupt() {
        assertEmpty(storage.open(day, ranks));
        assertEquals(GameStorage.Recovery.CORRUPT_SAVE, storage.recovery());
        assertFalse(prefs.contains("round"));
        assertFalse(prefs.contains("day"));
        assertFalse(prefs.contains("entries"));
    }

    private static void assertEmpty(GameRound round) {
        assertEquals(GameRound.Status.PLAYING, round.status);
        assertTrue(round.entries().isEmpty());
        assertEquals(0, round.attempts());
        assertEquals(0, round.guesses());
        assertEquals(0, round.hints());
        assertNull(round.latest());
        assertEquals(Integer.MAX_VALUE, round.best());
    }

    private static List<String> words(Collection<GameRound.Entry> entries) {
        List<String> result = new ArrayList<>();
        for (GameRound.Entry entry : entries) result.add(entry.word);
        return result;
    }

}
