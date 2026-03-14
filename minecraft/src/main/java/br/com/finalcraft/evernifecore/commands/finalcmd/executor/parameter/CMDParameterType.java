package br.com.finalcraft.evernifecore.commands.finalcmd.executor.parameter;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CMDParameterType<T> {

    public static final CMDParameterType[] ALLOWED_CLASSES = new CMDParameterType[]{
            CMDParameterType.of(CommandSender.class).build(),
            CMDParameterType.of(String.class).build(),
            CMDParameterType.of(MultiArgumentos.class).build(),
            CMDParameterType.of(HelpContext.class).build(),
            CMDParameterType.of(HelpLine.class).build(),

            CMDParameterType.of(ItemStack.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(Player.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(PlayerData.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(PDSection.class)
                    .setOnlyPlayer(true)
                    .setAllowExtends(true)
                    .build(),
    };

    private final Class<T> clazz;
    private final boolean checkExtends;
    private final boolean playerOnly;

    public CMDParameterType(Class<T> clazz, boolean checkExtends, boolean playerOnly) {
        this.clazz = clazz;
        this.checkExtends = checkExtends;
        this.playerOnly = playerOnly;
    }

    public static <T> Builder<T> of(Class<T> clazz){
        return new Builder<>(clazz);
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public boolean isCheckExtends() {
        return checkExtends;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public static class Builder<T>{

        private final Class<T> clazz;
        private boolean allowExtends = false;
        private boolean onlyPlayer = false;

        private Builder(Class<T> clazz) {
            this.clazz = clazz;
        }

        public Builder<T> setAllowExtends(boolean allowExtends) {
            this.allowExtends = allowExtends;
            return this;
        }

        public Builder<T> setOnlyPlayer(boolean onlyPlayer) {
            this.onlyPlayer = onlyPlayer;
            return this;
        }

        public CMDParameterType<T> build(){
            return new CMDParameterType<>(this.clazz, this.allowExtends, this.onlyPlayer);
        }
    }

    @Override
    public String toString() {
        return "CMDParameterType{" +
                "clazz=" + clazz +
                ", checkExtends=" + checkExtends +
                ", onlyPlayer=" + playerOnly +
                '}';
    }
}
