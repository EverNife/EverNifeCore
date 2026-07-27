package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves a registered {@link PDSection} from its {@code PluginName:SectionSimpleName}
 * identifier (e.g. {@code FinalJobs:PointsPlayerData}). Both halves match case-insensitively.
 * The plugin name comes from each section's owning {@code ECPluginData} (or {@code UnknownPlugin}
 * for sections registered via the no-plugin path), the section name is the class simple name.
 */
public class ArgParserPDSectionId extends ArgParser<Class<? extends PDSection>> {

    public ArgParserPDSectionId(ArgInfo argInfo) {
        super(argInfo);
    }

    public static String idOf(PDSectionConfiguration<?> cfg) {
        String pluginName = cfg.getPluginData() != null
                ? cfg.getPluginData().getMetaInfo().getName()
                : "UnknownPlugin";
        return pluginName + ":" + cfg.getPdSectionClass().getSimpleName();
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
    public Class<? extends PDSection> parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
        Class<? extends PDSection> resolved = resolve(argumento.toString());

        if (resolved == null) {
            if (argInfo.isRequired()) {

                NO_SUCH_PDSECTION
                    .addPlaceholder("id", argumento.toString())
                    .send(sender);

                List<String> ids = registeredIds();
                if (ids.isEmpty()) {
                    NO_PDSECTIONS_AVAILABLE
                        .send(sender);
                } else {
                    AVAILABLE_PDSECTIONS
                        .addPlaceholder("ids", String.join("§7, §f", ids))
                        .send(sender);
                }
                throw new ArgParseException();
            }
            return null;
        }

        return resolved;
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
