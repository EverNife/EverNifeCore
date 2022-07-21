package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.gui.layout.IHasLayout;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.entity.Player;

import java.util.function.Function;

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

    public PlayerGui(P playerData) {
        this.player = playerData == null ? null : playerData.getPlayer();
        this.playerData = playerData;
    }

    public PlayerGui(P playerData, G gui) {
        this.player = playerData == null ? null : playerData.getPlayer();
        this.playerData = playerData;
        this.gui = gui;
    }

    public PlayerGui(G gui) {
        this.gui = gui;
    }

    protected void setupLayout(IHasLayout iHasLayout){
        setupLayout(iHasLayout, null);
    }

    protected void setupLayout(IHasLayout iHasLayout, Function<String,String> titleParser){
        String title = iHasLayout.layout().getTitle();
        if (titleParser != null){
            title = titleParser.apply(title);
        }
        setGui((G) FCGuiFactory.simple()
                .rows(iHasLayout.layout().getRows())
                .title(title)
                .disableAllInteractions()
                .create()
        );

        //Set Background
        iHasLayout.layout().getBackgroundIcons().forEach(layoutIcon -> layoutIcon.applyTo(this));
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
        this.player = playerData == null ? null : playerData.getPlayer();
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
        this.open();
    }

    public void close(){
        this.getGui().close(getPlayer());
    }

    public PlayerGui getPreviousGui() {
        return previousGui;
    }
}
