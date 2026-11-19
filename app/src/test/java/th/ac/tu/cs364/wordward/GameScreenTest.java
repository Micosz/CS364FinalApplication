package th.ac.tu.cs364.wordward;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.*;

public class GameScreenTest {

    @Test
    public void canConfirmRestartAcceptsSameBangkokDayAndRejectsMidnightRollover() {
        LocalDate nov18 = LocalDate.of(2026, 11, 18);
        LocalDate nov19 = LocalDate.of(2026, 11, 19);

        assertTrue(GameScreen.canConfirmRestart(nov18, nov18));
        assertFalse(GameScreen.canConfirmRestart(nov18, nov19));
        assertFalse(GameScreen.canConfirmRestart(null, nov18));
        assertFalse(GameScreen.canConfirmRestart(nov18, null));
    }

    @Test
    public void resolveRepeatTargetFindsCanonicalEntryForAliasRepeatAndClearsAfterReset() throws Exception {
        WordRanker words = new WordRanker(
                getClass().getResourceAsStream("/starter/puzzles.tsv"),
                getClass().getResourceAsStream("/starter/aliases.tsv"));
        WordRanker.Ranking ranks = words.rankFor("ocean");
        GameRound round = new GameRound(LocalDate.of(2026, 11, 7));

        assertEquals(GameRound.GuessResult.ADDED, round.guess(words.canonical("book"), ranks));
        int attemptsAfterFirst = round.attempts();

        String aliasCanonical = words.canonical("books");
        assertEquals("book", aliasCanonical);
        assertEquals(GameRound.GuessResult.REPEAT, round.guess(aliasCanonical, ranks));
        assertEquals(attemptsAfterFirst, round.attempts());

        GameRound.Entry target = GameScreen.resolveRepeatTarget(round, aliasCanonical);
        assertNotNull(target);
        assertEquals("book", target.word);
        assertEquals(ranks.rank("book"), target.rank);
        assertFalse(target.isHint);

        assertNull(GameScreen.resolveRepeatTarget(round, "pencil"));
        assertNull(GameScreen.resolveRepeatTarget(round, null));
        assertNull(GameScreen.resolveRepeatTarget(null, "book"));

        round.reset();
        assertNull(GameScreen.resolveRepeatTarget(round, "book"));
    }

    @Test
    public void rankTierAndBarFractionCoverThousandWordBoundariesWithoutAssumingRank1001() {
        int totalWords = 1000;

        assertEquals(GameScreen.RankTier.GOLD, GameScreen.rankTier(1, totalWords));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(2, totalWords));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(200, totalWords));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(201, totalWords));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(600, totalWords));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(601, totalWords));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(1000, totalWords));

        float f1 = GameScreen.barFraction(1, totalWords);
        float f2 = GameScreen.barFraction(2, totalWords);
        float f10 = GameScreen.barFraction(10, totalWords);
        float f100 = GameScreen.barFraction(100, totalWords);
        float f200 = GameScreen.barFraction(200, totalWords);
        float f600 = GameScreen.barFraction(600, totalWords);
        float f1000 = GameScreen.barFraction(1000, totalWords);

        assertEquals(1.0f, f1, 0.0001f);
        assertTrue(f1 > f2);
        assertTrue(f2 > f10);
        assertTrue(f10 > f100);
        assertTrue(f100 > f200);
        assertTrue(f200 > f600);
        assertTrue(f600 > f1000);
        assertTrue(f1000 >= 0.08f);
    }

    @Test
    public void rankTierScalesAcrossStarterAndProductionVocabulariesAndRejectsInvalidRank() {
        assertEquals(GameScreen.RankTier.GOLD, GameScreen.rankTier(1, 14));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(2, 14));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(3, 14));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(4, 14));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(9, 14));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(10, 14));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(14, 14));

        // Bug #2 Repro: In 5,000-word production vocabulary:
        // Green is strictly #2..#200 (e.g. paper #7), Warm Amber is #201..#1500 (e.g. cool #957),
        // and Far Coral is #1501..#5000 (e.g. #2732, #3117, #4579, #4637).
        assertEquals(GameScreen.RankTier.GOLD, GameScreen.rankTier(1, 5000));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(7, 5000));
        assertEquals(GameScreen.RankTier.PASTEL_GREEN, GameScreen.rankTier(200, 5000));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(201, 5000));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(957, 5000));
        assertEquals(GameScreen.RankTier.WARM_AMBER, GameScreen.rankTier(1500, 5000));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(1501, 5000));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(2732, 5000));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(3117, 5000));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(4579, 5000));
        assertEquals(GameScreen.RankTier.FAR_CORAL, GameScreen.rankTier(5000, 5000));

        // Progress bar at #957 must be ~40%-55% filled (not ~80% filled), leaving clear visual headroom
        float f957 = GameScreen.barFraction(957, 5000);
        assertTrue("Expected #957 bar fraction between 0.40 and 0.55 but was " + f957,
                f957 >= 0.40f && f957 <= 0.55f);
        float f100 = GameScreen.barFraction(100, 5000);
        float f7 = GameScreen.barFraction(7, 5000);
        assertTrue(f7 > f100);
        assertTrue(f100 > f957);

        assertThrows(IllegalArgumentException.class, () -> GameScreen.rankTier(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> GameScreen.barFraction(-1, 1000));

        int farGreenDay = GameScreen.tierFillColor(
                GameScreen.RankTier.PASTEL_GREEN,
                GameScreen.barFraction(190, 5000),
                false);
        int closeGreenDay = GameScreen.tierFillColor(
                GameScreen.RankTier.PASTEL_GREEN,
                GameScreen.barFraction(5, 5000),
                false);
        int goldDay = GameScreen.tierFillColor(
                GameScreen.RankTier.GOLD,
                GameScreen.barFraction(1, 5000),
                false);
        assertNotEquals(farGreenDay, closeGreenDay);
        assertNotEquals(closeGreenDay, goldDay);
        assertEquals(0xFFFFD700, goldDay);

        // Metallic Gold #1 row styling: 5-stop gradient, shimmer stops, champagne borders, and high-contrast bronze text
        assertArrayEquals(
                new int[] { 0xFFFFF4B8, 0xFFFFD700, 0xFFF0B400, 0xFFC98A00, 0xFFFFE47A },
                GameScreen.goldGradientStops());
        assertArrayEquals(
                new int[] { 0x00FFFFFF, 0x99FFFDF0, 0x00FFFFFF },
                GameScreen.goldShimmerStops());
        assertEquals(0xFFB87D00, GameScreen.goldOuterBorderColor(false));
        assertEquals(0xFFFFF3A1, GameScreen.goldOuterBorderColor(true));
        assertEquals(0xFFFFF3A1, GameScreen.goldInnerHighlightColor());
        assertEquals(0xFF241500, GameScreen.goldTextColor());

        // Rank #1 rows must omit the redundant SECRET #1 badge; only hints or duplicate targets show a pill badge
        assertFalse(GameScreen.shouldShowEntryBadge(new GameRound.Entry("mirror", 1, false), false));
        assertFalse(GameScreen.shouldShowEntryBadge(new GameRound.Entry("paper", 7, false), false));
        assertTrue(GameScreen.shouldShowEntryBadge(new GameRound.Entry("water", 12, true), false));
        assertTrue(GameScreen.shouldShowEntryBadge(new GameRound.Entry("paper", 7, false), true));
        assertFalse(GameScreen.shouldShowEntryBadge(null, false));

        // Contexto-style pinned latest guess outline border
        assertEquals(0xFF1E293B, GameScreen.pinnedLatestBorderColor(false));
        assertEquals(0xFFE2E8F0, GameScreen.pinnedLatestBorderColor(true));

        // Double-submit & empty-input guard: trim input, ignore empty strings, and ignore key-up Enter events
        assertEquals("hello", GameScreen.normalizeSubmittedGuess("  hello \n"));
        assertEquals("", GameScreen.normalizeSubmittedGuess("   "));
        assertEquals("", GameScreen.normalizeSubmittedGuess(""));
        assertEquals("", GameScreen.normalizeSubmittedGuess(null));
        assertTrue(GameScreen.shouldSubmitEditorAction(
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE, -1, 0));
        assertTrue(GameScreen.shouldSubmitEditorAction(
                android.view.inputmethod.EditorInfo.IME_ACTION_GO, -1, 0));
        assertTrue(GameScreen.shouldSubmitEditorAction(
                android.view.inputmethod.EditorInfo.IME_NULL,
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_ENTER));
        assertFalse("Enter ACTION_UP must not trigger a second submit on the cleared EditText",
                GameScreen.shouldSubmitEditorAction(
                        android.view.inputmethod.EditorInfo.IME_ACTION_DONE,
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_ENTER));
    }

    @Test
    public void themeResolutionRespectsExplicitLightDarkAndSystemFallbackAndTogglesCleanly() {
        assertTrue(GameScreen.isNightMode("dark", false));
        assertTrue(GameScreen.isNightMode("dark", true));
        assertFalse(GameScreen.isNightMode("light", false));
        assertFalse(GameScreen.isNightMode("light", true));
        assertTrue(GameScreen.isNightMode("system", true));
        assertFalse(GameScreen.isNightMode("system", false));
        assertTrue(GameScreen.isNightMode("", true));
        assertFalse(GameScreen.isNightMode(null, false));

        assertEquals("dark", GameScreen.nextTheme(false));
        assertEquals("light", GameScreen.nextTheme(true));
        assertTrue("Day mode must use dark icons (light status/nav bar appearance = true)",
                GameScreen.useLightSystemBarIcons(false));
        assertFalse("Night mode must use light icons (light status/nav bar appearance = false)",
                GameScreen.useLightSystemBarIcons(true));
        assertEquals(0x18, GameScreen.systemBarsAppearanceValue(false));
        assertEquals(0, GameScreen.systemBarsAppearanceValue(true));
    }

    @Test
    public void bilingualStringsAndDayNightColorsMatchTeamAgreements() throws Exception {
        File resDir = new File("src/main/res");
        if (!resDir.exists()) resDir = new File("app/src/main/res");

        Map<String, String> en = parseXmlEntries(new File(resDir, "values/strings.xml"), "string");
        Map<String, String> th = parseXmlEntries(new File(resDir, "values-th/strings.xml"), "string");

        assertEquals("English and Thai string keys must match 1:1", en.keySet(), th.keySet());

        assertEquals("Restart today's round?", en.get("restart_confirm_title"));
        assertEquals(
                "All guesses and hints in this round will be deleted. You will restart with the same puzzle.",
                en.get("restart_confirm_message"));
        assertEquals("Cancel", en.get("restart_confirm_cancel"));
        assertEquals("Restart", en.get("restart_confirm_positive"));

        assertEquals("เริ่มรอบวันนี้ใหม่?", th.get("restart_confirm_title"));
        assertEquals(
                "คำที่เดาและคำใบ้ทั้งหมดในรอบนี้จะถูกลบ คุณจะเริ่มใหม่ด้วยโจทย์เดิม",
                th.get("restart_confirm_message"));
        assertEquals("ยกเลิก", th.get("restart_confirm_cancel"));
        assertEquals("เริ่มใหม่", th.get("restart_confirm_positive"));

        assertTrue(en.get("rules").contains("31"));
        assertFalse(en.get("rules").contains("365"));
        assertTrue(th.get("rules").contains("31"));
        assertFalse(th.get("rules").contains("365"));

        File dayColorsFile = new File(resDir, "values/colors.xml");
        File nightColorsFile = new File(resDir, "values-night/colors.xml");
        assertTrue("Day colors.xml must exist", dayColorsFile.exists());
        assertTrue("Night colors.xml must exist", nightColorsFile.exists());

        Map<String, String> dayColors = parseXmlEntries(dayColorsFile, "color");
        Map<String, String> nightColors = parseXmlEntries(nightColorsFile, "color");
        assertEquals("Day and Night color keys must match 1:1", dayColors.keySet(), nightColors.keySet());
        assertNotEquals(dayColors.get("bg_screen"), nightColors.get("bg_screen"));
        assertNotEquals(dayColors.get("text_primary"), nightColors.get("text_primary"));
        assertTrue(dayColors.containsKey("tier_gold_fill"));
        assertTrue(dayColors.containsKey("tier_green_fill"));

        // Check Contexto flat layout in activity_game.xml, ScrollView clipToPadding, and themes.xml
        String layoutXml = new String(
                Files.readAllBytes(new File(resDir, "layout/activity_game.xml").toPath()),
                StandardCharsets.UTF_8);
        assertTrue("TopAppBar and input bar must disable baselineAligned",
                layoutXml.contains("android:baselineAligned=\"false\""));
        assertTrue("ScrollView must disable clipToPadding to prevent bottom clipping",
                layoutXml.contains("android:clipToPadding=\"false\""));
        assertTrue("Flat Contexto layout must include top_app_bar, menu_button, and input_bar",
                layoutXml.contains("@+id/top_app_bar")
                        && layoutXml.contains("@+id/menu_button")
                        && layoutXml.contains("@+id/input_bar"));
        assertFalse("Flat Contexto layout must not wrap sections in nested card containers",
                layoutXml.contains("@+id/header_card")
                        || layoutXml.contains("@+id/input_card")
                        || layoutXml.contains("@+id/latest_card")
                        || layoutXml.contains("@+id/history_card"));
        assertTrue(en.containsKey("top_bar_title") && th.containsKey("top_bar_title"));
        assertTrue(en.containsKey("top_bar_totals") && th.containsKey("top_bar_totals"));
        assertTrue(en.containsKey("won_compact") && th.containsKey("won_compact"));
        assertTrue(en.containsKey("menu_how_to_play") && th.containsKey("menu_how_to_play"));

        File dayThemeFile = new File(resDir, "values/themes.xml");
        File nightThemeFile = new File(resDir, "values-night/themes.xml");
        assertTrue("Day themes.xml must exist", dayThemeFile.exists());
        assertTrue("Night themes.xml must exist", nightThemeFile.exists());

        assertFalse("Root ScrollView must not set fitsSystemWindows=true",
                layoutXml.contains("android:fitsSystemWindows=\"true\""));
        String dayThemeXml = new String(Files.readAllBytes(dayThemeFile.toPath()), StandardCharsets.UTF_8);
        String nightThemeXml = new String(Files.readAllBytes(nightThemeFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(dayThemeXml.contains("android:windowBackground\">@color/bg_screen"));
        assertTrue(nightThemeXml.contains("android:windowBackground\">@color/bg_screen"));
    }

    @Test
    public void oceanAcceptanceFlowSeparatesLatestFromSortedAndHandlesHintsRepeatsAndPluralWin() throws Exception {
        WordRanker words = new WordRanker(
                getClass().getResourceAsStream("/starter/puzzles.tsv"),
                getClass().getResourceAsStream("/starter/aliases.tsv"));
        WordRanker.Ranking ranks = words.rankFor("ocean");
        GameRound round = new GameRound(LocalDate.of(2026, 11, 7));

        // 1. Guess "blue" then "pencil": latest is "pencil", sorted keeps "blue" before "pencil"
        assertEquals(GameRound.GuessResult.ADDED, round.guess(words.canonical("blue"), ranks));
        assertEquals(GameRound.GuessResult.ADDED, round.guess(words.canonical("pencil"), ranks));
        assertEquals("pencil", round.latest().word);
        assertEquals("blue", round.sorted().get(0).word);
        assertEquals("pencil", round.sorted().get(1).word);

        // 2. Request a hint: hint is added to the same rank-sorted list and marked isHint
        HintEngine hints = new HintEngine(new java.util.Random(364));
        String hintWord = hints.next(round, ranks);
        assertNotNull(hintWord);
        assertTrue(round.hint(hintWord, ranks));
        assertEquals(3, round.attempts());
        assertEquals(2, round.guesses());
        assertEquals(1, round.hints());

        // 3. Guess "book" then plural "books": repeat target resolves to "book" without increasing attempts
        assertEquals(GameRound.GuessResult.ADDED, round.guess(words.canonical("book"), ranks));
        assertEquals(4, round.attempts());
        assertEquals(GameRound.GuessResult.REPEAT, round.guess(words.canonical("books"), ranks));
        assertEquals(4, round.attempts());
        assertEquals("book", GameScreen.resolveRepeatTarget(round, words.canonical("books")).word);
        assertEquals("book", GameScreen.inferRepeatFromRawInput(round, "  BOOKS ").word);

        // 4. Win with plural "oceans": status is WON, #1 has GOLD tier and 100% bar fill
        assertEquals(GameRound.GuessResult.ADDED, round.guess(words.canonical("oceans"), ranks));
        assertEquals(GameRound.Status.WON, round.status);
        assertEquals("ocean", round.latest().word);
        assertEquals(1, round.latest().rank);
        assertEquals(GameScreen.RankTier.GOLD, GameScreen.rankTier(round.latest().rank, ranks.size()));
        assertEquals(1.0f, GameScreen.barFraction(round.latest().rank, ranks.size()), 0.0001f);
        assertEquals(GameRound.GuessResult.CLOSED, round.guess(words.canonical("tree"), ranks));
    }

    private static Map<String, String> parseXmlEntries(File file, String tag) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("<" + tag + "\\s+name=\"([^\"]+)\">([\\s\\S]*?)</" + tag + ">");
        Matcher matcher = pattern.matcher(content);
        Map<String, String> entries = new LinkedHashMap<>();
        while (matcher.find()) {
            entries.put(matcher.group(1), matcher.group(2).replace("\\'", "'").trim());
        }
        return entries;
    }
}
