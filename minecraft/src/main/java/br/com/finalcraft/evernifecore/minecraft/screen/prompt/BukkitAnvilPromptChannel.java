package br.com.finalcraft.evernifecore.minecraft.screen.prompt;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.ecnmsutils.anvil.AnvilHandle;
import br.com.finalcraft.ecnmsutils.anvil.AnvilRequest;
import br.com.finalcraft.ecnmsutils.anvil.AnvilVerdict;
import br.com.finalcraft.ecnmsutils.anvil.NmsAnvil;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestItems;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestScreenRenderer;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptReply;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSession;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The anvil channel, over ECNmsUtils' virtual anvil: the question as the title (in the icon's lore before 1.14), the
 * spec's initial text in the field and its icon (paper by default) in the first slot. Clicking the result answers; a
 * refused answer keeps the anvil open with the parser's text as the result's name; closing the anvil cancels. Repair
 * cost, item safety and the per-version window are the library's. Serves a player only on a server where the library
 * has a bridge and its anvil classes link - elsewhere the registry asks through chat.
 */
public final class BukkitAnvilPromptChannel implements PromptChannel {

    private final Supplier<NmsAnvil> anvil;
    private final BooleanSupplier available;
    private final Map<PromptSession<?>, AnvilHandle> handles = new ConcurrentHashMap<>();

    public BukkitAnvilPromptChannel() {
        this(() -> NMSUtils.get().anvil(), () -> NMSUtils.isSupported() && NMSUtils.get().anvil().isAvailable());
    }

    /** {@code available} is asked on every question; {@code anvil} only once it said yes. */
    BukkitAnvilPromptChannel(@Nonnull Supplier<NmsAnvil> anvil, @Nonnull BooleanSupplier available) {
        this.anvil = anvil;
        this.available = available;
    }

    @Nonnull
    @Override
    public PromptKind getKind() {
        return PromptKind.ANVIL;
    }

    @Override
    public boolean isSupported(@Nonnull FPlayer player) {
        return available.getAsBoolean() && ChestScreenRenderer.playerOf(player) != null;
    }

    @Override
    public void open(@Nonnull PromptSession<?> session) {
        Player player = ChestScreenRenderer.playerOf(session.getPlayer());
        if (player == null || !player.isOnline()) {
            session.cancel();
            return;
        }
        PromptSpec<?> spec = session.getSpec();
        AnvilRequest request = new AnvilRequest(typed -> verdictOf(session.answer(typed == null ? "" : typed)))
                .setTitle(spec.getQuestion())
                .setInitialText(spec.getInitialText())
                .setOnClose(session::cancel); //the session ignores it once the question ended
        ItemStack icon = ChestItems.stackOf(spec.getIcon());
        if (icon != null) {
            request.setIcon(icon);
        }
        handles.put(session, anvil.get().open(player, request));
    }

    @Override
    public void dismiss(@Nonnull PromptSession<?> session) {
        AnvilHandle handle = handles.remove(session);
        if (handle != null) {
            handle.close();
        }
    }

    private static AnvilVerdict verdictOf(PromptReply reply) {
        return reply.isAccepted() ? AnvilVerdict.accept() : AnvilVerdict.reject(reply.getMessage());
    }

}
