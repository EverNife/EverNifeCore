package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nullable;

/**
 * Gates one verb of a mounted {@link RegionCommandNode} on {@code <permissionBase>.<verb>} when the node was given a
 * base. It runs before any argument is parsed, and on tab and help too, so a verb the sender may not run is neither
 * offered nor described. A verb of the {@code group} or {@code terrain} branch is gated by its own label
 * ({@code <base>.add}, {@code <base>.restore}).
 */
public final class RegionVerbAccess extends CMDAccessValidation {

    @Override
    public boolean onPreCommandValidation(AccessContext context) {
        String permission = verbPermission(context);
        return permission == null || FCMessageUtil.hasThePermission(context.getSender(), permission);
    }

    @Override
    public boolean onPreTabValidation(AccessContext context) {
        String permission = verbPermission(context);
        return permission == null || context.getSender().hasPermission(permission);
    }

    private static @Nullable String verbPermission(AccessContext context) {
        CMDMethodInterpreter interpreter = context.getInterpreter();
        if (interpreter == null) {
            return null;   //a branch with no method of its own; its leaves carry the check
        }
        RegionCommandNode node = RegionCommandNode.of(interpreter.getExecutor());
        return node == null ? null : node.getVerbPermission(context.getCmdData().getLabels()[0]);
    }
}
