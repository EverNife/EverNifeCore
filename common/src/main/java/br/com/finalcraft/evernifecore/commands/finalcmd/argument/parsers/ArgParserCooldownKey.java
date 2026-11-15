package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * A cooldown's full key, {@code <Plugin>#<id>} ({@link Cooldowns#keyOf}): the plugin part must name a loaded plugin, and
 * comes back in that plugin's own casing. Completes the {@code <Plugin>#} of every loaded plugin, then the server
 * cooldowns already known under it.
 */
public class ArgParserCooldownKey extends ArgParser<String> {

    public ArgParserCooldownKey(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[${key}]§c names no loaded plugin. Name the cooldown as §e<Plugin>#<id>§c, one of: §f${plugins}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[${key}]§c não nomeia nenhum plugin carregado. Nomeie o cooldown como §e<Plugin>#<id>§c, um de: §f${plugins}")
    private static LocaleMessage NOT_A_COOLDOWN_KEY;

    @Override
    public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
        String raw = call.getArgumento().toString();
        ECPluginData owner = Cooldowns.ownerOf(raw);
        if (owner != null && raw.indexOf('#') < raw.length() - 1) {
            return ParseResult.of(Cooldowns.keyOf(owner, raw.substring(raw.indexOf('#') + 1)));
        }
        return unrecognized(() -> Collections.singletonList(NOT_A_COOLDOWN_KEY
                .addPlaceholder("key", raw)
                .addPlaceholder("plugins", String.join("§7, §f", pluginPrefixes()))));
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        String typed = tabContext.getLastWord();
        List<String> matches = new ArrayList<>();
        if (typed.indexOf('#') < 0) {
            for (String prefix : pluginPrefixes()) {
                if (FCStringUtil.startsWithIgnoreCase(prefix, typed)) {
                    matches.add(prefix);
                }
            }
            return matches;
        }
        synchronized (Cooldowns.getMapOfCooldowns()) {
            for (String key : Cooldowns.getMapOfCooldowns().keySet()) {
                if (FCStringUtil.startsWithIgnoreCase(key, typed)) {
                    matches.add(key);
                }
            }
        }
        return matches;
    }

    private static List<String> pluginPrefixes() {
        TreeSet<String> prefixes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ECPluginData plugin : ECPluginManager.getECPluginsMap().values()) {
            prefixes.add(plugin.getMetaInfo().getName() + "#");
        }
        return new ArrayList<>(prefixes);
    }
}
