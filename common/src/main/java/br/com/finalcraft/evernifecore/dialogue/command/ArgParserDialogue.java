package br.com.finalcraft.evernifecore.dialogue.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.dialogue.Dialogue;
import br.com.finalcraft.evernifecore.dialogue.DialogueId;
import br.com.finalcraft.evernifecore.dialogue.Dialogues;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A registered dialogue by {@code owner:name}, or by its bare name when exactly one plugin has a dialogue so called:
 * {@code @Arg("<dialogue>") Dialogue dialogue}, then {@code Dialogues.start(...)} from the plugin's own command.
 */
public class ArgParserDialogue extends ArgParser<Dialogue> {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no dialogue called [§e${dialogue_name}§c]")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhum diálogo chamado [§e${dialogue_name}§c]")
    public static LocaleMessage NO_DIALOGUE_CALLED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cMore than one dialogue is called [§e${dialogue_name}§c] - name it as owner:name: §7${dialogue_list}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cMais de um diálogo se chama [§e${dialogue_name}§c] - informe como dono:nome: §7${dialogue_list}")
    public static LocaleMessage MORE_THAN_ONE_DIALOGUE_CALLED;

    public ArgParserDialogue(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public @Nonnull ParseResult<Dialogue> parse(@Nonnull ParseCall call) {
        String text = call.getArgumento().toString();
        if (text.indexOf(':') >= 0) {
            try {
                Dialogue found = Dialogues.find(DialogueId.parse(text)).orElse(null);
                return found != null ? ParseResult.of(found) : noDialogueCalled(text);
            } catch (IllegalArgumentException notAnId) {
                return noDialogueCalled(text);
            }
        }
        List<Dialogue> named = new ArrayList<>();
        for (Dialogue dialogue : Dialogues.all()) {
            if (dialogue.getId().getName().equalsIgnoreCase(text)) {
                named.add(dialogue);
            }
        }
        if (named.isEmpty()) {
            return noDialogueCalled(text);
        }
        if (named.size() > 1) {
            return denied(MORE_THAN_ONE_DIALOGUE_CALLED.addPlaceholder("dialogue_name", text).addPlaceholder("dialogue_list",
                    named.stream().map(dialogue -> dialogue.getId().toString()).sorted().collect(Collectors.joining(", "))));
        }
        return ParseResult.of(named.get(0));
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return Dialogues.all().stream()
                .map(dialogue -> dialogue.getId().toString())
                .filter(candidate -> FCStringUtil.startsWithIgnoreCase(candidate, tabContext.getLastWord()))
                .sorted()
                .collect(Collectors.toList());
    }

    private ParseResult<Dialogue> noDialogueCalled(String text) {
        return unrecognized(NO_DIALOGUE_CALLED.addPlaceholder("dialogue_name", text));
    }
}
