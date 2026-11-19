package th.ac.tu.cs364.wordward;

import android.content.SharedPreferences;
import java.util.*;

/** Implements storage semantics only, not asynchronous disk writes or listeners. */
final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> data = new HashMap<>();
    @Override public Map<String, ?> getAll() { return new HashMap<>(data); }
    @Override public String getString(String key, String fallback) {
        return data.containsKey(key) ? (String) data.get(key) : fallback;
    }
    @Override @SuppressWarnings("unchecked") public Set<String> getStringSet(String key, Set<String> fallback) {
        return data.containsKey(key) ? new HashSet<>((Set<String>) data.get(key)) : fallback;
    }
    @Override public int getInt(String key, int fallback) { return data.containsKey(key) ? (Integer) data.get(key) : fallback; }
    @Override public long getLong(String key, long fallback) { return data.containsKey(key) ? (Long) data.get(key) : fallback; }
    @Override public float getFloat(String key, float fallback) { return data.containsKey(key) ? (Float) data.get(key) : fallback; }
    @Override public boolean getBoolean(String key, boolean fallback) { return data.containsKey(key) ? (Boolean) data.get(key) : fallback; }
    @Override public boolean contains(String key) { return data.containsKey(key); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    @Override public Editor edit() {
        return new Editor() {
            private final Map<String, Object> updates = new HashMap<>();
            private boolean clear;
            @Override public Editor putString(String key, String value) { updates.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) { updates.put(key, value == null ? null : new HashSet<>(value)); return this; }
            @Override public Editor putInt(String key, int value) { updates.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { updates.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { updates.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { updates.put(key, value); return this; }
            @Override public Editor remove(String key) { updates.put(key, null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) data.clear();
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    if (entry.getValue() == null) data.remove(entry.getKey());
                    else data.put(entry.getKey(), entry.getValue());
                }
            }
        };
    }
}
