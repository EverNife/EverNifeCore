package br.com.finalcraft.evernifecore.region.group;

/** What changed in an {@link AccessGroup}. A change of one of its flag values is reported as a flag change, not here. */
public enum GroupChange {
    CREATED, REMOVED, MEMBER_ADDED, MEMBER_REMOVED,
    /** A timed membership reached its deadline. */
    MEMBER_EXPIRED,
    PERMISSION_SET, PREDICATE_SET, PRIORITY_SET, CHECK_SET
}
