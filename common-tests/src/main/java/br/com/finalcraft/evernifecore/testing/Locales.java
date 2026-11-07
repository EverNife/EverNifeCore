package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;

import java.nio.file.Path;
import java.util.Locale;
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
        try {
            return new Locales(PlayerDataWorld.with(Storages.memory()).boot(baseDir), previous);
        } catch (RuntimeException | Error failedToBoot) {
            //the setting is process-wide and nobody holds a Locales to close: a boot that broke here
            //would leave every later test running with per-player locale turned on
            ECSettings.PER_PLAYER_LOCALE = previous;
            throw failedToBoot;
        }
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
     * Gives the message {@code plugin} registered under {@code key} the text {@code text} in {@code lang}, the way an
     * admin's edit of the lang file does, until the returned handle closes and puts the old text back.
     *
     * @throws IllegalArgumentException when {@code plugin} registered no message under {@code key}
     */
    public static Retext retext(ECPluginData plugin, String key, String lang, String text) {
        LocaleMessageImp message = ECPluginManager.getOrCreateECorePluginData(plugin).getLocalizedMessages().get(key);
        if (message == null) {
            throw new IllegalArgumentException(plugin.getMetaInfo().getName() + " registered no locale message '" + key + "'. Load the"
                    + " class that declares it first: FCLocaleManager.loadLocale(plugin, true, TheClass.class).");
        }
        String normalized = LocaleType.normalize(lang);
        FancyText previous = message.getFancyText(normalized);
        message.addLocale(normalized, FancyText.of(text));
        return () -> {
            if (previous == null) {
                message.getFancyTextMap().remove(normalized.toUpperCase(Locale.ROOT));
            } else {
                message.addLocale(normalized, previous);
            }
        };
    }

    /** What {@link #retext} hands back: closing it puts the text it replaced back. */
    public interface Retext extends AutoCloseable {
        @Override
        void close();
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
