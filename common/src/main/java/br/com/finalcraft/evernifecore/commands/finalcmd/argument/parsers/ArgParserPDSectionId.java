package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionIds;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves a registered {@link PDSection} from its {@code plugin:sectionId} identifier (e.g.
 * {@code finaljobs:points}). Both halves match case-insensitively. The plugin half comes from each
 * section's owning {@code ECPluginData} (or {@code UnknownPlugin} for sections registered via the
 * no-plugin path), the second half is the stable section id declared at registration - the same
 * identifier that names the collection and the storage.yml entry.
 */
public class ArgParserPDSectionId extends ArgParser<Class<? extends PDSection>> {

    public ArgParserPDSectionId(ArgInfo argInfo) {
        super(argInfo);
    }

    public static String idOf(PDSectionConfiguration<?> cfg) {
        return SectionIds.sanitizePlugin(PlayerController.pluginNameOf(cfg.getPluginData()))
                + ":" + cfg.getSectionId();
    }

    /** Every registered section id, sorted - used by the command and the tab-completion. */
    public static List<String> registeredIds() {
        return PlayerController.getConfiguredPDSections().values().stream()
                .map(ArgParserPDSectionId::idOf)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /** Resolves an id to its registered section class, or null when nothing matches. */
    public static Class<? extends PDSection> resolve(String id) {
        for (PDSectionConfiguration<?> cfg : PlayerController.getConfiguredPDSections().values()) {
            if (idOf(cfg).equalsIgnoreCase(id)) {
                return cfg.getPdSectionClass();
            }
        }
        return null;
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThere is no registered PDSection with the id §e[${id}]§c.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNão existe nenhuma PDSection registrada com o id §e[${id}]§c.")
    private static LocaleMessage NO_SUCH_PDSECTION;

    @FCLocale(lang = LocaleType.EN_US, text = "§cThere are no registered PDSections on this server.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cNão há PDSections registradas neste servidor.")
    private static LocaleMessage NO_PDSECTIONS_AVAILABLE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7Available: §f${ids}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Disponíveis: §f${ids}")
    private static LocaleMessage AVAILABLE_PDSECTIONS;

    @Override
    public ParseResult<Class<? extends PDSection>> parse(@Nonnull ParseCall call) {
        Class<? extends PDSection> resolved = resolve(call.getArgumento().toString());

        if (resolved != null) {
            return ParseResult.of(resolved);
        }

        //Lazy because listing every registered section walks and sorts the whole registry, and on an
        //optional argument nobody ever reads the answer
        return unrecognized(() -> {
            List<ILocaleMessageBase> reason = new ArrayList<>();
            reason.add(NO_SUCH_PDSECTION.addPlaceholder("id", call.getArgumento().toString()));

            List<String> ids = registeredIds();
            reason.add(ids.isEmpty()
                    ? NO_PDSECTIONS_AVAILABLE
                    : AVAILABLE_PDSECTIONS.addPlaceholder("ids", String.join("§7, §f", ids)));

            return reason;
        });
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        List<String> matches = new ArrayList<>();
        for (String id : registeredIds()) {
            if (FCStringUtil.startsWithIgnoreCase(id, tabContext.getLastWord())) {
                matches.add(id);
            }
        }
        return matches;
    }
}
