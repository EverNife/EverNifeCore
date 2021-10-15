package br.com.finalcraft.evernifecore.featherboard;

import be.maximvdw.featherboard.api.FeatherBoardAPI;
import br.com.finalcraft.evernifecore.EverNifeCore;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

class FeatherBoardTimer extends BukkitRunnable {

    protected boolean firstLoop = true;

    protected final Player player;
    protected String scoreBoardName;
    protected long timeToEnd;
    protected int priority;
    protected boolean force;
    protected boolean hasBeenForced;

    public void addSeconds(int seconds){
        timeToEnd = timeToEnd + (1000 * seconds );
    }

    public void remove(){
        timeToEnd = 0;
    }

    public FeatherBoardTimer(Player player, String scoreBoardName, int seconds, int priority, boolean force) {
        this.player = player;
        this.scoreBoardName = scoreBoardName;
        this.timeToEnd = System.currentTimeMillis() + 1000 * seconds;
        this.priority = priority;
        this.force = force;

        if (force && !FeatherBoardAPI.isToggled(player) ){
            hasBeenForced = true;
        }

    }

    private void terminate(){
        FeatherBoardUtils.mapOfScoreBoards.get(player.getName()).remove(this);

        if (hasBeenForced && FeatherBoardAPI.isToggled(player)){
            FeatherBoardAPI.toggle(player, true);
        }
        this.cancel();
    }

    private boolean condition(){
        return true;
    }


    @Override
    public void run() {

        if (!player.isOnline()){
            this.terminate();
            return;
        }

        if (!condition()){
            this.terminate();
            return;
        }

        if (!FeatherBoardAPI.isToggled(player)) {
            if (firstLoop) {
                if (!force) {
                    this.terminate();
                    return;
                }
                FeatherBoardAPI.toggle(player, true);
            } else {
                this.terminate();
                return;
            }
        }

        if (firstLoop){
            FeatherBoardAPI.showScoreboard(player, scoreBoardName);
            EverNifeCore.info("fb show " + player.getName() + " " + scoreBoardName);
            firstLoop = false;
            return;
        }

        if (System.currentTimeMillis() >= timeToEnd){
            FeatherBoardAPI.removeScoreboardOverride(player,scoreBoardName);
            this.terminate();
        }
    }
}