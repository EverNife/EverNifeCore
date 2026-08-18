package br.com.finalcraft.evernifecore.playerdata.account;

import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * One identity that belongs to an {@link Account}: a platform/provider uuid plus a display name.
 *
 * <p>The pair {@code (provider, providerUid)} is UNIQUE across the whole account collection -
 * a given platform identity may belong to at most one account, and it IS this member's identity
 * ({@code equals}/{@code hashCode} are keyed by it). The invariant is enforced in code when a member
 * is added to an account (see {@link Account#addMember(AccountMember)}); it is not a storage
 * constraint, so callers must not add the same {@code (provider, providerUid)} to two accounts.</p>
 *
 * <p>{@code provider} is an open string (not an enum) so new platforms/services can be plugged in
 * without a framework change - e.g. {@code "minecraft"}, {@code "hytale"}, {@code "discord"}. Only
 * the platform ones are named by
 * {@link br.com.finalcraft.evernifecore.api.common.providers.platform.PlatformId PlatformId}: a tag
 * such as {@code "discord"} is a perfectly valid provider that no platform ever reports.</p>
 */
@JsonAutoDetectFieldsOnly
@Getter
@EqualsAndHashCode(of = {"provider", "providerUid"})
public class AccountMember {

    private String provider;
    private String providerUid;

    @Setter
    private String name;

    /** Epoch millis this identity was first linked into an account, or {@code 0} when unknown. */
    private long linkedAt;

    /**
     * Who linked this identity, as {@link AccountActor#describe()} ({@code "admin:Petrus"},
     * {@code "integration:finalcraftlogin"}, ...), or {@code null} when unknown - an identity present
     * since before audit stamping, or one that was never explicitly linked.
     */
    private String linkedBy;

    public AccountMember() {
        //Jackson no-arg constructor
    }

    public AccountMember(String provider, String providerUid, String name) {
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.providerUid = Objects.requireNonNull(providerUid, "providerUid cannot be null");
        this.name = name;
    }

    /**
     * Stamps who linked this identity and when, the first time it joins an account via a link
     * operation (first-link-wins: a later merge that moves it does not rewrite the original stamp).
     * The account layer is the only caller.
     */
    void stampLinked(long millis, String linkedBy) {
        if (linkedAt == 0) {
            this.linkedAt = millis;
            this.linkedBy = linkedBy;
        }
    }

    @Override
    public String toString() {
        return "AccountMember{" + provider + ":" + providerUid + " (" + name + ")}";
    }
}
