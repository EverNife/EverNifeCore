package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * The help of ONE node, for ONE dispatch: a line per child the sender may reach, under the alias that
 * dispatch was called by. Only the level below is rendered - a four-level tree printed at once scrolls
 * the line that mattered off the chat, so each node answers for its own children and every branch is
 * one click away.
 * <p>
 * It carries the {@link CommandPath} it was rendered for, which is why {@link #sendTo(FCommandSender)}
 * takes nothing else: whoever asks for the help of a command already walked that command.
 */
public class HelpContext {

    @FCLocale(lang = LocaleType.EN_US, text = "§3§oMove the mouse over the commands to see their description!", hover = "§7Move the mouse over the commands to see their description!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§3§oPasse o mouse em cima dos comandos para ver a descrição!", hover = "§7Passe o mouse em cima dos comandos para ver a descrição!")
    public static LocaleMessage HOLD_MOUSE_OVER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7Page §e${page}§7/§e${pages}§7 - §b${next_command}§7 for the next one")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Página §e${page}§7/§e${pages}§7 - §b${next_command}§7 para a próxima")
    public static LocaleMessage HELP_PAGE_FOOTER;

    private final HelpContextTemplate template;
    private final CommandPath path;

    HelpContext(@Nonnull HelpContextTemplate template, @Nonnull CommandPath path) {
        this.template = template;
        this.path = path;
    }

    public String getHelpHeader() {
        return template.getHelpHeader();
    }

    public CommandNode getNode() {
        return template.getNode();
    }

    public List<HelpLineTemplate> getHelpLineTemplates() {
        return template.getHelpLineTemplates();
    }

    public HelpLineTemplate getHelpLineTemplate(int index) {
        return template.getHelpLineTemplate(index);
    }

    public int size() {
        return template.size();
    }

    /** The line this help was reached by - the alias typed, plus every token the path consumed. */
    public CommandPath getPath() {
        return path;
    }

    /** @see #sendTo(FCommandSender, int) */
    public void sendTo(FCommandSender sender) {
        sendTo(sender, 1);
    }

    /**
     * Prints the children this sender can actually reach, under this help's own path.
     * <p>
     * The gate is the whole chain, not the child alone: a sender who cannot pass this node - or any
     * node above it - sees nothing, because a line they cannot run is not help. The ancestry is
     * shared by every child, so it is walked once, and each child then adds only its own.
     * <p>
     * A help that fits in one page is printed whole, with no page indicator: paging is for the tree
     * that would otherwise scroll off the screen, and announcing "page 1/1" is noise.
     *
     * @param page which page to print, 1-based; anything outside the range prints the nearest page
     * that exists, because a number nobody can reach is not a reason to say nothing
     */
    public void sendTo(FCommandSender sender, int page) {

        boolean isPlayer = sender.isPlayer();

        for (CommandNode ancestor = getNode(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!isPlayer && ancestor.isPlayerOnly()){
                FCMessageUtil.needsToBeAPlayer(sender);
                return;
            }
            if (!isReachable(ancestor, sender)){
                FCMessageUtil.needsThePermission(sender);
                return;
            }
        }

        List<HelpLine> reachableLines = new ArrayList<>();
        boolean anythingHiddenForNotBeingAPlayer = false;

        for (CommandNode child : getNode().getChildren()) {
            HelpLineTemplate template = child.getHelpLineTemplate();
            if (template == null){
                continue;
            }

            if (!isPlayer && child.isPlayerOnly()){
                anythingHiddenForNotBeingAPlayer = true;
                continue;
            }

            if (!isReachable(child, sender)){
                continue;
            }

            //The child's usage path already carries every ancestor label and captured argument, so a
            //line rendered four levels down still reads as the whole command the sender has to type.
            reachableLines.add(template.render(child.toUsagePath(path.getLabel(), true)));
        }

        if (reachableLines.isEmpty()){
            //Nothing to show and two reasons it could be: say the one that is actually true, because a
            //console sent hunting for a permission node that would not have helped is worse than silence
            if (anythingHiddenForNotBeingAPlayer){
                FCMessageUtil.needsToBeAPlayer(sender);
            }else {
                FCMessageUtil.needsThePermission(sender);
            }
            return;
        }

        int pageSize = Math.max(1, ECSettings.COMMAND_HELP_PAGE_SIZE);
        int pages = (reachableLines.size() + pageSize - 1) / pageSize;
        int currentPage = Math.min(Math.max(page, 1), pages);
        int from = (currentPage - 1) * pageSize;

        sender.sendMessage(getHelpHeader().isEmpty() ? "§2§m-----------------------------------------------------" : getHelpHeader());
        for (HelpLine helpLine : reachableLines.subList(from, Math.min(from + pageSize, reachableLines.size()))) {
            helpLine.sendTo(sender);
        }
        sender.sendMessage("");
        if (pages > 1){
            sendPageFooter(sender, currentPage, pages);
        }
        HOLD_MOUSE_OVER.send(sender);
        sender.sendMessage("§2§m-----------------------------------------------------");
    }

    /**
     * The one line a paged help adds: where the sender is, and the exact command that moves them on -
     * this help's own path plus a page number, so it works at any depth and wraps around at the end.
     */
    private void sendPageFooter(FCommandSender sender, int currentPage, int pages) {
        String nextCommand = path.full() + " " + HelpWords.all().get(0) + " " + (currentPage < pages ? currentPage + 1 : 1);

        HELP_PAGE_FOOTER
                .addPlaceholder("page", currentPage)
                .addPlaceholder("pages", pages)
                .addPlaceholder("next_command", nextCommand)
                .send(sender);
    }

    /**
     * Whether this one node's own ACCESS declaration lets the sender through - its permission and its
     * validations. {@code playerOnly} is asked separately by both callers, because it is the one refusal
     * that has to be told apart out loud: no permission ever grants it.
     * <p>
     * The executable's own {@code @FinalCMD.Execute} declaration is deliberately NOT consulted: a node
     * that runs a method also holds children, and a denied {@code @Execute} says nothing about them.
     * A leaf declares one thing anyway, so its method and its node are the same answer.
     */
    private static boolean isReachable(CommandNode node, FCommandSender sender){
        return CMDAccessValidation.allows(sender, node, CMDAccessValidation.AccessMode.LIST);
    }
}
