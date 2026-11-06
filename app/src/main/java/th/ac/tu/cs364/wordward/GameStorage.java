package th.ac.tu.cs364.wordward;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.time.LocalDate;

final class GameStorage {
    private final SharedPreferences prefs;

    GameStorage(Context context) {
        prefs = context.getSharedPreferences("daily_game_v1", Context.MODE_PRIVATE);
    }

    String language() { return prefs.getString("language", ""); }
    void language(String code) { prefs.edit().putString("language", code).apply(); }

    GameRound open(LocalDate day) {
        GameRound round = new GameRound(day);
        if (!day.toString().equals(prefs.getString("day", ""))) return round;
        try {
            JSONArray rows = new JSONArray(prefs.getString("entries", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                round.add(new GameRound.Entry(row.getString(0), row.getInt(1), row.getBoolean(2)));
            }
        } catch (JSONException error) {
            throw new IllegalStateException("Invalid saved round", error);
        }
        return round;
    }

    void save(GameRound round) {
        JSONArray rows = new JSONArray();
        for (GameRound.Entry entry : round.entries()) {
            rows.put(new JSONArray().put(entry.word).put(entry.rank).put(entry.isHint));
        }
        prefs.edit().putString("day", round.date.toString())
                .putString("entries", rows.toString()).apply();
    }
}
