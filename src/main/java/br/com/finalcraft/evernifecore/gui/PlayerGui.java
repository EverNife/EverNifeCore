package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.entity.Player;

public class PlayerGui<P extends IPlayerData> {

    protected final Player player;
    protected final P playerData;
    protected final Gui gui;

    public PlayerGui(P playerData, Gui gui) {
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

    public Gui getGui() {
        return gui;
    }

    public void open(){
        this.gui.open(player);
    }

    public void close(){
        this.gui.close(player);
    }
}
