package br.com.finalcraft.evernifecore.logger;

import java.util.logging.Level;

public interface ILogAdapter {

    public void info(String string);

    public void warning(String string);

    public void severe(String string);

    public void log(Level level, String string);

}
