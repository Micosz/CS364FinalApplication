package th.ac.tu.cs364.wordward;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Each puzzle lists the same vocabulary in order, closest first. */
final class WordRanker {
    private static final Pattern SPACES = Pattern.compile(" +");
    private static final Pattern CANONICAL_WORD = Pattern.compile("[a-z]+");
    private final Map<String, String> puzzleRows = new LinkedHashMap<>();
    private final Map<String, String> forms = new HashMap<>();

    private final int vocabularySize;
    // GameSession holds the active ranking. Reuse it while any caller still holds it.
    private final Map<String, WeakReference<Ranking>> rankings = new HashMap<>();

    WordRanker(InputStream puzzlesFile, InputStream aliasesFile) throws IOException {
        try (BufferedReader puzzles = reader(puzzlesFile);
             BufferedReader aliases = reader(aliasesFile)) {
            loadPuzzles(puzzles);
            vocabularySize = forms.size();
            loadAliases(aliases);
        }
    }

    private void loadPuzzles(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab + 1 == line.length() || line.indexOf('\t', tab + 1) >= 0) {
                throw new IOException("Expected secret and ordered words");
            }
            String secret = line.substring(0, tab);
            String row = line.substring(tab + 1);
            int firstSpace = row.indexOf(' ');
            if (!secret.equals(firstSpace < 0 ? row : row.substring(0, firstSpace))) {
                throw new IOException("Invalid secret: " + secret);
            }
            if (puzzleRows.isEmpty()) {
                // canonical() must work before a puzzle is selected.
                Matcher matcher = CANONICAL_WORD.matcher("");
                for (String word : SPACES.split(row)) {
                    if (!matcher.reset(word).matches() || forms.put(word, word) != null) {
                        throw new IOException("Invalid or duplicate canonical word: " + word);
                    }
                }
            }
            if (!forms.containsKey(secret) || puzzleRows.put(secret, row) != null) {
                throw new IOException("Unknown or duplicate secret: " + secret);
            }
        }
        if (puzzleRows.isEmpty()) {
            throw new IOException("No puzzles");
        }
    }

    private void loadAliases(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\\t");
            if (fields.length != 2) {
                throw new IOException("Invalid alias: " + line);
            }

            String alias = fields[0];
            String canonicalWord = fields[1];
            if (!canonicalWord.equals(forms.get(canonicalWord))) {
                throw new IOException("Invalid alias: " + line);
            }
            if (forms.containsKey(alias)) {
                throw new IOException("Invalid alias: " + line);
            }
            forms.put(alias, canonicalWord);
        }
    }

    private static BufferedReader reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    String canonical(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return forms.get(normalized);
    }

    List<String> daily() {
        return new ArrayList<>(puzzleRows.keySet());
    }

    synchronized Ranking rankFor(String secret) {
        String row = puzzleRows.get(secret);
        if (row == null) return null;
        WeakReference<Ranking> reference = rankings.get(secret);
        Ranking cached = reference == null ? null : reference.get();
        if (cached != null) return cached;

        try {
            Ranking ranking = new Ranking(Arrays.asList(SPACES.split(row)));
            if (ranking.size() != vocabularySize) {
                throw new IOException("Unexpected vocabulary size: " + ranking.size());
            }
            for (String word : ranking.words) {
                if (!word.equals(forms.get(word))) {
                    throw new IOException("Unknown canonical word: " + word);
                }
            }
            rankings.put(secret, new WeakReference<>(ranking));
            return ranking;
        } catch (IOException error) {
            throw new IllegalStateException("Invalid bundled ranking for " + secret, error);
        }
    }

    static final class Ranking {
        private final List<String> words;
        private final Map<String, Integer> positions = new HashMap<>();

        Ranking(List<String> ordered) throws IOException {
            words = new ArrayList<>(ordered);
            Matcher matcher = CANONICAL_WORD.matcher("");
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (!matcher.reset(word).matches()) {
                    throw new IOException("Invalid ranked word: " + word);
                }

                // Players see ranks starting at 1; the list starts at index 0.
                int rank = i + 1;
                if (positions.putIfAbsent(word, rank) != null) {
                    throw new IOException("Invalid ranked word: " + word);
                }
            }
        }

        int rank(String word) { return positions.get(word); }
        int size() { return words.size(); }
        String wordAt(int rank) { return words.get(rank - 1); }
    }
}
