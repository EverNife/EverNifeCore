package br.com.finalcraft.evernifecore.group;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Moves groups between the servers of a network (a proxy). The core ships no implementation: a plugin registers its
 * own with {@link Groups#registerNetwork}, and until then {@link Groups#network()} answers one whose every call fails
 * with {@link GroupNetworkUnavailableException} - ask {@link Groups#hasNetwork()} beforehand.
 *
 * <p><b>Threads:</b> any; the futures complete wherever the implementation finishes.</p>
 */
public interface GroupNetwork {

    /** Sends the online members of {@code group} to {@code server}: who moved, and who stayed with the reason why. */
    CompletableFuture<SendResult> send(Group group, String server);

    /** The server {@code player} is on anywhere in the network; empty when offline everywhere. */
    Optional<String> getServer(UUID player);

    /**
     * One shot: sends the group to the server its leader is on. Following the leader on every move is a product's
     * mode, built over this and {@link #getServer}.
     */
    CompletableFuture<SendResult> follow(Group group);
}
