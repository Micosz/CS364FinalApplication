package th.ac.tu.cs364.wordward;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Each puzzle lists the same vocabulary in order, closest first. */
final class WordRanker {
    private final Map<String, Ranking> puzzles = new LinkedHashMap<>();
    private final Map<String, String> forms = new HashMap<>();

    WordRanker(InputStream puzzlesFile, InputStream aliasesFile) throws IOException {
        try (BufferedReader reader = reader(puzzlesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t");
                if (fields.length != 2) throw new IOException("Expected secret and ordered words");
                List<String> words = Arrays.asList(fields[1].split(" +"));
                Ranking ranks = new Ranking(words);
                if (!fields[0].equals(words.get(0)) || puzzles.put(fields[0], ranks) != null) {
                    throw new IOException("Invalid secret: " + fields[0]);
                }
                if (forms.isEmpty()) {
                    for (String word : words) forms.put(word, word);
                } else if (!forms.keySet().equals(ranks.positions.keySet())) {
                    throw new IOException("Every puzzle must rank the same vocabulary");
                }
            }
        }
        if (puzzles.isEmpty()) throw new IOException("No puzzles");
        try (BufferedReader reader = reader(aliasesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] pair = line.split("\\t");
                if (pair.length != 2 || !pair[1].equals(forms.get(pair[1]))
                        || forms.putIfAbsent(pair[0], pair[1]) != null) {
                    throw new IOException("Invalid alias: " + line);
                }
            }
        }
    }

    private static BufferedReader reader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    String canonical(String input) { return forms.get(input.trim().toLowerCase(Locale.ROOT)); }
    List<String> daily() { return new ArrayList<>(puzzles.keySet()); }
    Ranking rankFor(String secret) { return puzzles.get(secret); }

    static final class Ranking {
        private final List<String> words;
        private final Map<String, Integer> positions = new HashMap<>();

        Ranking(List<String> ordered) throws IOException {
            words = new ArrayList<>(ordered);
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (!word.matches("[a-z]+") || positions.put(word, i + 1) != null) {
                    throw new IOException("Invalid ranked word: " + word);
                }
            }
        }

        int rank(String word) { return positions.get(word); }
        int size() { return words.size(); }
        String wordAt(int rank) { return words.get(rank - 1); }
    }
}
