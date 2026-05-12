package br.com.finalcraft.evernifecore.minecraft.api;

import br.com.finalcraft.evernifecore.api.common.commandsender.BaseFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import jakarta.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class MinecraftFCommandSender<DELEGATE extends CommandSender> extends BaseFCommandSender<DELEGATE> {

    public MinecraftFCommandSender(DELEGATE delegate) {
        super(delegate);
    }

    public static MinecraftFCommandSender of(CommandSender commandSender) {
        return new FCommandSenderSender(commandSender);
    }

    @Override
    public void sendMessage(@Nonnull Component component) {
        FCMinecraftAdventureUtil.sendMessage(getDelegate(), component);
    }

    public static class FCommandSenderSender extends MinecraftFCommandSender<CommandSender> {

        public FCommandSenderSender(CommandSender commandSender) {
            super(commandSender);
        }

        @Override
        public String getName() {
            return getDelegate().getName();
        }

        @Override
        public UUID getUniqueId() {
            return this.getDelegate() instanceof Player ? ((Player)getDelegate()).getUniqueId() : null;
        }

        @Override
        public boolean hasPermission(String permission) {
            return this.getDelegate().hasPermission(permission);
        }
    }

}
