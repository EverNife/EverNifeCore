package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseEngine;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseOutcome;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import jakarta.annotation.Nonnull;

/**
 * The same parsers, answering about a config file instead of about a command line: a value nobody can
 * read is a line in the owning plugin's log, never a coloured chat message aimed at whoever happens to
 * be holding the console.
 */
final class ConfigParseEngine extends ParseEngine {

    private final ECPluginData ecPluginData;

    ConfigParseEngine(ECPluginData ecPluginData) {
        this.ecPluginData = ecPluginData;
    }

    @Override
    protected void report(@Nonnull ParseOutcome<?> outcome) {
        for (ILocaleMessageBase message : outcome.getResult().getReason()) {
            ecPluginData.getLog().warning(message.getFancyText(null).toPlainText());
        }

        Throwable cause = outcome.getResult().getCause();
        if (cause != null){
            //An internal error carries no reason to print - the stack IS the whole report, and it is
            //addressed to whoever owns the parser rather than to whoever wrote the file
            ecPluginData.getLog().warning("[" + outcome.getParserClass().getName() + "] failed while reading "
                    + outcome.getCall().describeArgument());
            cause.printStackTrace();
        }
    }
}
