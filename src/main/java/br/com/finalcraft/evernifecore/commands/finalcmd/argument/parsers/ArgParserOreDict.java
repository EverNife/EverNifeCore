package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.cache.CacheableSupplier;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArgParserOreDict extends ArgParser<OreDictEntry> {

    public ArgParserOreDict(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThere is no OreDict with the name: §e%oredict_name%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNão existe nenhum OreDict com o nome: §e%oredict_name%")
    public static LocaleMessage THERE_IS_NO_OREDICT_WITH_THE_NAME;

    @Override
    public OreDictEntry parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        Set<String> allOreNames = new HashSet<>(NMSUtils.get().getOreRegistry().getAllOreNames());

        if (!allOreNames.contains(argumento.toString())){
            if (argInfo.isRequired()){
                THERE_IS_NO_OREDICT_WITH_THE_NAME
                        .addPlaceholder("%oredict_name%", argumento.toString())
                        .send(sender);
                throw new ArgParseException();
            }else {
                return null;
            }
        }

        return new OreDictEntry(argumento.toString());
    }

    /**
     * Hold only the names of the OreDicts that have at least one ItemStack
     */
    private final CacheableSupplier<List<String>> CACHED_OREDICT_NAMES = CacheableSupplier.of(
            () -> NMSUtils.get().getOreRegistry().getAllOreEntries().stream()
                    .filter(oreDictEntry -> oreDictEntry.getItemStacks().size() > 0)
                    .map(OreDictEntry::getOreName)
                    .sorted().collect(Collectors.toList())
    ).withInterval(TimeUnit.SECONDS, 10);

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {
        return CACHED_OREDICT_NAMES.getValue().stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .limit(100)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
