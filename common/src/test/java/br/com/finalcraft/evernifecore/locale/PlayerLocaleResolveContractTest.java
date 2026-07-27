package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the per-player locale resolve (C16/C17). With the setting off (the default), a message
 * resolves exactly like it does today - the plugin's default for every recipient, and no locale
 * section is even registered. With it on, a player who picked a language sees their translation,
 * while a player without a preference keeps the plugin's default.
 */
@ECoreTest
class PlayerLocaleResolveContractTest {


    @TempDir
    Path tempDir;

    private boolean originalPerPlayerLocale;
    private String registeredPluginName;

    @org.junit.jupiter.api.BeforeEach
    void rememberSetting() {
        originalPerPlayerLocale = ECSettings.PER_PLAYER_LOCALE;
    }

    @AfterEach
    void teardown() {
        //A static flag on a shared test JVM: restore it so no later test class observes it flipped.
        ECSettings.PER_PLAYER_LOCALE = originalPerPlayerLocale;
        PlayerDataWorld.tearDown();
        if (registeredPluginName != null) {
            ECPluginManager.removePluginData(registeredPluginName);
            registeredPluginName = null;
        }
    }

    // C16 - the OFF path: nothing registered, and every recipient still gets the plugin default.
    @Test
    void settingOffRegistersNoSectionAndResolvesToDefaultForEveryone() throws IOException {
        ECSettings.PER_PLAYER_LOCALE = false;
        PlayerController.initialize(Storages.h2("f_locale_off").writeTo(tempDir));

        assertFalse(PlayerController.getConfiguredPDSections().containsKey(LocalePDSection.class),
                "with PER_PLAYER_LOCALE off, no LocalePDSection may be registered");

        LocaleMessageImp message = twoLanguageMessage("LocaleOffPlugin", "en_greeting");
        FancyText expected = message.getDefaultFancyText();

        TestFPlayerSender player = new TestFPlayerSender("Steve", UUID.randomUUID());
        TestCommandSender console = new TestCommandSender("CONSOLE");

        assertSame(expected, message.getFancyText(player),
                "off: a player must resolve to the plugin default, unchanged");
        assertSame(expected, message.getFancyText(console),
                "off: the console must resolve to the plugin default, unchanged");
    }

    // C17 - the ON path: the player who picked PT_BR sees it; a player without a preference does not.
    @Test
    void settingOnResolvesPerPlayerLanguageWithDefaultFallback() throws Exception {
        ECSettings.PER_PLAYER_LOCALE = true;
        PlayerController.initialize(Storages.h2("f_locale_on").writeTo(tempDir));

        LocaleMessageImp message = twoLanguageMessage("LocaleOnPlugin", "en_greeting");
        FancyText defaultText = message.getDefaultFancyText();
        FancyText brazilianText = message.getFancyText(LocaleType.PT_BR);
        assertNotSame(defaultText, brazilianText,
                "the two languages must be distinct FancyText for this test to mean anything");

        UUID brazilianUuid = UUID.randomUUID();
        PlayerData brazilian = PlayerController.handleLogin(brazilianUuid, "Alberto").join();
        brazilian.getPDSection(LocalePDSection.class).join().setLang(LocaleType.PT_BR);

        UUID neutralUuid = UUID.randomUUID();
        PlayerController.handleLogin(neutralUuid, "Neutral").join();

        TestFPlayerSender brazilianSender = new TestFPlayerSender("Alberto", brazilianUuid);
        TestFPlayerSender neutralSender = new TestFPlayerSender("Neutral", neutralUuid);

        assertSame(brazilianText, message.getFancyText(brazilianSender),
                "on: a player who picked PT_BR must resolve to the PT_BR translation");
        assertSame(defaultText, message.getFancyText(neutralSender),
                "on: a player without a preference must resolve to the plugin default");
    }

    // A message with a deterministic English default plus a distinct Brazilian translation.
    private LocaleMessageImp twoLanguageMessage(String pluginName, String key) {
        ECPluginData plugin = pluginData(pluginName);
        forcePluginLanguage(plugin, LocaleType.EN_US);
        LocaleMessageImp message = new LocaleMessageImp(plugin, key, false);
        message.addLocale(LocaleType.EN_US, new FancySegment("Hello"));
        message.addLocale(LocaleType.PT_BR, new FancySegment("Ola"));
        return message;
    }

    private static void forcePluginLanguage(ECPluginData plugin, String language) {
        try {
            Field field = ECPluginData.class.getDeclaredField("pluginLanguage");
            field.setAccessible(true);
            field.set(plugin, language);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("test could not fix the plugin language", e);
        }
    }


    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredPluginName = pluginName;
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }


}
