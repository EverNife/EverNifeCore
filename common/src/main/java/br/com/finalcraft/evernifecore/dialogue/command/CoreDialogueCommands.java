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
        sender.sendMessage("§6Dialogue of §e" + target.getName() + "§6: §e" + session.getDialogue().getId());
        sender.sendMessage(" §7node §f" + session.getNode().getId() + " §7for §f" + since.getSeconds() + "s §7(since "
                + session.getStartedAt() + ")");
        sender.sendMessage(" §7started by §f" + session.getCaller() + (npc == null ? "" : " §7with §f" + npc));
    }
}
