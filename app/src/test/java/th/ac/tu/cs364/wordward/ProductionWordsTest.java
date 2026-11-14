package th.ac.tu.cs364.wordward;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ProductionWordsTest {
    private static final Path ASSETS = Paths.get("src/main/assets");

    @Test public void ranksTheSame5000CanonicalWordsInAll31Puzzles() throws IOException {
        WordRanker words = openProduction();
        List<String> secrets = words.daily();
        assertEquals(31, secrets.size());
        assertEquals(31, new HashSet<>(secrets).size());
        assertEquals(Files.readAllLines(Paths.get("../tools/wordward/secrets.txt")), secrets);
        Set<String> expected = new HashSet<>();
        for (String secret : secrets) {
            WordRanker.Ranking ranks = words.rankFor(secret);
            assertEquals(5000, ranks.size());
            assertEquals(secret, ranks.wordAt(1));
            Set<String> found = new HashSet<>();
            for (int rank = 1; rank <= ranks.size(); rank++) {
                String word = ranks.wordAt(rank);
                assertTrue(found.add(word));
                assertEquals(word, words.canonical(word));
                assertEquals(rank, ranks.rank(word));
            }
            if (expected.isEmpty()) expected.addAll(found);
            assertEquals(expected, found);
        }
    }

    @Test public void resolvesAllAliasesAndRejectsUnknownWords() throws IOException {
        WordRanker words = openProduction();
        Set<String> forms = new HashSet<>();
        WordRanker.Ranking ranks = words.rankFor("ocean");
        for (int rank = 1; rank <= ranks.size(); rank++) forms.add(ranks.wordAt(rank));
        for (String row : Files.readAllLines(ASSETS.resolve("aliases.tsv"))) {
            String[] fields = row.split("\\t", -1);
            assertEquals(2, fields.length);
            assertTrue(fields[0].matches("[a-z]+"));
            assertTrue("Duplicate form: " + fields[0], forms.add(fields[0]));
            assertEquals(fields[1], words.canonical(fields[0]));
            assertEquals(fields[1], ranks.wordAt(ranks.rank(fields[1])));
        }
        assertEquals("apple", words.canonical("  APPLES "));
        assertEquals("child", words.canonical("CHILDREN"));
        assertNull(words.canonical(""));
        assertNull(words.canonical("not-in-vocabulary"));
        assertNull(words.canonical("qzxqzxzz"));
        assertTrue(ranks.rank("sea") < ranks.rank("pencil"));
        assertTrue(ranks.rank("blue") > 1);
    }

    @Test public void rejectsCorruptPuzzleRowsAndAliasCollisions() {
        assertThrows(IOException.class, () -> load("sea sea water\n", ""));
        assertThrows(IOException.class, () -> load("sea\tsea sea\n", ""));
        assertThrows(IOException.class, () -> load("sea\twater sea\n", ""));
        assertThrows(IOException.class, () -> load("sea\tsea water\nwater\twater\n", ""));
        assertThrows(IOException.class, () -> load("sea\tsea water\nsea\tsea water\n", ""));
        assertThrows(IOException.class, () -> load("sea\tsea water\n", "sea\twater\n"));
        assertThrows(IOException.class, () -> load("sea\tsea water\n", "seas\tunknown\n"));
    }

    private static WordRanker openProduction() throws IOException {
        try (InputStream puzzles = Files.newInputStream(ASSETS.resolve("puzzles.tsv"));
             InputStream aliases = Files.newInputStream(ASSETS.resolve("aliases.tsv"))) {
            return new WordRanker(puzzles, aliases);
        }
    }

    private static WordRanker load(String puzzles, String aliases) throws IOException {
        return new WordRanker(new ByteArrayInputStream(puzzles.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(aliases.getBytes(StandardCharsets.UTF_8)));
    }
}
