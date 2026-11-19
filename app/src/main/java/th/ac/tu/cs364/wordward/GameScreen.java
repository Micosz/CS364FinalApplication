package th.ac.tu.cs364.wordward;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class GameScreen {
    enum RankTier { GOLD, PASTEL_GREEN, WARM_AMBER, FAR_CORAL }

    private static final String PREFS_NAME = "daily_game_v1";
    private static final String KEY_THEME = "theme";
    private static final int DEFAULT_VOCAB_SIZE = 5000;
    private static final long GOLD_SHIMMER_DURATION_MS = 2500L;

    private static final int[] GOLD_GRADIENT_STOPS = new int[] {
        0xFFFFF4B8, // Stop 1: Champagne Highlight
        0xFFFFD700, // Stop 2: Bright Pure Gold
        0xFFF0B400, // Stop 3: Rich Metallic Gold
        0xFFC98A00, // Stop 4: Warm Deep Gold Shadow
        0xFFFFE47A  // Stop 5: Secondary Edge Sheen
    };

    private static final float[] GOLD_GRADIENT_POSITIONS = new float[] {
        0.0f, 0.24f, 0.52f, 0.78f, 1.0f
    };

    private static final int[] GOLD_SHIMMER_STOPS = new int[] {
        0x00FFFFFF,
        0x44FFF4B8,
        0x99FFFDF0,
        0x44FFF4B8,
        0x00FFFFFF
    };

    private static final float[] GOLD_SHIMMER_POSITIONS = new float[] {
        0.0f, 0.30f, 0.50f, 0.70f, 1.0f
    };

    static int[] goldGradientStops() {
        return GOLD_GRADIENT_STOPS.clone();
    }

    static int[] goldShimmerStops() {
        return new int[] { 0x00FFFFFF, 0x99FFFDF0, 0x00FFFFFF };
    }

    static int goldOuterBorderColor(boolean night) {
        return night ? 0xFFFFF3A1 : 0xFFB87D00;
    }

    static int goldInnerHighlightColor() {
        return 0xFFFFF3A1;
    }

    static int goldTextColor() {
        return 0xFF241500;
    }

    static boolean shouldShowEntryBadge(GameRound.Entry entry, boolean isRepeatTarget) {
        if (entry == null) return false;
        return entry.isHint || isRepeatTarget;
    }

    static int pinnedLatestBorderColor(boolean night) {
        return night ? 0xFFE2E8F0 : 0xFF1E293B;
    }

    static String normalizeSubmittedGuess(String raw) {
        if (raw == null) return "";
        return raw.trim();
    }

    static boolean shouldSubmitEditorAction(int actionId, int keyAction, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            return keyAction == KeyEvent.ACTION_DOWN;
        }
        return actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_GO
                || actionId == EditorInfo.IME_ACTION_SEND;
    }

    private static int closeLimitFor(int cap) {
        if (cap < 100) return Math.max(2, (int) Math.floor(cap * 0.25));
        return Math.min(200, (int) Math.floor(cap * 0.20));
    }

    private static int warmLimitFor(int cap, int closeLimit) {
        if (cap < 100) return Math.max(closeLimit + 1, (int) Math.floor(cap * 0.65));
        return Math.max(closeLimit + 1, Math.min(1500, (int) Math.floor(cap * 0.60)));
    }

    static RankTier rankTier(int rank, int totalWords) {
        if (rank < 1) throw new IllegalArgumentException("Rank must be >= 1");
        if (rank == 1) return RankTier.GOLD;
        int cap = Math.max(totalWords < 2 ? DEFAULT_VOCAB_SIZE : totalWords, rank);
        int closeLimit = closeLimitFor(cap);
        int warmLimit = warmLimitFor(cap, closeLimit);
        if (rank <= closeLimit) return RankTier.PASTEL_GREEN;
        if (rank <= warmLimit) return RankTier.WARM_AMBER;
        return RankTier.FAR_CORAL;
    }

    static float barFraction(int rank, int totalWords) {
        if (rank < 1) throw new IllegalArgumentException("Rank must be >= 1");
        if (rank == 1) return 1.0f;
        int cap = Math.max(totalWords < 2 ? DEFAULT_VOCAB_SIZE : totalWords, rank);
        int closeLimit = closeLimitFor(cap);
        int warmLimit = warmLimitFor(cap, closeLimit);

        if (rank <= closeLimit) {
            double logPos = Math.log(rank) - Math.log(2.0);
            double logSpan = Math.max(0.0001, Math.log(closeLimit) - Math.log(2.0));
            float t = (float) Math.max(0.0, Math.min(1.0, logPos / logSpan));
            return 0.96f - t * (0.96f - 0.62f);
        }
        if (rank <= warmLimit) {
            float t = (float) (rank - closeLimit) / (float) Math.max(1, warmLimit - closeLimit);
            return 0.60f - Math.max(0.0f, Math.min(1.0f, t)) * (0.60f - 0.35f);
        }
        float t = (float) (rank - warmLimit) / (float) Math.max(1, cap - warmLimit);
        return 0.33f - Math.max(0.0f, Math.min(1.0f, t)) * (0.33f - 0.08f);
    }

    static boolean canConfirmRestart(LocalDate dialogDate, LocalDate currentBangkokDate) {
        return dialogDate != null && dialogDate.equals(currentBangkokDate);
    }

    static GameRound.Entry resolveRepeatTarget(GameRound round, String repeatedCanonical) {
        if (round == null || repeatedCanonical == null || repeatedCanonical.isEmpty()) return null;
        return round.entry(repeatedCanonical);
    }

    static GameRound.Entry inferRepeatFromRawInput(GameRound round, String rawInput) {
        if (round == null || rawInput == null) return null;
        String cleaned = rawInput.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) return null;
        GameRound.Entry direct = round.entry(cleaned);
        if (direct != null) return direct;
        if (cleaned.endsWith("ies") && cleaned.length() > 3) {
            GameRound.Entry yForm = round.entry(cleaned.substring(0, cleaned.length() - 3) + "y");
            if (yForm != null) return yForm;
        }
        if (cleaned.endsWith("ves") && cleaned.length() > 3) {
            String stem = cleaned.substring(0, cleaned.length() - 3);
            GameRound.Entry fForm = round.entry(stem + "f");
            if (fForm != null) return fForm;
            GameRound.Entry feForm = round.entry(stem + "fe");
            if (feForm != null) return feForm;
        }
        if (cleaned.endsWith("es") && cleaned.length() > 2) {
            GameRound.Entry esForm = round.entry(cleaned.substring(0, cleaned.length() - 2));
            if (esForm != null) return esForm;
        }
        if (cleaned.endsWith("s") && cleaned.length() > 1) {
            GameRound.Entry sForm = round.entry(cleaned.substring(0, cleaned.length() - 1));
            if (sForm != null) return sForm;
        }
        return null;
    }

    static boolean isNightMode(String themePref, boolean systemNight) {
        if ("dark".equalsIgnoreCase(themePref)) return true;
        if ("light".equalsIgnoreCase(themePref)) return false;
        return systemNight;
    }

    static String nextTheme(boolean currentlyNight) {
        return currentlyNight ? "light" : "dark";
    }

    static boolean useLightSystemBarIcons(boolean nightMode) {
        return !nightMode;
    }

    static int systemBarsAppearanceValue(boolean nightMode) {
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        return useLightSystemBarIcons(nightMode) ? mask : 0;
    }

    static int tierFillColor(RankTier tier, float fraction, boolean night) {
        Palette p = Palette.forMode(night);
        if (tier == RankTier.GOLD) return p.gold.fill;
        if (tier == RankTier.PASTEL_GREEN) {
            float clamped = Math.max(0.62f, Math.min(0.96f, fraction));
            float t = (clamped - 0.62f) / 0.34f;
            int outerGreen = night ? 0xFF1B4A37 : 0xFFC4F0D8;
            int innerGreen = night ? 0xFF24634A : 0xFF94E2B8;
            return blendColor(outerGreen, innerGreen, t);
        }
        if (tier == RankTier.WARM_AMBER) return p.warmAmber.fill;
        return p.farCoral.fill;
    }

    private static int blendColor(int fromColor, int toColor, float ratio) {
        float t = Math.max(0.0f, Math.min(1.0f, ratio));
        int a = Math.round(((fromColor >>> 24) & 0xFF) * (1 - t) + ((toColor >>> 24) & 0xFF) * t);
        int r = Math.round(((fromColor >>> 16) & 0xFF) * (1 - t) + ((toColor >>> 16) & 0xFF) * t);
        int g = Math.round(((fromColor >>> 8) & 0xFF) * (1 - t) + ((toColor >>> 8) & 0xFF) * t);
        int b = Math.round((fromColor & 0xFF) * (1 - t) + (toColor & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private final Activity activity;
    private final ScrollView scrollView;
    private final View inputBar;
    private final EditText input;
    private final Button guessButton;
    private final Button hintButton;
    private final Button menuButton;
    private final Button languageButton;
    private final Button themeButton;
    private final Button restartButton;
    private final TextView noticeView;
    private final LinearLayout latestRowHolder;
    private final View latestDivider;
    private final LinearLayout historyContainer;
    private final TextView historyEmptyView;
    private final Runnable onHintCallback;
    private final Runnable onLanguageCallback;
    private final Runnable onRestartCallback;
    private final Runnable onThemeCallback;

    private int vocabularySize = DEFAULT_VOCAB_SIZE;
    private GameRound currentRound;
    private String currentNotice = "";
    private String currentRepeatedWord;
    private String pendingGuessRaw;
    private int attemptsBeforePendingGuess = -1;
    private boolean inputClearedSinceGuess;
    private AlertDialog restartDialog;
    private AlertDialog howToPlayDialog;
    private PopupMenu activePopupMenu;

    GameScreen(Activity activity, Consumer<String> onGuess, Runnable onHint, Runnable onLanguage) {
        this(activity, onGuess, onHint, onLanguage, null, null);
    }

    @SuppressWarnings("deprecation")
    GameScreen(
            Activity activity,
            Consumer<String> onGuess,
            Runnable onHint,
            Runnable onLanguage,
            Runnable onRestart,
            Runnable onTheme) {
        this.activity = activity;
        this.onHintCallback = onHint;
        this.onLanguageCallback = onLanguage;
        this.onRestartCallback = onRestart;
        this.onThemeCallback = onTheme;

        Window window = activity.getWindow();
        if (window != null && android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }

        activity.setContentView(R.layout.activity_game);
        scrollView = activity.findViewById(R.id.screen);
        scrollView.setFitsSystemWindows(false);
        scrollView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                dismissDialogSafely();
            }
        });
        final int basePad = dp(16);
        final int topPad = dp(10);
        final int extraBottomPad = dp(28);
        final View content = activity.findViewById(R.id.content_container);
        content.setFitsSystemWindows(false);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(
                    basePad + left,
                    topPad + top,
                    basePad + right,
                    extraBottomPad + bottom);
            boolean night = resolveCurrentNightMode();
            applySystemBarsAppearance(night, Palette.forMode(night).screenBg);
            return android.os.Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets;
        });
        scrollView.requestApplyInsets();

        inputBar = activity.findViewById(R.id.input_bar);
        input = activity.findViewById(R.id.input);
        guessButton = activity.findViewById(R.id.guess);
        hintButton = activity.findViewById(R.id.hint);
        menuButton = activity.findViewById(R.id.menu_button);
        languageButton = activity.findViewById(R.id.language);
        themeButton = activity.findViewById(R.id.theme);
        restartButton = activity.findViewById(R.id.restart);
        noticeView = activity.findViewById(R.id.notice);
        latestRowHolder = activity.findViewById(R.id.latest_row_holder);
        latestDivider = activity.findViewById(R.id.latest_divider);
        historyContainer = activity.findViewById(R.id.history);
        historyEmptyView = activity.findViewById(R.id.history_empty);
        if (latestRowHolder != null) {
            latestRowHolder.setClipToPadding(false);
            latestRowHolder.setClipChildren(false);
        }
        if (historyContainer != null) {
            historyContainer.setClipToPadding(false);
            historyContainer.setClipChildren(false);
        }

        Runnable submitGuess = () -> {
            String trimmed = normalizeSubmittedGuess(
                    input.getText() != null ? input.getText().toString() : null);
            if (trimmed.isEmpty()) {
                return;
            }
            pendingGuessRaw = trimmed;
            attemptsBeforePendingGuess = currentRound != null ? currentRound.attempts() : -1;
            inputClearedSinceGuess = false;
            onGuess.accept(trimmed);
        };

        if (guessButton != null) {
            guessButton.setOnClickListener(v -> submitGuess.run());
        }
        input.setOnEditorActionListener((v, action, event) -> {
            int keyAction = event != null ? event.getAction() : -1;
            int keyCode = event != null ? event.getKeyCode() : KeyEvent.KEYCODE_UNKNOWN;
            boolean isEnterKeyEvent = event != null
                    && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER);
            if (shouldSubmitEditorAction(action, keyAction, keyCode)) {
                submitGuess.run();
                return true;
            }
            return isEnterKeyEvent;
        });

        Runnable triggerHint = () -> {
            pendingGuessRaw = null;
            attemptsBeforePendingGuess = -1;
            inputClearedSinceGuess = false;
            onHint.run();
        };

        hintButton.setOnClickListener(v -> triggerHint.run());

        if (menuButton != null) {
            menuButton.setOnClickListener(this::showOverflowMenu);
        }

        if (languageButton != null) {
            languageButton.setOnClickListener(v -> {
                dismissDialogSafely();
                onLanguage.run();
            });
        }
        if (themeButton != null) {
            themeButton.setOnClickListener(v -> toggleTheme());
        }
        if (restartButton != null) {
            restartButton.setOnClickListener(v -> showRestartConfirmationDialog());
        }

        applyThemePalette(resolveCurrentNightMode());
    }

    void setVocabularySize(int totalWords) {
        if (totalWords >= 2) {
            this.vocabularySize = totalWords;
        }
    }

    private void text(int id, String value) {
        View view = activity.findViewById(id);
        if (view instanceof TextView) {
            ((TextView) view).setText(value);
        }
    }

    void clearInput() {
        inputClearedSinceGuess = true;
        input.setText("");
    }

    void show(GameRound round, String notice) {
        boolean isIntermediateRefresh = round != null
                && pendingGuessRaw != null
                && !inputClearedSinceGuess
                && (notice == null || notice.isEmpty())
                && round.attempts() == attemptsBeforePendingGuess;
        String inferredRepeat = null;
        if (round != null
                && pendingGuessRaw != null
                && inputClearedSinceGuess
                && notice != null
                && !notice.isEmpty()
                && round.attempts() == attemptsBeforePendingGuess) {
            GameRound.Entry entry = inferRepeatFromRawInput(round, pendingGuessRaw);
            if (entry != null) {
                inferredRepeat = entry.word;
            }
        }
        if (!isIntermediateRefresh) {
            pendingGuessRaw = null;
            attemptsBeforePendingGuess = -1;
            inputClearedSinceGuess = false;
        }
        show(round, notice, inferredRepeat);
    }

    void show(GameRound round, String notice, String repeatedWord) {
        if (round == null) return;
        this.currentRound = round;
        this.currentNotice = notice == null ? "" : notice;
        this.currentRepeatedWord = repeatedWord;

        boolean night = resolveCurrentNightMode();
        applyThemePalette(night);

        boolean playing = round.status == GameRound.Status.PLAYING;
        long puzzleNumber = DailyWordProvider.number(round.date);
        text(R.id.title, activity.getString(R.string.top_bar_title, puzzleNumber));
        text(R.id.day, activity.getString(R.string.date_with_number, puzzleNumber, round.date.toString()));

        GameRound.Entry latest = round.latest();
        text(R.id.totals, activity.getString(R.string.top_bar_totals, round.guesses(), round.hints()));

        if (hintButton != null) {
            hintButton.setText(round.hints() > 0 ? "💡 " + round.hints() : "💡");
        }

        // Single Pinned Latest Guess Slot (Contexto-style thin dark outline border)
        latestRowHolder.removeAllViews();
        if (latest == null) {
            text(R.id.latest, activity.getString(R.string.latest_empty));
            latestRowHolder.setVisibility(View.GONE);
            if (latestDivider != null) {
                latestDivider.setVisibility(View.GONE);
            }
        } else {
            text(R.id.latest, activity.getString(R.string.latest, latest.word, latest.rank));
            latestRowHolder.setVisibility(View.VISIBLE);
            latestRowHolder.addView(buildEntryRow(latest, false, true, night));
            if (latestDivider != null) {
                latestDivider.setVisibility(View.VISIBLE);
            }
        }

        // Compact Status / Error / Win Notice Banner (no triple-repeated secret word)
        GameRound.Entry repeatedEntry = resolveRepeatTarget(round, repeatedWord);
        if (!playing && latest != null) {
            String wonText = activity.getString(
                    R.string.won_compact,
                    round.attempts(),
                    round.guesses(),
                    round.hints());
            noticeView.setText(wonText);
            noticeView.setVisibility(View.VISIBLE);
            styleVictoryBanner(noticeView, night);
        } else if (!this.currentNotice.isEmpty()) {
            noticeView.setText(this.currentNotice);
            noticeView.setVisibility(View.VISIBLE);
            Palette p = Palette.forMode(night);
            if (repeatedEntry != null) {
                styleBanner(noticeView, p.repeatBg, p.repeatBorder, p.repeatText);
            } else {
                styleBanner(noticeView, p.warnBg, p.warnBorder, p.warnText);
            }
        } else {
            noticeView.setText("");
            noticeView.setVisibility(View.GONE);
        }

        // Flat Sorted Ranking List
        historyContainer.removeAllViews();
        List<GameRound.Entry> sortedEntries = round.sorted();
        int effectiveVocabSize = vocabularySize;
        for (GameRound.Entry e : sortedEntries) {
            if (e.rank > effectiveVocabSize) {
                effectiveVocabSize = e.rank;
            }
        }
        this.vocabularySize = effectiveVocabSize;

        if (sortedEntries.isEmpty()) {
            historyEmptyView.setVisibility(View.VISIBLE);
            historyContainer.setVisibility(View.GONE);
        } else {
            historyEmptyView.setVisibility(View.GONE);
            historyContainer.setVisibility(View.VISIBLE);
            View targetScrollRow = null;
            for (GameRound.Entry entry : sortedEntries) {
                boolean isRepeatTarget = repeatedEntry != null && repeatedEntry.word.equals(entry.word);
                View rowView = buildEntryRow(entry, isRepeatTarget, false, night);
                rowView.setTag(entry.word);
                historyContainer.addView(rowView);
                if (isRepeatTarget) {
                    targetScrollRow = rowView;
                }
            }
            if (targetScrollRow != null) {
                final View rowToScroll = targetScrollRow;
                scrollView.post(() -> scrollToView(rowToScroll));
            }
        }

        input.setEnabled(playing);
        if (guessButton != null) {
            guessButton.setEnabled(playing);
            guessButton.setAlpha(playing ? 1.0f : 0.5f);
        }
        hintButton.setEnabled(playing);
        hintButton.setAlpha(playing ? 1.0f : 0.45f);
        if (restartButton != null) {
            restartButton.setEnabled(true);
        }
    }

    private View buildEntryRow(GameRound.Entry entry, boolean isRepeatTarget, boolean isPinnedLatest, boolean night) {
        Palette p = Palette.forMode(night);
        RankTier tier = rankTier(entry.rank, vocabularySize);
        float fraction = barFraction(entry.rank, vocabularySize);
        boolean isGold = tier == RankTier.GOLD;

        TierColors tc = p.colorsFor(tier, fraction, night);
        int borderColor;
        int borderWidthPx;
        if (isRepeatTarget) {
            borderColor = p.repeatBorder;
            borderWidthPx = dp(3);
        } else if (isGold) {
            borderColor = goldOuterBorderColor(night);
            borderWidthPx = dp(2);
        } else if (isPinnedLatest) {
            borderColor = pinnedLatestBorderColor(night);
            borderWidthPx = dp(2);
        } else {
            borderColor = tc.border;
            borderWidthPx = dp(1);
        }

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(isGold ? 8 : 6);
        if (isGold) {
            rowLp.bottomMargin = dp(4);
        }
        row.setLayoutParams(rowLp);

        if (isGold) {
            MetallicGoldDrawable goldDrawable = new MetallicGoldDrawable(
                    borderColor,
                    goldInnerHighlightColor(),
                    borderWidthPx,
                    Math.max(1, dp(1)),
                    dp(10),
                    !night);
            row.setBackground(goldDrawable);
            row.setElevation(dp(6));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                row.setOutlineSpotShadowColor(0xFFFFD700);
                row.setOutlineAmbientShadowColor(0xFFFFD700);
            }
            attachGoldShimmerAnimator(row, goldDrawable);
        } else {
            row.setBackground(new RankBarDrawable(tc.track, tc.fill, borderColor, borderWidthPx, dp(10), fraction));
        }
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));

        int rowTextColor = isGold ? goldTextColor() : tc.text;

        // Left: Canonical Word
        TextView wordView = new TextView(activity);
        wordView.setText(entry.word);
        wordView.setTextColor(rowTextColor);
        wordView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        wordView.setTypeface(Typeface.DEFAULT_BOLD);
        if (isGold) {
            wordView.setShadowLayer(dp(1), 0f, dp(1) * 0.6f, 0xAAFFF4B8);
        }
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f);
        wordLp.rightMargin = dp(8);
        wordLp.setMarginEnd(dp(8));
        wordView.setLayoutParams(wordLp);
        row.addView(wordView);

        // Middle: Status Badge (HINT or REPEATED only; Rank #1 omits redundant SECRET #1 badge)
        if (shouldShowEntryBadge(entry, isRepeatTarget)) {
            TextView badge = new TextView(activity);
            String badgeLabel;
            if (isRepeatTarget) {
                badgeLabel = activity.getString(R.string.badge_repeat);
                if (entry.isHint) {
                    badgeLabel = badgeLabel + " · " + activity.getString(R.string.badge_hint);
                }
            } else {
                badgeLabel = activity.getString(R.string.badge_hint);
            }
            badge.setText(badgeLabel);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(isRepeatTarget ? p.repeatText : tc.text);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(6));
            badgeBg.setColor(isRepeatTarget ? p.repeatBg : tc.track);
            badgeBg.setStroke(dp(1), isRepeatTarget ? p.repeatBorder : tc.border);
            badge.setBackground(badgeBg);

            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.rightMargin = dp(10);
            badgeLp.setMarginEnd(dp(10));
            badge.setLayoutParams(badgeLp);
            row.addView(badge);
        }

        // Right: Rank Number (#rank) with consistent end padding so #1 aligns vertically with #2, #3, #4
        TextView rankView = new TextView(activity);
        rankView.setText("#" + entry.rank);
        rankView.setTextColor(rowTextColor);
        rankView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rankView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        rankView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        rankView.setPadding(dp(4), 0, dp(2), 0);
        rankView.setPaddingRelative(dp(4), 0, dp(2), 0);
        if (isGold) {
            rankView.setShadowLayer(dp(1), 0f, dp(1) * 0.6f, 0xAAFFF4B8);
        }
        LinearLayout.LayoutParams rankLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rankLp.rightMargin = dp(2);
        rankLp.setMarginEnd(dp(2));
        rankView.setLayoutParams(rankLp);
        row.addView(rankView);

        return row;
    }

    private void attachGoldShimmerAnimator(View row, MetallicGoldDrawable drawable) {
        final ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(GOLD_SHIMMER_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(anim -> {
            Object value = anim.getAnimatedValue();
            if (value instanceof Float) {
                drawable.setShimmerProgress((Float) value);
            }
        });
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!animator.isStarted()) {
                    animator.start();
                    animator.setCurrentFraction(0.38f);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
            }
        });
        animator.start();
        animator.setCurrentFraction(0.38f);
    }

    private void scrollToView(View targetRow) {
        Rect rect = new Rect();
        targetRow.getDrawingRect(rect);
        scrollView.offsetDescendantRectToMyCoords(targetRow, rect);
        int scrollY = scrollView.getScrollY();
        int viewportTop = scrollY + dp(48);
        int viewportBottom = scrollY + scrollView.getHeight() - dp(48);
        if (rect.top >= viewportTop && rect.bottom <= viewportBottom) {
            return;
        }
        int targetY;
        if (rect.top < viewportTop) {
            targetY = Math.max(0, rect.top - dp(80));
        } else {
            targetY = Math.max(0, rect.bottom - scrollView.getHeight() + dp(80));
        }
        scrollView.smoothScrollTo(0, targetY);
    }

    private void showRestartConfirmationDialog() {
        dismissDialogSafely();
        final LocalDate dialogDate = currentRound != null
                ? currentRound.date
                : DailyWordProvider.dateAt(Instant.now());

        restartDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.restart_confirm_title)
                .setMessage(R.string.restart_confirm_message)
                .setNegativeButton(R.string.restart_confirm_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.restart_confirm_positive, (dialog, which) -> {
                    LocalDate nowBangkok = DailyWordProvider.dateAt(Instant.now());
                    if (!canConfirmRestart(dialogDate, nowBangkok)) {
                        if (currentRound != null) {
                            show(currentRound, activity.getString(R.string.restart_day_changed), null);
                        }
                        return;
                    }
                    clearInput();
                    if (onRestartCallback != null) {
                        onRestartCallback.run();
                    } else if (currentRound != null) {
                        currentRound.reset();
                        new GameStorage(activity).save(currentRound);
                        show(currentRound, "", null);
                    }
                })
                .create();
        restartDialog.show();
    }

    private void showOverflowMenu(View anchor) {
        boolean night = resolveCurrentNightMode();
        boolean playing = currentRound == null || currentRound.status == GameRound.Status.PLAYING;
        PopupMenu popup = new PopupMenu(activity, anchor);
        final int idHint = 1;
        final int idLanguage = 2;
        final int idTheme = 3;
        final int idRestart = 4;
        final int idHowToPlay = 5;

        if (playing) {
            popup.getMenu().add(0, idHint, 0, "💡 " + activity.getString(R.string.hint));
        }
        popup.getMenu().add(0, idLanguage, 1, activity.getString(R.string.switch_language));
        popup.getMenu().add(
                0,
                idTheme,
                2,
                activity.getString(night ? R.string.switch_theme_day : R.string.switch_theme_night));
        popup.getMenu().add(0, idRestart, 3, activity.getString(R.string.restart_round));
        popup.getMenu().add(0, idHowToPlay, 4, activity.getString(R.string.menu_how_to_play));

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == idHint) {
                if (onHintCallback != null) {
                    pendingGuessRaw = null;
                    attemptsBeforePendingGuess = -1;
                    inputClearedSinceGuess = false;
                    onHintCallback.run();
                }
                return true;
            }
            if (itemId == idLanguage) {
                dismissDialogSafely();
                if (onLanguageCallback != null) {
                    onLanguageCallback.run();
                }
                return true;
            }
            if (itemId == idTheme) {
                toggleTheme();
                return true;
            }
            if (itemId == idRestart) {
                showRestartConfirmationDialog();
                return true;
            }
            if (itemId == idHowToPlay) {
                showHowToPlayDialog();
                return true;
            }
            return false;
        });
        popup.setOnDismissListener(menu -> {
            if (activePopupMenu == menu) {
                activePopupMenu = null;
            }
        });
        activePopupMenu = popup;
        popup.show();
    }

    private void showHowToPlayDialog() {
        dismissDialogSafely();
        String dayHeader = "";
        String totalsLine = "";
        if (currentRound != null) {
            long puzzleNumber = DailyWordProvider.number(currentRound.date);
            dayHeader = activity.getString(
                    R.string.date_with_number,
                    puzzleNumber,
                    currentRound.date.toString()) + "\n\n";
            totalsLine = "\n\n" + activity.getString(
                    R.string.totals,
                    currentRound.attempts(),
                    currentRound.guesses(),
                    currentRound.hints());
        }
        String message = dayHeader + activity.getString(R.string.rules) + totalsLine;
        howToPlayDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_how_to_play)
                .setMessage(message)
                .setPositiveButton(R.string.how_to_play_close, (dialog, which) -> dialog.dismiss())
                .create();
        howToPlayDialog.show();
    }

    private void dismissDialogSafely() {
        if (activePopupMenu != null) {
            activePopupMenu.dismiss();
            activePopupMenu = null;
        }
        if (restartDialog != null && restartDialog.isShowing()) {
            restartDialog.dismiss();
        }
        restartDialog = null;
        if (howToPlayDialog != null && howToPlayDialog.isShowing()) {
            howToPlayDialog.dismiss();
        }
        howToPlayDialog = null;
    }

    private boolean resolveCurrentNightMode() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pref = prefs.getString(KEY_THEME, "system");
        int nightMask = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean systemNight = nightMask == Configuration.UI_MODE_NIGHT_YES;
        return isNightMode(pref, systemNight);
    }

    private void toggleTheme() {
        dismissDialogSafely();
        boolean currentlyNight = resolveCurrentNightMode();
        String next = nextTheme(currentlyNight);
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, next)
                .apply();
        if (onThemeCallback != null) {
            onThemeCallback.run();
        } else if (currentRound != null) {
            show(currentRound, currentNotice, currentRepeatedWord);
        } else {
            applyThemePalette(!currentlyNight);
        }
    }

    private void applyThemePalette(boolean night) {
        Palette p = Palette.forMode(night);
        scrollView.setBackgroundColor(p.screenBg);
        applySystemBarsAppearance(night, p.screenBg);

        setTextColor(R.id.title, p.brandText);
        setTextColor(R.id.day, p.secondaryText);
        setTextColor(R.id.rules, p.secondaryText);
        setTextColor(R.id.totals, p.secondaryText);
        setTextColor(R.id.latest, p.secondaryText);
        setTextColor(R.id.history_empty, p.secondaryText);

        if (latestDivider != null) {
            latestDivider.setBackgroundColor(p.cardBorder);
        }

        if (inputBar != null) {
            GradientDrawable inputBarBg = new GradientDrawable();
            inputBarBg.setColor(p.inputBg);
            inputBarBg.setCornerRadius(dp(10));
            inputBarBg.setStroke(dp(1), p.inputBorder);
            inputBar.setBackground(inputBarBg);
        }
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setTextColor(p.primaryText);
        input.setHintTextColor(p.secondaryText);

        styleButton(hintButton, p.secondaryBtnBg, p.secondaryBtnText, p.cardBorder);
        if (menuButton != null) {
            styleButton(menuButton, p.secondaryBtnBg, p.primaryText, p.cardBorder);
        }
        styleButton(guessButton, p.primaryAccent, p.onPrimaryText, p.primaryAccent);
        styleButton(languageButton, p.secondaryBtnBg, p.secondaryBtnText, p.cardBorder);
        styleButton(themeButton, p.secondaryBtnBg, p.secondaryBtnText, p.cardBorder);
        styleButton(restartButton, p.restartBtnBg, p.restartBtnText, p.restartBorder);

        if (themeButton != null) {
            themeButton.setText(night ? R.string.switch_theme_day : R.string.switch_theme_night);
        }
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarsAppearance(boolean night, int barColor) {
        Window window = activity.getWindow();
        if (window == null) return;

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        window.setBackgroundDrawable(new ColorDrawable(barColor));
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(barColor);
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (scrollView.getParent() instanceof View) {
            View parent = (View) scrollView.getParent();
            while (parent != null) {
                parent.setBackgroundColor(barColor);
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                } else {
                    break;
                }
            }
        }

        final boolean lightIcons = useLightSystemBarIcons(night);
        final int appearance = systemBarsAppearanceValue(night);
        final int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;

        View decor = window.getDecorView();
        if (decor != null) {
            decor.setBackgroundColor(barColor);
            tintSystemBarBackgroundViews(decor, barColor);
            int flags = decor.getSystemUiVisibility();
            int lightStatusFlag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            int lightNavFlag = android.os.Build.VERSION.SDK_INT >= 26
                    ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : 0;
            if (lightIcons) {
                flags |= (lightStatusFlag | lightNavFlag);
            } else {
                flags &= ~(lightStatusFlag | lightNavFlag);
            }
            decor.setSystemUiVisibility(flags);
        }

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(appearance, mask);
            }
            if (decor != null) {
                decor.post(() -> {
                    tintSystemBarBackgroundViews(decor, barColor);
                    WindowInsetsController attachedController = window.getInsetsController();
                    if (attachedController != null) {
                        attachedController.setSystemBarsAppearance(appearance, mask);
                    }
                });
            }
        }
    }

    private void tintSystemBarBackgroundViews(View decor, int barColor) {
        View statusBg = decor.findViewById(android.R.id.statusBarBackground);
        if (statusBg != null) {
            statusBg.setBackgroundColor(barColor);
        }
        View navBg = decor.findViewById(android.R.id.navigationBarBackground);
        if (navBg != null) {
            navBg.setBackgroundColor(barColor);
        }
        if (decor instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) decor;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != null && child.getId() != android.R.id.content) {
                    child.setBackgroundColor(barColor);
                }
            }
        }
    }

    private void styleCard(View card, int bgColor, int borderColor) {
        if (card == null) return;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), borderColor);
        card.setBackground(drawable);
    }

    private void styleBanner(TextView banner, int bgColor, int borderColor, int textColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(2), borderColor);
        banner.setBackground(drawable);
        banner.setTextColor(textColor);
        banner.setElevation(0f);
    }

    private void styleVictoryBanner(TextView banner, boolean night) {
        int[] bannerColors = night
                ? new int[] { 0xFF3A2A06, 0xFF2B1E04, 0xFF473308 }
                : new int[] { 0xFFFFFBEB, 0xFFFFF1B8, 0xFFFFE580 };
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                bannerColors);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(2), night ? 0xFFFFF3A1 : 0xFFB87D00);
        banner.setBackground(drawable);
        banner.setTextColor(night ? 0xFFFFF4B8 : 0xFF241500);
        banner.setElevation(dp(3));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            banner.setOutlineSpotShadowColor(0xFFFFD700);
            banner.setOutlineAmbientShadowColor(0xFFFFD700);
        }
    }

    private void styleButton(Button button, int bgColor, int textColor, int strokeColor) {
        if (button == null) return;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), strokeColor);
        button.setBackground(drawable);
        button.setTextColor(textColor);
    }

    private void setTextColor(int viewId, int color) {
        View view = activity.findViewById(viewId);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics()));
    }

    private static final class TierColors {
        final int fill;
        final int track;
        final int border;
        final int text;

        TierColors(int fill, int track, int border, int text) {
            this.fill = fill;
            this.track = track;
            this.border = border;
            this.text = text;
        }
    }

    private static final class Palette {
        private static final Palette DAY = new Palette(
                0xFFF3F8F5, 0xFFFFFFFF, 0xFFD2E3DC,
                0xFFFFFFFF, 0xFF9BBCAE,
                0xFF102A22, 0xFF48655B, 0xFF104838,
                0xFF145A43, 0xFFFFFFFF,
                0xFFE2F0EA, 0xFF145A43,
                0xFFFCEAEA, 0xFF9E2A2B, 0xFFE8B4B4,
                0xFFFFF4E0, 0xFFE6B45E, 0xFF734400,
                0xFFFFF8D6, 0xFFD4A017, 0xFF5C4300,
                0xFFDBEAFE, 0xFF2563EB, 0xFF1E3A8A,
                new TierColors(0xFFFFD700, 0xFFFFF4B8, 0xFFB87D00, 0xFF241500),
                new TierColors(0xFFA8E6C3, 0xFFFFFFFF, 0xFFDCE5E0, 0xFF0F291E),
                new TierColors(0xFFFDE0A6, 0xFFFFFFFF, 0xFFDCE5E0, 0xFF2E1B03),
                new TierColors(0xFFFACAD0, 0xFFFFFFFF, 0xFFDCE5E0, 0xFF381015));

        private static final Palette NIGHT = new Palette(
                0xFF0E1714, 0xFF172621, 0xFF2B433B,
                0xFF121F1B, 0xFF3D6155,
                0xFFECFDF5, 0xFFA6C4BB, 0xFF6EE7B7,
                0xFF34D399, 0xFF06281E,
                0xFF223830, 0xFFA7F3D0,
                0xFF3B1D20, 0xFFFCA5A5, 0xFF7F343A,
                0xFF332510, 0xFFB47B22, 0xFFFDE68A,
                0xFF382C0C, 0xFFEAB308, 0xFFFEF08A,
                0xFF1E3A5F, 0xFF60A5FA, 0xFFBFDBFE,
                new TierColors(0xFFFFD700, 0xFFFFF4B8, 0xFFFFF3A1, 0xFF241500),
                new TierColors(0xFF1F5741, 0xFF16241F, 0xFF263D35, 0xFFECFDF5),
                new TierColors(0xFF543A16, 0xFF16241F, 0xFF263D35, 0xFFFEF3C7),
                new TierColors(0xFF52222A, 0xFF16241F, 0xFF263D35, 0xFFFFE4E6));

        final int screenBg;
        final int cardBg;
        final int cardBorder;
        final int inputBg;
        final int inputBorder;
        final int primaryText;
        final int secondaryText;
        final int brandText;
        final int primaryAccent;
        final int onPrimaryText;
        final int secondaryBtnBg;
        final int secondaryBtnText;
        final int restartBtnBg;
        final int restartBtnText;
        final int restartBorder;
        final int warnBg;
        final int warnBorder;
        final int warnText;
        final int wonBg;
        final int wonBorder;
        final int wonText;
        final int repeatBg;
        final int repeatBorder;
        final int repeatText;
        final TierColors gold;
        final TierColors pastelGreen;
        final TierColors warmAmber;
        final TierColors farCoral;

        Palette(
                int screenBg, int cardBg, int cardBorder,
                int inputBg, int inputBorder,
                int primaryText, int secondaryText, int brandText,
                int primaryAccent, int onPrimaryText,
                int secondaryBtnBg, int secondaryBtnText,
                int restartBtnBg, int restartBtnText, int restartBorder,
                int warnBg, int warnBorder, int warnText,
                int wonBg, int wonBorder, int wonText,
                int repeatBg, int repeatBorder, int repeatText,
                TierColors gold, TierColors pastelGreen, TierColors warmAmber, TierColors farCoral) {
            this.screenBg = screenBg;
            this.cardBg = cardBg;
            this.cardBorder = cardBorder;
            this.inputBg = inputBg;
            this.inputBorder = inputBorder;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.brandText = brandText;
            this.primaryAccent = primaryAccent;
            this.onPrimaryText = onPrimaryText;
            this.secondaryBtnBg = secondaryBtnBg;
            this.secondaryBtnText = secondaryBtnText;
            this.restartBtnBg = restartBtnBg;
            this.restartBtnText = restartBtnText;
            this.restartBorder = restartBorder;
            this.warnBg = warnBg;
            this.warnBorder = warnBorder;
            this.warnText = warnText;
            this.wonBg = wonBg;
            this.wonBorder = wonBorder;
            this.wonText = wonText;
            this.repeatBg = repeatBg;
            this.repeatBorder = repeatBorder;
            this.repeatText = repeatText;
            this.gold = gold;
            this.pastelGreen = pastelGreen;
            this.warmAmber = warmAmber;
            this.farCoral = farCoral;
        }

        static Palette forMode(boolean night) {
            return night ? NIGHT : DAY;
        }

        TierColors colorsFor(RankTier tier, float fraction, boolean night) {
            int fill = tierFillColor(tier, fraction, night);
            switch (tier) {
                case GOLD:
                    return new TierColors(fill, gold.track, gold.border, gold.text);
                case PASTEL_GREEN:
                    return new TierColors(fill, pastelGreen.track, pastelGreen.border, pastelGreen.text);
                case WARM_AMBER:
                    return new TierColors(fill, warmAmber.track, warmAmber.border, warmAmber.text);
                case FAR_CORAL:
                default:
                    return new TierColors(fill, farCoral.track, farCoral.border, farCoral.text);
            }
        }
    }

    private static final class RankBarDrawable extends Drawable {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();
        private final RectF fillRect = new RectF();
        private final float cornerRadius;
        private final float fraction;
        private final float borderInset;

        RankBarDrawable(int trackColor, int fillColor, int borderColor, int borderWidthPx, int cornerRadiusPx, float fraction) {
            trackPaint.setStyle(Paint.Style.FILL);
            trackPaint.setColor(trackColor);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidthPx);
            borderPaint.setColor(borderColor);

            this.cornerRadius = cornerRadiusPx;
            this.fraction = Math.max(0.0f, Math.min(1.0f, fraction));
            this.borderInset = borderWidthPx / 2.0f;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            rect.set(
                    bounds.left + borderInset,
                    bounds.top + borderInset,
                    bounds.right - borderInset,
                    bounds.bottom - borderInset);

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, trackPaint);

            float fillWidth = rect.width() * fraction;
            if (fillWidth > 0) {
                int save = canvas.save();
                clipPath.reset();
                clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
                canvas.clipPath(clipPath);
                fillRect.set(rect.left, rect.top, rect.left + fillWidth, rect.bottom);
                canvas.drawRect(fillRect, fillPaint);
                canvas.restoreToCount(save);
            }

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            trackPaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            trackPaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            if (!bounds.isEmpty()) {
                outline.setRoundRect(bounds, cornerRadius);
                outline.setAlpha(0.6f);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class MetallicGoldDrawable extends Drawable {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();
        private final RectF innerRect = new RectF();
        private final RectF topSheenRect = new RectF();
        private final float cornerRadius;
        private final float outerInset;
        private final float innerInset;
        private final boolean drawInnerHighlight;
        private float shimmerProgress = 0.38f;

        MetallicGoldDrawable(
                int outerBorderColor,
                int innerHighlightColor,
                int outerBorderWidthPx,
                int innerHighlightWidthPx,
                int cornerRadiusPx,
                boolean drawInnerHighlight) {
            basePaint.setStyle(Paint.Style.FILL);
            sheenPaint.setStyle(Paint.Style.FILL);
            shimmerPaint.setStyle(Paint.Style.FILL);

            outerBorderPaint.setStyle(Paint.Style.STROKE);
            outerBorderPaint.setStrokeWidth(outerBorderWidthPx);
            outerBorderPaint.setColor(outerBorderColor);

            innerBorderPaint.setStyle(Paint.Style.STROKE);
            innerBorderPaint.setStrokeWidth(innerHighlightWidthPx);
            innerBorderPaint.setColor(innerHighlightColor);

            this.cornerRadius = cornerRadiusPx;
            this.outerInset = outerBorderWidthPx / 2.0f;
            this.innerInset = outerBorderWidthPx + (innerHighlightWidthPx / 2.0f);
            this.drawInnerHighlight = drawInnerHighlight;
        }

        void setShimmerProgress(float progress) {
            this.shimmerProgress = Math.max(0.0f, Math.min(1.0f, progress));
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            rect.set(
                    bounds.left + outerInset,
                    bounds.top + outerInset,
                    bounds.right - outerInset,
                    bounds.bottom - outerInset);

            float width = rect.width();
            float height = rect.height();
            if (width <= 0 || height <= 0) return;

            // 1. Multi-stop 45-degree diagonal metallic gold gradient
            LinearGradient goldGradient = new LinearGradient(
                    rect.left,
                    rect.top - height * 0.45f,
                    rect.right,
                    rect.bottom + height * 0.45f,
                    GOLD_GRADIENT_STOPS,
                    GOLD_GRADIENT_POSITIONS,
                    Shader.TileMode.CLAMP);
            basePaint.setShader(goldGradient);

            int save = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);

            canvas.drawRect(rect, basePaint);

            // Top metallic specular highlight along the upper bevel
            topSheenRect.set(rect.left, rect.top, rect.right, rect.top + height * 0.48f);
            LinearGradient topSheen = new LinearGradient(
                    topSheenRect.left,
                    topSheenRect.top,
                    topSheenRect.left,
                    topSheenRect.bottom,
                    0x55FFFDF4,
                    0x00FFFDF4,
                    Shader.TileMode.CLAMP);
            sheenPaint.setShader(topSheen);
            canvas.drawRect(topSheenRect, sheenPaint);

            // 2. Animated tilted glossy shimmer sweep (#00FFFFFF -> #99FFFDF0 -> #00FFFFFF)
            float bandWidth = Math.max(height * 1.6f, width * 0.34f);
            float travel = width + bandWidth * 2.2f;
            float bandStartX = (rect.left - bandWidth * 1.1f) + travel * shimmerProgress;
            float tiltOffset = height * 0.65f;
            LinearGradient shimmerGradient = new LinearGradient(
                    bandStartX,
                    rect.top,
                    bandStartX + bandWidth,
                    rect.bottom + tiltOffset,
                    GOLD_SHIMMER_STOPS,
                    GOLD_SHIMMER_POSITIONS,
                    Shader.TileMode.CLAMP);
            shimmerPaint.setShader(shimmerGradient);
            canvas.drawRect(rect, shimmerPaint);

            canvas.restoreToCount(save);

            // 3. Crisp champagne inner highlight & outer border
            if (drawInnerHighlight) {
                innerRect.set(
                        bounds.left + innerInset,
                        bounds.top + innerInset,
                        bounds.right - innerInset,
                        bounds.bottom - innerInset);
                float innerRadius = Math.max(2f, cornerRadius - outerInset);
                canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerBorderPaint);
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outerBorderPaint);
        }

        @Override
        public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            if (!bounds.isEmpty()) {
                outline.setRoundRect(bounds, cornerRadius);
                outline.setAlpha(1.0f);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            basePaint.setAlpha(alpha);
            sheenPaint.setAlpha(alpha);
            shimmerPaint.setAlpha(alpha);
            innerBorderPaint.setAlpha(alpha);
            outerBorderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            basePaint.setColorFilter(colorFilter);
            sheenPaint.setColorFilter(colorFilter);
            shimmerPaint.setColorFilter(colorFilter);
            innerBorderPaint.setColorFilter(colorFilter);
            outerBorderPaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
