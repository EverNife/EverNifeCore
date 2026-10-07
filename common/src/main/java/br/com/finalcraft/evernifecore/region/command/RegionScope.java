package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.region.RegionManager;

/**
 * What a region parser needs from the command it runs under to give an owner to a bare name: the manager whose
 * regions that command edits. A parser tests for this interface on {@code ParseCall.getExecutor()}, never for a
 * concrete node class - inside a nested branch the executor is the nested instance, which implements it too.
 */
public interface RegionScope {

    RegionManager getManager();
}
