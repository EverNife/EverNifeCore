package br.com.finalcraft.evernifecore.api.hytale;

import br.com.finalcraft.evernifecore.api.common.commandsender.BaseFCommandSender;
import br.com.finalcraft.evernifecore.util.FCAdventureUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.receiver.IMessageReceiver;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public abstract class HytaleFCommandSender<DELEGATE extends IMessageReceiver> extends BaseFCommandSender<DELEGATE> {

    public HytaleFCommandSender(DELEGATE delegate) {
        super(delegate);
    }

    public static HytaleFCommandSender of(PlayerRef playerRef) {
        return new PlayerRefSenderF(playerRef);
    }

    public static HytaleFCommandSender of(CommandSender commandSender) {
        return new FCommandSenderSender(commandSender);
    }

    @Override
    public void sendMessage(@NonNull Component component) {
        Message message = FCAdventureUtil.toHytaleMessage(component);
        getDelegate().sendMessage(message);
    }

    public static class PlayerRefSenderF extends HytaleFCommandSender<PlayerRef> {

        public PlayerRefSenderF(PlayerRef playerRef) {
            super(playerRef);
        }

        @Override
        public String getName() {
            return getDelegate().getUsername();
        }

        @Override
        public UUID getUniqueId() {
            return getDelegate().getUuid();
        }

        @Override
        public boolean hasPermission(String permission) {
            return PermissionsModule.get().hasPermission(this.getDelegate().getUuid(), permission);
        }
    }

    public static class FCommandSenderSender extends HytaleFCommandSender<CommandSender> {

        public FCommandSenderSender(CommandSender commandSender) {
            super(commandSender);
        }

        @Override
        public String getName() {
            return getDelegate().getDisplayName();
        }

        @Override
        public UUID getUniqueId() {
            return this.getDelegate() instanceof Player ? getDelegate().getUuid() : null;
        }

        @Override
        public boolean hasPermission(String permission) {
            return this.getDelegate().hasPermission(permission);
        }
    }

}
