package th.ac.tu.cs364.wordward;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private final Map<String, Ranking> puzzles = new LinkedHashMap<>();
    private final Map<String, String> forms = new HashMap<>();

    WordRanker(InputStream puzzlesFile, InputStream aliasesFile) throws IOException {
        loadPuzzles(puzzlesFile);
        loadAliases(aliasesFile);
    }

    private void loadPuzzles(InputStream puzzlesFile) throws IOException {
        try (BufferedReader reader = reader(puzzlesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t");
                if (fields.length != 2) {
                    throw new IOException("Expected secret and ordered words");
                }

                String secret = fields[0];
                List<String> orderedWords = Arrays.asList(fields[1].split(" +"));
                Ranking ranking = new Ranking(orderedWords);
                if (!secret.equals(orderedWords.get(0))) {
                    throw new IOException("Invalid secret: " + secret);
                }
                if (puzzles.containsKey(secret)) {
                    throw new IOException("Invalid secret: " + secret);
                }

                checkVocabulary(orderedWords, ranking);
                puzzles.put(secret, ranking);
            }
        }
        if (puzzles.isEmpty()) {
            throw new IOException("No puzzles");
        }
    }

    private void checkVocabulary(List<String> orderedWords, Ranking ranking) throws IOException {
        if (forms.isEmpty()) {
            // The first puzzle defines the shared vocabulary; aliases are loaded later.
            for (String word : orderedWords) {
                forms.put(word, word);
            }
        } else if (!forms.keySet().equals(ranking.positions.keySet())) {
            throw new IOException("Every puzzle must rank the same vocabulary");
        }
    }

    private void loadAliases(InputStream aliasesFile) throws IOException {
        try (BufferedReader reader = reader(aliasesFile)) {
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
    }

    private static BufferedReader reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    String canonical(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return forms.get(normalized);
    }

    List<String> daily() {
        return new ArrayList<>(puzzles.keySet());
    }

    Ranking rankFor(String secret) {
        return puzzles.get(secret);
    }

    static final class Ranking {
        private static final Pattern CANONICAL_WORD = Pattern.compile("[a-z]+");
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
