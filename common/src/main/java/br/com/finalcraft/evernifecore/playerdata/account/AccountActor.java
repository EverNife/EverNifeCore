package br.com.finalcraft.evernifecore.playerdata.account;

import java.util.Locale;
import java.util.Objects;

/**
 * Who (or what) an account operation is attributed to - the origin an {@link Accounts}
 * link/unlink/merge carries onto its events, and what a member's {@code linkedBy} records. There is
 * no runtime authority behind it: the caller states the actor and the framework only carries it. It
 * is never null in the API - use {@link #system()} for an operation with no human or integration
 * behind it.
 *
 * <p>{@link #describe()} is the compact form stored on {@link AccountMember#getLinkedBy()}
 * ({@code "admin:Petrus"}, {@code "integration:finalcraftlogin"}, bare {@code "system"}); the
 * structured form ({@link #getKind()} + {@link #getDetail()}) rides the account events.</p>
 */
public final class AccountActor {

    /** The broad category of an account operation's origin. */
    public enum Kind {
        /** A server operator acting in-game or through the console (e.g. {@code /ecaccount link}). */
        ADMIN,
        /** An external integration or bridge - a registration site, a Discord bot, ... */
        INTEGRATION,
        /** The framework itself, with no human or integration behind the operation. */
        SYSTEM
    }

    private static final AccountActor SYSTEM = new AccountActor(Kind.SYSTEM, null);

    private final Kind kind;
    private final String detail; // nullable: the admin name, the integration id, ...

    private AccountActor(Kind kind, String detail) {
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.detail = detail;
    }

    /** An admin/operator, identified by {@code name} (nullable when the name is unknown). */
    public static AccountActor admin(String name) {
        return new AccountActor(Kind.ADMIN, name);
    }

    /** An external integration, identified by {@code id} (nullable when unspecified). */
    public static AccountActor integration(String id) {
        return new AccountActor(Kind.INTEGRATION, id);
    }

    /** The framework acting on its own behalf - no human or integration involved. */
    public static AccountActor system() {
        return SYSTEM;
    }

    public Kind getKind() {
        return kind;
    }

    /** The actor detail (admin name, integration id), or {@code null} when none was given. */
    public String getDetail() {
        return detail;
    }

    /**
     * The compact {@code kind[:detail]} descriptor stored on a member's {@code linkedBy}:
     * {@code "admin:Petrus"}, {@code "integration:finalcraftlogin"}, or a bare {@code "system"} /
     * {@code "admin"} when there is no detail.
     */
    public String describe() {
        String tag = kind.name().toLowerCase(Locale.ROOT);
        return (detail == null || detail.isEmpty()) ? tag : tag + ":" + detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountActor)) return false;
        AccountActor that = (AccountActor) o;
        return kind == that.kind && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, detail);
    }

    @Override
    public String toString() {
        return "AccountActor{" + describe() + "}";
    }
}
