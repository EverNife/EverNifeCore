package br.com.finalcraft.evernifecore.thread;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;

public class SaveConfigThread extends SimpleThread {
    public static SaveConfigThread INSTANCE = new SaveConfigThread();

    @Override
    public void run() throws InterruptedException {
        while (true) {
            Thread.sleep(30000);//30 Seconds
            try {
                PlayerController.savePlayerDataOnConfig();
            } catch (Exception e) {
                EverNifeCore.warning("Failed to save PlayerData, this is a serious problem:");
                e.printStackTrace();
            }
        }
    }
}