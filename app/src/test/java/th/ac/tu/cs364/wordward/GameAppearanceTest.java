package th.ac.tu.cs364.wordward;

import android.content.res.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameAppearanceTest {
    @Test public void explicitThemeOverridesSystemNightAndPreservesOtherFlags() {
        int otherFlags = Configuration.UI_MODE_TYPE_CAR;
        assertEquals(otherFlags | Configuration.UI_MODE_NIGHT_YES,
                GameAppearance.uiMode("dark", otherFlags | Configuration.UI_MODE_NIGHT_NO));
        assertEquals(otherFlags | Configuration.UI_MODE_NIGHT_NO,
                GameAppearance.uiMode("light", otherFlags | Configuration.UI_MODE_NIGHT_YES));
    }

    @Test public void systemThemeKeepsTheCurrentSystemConfiguration() {
        int original = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        assertEquals(original, GameAppearance.uiMode("system", original));
    }
}
