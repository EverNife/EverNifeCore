package br.com.finalcraft.evernifecore.group;

/** What every call of {@link Groups#network()} fails with while no plugin registered a {@link GroupNetwork}. */
public final class GroupNetworkUnavailableException extends IllegalStateException {

    static final String MESSAGE = "No GroupNetwork is registered on this server, so groups cannot be sent across the network."
            + " Install a plugin that provides one or register your own with Groups.registerNetwork(owner, impl);"
            + " Groups.hasNetwork() tells beforehand.";

    public GroupNetworkUnavailableException() {
        super(MESSAGE);
    }
}
