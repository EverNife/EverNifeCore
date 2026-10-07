package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** A registered flag by {@code owner:name}, or by a name only one flag has. */
public class ArgParserRegionFlag extends ArgParser<RegionFlag> {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no flag called [§e${flag_name}§c]${close_matches}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma flag chamada [§e${flag_name}§c]${close_matches}")
    public static LocaleMessage NO_FLAG_CALLED;

    public ArgParserRegionFlag(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public @Nonnull ParseResult<RegionFlag> parse(@Nonnull ParseCall call) {
        String text = call.getArgumento().toString();
        Optional<RegionFlag<?>> flag = FlagRegistry.get().find(text);
        return flag.isPresent() ? ParseResult.<RegionFlag>of(flag.get()) : unrecognized(noFlagCalled(text));
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return completeKeys(tabContext.getLastWord());
    }

    static ILocaleMessageBase noFlagCalled(String text) {
        List<String> close = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            String name = flag.getKey().getName();
            if (name.contains(lower) || lower.contains(name)) {
                close.add(flag.getKey().toString());
            }
        }
        close.sort(null);
        return NO_FLAG_CALLED.addPlaceholder("flag_name", text)
                .addPlaceholder("close_matches", close.isEmpty() ? "" : " §7- did you mean: " + String.join(", ", close));
    }

    /** Every registered key, plus each name only one flag has. */
    static List<String> completeKeys(String partial) {
        List<String> keys = new ArrayList<>();
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            keys.add(flag.getKey().toString());
            if (FlagRegistry.get().find(flag.getKey().getName()).isPresent()) {
                keys.add(flag.getKey().getName());
            }
        }
        return keys.stream()
                .filter(key -> FCStringUtil.startsWithIgnoreCase(key, partial))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * The flag as the text the verb typed it, accepting {@link FlagValues#DENY_BUILD} too - for the verbs that set a
     * value, where the shortcut stands for two flags and so cannot be one {@link RegionFlag}. The verb resolves it
     * with {@link FlagValues#targets}.
     */
    public static class OrShortcut extends ArgParser<String> {

        public OrShortcut(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
            String text = call.getArgumento().toString();
            return FlagValues.targets(text).isEmpty() ? unrecognized(noFlagCalled(text)) : ParseResult.of(text);
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            List<String> keys = completeKeys(tabContext.getLastWord());
            if (FCStringUtil.startsWithIgnoreCase(FlagValues.DENY_BUILD, tabContext.getLastWord())) {
                keys.add(FlagValues.DENY_BUILD);
            }
            return keys;
        }
    }
}
