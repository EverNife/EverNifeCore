package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is a ArgParser for FCTimeFrame
 *
 * It will accept any format accepted by {@link FCTimeUtil#toMillis(String)}
 */
public class ArgParserFCTimeFrame extends ArgParser<FCTimeFrame> {

    public ArgParserFCTimeFrame(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe expression '§e${time}§c' is not a valid Time Frame! §3Insert something like: '§e2§6h30§6m§3' or '§e1§6d30§6m50§6s§3'!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cA expressão '§e${time}§c' não é um Time Frame válido! §3Insira algo como: '§e2§6h30§6m§3' ou '§e1§6d30§6m50§6s§3'!")
    public static LocaleMessage THIS_IS_NOT_A_VALID_TIME_FRAME;

    @Override
    public ParseResult<FCTimeFrame> parse(@Nonnull ParseCall call) {
        Long millisConverted = FCTimeUtil.toMillis(call.getArgumento().toString());

        return millisConverted == null
                ? unrecognized(THIS_IS_NOT_A_VALID_TIME_FRAME.addPlaceholder("time", call.getArgumento().toString()))
                : ParseResult.of(FCTimeFrame.of(millisConverted));
    }

    private final List<String> TIMEFRAME_EXAMPLES = Arrays.asList(
            "1d",
            "2h30m",
            "1d30m50s"
    );

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return TIMEFRAME_EXAMPLES.stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }

}
