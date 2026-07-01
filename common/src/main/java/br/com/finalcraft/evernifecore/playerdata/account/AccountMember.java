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
 * without a framework change - e.g. {@code "minecraft"}, {@code "hytale"}, {@code "discord"}.</p>
 */
@JsonAutoDetectFieldsOnly
@Getter
@EqualsAndHashCode(of = {"provider", "providerUid"})
public class AccountMember {

    private String provider;
    private String providerUid;

    @Setter
    private String name;

    public AccountMember() {
        //Jackson no-arg constructor
    }

    public AccountMember(String provider, String providerUid, String name) {
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.providerUid = Objects.requireNonNull(providerUid, "providerUid cannot be null");
        this.name = name;
    }

    @Override
    public String toString() {
        return "AccountMember{" + provider + ":" + providerUid + " (" + name + ")}";
    }
}
