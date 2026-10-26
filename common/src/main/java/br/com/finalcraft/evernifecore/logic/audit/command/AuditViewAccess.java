package br.com.finalcraft.evernifecore.logic.audit.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nullable;

/**
 * Gates a mounted {@link AuditCommandNode} on {@code <permissionBase>.view}, before any argument is read, and on tab
 * and help too.
 */
public final class AuditViewAccess extends CMDAccessValidation {

    @Override
    public boolean onPreCommandValidation(AccessContext context) {
        String permission = viewPermission(context);
        return permission == null || FCMessageUtil.hasThePermission(context.getSender(), permission);
    }

    @Override
    public boolean onPreTabValidation(AccessContext context) {
        String permission = viewPermission(context);
        return permission == null || context.getSender().hasPermission(permission);
    }

    private static @Nullable String viewPermission(AccessContext context) {
        CMDMethodInterpreter interpreter = context.getInterpreter();
        if (interpreter == null || !(interpreter.getExecutor() instanceof AuditCommandNode)) {
            return null;
        }
        return ((AuditCommandNode) interpreter.getExecutor()).getViewPermission();
    }
}
