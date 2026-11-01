package br.com.finalcraft.evernifecore.minecraft.screen.prompt;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.chat.IChatAction;
import br.com.finalcraft.evernifecore.minecraft.screen.BukkitScreenScheduler;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestScreenRenderer;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptReply;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSession;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chat channel: the screen's window goes, the question is sent, and the next line the player types is the answer.
 * A refused line never reaches public chat: the player reads why and the question again. Serves every online player,
 * so it is where a question lands when nothing richer does.
 *
 * <p>It rides the core's {@code ChatExpectationListener}, which hears the async chat event; the session hops to the main
 * thread itself.</p>
 */
public final class BukkitChatPromptChannel implements PromptChannel {

    /** The chat wait of each question out, to stop when the registry ends it. */
    private final Map<PromptSession<?>, ExpectedChat> waits = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public PromptKind getKind() {
        return PromptKind.CHAT;
    }

    @Override
    public boolean isSupported(@Nonnull FPlayer player) {
        return ChestScreenRenderer.playerOf(player) != null;
    }

    @Override
    public void open(@Nonnull PromptSession<?> session) {
        Player player = ChestScreenRenderer.playerOf(session.getPlayer());
        if (player == null || !player.isOnline()) {
            session.cancel();
            return;
        }
        player.sendMessage(session.getSpec().getQuestion());
        //no expiry and no quit callback: the registry is the one that times a question out and hears a player leave
        waits.put(session, ChatExpectationListener.get().expectPlayerChat(player, line -> onChat(session, line), 0L, null, null));
    }

    @Override
    public void dismiss(@Nonnull PromptSession<?> session) {
        ChatExpectationListener.get().stopExpecting(waits.remove(session));
    }

    /** Runs on the async chat thread. */
    private IChatAction.ActionResult onChat(PromptSession<?> session, String line) {
        PromptReply reply = session.answer(line == null ? "" : line);
        if (reply.isAccepted()) {
            return IChatAction.ActionResult.SUCCESS_AND_CONSUME;
        }
        BukkitScreenScheduler.INSTANCE.run(() -> {
            Player player = ChestScreenRenderer.playerOf(session.getPlayer());
            if (player != null && player.isOnline() && session.isOpen()) {
                player.sendMessage(reply.getMessage());
                player.sendMessage(session.getSpec().getQuestion());
            }
        });
        return IChatAction.ActionResult.CONSUME_AND_CONTINUE;
    }

}
