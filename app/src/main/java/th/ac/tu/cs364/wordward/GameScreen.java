package th.ac.tu.cs364.wordward;

import android.app.Activity;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.function.Consumer;

final class GameScreen {
    private final Activity activity;
    private final EditText input;

    GameScreen(Activity activity, Consumer<String> onGuess, Runnable onHint, Runnable onLanguage) {
        this.activity = activity;
        activity.setContentView(R.layout.activity_game);
        activity.findViewById(R.id.screen).setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        input = activity.findViewById(R.id.input);
        activity.findViewById(R.id.guess).setOnClickListener(v -> onGuess.accept(input.getText().toString()));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            onGuess.accept(input.getText().toString());
            return true;
        });
        activity.findViewById(R.id.hint).setOnClickListener(v -> onHint.run());
        activity.findViewById(R.id.language).setOnClickListener(v -> onLanguage.run());
    }

    private void text(int id, String value) {
        ((TextView) activity.findViewById(id)).setText(value);
    }

    void clearInput() { input.setText(""); }

    void show(GameRound round, String notice) {
        boolean playing = round.status == GameRound.Status.PLAYING;
        text(R.id.day, activity.getString(R.string.date, round.date.toString()));
        GameRound.Entry latest = round.latest();
        text(R.id.latest, latest == null ? "" : activity.getString(R.string.latest, latest.word, latest.rank));
        text(R.id.totals, activity.getString(R.string.totals, round.attempts(), round.guesses(), round.hints()));
        text(R.id.notice, playing ? notice : activity.getString(R.string.won, latest.word));
        StringBuilder rows = new StringBuilder();
        for (GameRound.Entry entry : round.sorted()) {
            String word = entry.isHint ? activity.getString(R.string.hint_row, entry.word) : entry.word;
            rows.append('#').append(entry.rank).append("   ").append(word).append('\n');
        }
        text(R.id.history, rows.toString());
        input.setEnabled(playing);
        activity.findViewById(R.id.guess).setEnabled(playing);
        activity.findViewById(R.id.hint).setEnabled(playing);
    }
}
