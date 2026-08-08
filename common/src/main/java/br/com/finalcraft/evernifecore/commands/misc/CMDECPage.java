package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.pageviewer.PageRegistry;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageSession;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageSessionManager;

/** What every page button of the core runs: turn the page named by {@code page-ref} to {@code page}. */
public class CMDECPage {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThis page is no longer open. Run the command again.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cEsta página não está mais aberta. Rode o comando de novo.")
    private static LocaleMessage PAGE_EXPIRED;

    @FinalCMD(
            aliases = {"ecpage"}
    )
    public void onCommand(FCommandSender sender, @Arg("<page-ref>") String pageRef, @Arg("<page>") Integer page) {

        PageViewer<?> viewer = PageRegistry.find(pageRef);
        if (viewer != null) {
            //A registered page is reachable by anyone who may run the command that shows it: the id
            //carries no reader, so there is nothing here that could belong to somebody else.
            viewer.send(page, sender);
            return;
        }

        PageSession session = PageSessionManager.find(pageRef);

        //A session another reader owns answers exactly like one that never existed: knowing the
        //handle of someone else's page must not be the same as being able to page it.
        if (session == null || !session.isOwnedBy(sender)) {
            PAGE_EXPIRED.send(sender);
            return;
        }

        PageSessionManager.renew(session);
        session.goTo(page, sender);
    }
}
