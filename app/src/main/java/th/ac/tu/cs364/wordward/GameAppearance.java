package th.ac.tu.cs364.wordward;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Applies saved locale and theme before layouts and native dialogs are inflated. */
final class GameAppearance {
    private GameAppearance() { }

    static int uiMode(String theme, int systemUiMode) {
        if ("system".equals(theme)) return systemUiMode;
        int night = "dark".equals(theme)
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return (systemUiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
    }

    static Context wrap(Context base) {
        GameStorage settings = new GameStorage(base);
        String theme = settings.theme();
        // Normalize old/wrong types before GameScreen reads the same preference key.
        settings.theme(theme);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = uiMode(theme, config.uiMode);
        String language = settings.language();
        if (!language.isEmpty()) config.setLocale(Locale.forLanguageTag(language));
        return base.createConfigurationContext(config);
    }
}
