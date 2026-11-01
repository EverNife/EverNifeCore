package br.com.finalcraft.evernifecore.quest.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.NodeVerbAccess;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.VerbPermissionBase;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.quest.Stage;
import br.com.finalcraft.evernifecore.quest.StartResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserObjectNode;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The quest verbs a plugin mounts under its own command, acting on one {@link QuestEngine}:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "quest", permission = "finalquests.quest")
 * private final QuestCommandNode quests = new QuestCommandNode(engine, "finalquests.quest");
 * }</pre>
 * The mount's permission is the door; a {@code permissionBase} adds {@code <base>.<verb>} per verb, checked before any
 * argument is read. {@code reset}, {@code complete} and {@code progress} are admin verbs: give their permission only to
 * staff. A verb naming no player acts on the sender.
 */
public class QuestCommandNode implements VerbPermissionBase {

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aQuest §e${quest}§a ${action} for §e${owner}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aQuest §e${quest}§a ${action} para §e${owner}§a.")
    public static LocaleMessage QUEST_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c${reason}")
    public static LocaleMessage REFUSED;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Quests of §e${player}§6 (${filter}): §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Quests de §e${player}§6 (${filter}): §e${count}")
    public static LocaleMessage LIST_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aTracking §e${quest}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aAcompanhando §e${quest}§a.")
    public static LocaleMessage TRACKING;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eNo quest tracked.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eNenhuma quest acompanhada.")
    public static LocaleMessage UNTRACKED;

    private final QuestEngine engine;
    private final @Nullable String permissionBase;

    public QuestCommandNode(QuestEngine engine) {
        this(engine, null);
    }

    /** @param permissionBase when not null, each verb also needs {@code <permissionBase>.<verb>} */
    public QuestCommandNode(QuestEngine engine, @Nullable String permissionBase) {
        this.engine = engine;
        this.permissionBase = permissionBase;
    }

    public QuestEngine getEngine() {
        return engine;
    }

    @Override
    public @Nullable String getPermissionBase() {
        return permissionBase;
    }

    // ------------------------------------------------------------------
    //  Verbs
    // ------------------------------------------------------------------

    @FinalCMD.SubCMD(subcmd = "list", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The quests of a player, optionally only those of one status"),
            @FCLocale(lang = LocaleType.PT_BR, text = "As quests de um jogador, opcionalmente só as de um status")
    })
    public void list(FCommandSender sender, @Arg("[status]") QuestStatus status, @Arg(value = "[player]", fromSender = true) FPlayer target) {
        if (target == null) {
            REFUSED.addPlaceholder("reason", "Name a player: the console has no quests.").send(sender);
            return;
        }
        Map<QuestDefinition, QuestStatus> shown = new LinkedHashMap<>();
        for (QuestDefinition definition : engine.getAll()) {
            ProgressOwner owner = engine.getOwnerFor(target.getUniqueId(), definition.getId());
            QuestStatus current = owner == null ? QuestStatus.LOCKED : engine.getStatus(owner, definition.getId());
            if (status == null || status == current) {
                shown.put(definition, current);
            }
        }
        LIST_HEADER.addPlaceholder("player", target.getName()).addPlaceholder("filter", status == null ? "all" : status.name())
                .addPlaceholder("count", shown.size()).send(sender);
        for (Map.Entry<QuestDefinition, QuestStatus> line : shown.entrySet()) {
            sender.sendMessage("§7 - §e" + line.getKey().getId().getName() + "§7 (" + line.getKey().getName() + "): §f" + line.getValue());
        }
    }

    @FinalCMD.SubCMD(subcmd = "info", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "A quest's stages and objectives, and where you stand in it"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Os estágios e objetivos de uma quest, e onde você está nela")
    })
    public void info(FCommandSender sender, @Arg("<quest>") QuestDefinition quest) {
        sender.sendMessage("§6Quest §e" + quest.getId() + "§6: §f" + quest.getName() + (quest.getDescription().isEmpty() ? "" : "§7 - " + quest.getDescription()));
        Optional<QuestState> state = Optional.empty();
        if (sender.isPlayer()) {
            ProgressOwner owner = engine.getOwnerFor(sender.getUniqueId(), quest.getId());
            if (owner != null) {
                state = engine.getState(owner, quest.getId());
                sender.sendMessage("§7 status: §f" + engine.getStatus(owner, quest.getId()));
            }
        }
        for (Stage stage : quest.getStages()) {
            boolean current = state.isPresent() && stage.getId().equals(state.get().getStageId());
            sender.sendMessage((current ? "§a > " : "§7   ") + "stage §e" + stage.getId());
            for (Objective objective : stage.getObjectives().getObjectives()) {
                if (objective.isHidden()) {
                    continue;
                }
                long count = state.map(found -> found.getCount(objective.getId())).orElse(0L);
                sender.sendMessage("§7     - " + objective.getId() + ": §f" + count + "/" + objective.getGoal());
            }
        }
    }

    @FinalCMD.SubCMD(subcmd = "start", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Start a quest for a player, optionally with params k=v"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Inicia uma quest para um jogador, opcionalmente com params k=v")
    })
    public void start(FCommandSender sender, @Arg("<quest>") QuestDefinition quest, @Arg(value = "[player]", fromSender = true) FPlayer target,
                      @Arg("[params...]") String paramsText) {
        if (target == null) {
            REFUSED.addPlaceholder("reason", "Name a player: the console starts no quest of its own.").send(sender);
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            ArgParserObjectNode.parse(paramsText == null ? "" : paramsText)
                    .fields().forEachRemaining(field -> values.put(field.getKey(), field.getValue().asText()));
        } catch (IllegalArgumentException malformed) {
            REFUSED.addPlaceholder("reason", malformed.getMessage()).send(sender);
            return;
        }
        if (ownerOf(sender, target, quest.getId()) == null) {
            return;
        }
        reportStart(sender, quest, target, engine.start(ProgressOwner.player(target.getUniqueId()), quest.getId(), QuestParams.of(values)));
    }

    @FinalCMD.SubCMD(subcmd = "offer", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Offer an available quest to a player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Oferece uma quest disponível a um jogador")
    })
    public void offer(FCommandSender sender, @Arg("<quest>") QuestDefinition quest, @Arg("<player>") FPlayer target) {
        ProgressOwner owner = ownerOf(sender, target, quest.getId());
        if (owner != null) {
            reportStart(sender, quest, target, engine.offer(owner, quest.getId()));
        }
    }

    @FinalCMD.SubCMD(subcmd = "abandon", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Abandon an active quest"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Abandona uma quest ativa")
    })
    public void abandon(FCommandSender sender, @Arg("<quest>") QuestDefinition quest, @Arg(value = "[player]", fromSender = true) FPlayer target) {
        ProgressOwner owner = ownerOf(sender, target, quest.getId());
        if (owner != null) {
            report(sender, quest.getId(), owner, "abandoned", engine.abandon(owner, quest.getId()));
        }
    }

    @FinalCMD.SubCMD(subcmd = "reset", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Admin: drop a player's state of a quest, a quest no longer registered included"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Admin: apaga o estado de uma quest de um jogador, inclusive de quest que não existe mais")
    })
    public void reset(FCommandSender sender, @Arg("<quest>") String questText, @Arg("<player>") FPlayer target) {
        QuestId id;
        try {
            id = QuestId.parse(questText, engine.getOwner().getMetaInfo().getName());
        } catch (IllegalArgumentException invalid) {
            REFUSED.addPlaceholder("reason", invalid.getMessage()).send(sender);
            return;
        }
        ProgressOwner owner = ownerOf(sender, target, id);
        if (owner != null) {
            report(sender, id, owner, "reset", engine.reset(owner, id));
        }
    }

    @FinalCMD.SubCMD(subcmd = "complete", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Admin: complete an active quest, paying what completion pays"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Admin: conclui uma quest ativa, pagando o que a conclusão paga")
    })
    public void complete(FCommandSender sender, @Arg("<quest>") QuestDefinition quest, @Arg("<player>") FPlayer target) {
        ProgressOwner owner = ownerOf(sender, target, quest.getId());
        if (owner != null) {
            report(sender, quest.getId(), owner, "completed", engine.complete(owner, quest.getId()));
        }
    }

    @FinalCMD.SubCMD(subcmd = "progress", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Admin: add to an objective of the current stage"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Admin: soma a um objetivo do estágio atual")
    })
    public void progress(FCommandSender sender, @Arg("<quest>") QuestDefinition quest, @Arg("<objective>") String objective,
                         @Arg("<delta>") Long delta, @Arg(value = "[player]", fromSender = true) FPlayer target) {
        ProgressOwner owner = ownerOf(sender, target, quest.getId());
        if (owner != null) {
            report(sender, quest.getId(), owner, "progressed (" + objective + " +" + delta + ")",
                    engine.progress(owner, quest.getId(), objective, delta));
        }
    }

    @FinalCMD.SubCMD(subcmd = "track", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Track a quest on your screen, or stop with 'off'"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Acompanha uma quest na tela, ou para com 'off'")
    })
    public void track(FPlayer player, @Arg("<quest>") String questText) {
        if (questText.equalsIgnoreCase("off")) {
            engine.track(player, null).whenComplete((done, failure) -> {
                if (failure != null) {
                    REFUSED.addPlaceholder("reason", reasonOf(failure)).send(player);
                } else {
                    UNTRACKED.send(player);
                }
            });
            return;
        }
        QuestDefinition quest;
        try {
            quest = engine.get(QuestId.parse(questText, engine.getOwner().getMetaInfo().getName()));
        } catch (IllegalArgumentException invalid) {
            quest = null;
        }
        if (quest == null) {
            ArgParserQuest.NO_QUEST_CALLED.addPlaceholder("quest", questText).send(player);
            return;
        }
        QuestId id = quest.getId();
        engine.track(player, id).whenComplete((done, failure) -> {
            if (failure != null) {
                REFUSED.addPlaceholder("reason", reasonOf(failure)).send(player);
            } else {
                TRACKING.addPlaceholder("quest", id).send(player);
            }
        });
    }

    // ------------------------------------------------------------------

    /** The holder of {@code target}'s instance; null (and the sender told why) when there is none. */
    private @Nullable ProgressOwner ownerOf(FCommandSender sender, @Nullable FPlayer target, QuestId id) {
        if (target == null) {
            REFUSED.addPlaceholder("reason", "Name a player: the console has no quests.").send(sender);
            return null;
        }
        ProgressOwner owner = engine.getOwnerFor(target.getUniqueId(), id);
        if (owner == null) {
            QuestDefinition definition = engine.get(id);
            if (definition != null && definition.getOwnerKind().equals(ProgressOwner.GROUP)) {
                refuse(sender, target, definition, StartResult.noGroup(definition, ProgressOwner.player(target.getUniqueId())));
            } else {
                REFUSED.addPlaceholder("reason", "Quest '" + id + "' is owned by a " + (definition == null ? "?" : definition.getOwnerKind())
                        + ", not by a player.").send(sender);
            }
        }
        return owner;
    }

    private void reportStart(FCommandSender sender, QuestDefinition quest, FPlayer target, CompletableFuture<StartResult> started) {
        started.whenComplete((result, failure) -> {
            if (failure != null) {
                REFUSED.addPlaceholder("reason", reasonOf(failure)).send(sender);
            } else if (!result.isSuccess()) {
                refuse(sender, target, quest, result);
            } else {
                QUEST_DONE.addPlaceholder("quest", quest.getId()).addPlaceholder("action", result.getOutcome().name().toLowerCase(Locale.ROOT))
                        .addPlaceholder("owner", target.getName()).send(sender);
            }
        });
    }

    /** The engine's presenter rewrites the refusal when it wants to; else the core's text. */
    private void refuse(FCommandSender sender, FPlayer target, QuestDefinition quest, StartResult result) {
        FancyText rewritten = engine.getPresenter().refusal(target, quest, result);
        if (rewritten != null) {
            sender.sendMessage(rewritten);
            return;
        }
        REFUSED.addPlaceholder("reason", result.getWhy() != null ? result.getWhy()
                : result.getGate() != null ? result.getGate().explain() : result.toString()).send(sender);
    }

    private static void report(FCommandSender sender, QuestId id, ProgressOwner owner, String action, CompletableFuture<Void> task) {
        task.whenComplete((done, failure) -> {
            if (failure != null) {
                REFUSED.addPlaceholder("reason", reasonOf(failure)).send(sender);
            } else {
                QUEST_DONE.addPlaceholder("quest", id).addPlaceholder("action", action).addPlaceholder("owner", owner).send(sender);
            }
        });
    }

    private static String reasonOf(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
