package br.com.finalcraft.evernifecore.quest.command;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestIndex;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.quest.Stage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@code /ecore quest <player>}: what every engine holds for a player - the active quests with stage and counters, the
 * orphan states, the objectives still waiting for their type - and the action sources the quest index listens to,
 * with the player's objectives on each. Read only: no core verb starts or changes a quest.
 */
public final class CoreQuestCommands {

    private CoreQuestCommands() {
    }

    public static void show(FCommandSender sender, FPlayer target) {
        ProgressOwner player = ProgressOwner.player(target.getUniqueId());
        sender.sendMessage("§6Quests of §e" + target.getName() + "§6:");
        for (QuestEngine engine : QuestEngine.all()) {
            sender.sendMessage("§6 " + engine.getOwner().getMetaInfo().getName() + "§7 (" + engine.getAll().size() + " quest(s))");
            for (QuestState state : engine.getStates(player)) {
                if (state.getStatus() == QuestStatus.ACTIVE) {
                    QuestDefinition definition = engine.get(QuestId.parse(state.getQuestId(), engine.getOwner().getMetaInfo().getName()));
                    Stage stage = definition == null ? null : definition.getStage(state.getStageId());
                    StringBuilder counts = new StringBuilder();
                    for (Objective objective : stage == null ? Collections.<Objective>emptyList() : stage.getObjectives().getObjectives()) {
                        counts.append(' ').append(objective.getId()).append('=').append(state.getCount(objective.getId()));
                    }
                    sender.sendMessage("§a   " + state.getQuestId() + "§7 stage §f" + state.getStageId() + "§7" + counts);
                }
            }
            Collection<String> orphans = engine.getOrphans(player);
            if (!orphans.isEmpty()) {
                sender.sendMessage("§7   orphan states (quest not registered; reset drops them): §f" + String.join(", ", orphans));
            }
            List<String> pending = new ArrayList<>();
            for (QuestDefinition definition : engine.getAll()) {
                for (Objective objective : definition.getObjectives()) {
                    if (objective.getPendingType() != null) {
                        pending.add(definition.getId().getName() + "/" + objective.getId() + " waits for " + objective.getPendingType());
                    }
                }
            }
            if (!pending.isEmpty()) {
                sender.sendMessage("§7   objectives waiting for their type: §f" + String.join(", ", pending));
            }
        }
        List<EventSource> listened = QuestIndex.get().getListenedSources();
        sender.sendMessage("§6Sources listened to: §e" + listened.size());
        for (EventSource source : listened) {
            List<String> mine = new ArrayList<>();
            for (QuestIndex.Entry entry : QuestIndex.get().getEntries(source, player)) {
                mine.add(entry.getQuestId() + "/" + entry.getObjective().getId());
            }
            sender.sendMessage("§7 - §f" + source.getName() + (mine.isEmpty() ? "" : "§7 for " + String.join(", ", mine)));
        }
    }
}
