package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

/**
 * How a platform hands a player the selection tool. Recognising the tool when it is clicked is the platform's
 * own listener's job, before it calls {@link SelectionTool#handleClick}.
 */
public interface ISelectionItems {

    /** The answer of a platform that has no item to give: every call is a no-op. */
    ISelectionItems NONE = (player, spec) -> {};

    /** Hands {@code player} the native item {@code spec} describes, already carrying the selection mark. */
    void give(FPlayer player, ItemSpec spec);
}
