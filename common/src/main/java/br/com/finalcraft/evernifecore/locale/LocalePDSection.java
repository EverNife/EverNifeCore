package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.playerdata.PDSection;

/**
 * One player's chosen language, persisted per player. Only registered (and only consulted) when
 * {@code ECSettings.PER_PLAYER_LOCALE} is enabled; while it is off this section never exists and
 * every message keeps rendering in the plugin's configured locale.
 *
 * <p>A {@code null} {@link #getLang() lang} means "no personal preference": the message falls back
 * to the plugin's default language, which is exactly today's behaviour. The stored value is always
 * normalized through {@link LocaleType#normalize(String)} so it matches the keys under which a
 * message's translations are registered.</p>
 */
public class LocalePDSection extends PDSection {

    private String lang;

    public LocalePDSection() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
    }

    /** The player's chosen language, or {@code null} when they follow the plugin's default. */
    public String getLang() {
        return lang;
    }

    /**
     * Sets (or clears, with {@code null}) this player's language. The value is normalized so it lines
     * up with the translation keys of a {@code LocaleMessage}.
     */
    public void setLang(String lang) {
        this.lang = LocaleType.normalize(lang);
        markDirty();
    }
}
