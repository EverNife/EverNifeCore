package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.entity.Player;

public class PlayerGui<P extends IPlayerData, G extends BaseGui> {

    private final Player player;
    private final P playerData;
    private final G gui;

    public PlayerGui(P playerData, G gui) {
        this.player = playerData.getPlayer();
        this.playerData = playerData;
        this.gui = gui;
    }

    public P getPlayerData() {
        return playerData;
    }

    public Player getPlayer() {
        return player;
    }

    public G getGui() {
        return gui;
    }

    public void open(){
        this.getGui().open(getPlayer());
    }

    public void close(){
        this.getGui().close(getPlayer());
    }
}
