package br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation;

import jakarta.annotation.Nullable;

/**
 * A mountable command node whose verbs {@link NodeVerbAccess} gates on {@code <permissionBase>.<verb>}; a branch of
 * the node answers its node's base.
 */
public interface VerbPermissionBase {

    /** The base each verb's permission hangs from; null when the mount's permission is all there is. */
    @Nullable String getPermissionBase();
}
