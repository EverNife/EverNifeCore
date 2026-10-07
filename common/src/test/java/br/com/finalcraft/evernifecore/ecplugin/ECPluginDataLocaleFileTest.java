package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hardcoded language file a plugin's locale loading writes is on disk by the time the bootstrap returns - so
 * nothing is still writing into the data folder when a test, or a server, deletes or moves it.
 */
@ECoreTest
class ECPluginDataLocaleFileTest {

    private static final int PLUGINS = 20;

    @TempDir
    Path tempDir;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void teardown() {
        created.forEach(Plugins::forget);
    }

    @Test
    void theHardcodedLanguageFileExistsTheMomentLoadLocaleReturns() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        long elapsedNanos = 0;
        for (int index = 0; index < PLUGINS; index++) {
            String name = "LocaleFile" + index;
            created.add(name);
            ECPluginData data = Plugins.fakePluginData(name, tempDir.resolve(name).toFile());

            long start = System.nanoTime();
            FCLocaleManager.loadLocale(data, ClassWithALocale.class);
            elapsedNanos += System.nanoTime() - start;

            File file = new File(data.getMetaInfo().getDataFolder(), "localization/lang_EN_US.yml");
            if (!file.exists()) {
                missing.add(name);
            }
            checked++;
        }

        String meanLoad = "mean loadLocale " + (elapsedNanos / PLUGINS / 1_000_000.0) + " ms";
        System.out.println(meanLoad); //the cost a synchronous write adds to every plugin's enable
        assertEquals(PLUGINS, checked, "every plugin data was checked");
        assertTrue(missing.isEmpty(), missing.size() + " of " + PLUGINS + " plugins had no lang_EN_US.yml when loadLocale returned: "
                + missing + " (" + meanLoad + ")");
    }

    static class ClassWithALocale {
        @FCLocale(lang = LocaleType.EN_US, text = "§aA message only this test declares.")
        public static LocaleMessage A_MESSAGE;
    }
}
