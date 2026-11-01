package br.com.finalcraft.evernifecore.quest.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser.TabContext;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A quest by its name under a {@link QuestCommandNode} (that node's engine), or by its full {@code owner:name}
 * anywhere - the engine of the plugin the id names.
 */
public class ArgParserQuest extends ArgParser<QuestDefinition> {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no quest called [§e${quest}§c] - see the quests with §elist§c.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma quest chamada [§e${quest}§c] - veja as quests com §elist§c.")
    public static LocaleMessage NO_QUEST_CALLED;

    public ArgParserQuest(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public @Nonnull ParseResult<QuestDefinition> parse(@Nonnull ParseCall call) {
        String text = call.getArgumento().toString();
        QuestEngine engine = call.getExecutor() instanceof QuestCommandNode ? ((QuestCommandNode) call.getExecutor()).getEngine() : null;
        QuestDefinition found = null;
        try {
            if (engine != null) {
                found = engine.get(QuestId.parse(text, engine.getOwner().getMetaInfo().getName()));
            } else if (text.indexOf(':') > 0) {
                QuestId id = QuestId.parse(text, "");
                QuestEngine owner = QuestEngine.find(id.getOwner());
                found = owner == null ? null : owner.get(id);
            }
        } catch (IllegalArgumentException notAnId) {
            found = null;
        }
        return found != null ? ParseResult.of(found) : unrecognized(NO_QUEST_CALLED.addPlaceholder("quest", text));
    }

    /** Under a node, its quests' names; otherwise every full id. */
    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        List<String> candidates = new ArrayList<>();
        if (tabContext.getExecutor() instanceof QuestCommandNode) {
            for (QuestDefinition definition : ((QuestCommandNode) tabContext.getExecutor()).getEngine().getAll()) {
                candidates.add(definition.getId().getName());
            }
        } else {
            for (QuestEngine engine : QuestEngine.all()) {
                for (QuestDefinition definition : engine.getAll()) {
                    candidates.add(definition.getId().toString());
                }
            }
        }
        return candidates.stream().filter(candidate -> FCStringUtil.startsWithIgnoreCase(candidate, tabContext.getLastWord()))
                .sorted().collect(Collectors.toList());
    }
}
