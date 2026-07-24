package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;

/**
 * Gates a subcommand behind {@code ECSettings.PER_PLAYER_LOCALE}: while the feature is off the
 * subcommand is hidden from tab completion and does nothing if invoked directly. It is the guard
 * that keeps '/eclocale self' invisible and inert until an admin opts into per-player language.
 */
public class PerPlayerLocaleAccessValidation extends CMDAccessValidation {

    @Override
    public boolean onPreCommandValidation(AccessContext accessContext) {
        return ECSettings.PER_PLAYER_LOCALE;
    }

    @Override
    public boolean onPreTabValidation(AccessContext accessContext) {
        return ECSettings.PER_PLAYER_LOCALE;
    }
}
