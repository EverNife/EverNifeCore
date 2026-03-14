package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class FCFileLogger {

    private PrintStream ps;

    public FCFileLogger(File rootFolder, String logFileName){
        File theLogFile = new File(rootFolder,logFileName);
        try {
            if (!theLogFile.exists()) {
                theLogFile.getParentFile().mkdirs();
                theLogFile.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(theLogFile, true);
            this.ps = new PrintStream(fos);
        } catch (IOException e) {
            EverNifeCore.getLog().severe("Failed to create the FCFileLogger at %s", theLogFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public PrintStream getPs() {
        return ps;
    }

    public void log(String message){
        this.getPs().println(message);
    }

    public void close() {
        this.ps.close();
    }

}