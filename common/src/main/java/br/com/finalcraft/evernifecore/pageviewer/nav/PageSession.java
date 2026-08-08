package br.com.finalcraft.evernifecore.pageviewer.nav;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.CommandMessageContext;
import br.com.finalcraft.evernifecore.fancytext.RenderContext;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** One reader's open page: the viewer they are reading and the command scope it was opened in. */
public final class PageSession {

    private final UUID handle;
    private final UUID reader;
    private final PageViewer<?> viewer;

    // The scope the page was FIRST sent in. Navigation runs inside /ecpage, and a line citing
    // ${label} must still name the command that produced the page.
    private final CommandMessageContext origin;

    PageSession(UUID handle, UUID reader, PageViewer<?> viewer, CommandMessageContext origin) {
        this.handle = handle;
        this.reader = reader;
        this.viewer = viewer;
        this.origin = origin;
    }

    public UUID getHandle() {
        return handle;
    }

    public PageViewer<?> getViewer() {
        return viewer;
    }

    public boolean isOwnedBy(@Nullable FCommandSender sender) {
        return sender != null && sender.getUniqueId() != null && reader.equals(sender.getUniqueId());
    }

    /** Sends {@code page} of this session's viewer, still speaking for the command that opened it. */
    public void goTo(int page, FCommandSender recipient) {
        viewer.send(RenderContext.of(recipient, origin), page, recipient);
    }
}
