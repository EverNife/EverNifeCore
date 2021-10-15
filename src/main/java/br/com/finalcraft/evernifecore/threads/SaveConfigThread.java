package br.com.finalcraft.evernifecore.threads;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;

public class SaveConfigThread extends Thread {

    public SaveConfigThread() {
        setName("EverNifeCore - Config Save");
    }

    public static SaveConfigThread saveConfigThread;

    public static void initialize(){
        if (saveConfigThread == null){
            saveConfigThread = new SaveConfigThread();
            saveConfigThread.start();
        }
    }

    public static void shutdown(){
        if (saveConfigThread != null) saveConfigThread.interrupt();
        saveConfigThread = null;

        PlayerController.savePlayerDataOnConfig();
    }

    @Override
    public void run() {
        int sleepTime = 30000;//30 Seconds

        while (true) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                return;
            }
            try {
                PlayerController.savePlayerDataOnConfig();
            } catch (Exception e) {
                EverNifeCore.warning("Failed to save PlayerData, this is a serious problem:");
                e.printStackTrace();
            }
        }
    }

}