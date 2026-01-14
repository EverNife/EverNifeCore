package br.com.finalcraft.evernifecore.featherboard;

import be.maximvdw.featherboard.api.FeatherBoardAPI;
import br.com.finalcraft.evernifecore.EverNifeCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatherBoardUtils {

    public static Map<String,List<FeatherBoardTimer>> mapOfScoreBoards = new HashMap<String, List<FeatherBoardTimer>>();
    public static boolean apiLoaded = false;

    public static void initialize(){
        if (apiLoaded = Bukkit.getPluginManager().isPluginEnabled("FeatherBoard")){
            EverNifeCore.getLog().info("Integration to FeatherBoard enabled!");
        }
    }

    public static void showScoreBoard(Player player, String scoreBoardName, int seconds, int priority, boolean force){

        if (!apiLoaded){
            return;
        }

        if (FeatherBoardAPI.isToggled(player) == false && force == false){
            return;
        }

        String keyName = player.getName();
        List<FeatherBoardTimer> playersBoards = mapOfScoreBoards.get(keyName);
        if (playersBoards == null){
            playersBoards = new ArrayList<>();
        }

        boolean highProority = true;

        for (FeatherBoardTimer featherBoardTimer : playersBoards){
            if (featherBoardTimer.scoreBoardName.equalsIgnoreCase(scoreBoardName)){
                featherBoardTimer.addSeconds(seconds);
                return;
            }
            if (featherBoardTimer.priority > priority){
                highProority = false;
            }
        }

        if (!highProority){
            return;
        }

        FeatherBoardTimer featherBoardTimer = new FeatherBoardTimer(player,scoreBoardName,seconds,priority,force);
        playersBoards.add(featherBoardTimer);
        mapOfScoreBoards.put(keyName,playersBoards);
        featherBoardTimer.runTaskTimerAsynchronously(EverNifeCore.instance, 1L, 20);
    }

    public static void hideScoreBoard(Player player, String scoreBoardName){

        if (!apiLoaded){
            return;
        }

        String keyName = player.getName();

        List<FeatherBoardTimer> playersBoards = mapOfScoreBoards.getOrDefault(keyName, new ArrayList<FeatherBoardTimer>());

        for (FeatherBoardTimer featherBoardTimer : playersBoards){
            if (featherBoardTimer.scoreBoardName.equalsIgnoreCase(scoreBoardName)){
                featherBoardTimer.remove();
                return;
            }
        }
    }

}


