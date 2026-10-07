package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The value of a flag, typed right after the flag. It is a variadic tail on the region verbs - a composite value has
 * spaces - and the framework hands a tail over without parsing it, so what this parser is for is completion: it finds
 * the flag token just before the tail and asks the flag's type. Conversion lives in {@link FlagValues#parse}.
 */
public class ArgParserFlagValue extends ArgParser<String> {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c${reason}")
    public static LocaleMessage VALUE_REFUSED;

    public ArgParserFlagValue(ArgInfo argInfo) {
        super(argInfo);
    }

    /** Validates the text against the flag parsed before it, when there is one; the text itself is the value. */
    @Override
    public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
        String text = call.getArgumento().toString();
        RegionFlag<?> flag = call.previouslyParsed(RegionFlag.class);
        if (flag != null) {
            try {
                FlagValues.parse(flag, text);
            } catch (FlagParseException refused) {
                return denied(VALUE_REFUSED.addPlaceholder("reason", refused.getMessage()));
            }
        }
        return ParseResult.of(text);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        RegionFlag<?> flag = flagBefore(argInfo, tabContext);
        if (flag == null) {
            return new ArrayList<>();
        }
        List<String> options = new ArrayList<>(flag.getType().tabComplete(tabContext.getLastWord()));
        if (FCStringUtil.startsWithIgnoreCase(FlagValues.CLEAR, tabContext.getLastWord())) {
            options.add(FlagValues.CLEAR);
        }
        return options;
    }

    /** The flag typed in the window position just before {@code tail}, read off the raw line. */
    static @Nullable RegionFlag<?> flagBefore(ArgInfo tail, TabContext tabContext) {
        int flagIndex = tabContext.getIndex() - tabContext.getLocalIndex() + tail.getIndex() - 1;
        if (flagIndex < 0 || flagIndex >= tabContext.getArgs().length) {
            return null;
        }
        List<RegionFlag<?>> targets = FlagValues.targets(tabContext.getArgs()[flagIndex]);
        return targets.isEmpty() ? null : targets.get(0);
    }

    /** The {@code field=value} settings of the flag before it: the fields, and the four forms after {@code revert=}. */
    public static class Settings extends ArgParser<String> {

        private static final String REVERT = "revert=";

        public Settings(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            RegionFlag<?> flag = flagBefore(argInfo, tabContext);
            if (flag == null) {
                return new ArrayList<>();
            }
            String partial = tabContext.getLastWord();
            List<String> fieldNames = FlagValues.getSettingsFieldNames(flag);
            if (partial.startsWith(REVERT) && fieldNames.contains("revert")) {
                return RevertStrategy.TYPE.tabComplete(partial.substring(REVERT.length())).stream()
                        .map(form -> REVERT + form)
                        .collect(Collectors.toList());
            }
            List<String> options = fieldNames.stream()
                    .map(name -> name + "=")
                    .filter(option -> FCStringUtil.startsWithIgnoreCase(option, partial))
                    .collect(Collectors.toList());
            if (FCStringUtil.startsWithIgnoreCase(FlagValues.CLEAR, partial)) {
                options.add(FlagValues.CLEAR);
            }
            return options;
        }
    }
}
