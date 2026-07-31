package br.com.finalcraft.evernifecore.commands.finalcmd.help;

import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * The help of ONE node, as far as registration can know it: the header and the line of each child.
 * What it still lacks is the line the sender actually typed - the alias they used and the tokens the
 * captures ate - so it is the shared, immutable half, and {@link #render(CommandPath)} turns it into
 * the {@link HelpContext} that can be printed. Same split as
 * {@link HelpLineTemplate} and {@link HelpLine}, for the same reason: one object per registration,
 * one per dispatch, and neither can be mistaken for the other.
 */
public class HelpContextTemplate {

    private final String helpHeader;
    private final CommandNode node;
    private final List<HelpLineTemplate> helpLineTemplates;

    public HelpContextTemplate(String helpHeader, CommandNode node) {
        this.helpHeader = helpHeader;
        this.node = node;
        List<HelpLineTemplate> lines = new ArrayList<>();
        for (CommandNode child : node.getChildren()) {
            HelpLineTemplate template = child.getHelpLineTemplate();
            if (template != null){
                lines.add(template);
            }
        }
        this.helpLineTemplates = ImmutableList.copyOf(lines);
    }

    /** This node's help as one dispatch will show it, under the alias that dispatch was called by. */
    public HelpContext render(@Nonnull CommandPath path) {
        return new HelpContext(this, path);
    }

    public String getHelpHeader() {
        return helpHeader;
    }

    public CommandNode getNode() {
        return node;
    }

    public List<HelpLineTemplate> getHelpLineTemplates() {
        return helpLineTemplates;
    }

    public HelpLineTemplate getHelpLineTemplate(int index) {
        return helpLineTemplates.get(index);
    }

    public int size() {
        return helpLineTemplates.size();
    }
}
