package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import jakarta.annotation.Nonnull;

/**
 * The template of one command's usage line: the text, the hover and the permission that gates it.
 * There is exactly one per executable (or per node), built at registration and shared by every
 * sender - so it carries no path of its own. {@link #render(CommandPath)} is what turns it into
 * something sendable, and it hands back a NEW object every time: two players running the same
 * command at the same time must not read each other's target in the hover.
 */
public class HelpLineTemplate {

    private final LocaleMessageImp localeMessage;
    private final String permission;

    public HelpLineTemplate(LocaleMessageImp localeMessage, String permission) {
        this.localeMessage = localeMessage;
        this.permission = permission;
    }

    public LocaleMessage getLocaleMessage() {
        return this.localeMessage;
    }

    public String getPermission(){
        return permission;
    }

    /** This line spoken for one concrete path: {@code ${label}}, {@code ${path}}, {@code ${parentpath}} and {@code ${subcmd}} resolved. */
    public HelpLine render(@Nonnull CommandPath path) {
        return new HelpLine(this.localeMessage
                .addPlaceholder("label", path.getLabel())
                .addPlaceholder("path", path.joined())
                .addPlaceholder("parentpath", path.parentJoined())
                .addPlaceholder("subcmd", path.lastLiteral()));
    }
}
