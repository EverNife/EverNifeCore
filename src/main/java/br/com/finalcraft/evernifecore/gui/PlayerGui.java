package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.entity.Player;

public class PlayerGui<P extends IPlayerData, G extends BaseGui> {

    private Player player = null;
    private P playerData = null;
    private G gui = null;
    private transient PlayerGui previousGui = null;

    public PlayerGui() {

    }

    @Deprecated
    public PlayerGui(Player player, P playerData, G gui) {
        this.player = player;
        this.playerData = playerData;
        this.gui = gui;
    }

    public PlayerGui(P playerData, G gui) {
        this.player = playerData == null ? null : playerData.getPlayer();
        this.playerData = playerData;
        this.gui = gui;
    }

    public PlayerGui(G gui) {
        this.gui = gui;
    }

    protected PlayerGui<P, G> setGui(G gui) {
        this.gui = gui;
        return this;
    }

    protected PlayerGui<P, G> setPlayer(Player player) {
        this.player = player;
        return this;
    }

    protected PlayerGui<P, G> setPlayerData(P playerData) {
        this.playerData = playerData;
        this.player = playerData != null ? player.getPlayer() : null;
        return this;
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

    public void open(PlayerGui previousGui){
        this.previousGui = previousGui;
        this.getGui().open(getPlayer());
    }

    public void close(){
        this.getGui().close(getPlayer());
    }

    public PlayerGui getPreviousGui() {
        return previousGui;
    }
}
