package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.io.*;

public class FCFileLogger {

    private PrintStream ps;

    public FCFileLogger(File rootFolder, String logFileName){
        File theLogFile = new File(rootFolder,logFileName);
        try {
            if (!theLogFile.exists()) {
                theLogFile.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(theLogFile, true);
            this.ps = new PrintStream(fos);
        } catch (FileNotFoundException e) {
            EverNifeCore.instance.getLogger().severe("Log file not found!");
            e.printStackTrace();
        } catch (IOException e) {
            EverNifeCore.instance.getLogger().severe("Could not create log file!");
            e.printStackTrace();
        }
    }

    public void log(String message){
        logLine(message);
    }

    public void logLine(String s) {
        ps.println(s);
    }

    public void close() {
        this.ps.close();
    }

}