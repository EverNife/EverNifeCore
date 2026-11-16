package br.com.finalcraft.evernifecore.quest.command;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
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

    @FCLocale(lang = LocaleType.EN_US, text = "§6Quests of §e${player}§6:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Quests de §e${player}§6:")
    private static LocaleMessage HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§6 ${plugin}§7 (${count} quest(s))")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6 ${plugin}§7 (${count} quest(s))")
    private static LocaleMessage ENGINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§a   ${quest}§7 stage §f${stage}§7${counts}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a   ${quest}§7 estágio §f${stage}§7${counts}")
    private static LocaleMessage ACTIVE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7   orphan states (quest not registered; reset drops them): §f${orphans}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7   estados órfãos (quest não registrada; o reset os apaga): §f${orphans}")
    private static LocaleMessage ORPHANS;

    @FCLocale(lang = LocaleType.EN_US, text = "${quest}/${objective} waits for ${type}")
    @FCLocale(lang = LocaleType.PT_BR, text = "${quest}/${objective} espera por ${type}")
    private static LocaleMessage PENDING_OBJECTIVE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7   objectives waiting for their type: §f${pending}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7   objetivos esperando pelo seu tipo: §f${pending}")
    private static LocaleMessage PENDING;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Sources listened to: §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Fontes ouvidas: §e${count}")
    private static LocaleMessage SOURCES;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 - §f${source}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 - §f${source}")
    private static LocaleMessage SOURCE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 - §f${source}§7 for ${objectives}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 - §f${source}§7 para ${objectives}")
    private static LocaleMessage SOURCE_FOR;

    private CoreQuestCommands() {
    }

    public static void show(FCommandSender sender, FPlayer target) {
        ProgressOwner player = ProgressOwner.player(target.getUniqueId());
        HEADER.addPlaceholder("player", target.getName()).send(sender);
        for (QuestEngine engine : QuestEngine.all()) {
            ENGINE.addPlaceholder("plugin", engine.getOwner().getMetaInfo().getName()).addPlaceholder("count", engine.getAll().size()).send(sender);
            for (QuestState state : engine.getStates(player)) {
                if (state.getStatus() == QuestStatus.ACTIVE) {
                    QuestDefinition definition = engine.get(QuestId.parse(state.getQuestId(), engine.getOwner().getMetaInfo().getName()));
                    Stage stage = definition == null ? null : definition.getStage(state.getStageId());
                    StringBuilder counts = new StringBuilder();
                    for (Objective objective : stage == null ? Collections.<Objective>emptyList() : stage.getObjectives().getObjectives()) {
                        counts.append(' ').append(objective.getId()).append('=').append(state.getCount(objective.getId()));
                    }
                    ACTIVE.addPlaceholder("quest", state.getQuestId()).addPlaceholder("stage", String.valueOf(state.getStageId()))
                            .addPlaceholder("counts", counts.toString()).send(sender);
                }
            }
            Collection<String> orphans = engine.getOrphans(player);
            if (!orphans.isEmpty()) {
                ORPHANS.addPlaceholder("orphans", String.join(", ", orphans)).send(sender);
            }
            List<String> pending = new ArrayList<>();
            for (QuestDefinition definition : engine.getAll()) {
                for (Objective objective : definition.getObjectives()) {
                    if (objective.getPendingType() != null) {
                        //one entry of a joined list, so it is filled here rather than sent
                        pending.add(PENDING_OBJECTIVE.getFancyText(sender).getText()
                                .replace("${quest}", definition.getId().getName())
                                .replace("${objective}", objective.getId())
                                .replace("${type}", String.valueOf(objective.getPendingType())));
                    }
                }
            }
            if (!pending.isEmpty()) {
                PENDING.addPlaceholder("pending", String.join(", ", pending)).send(sender);
            }
        }
        List<EventSource> listened = QuestIndex.get().getListenedSources();
        SOURCES.addPlaceholder("count", listened.size()).send(sender);
        for (EventSource source : listened) {
            List<String> mine = new ArrayList<>();
            for (QuestIndex.Entry entry : QuestIndex.get().getEntries(source, player)) {
                mine.add(entry.getQuestId() + "/" + entry.getObjective().getId());
            }
            if (mine.isEmpty()) {
                SOURCE.addPlaceholder("source", source.getName()).send(sender);
            } else {
                SOURCE_FOR.addPlaceholder("source", source.getName()).addPlaceholder("objectives", String.join(", ", mine)).send(sender);
            }
        }
    }
}
