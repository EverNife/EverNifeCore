package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Language, for a test that asserts on it: a message that really is registered in more than one, and
 * readers who really did choose one.
 *
 * <pre>{@code
 * try (Locales locales = Locales.perPlayerLocale(tempDir)) {
 *     TestFPlayerSender english = locales.reader("Steve", LocaleType.EN_US);
 *     TestFPlayerSender brazilian = locales.reader("Petrus", LocaleType.PT_BR);
 * }
 * }</pre>
 *
 * <p>Nothing here fakes the resolution: the choice is stored in the player's own
 * {@link LocalePDSection}, which is the single place {@code FCLocaleManager} reads it from.</p>
 */
public final class Locales implements AutoCloseable {

    private final PlayerDataWorld world;
    private final boolean previousPerPlayerLocale;

    private Locales(PlayerDataWorld world, boolean previousPerPlayerLocale) {
        this.world = world;
        this.previousPerPlayerLocale = previousPerPlayerLocale;
    }

    /**
     * Turns per-player locale on and boots the in-memory PlayerData layer that carries the choice.
     * The setting has to be on BEFORE the boot: that is when the controller decides whether the
     * locale section exists at all.
     */
    public static Locales perPlayerLocale(Path baseDir) {
        boolean previous = ECSettings.PER_PLAYER_LOCALE;
        ECSettings.PER_PLAYER_LOCALE = true;
        return new Locales(PlayerDataWorld.with(Storages.memory()).boot(baseDir), previous);
    }

    /** A player who is logged in and reads in {@code lang} - see {@link LocaleType}. */
    public TestFPlayerSender reader(String name, String lang) {
        UUID uniqueId = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uniqueId, name).join();
        playerData.getPDSection(LocalePDSection.class).join().setLang(lang);
        return new TestFPlayerSender(name, uniqueId);
    }

    @Override
    public void close() {
        ECSettings.PER_PLAYER_LOCALE = previousPerPlayerLocale;
        world.close();
    }

    /**
     * A message with one text per language, registered for {@code plugin} under {@code key}. The
     * pairs are {@code lang, text, lang, text} - see {@link LocaleType} for the language names.
     */
    public static LocaleMessage message(ECPluginData plugin, String key, String... langAndText) {
        if (langAndText.length % 2 != 0) {
            throw new IllegalArgumentException("Each language needs a text: pass them as lang, text, lang, text...");
        }
        LocaleMessageImp message = new LocaleMessageImp(plugin, key);
        for (int index = 0; index < langAndText.length; index += 2) {
            message.addLocale(LocaleType.normalize(langAndText[index]), FancyText.of(langAndText[index + 1]));
        }
        return message;
    }
}
