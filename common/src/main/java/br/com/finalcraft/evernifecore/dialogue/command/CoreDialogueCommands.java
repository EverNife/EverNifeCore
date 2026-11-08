package br.com.finalcraft.evernifecore.dialogue.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;
import br.com.finalcraft.evernifecore.dialogue.Dialogues;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.time.ECClock;

import java.time.Duration;

/**
 * {@code /ecore dialogue <player>}: the dialogue a player is in - which one, on which node, since when, who started it.
 * Only reads: dialogues are started by the plugin that owns them (its command, an NPC, a region, an effect).
 */
public final class CoreDialogueCommands {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cName the player whose dialogue to read: §e/ecore dialogue <player>")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cInforme o jogador cujo diálogo ler: §e/ecore dialogue <jogador>")
    public static LocaleMessage NEEDS_A_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7${player} is in no dialogue.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7${player} não está em nenhum diálogo.")
    public static LocaleMessage NO_DIALOGUE;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Dialogue of §e${player}§6: §e${dialogue}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Diálogo de §e${player}§6: §e${dialogue}")
    private static LocaleMessage HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = " §7node §f${node} §7for §f${seconds}s §7(since ${since})")
    @FCLocale(lang = LocaleType.PT_BR, text = " §7nó §f${node} §7há §f${seconds}s §7(desde ${since})")
    private static LocaleMessage NODE_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = " §7started by §f${caller}")
    @FCLocale(lang = LocaleType.PT_BR, text = " §7iniciado por §f${caller}")
    private static LocaleMessage STARTED_BY;

    @FCLocale(lang = LocaleType.EN_US, text = " §7started by §f${caller} §7with §f${npc}")
    @FCLocale(lang = LocaleType.PT_BR, text = " §7iniciado por §f${caller} §7com §f${npc}")
    private static LocaleMessage STARTED_BY_WITH_NPC;

    private CoreDialogueCommands() {
    }

    /** Sends what {@code target}'s open dialogue session is to {@code sender}. */
    public static void show(FCommandSender sender, FPlayer target) {
        DialogueSession session = Dialogues.sessionOf(target);
        if (session == null) {
            NO_DIALOGUE.addPlaceholder("player", target.getName()).send(sender);
            return;
        }
        Duration since = Duration.between(session.getStartedAt(), ECClock.now());
        NpcRef npc = session.getContext().getNpc();
        HEADER.custom().addPlaceholder("player", target.getName()).addPlaceholder("dialogue", session.getDialogue().getId().toString()).send(sender);
        NODE_LINE.custom().addPlaceholder("node", session.getNode().getId()).addPlaceholder("seconds", since.getSeconds())
                .addPlaceholder("since", session.getStartedAt().toString()).send(sender);
        (npc == null ? STARTED_BY : STARTED_BY_WITH_NPC).custom().addPlaceholder("caller", session.getCaller().toString())
                .addPlaceholder("npc", String.valueOf(npc)).send(sender);
    }
}
