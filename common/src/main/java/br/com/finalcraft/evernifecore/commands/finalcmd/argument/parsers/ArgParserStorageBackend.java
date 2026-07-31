package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * A storage backend name as declared and enabled in storage.yml. Returns the raw name; the
 * transfer call still performs the authoritative validation (declared + enabled + different).
 * This parser only resolves the casing of an enabled backend and produces an early friendly
 * error when the name is unknown, plus the tab-completion list.
 */
public class ArgParserStorageBackend extends ArgParser<String> {

    public ArgParserStorageBackend(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThere is no enabled storage backend named §e[${backend}]§c.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNão existe nenhum backend de armazenamento habilitado chamado §e[${backend}]§c.")
    private static LocaleMessage NO_SUCH_BACKEND;

    @FCLocale(lang = LocaleType.EN_US, text = "§cThere are no enabled storage backends configured in storage.yml.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cNão há backends de armazenamento habilitados configurados no storage.yml.")
    private static LocaleMessage NO_BACKENDS_AVAILABLE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7Enabled backends: §f${backends}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Backends habilitados: §f${backends}")
    private static LocaleMessage ENABLED_BACKENDS;

    @Override
    public ParseResult<String> parse(@Nonnull ParseCall call) {
        for (String backendName : PlayerController.getEnabledBackendNames()) {
            if (backendName.equalsIgnoreCase(call.getArgumento().toString())) {
                return ParseResult.of(backendName);
            }
        }

        //Lazy because listing the enabled backends reads storage.yml's resolved state, and on an
        //optional argument nobody ever reads the answer
        return unrecognized(() -> {
            List<ILocaleMessageBase> reason = new ArrayList<>();
            reason.add(NO_SUCH_BACKEND.addPlaceholder("backend", call.getArgumento().toString()));

            List<String> backends = PlayerController.getEnabledBackendNames();
            reason.add(backends.isEmpty()
                    ? NO_BACKENDS_AVAILABLE
                    : ENABLED_BACKENDS.addPlaceholder("backends", String.join("§7, §f", backends)));

            return reason;
        });
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        List<String> matches = new ArrayList<>();
        for (String backendName : PlayerController.getEnabledBackendNames()) {
            if (FCStringUtil.startsWithIgnoreCase(backendName, tabContext.getLastWord())) {
                matches.add(backendName);
            }
        }
        return matches;
    }
}
