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
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("Failed to call PlayerController.savePlayerDataOnConfig(), this is a serious problem:");
                e.printStackTrace();
            }
        }
    }
}