package br.com.finalcraft.evernifecore.minecraft.version;

public enum MCVersion {
    v1_7_10(MCDetailedVersion.v1_7_R4),
    v1_12(MCDetailedVersion.v1_12_R1),
    v1_13(MCDetailedVersion.v1_13_R2),
    v1_16(MCDetailedVersion.v1_16_R3),
    v1_19(MCDetailedVersion.v1_19_R3),
    v1_20(MCDetailedVersion.v1_20_R1),
    v1_21(MCDetailedVersion.v1_21_R1),
    // Minecraft left the 1.x scheme behind: after 1.21.11 the releases are named by year, so this
    // bucket is a whole year of them and not a single minor release like the ones above it.
    v26(MCDetailedVersion.v26_1)
    ;

    public static MCDetailedVersion getCurrent() {
        return MCDetailedVersion.getCurrent();
    }

    private final MCDetailedVersion detailedVersion;

    MCVersion(MCDetailedVersion detailedVersion) {
        this.detailedVersion = detailedVersion;
    }

    public MCDetailedVersion getDetailedVersion() {
        return detailedVersion;
    }

    // Comparisons against this coarse ladder answer per release family, so every 1.16.x server is
    // v1_16 and every 26.2.x server is one version.
    private static int compareCurrentTo(MCVersion otherVersion) {
        return MCDetailedVersion.getCurrent().compareFamily(otherVersion.getDetailedVersion());
    }

    public static boolean isLower(MCVersion otherVersion) {
        return compareCurrentTo(otherVersion) < 0;
    }

    public static boolean isLowerEquals(MCVersion otherVersion) {
        return compareCurrentTo(otherVersion) <= 0;
    }

    public static boolean isEqual(MCVersion otherVersion) {
        return compareCurrentTo(otherVersion) == 0;
    }

    public static boolean isHigher(MCVersion otherVersion) {
        return compareCurrentTo(otherVersion) > 0;
    }

    public static boolean isHigherEquals(MCVersion otherVersion) {
        return compareCurrentTo(otherVersion) >= 0;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Comparing with Detailed Version
    // -----------------------------------------------------------------------------------------------------------------

    public static boolean isLower(MCDetailedVersion otherVersion) {
        return MCDetailedVersion.getCurrent().isLower(otherVersion);
    }

    public static boolean isLowerEquals(MCDetailedVersion otherVersion) {
        return MCDetailedVersion.getCurrent().isLowerEquals(otherVersion);
    }

    public static boolean isEqual(MCDetailedVersion otherVersion) {
        return MCDetailedVersion.getCurrent().isEqual(otherVersion);
    }

    public static boolean isHigher(MCDetailedVersion otherVersion) {
        return MCDetailedVersion.getCurrent().isHigher(otherVersion);
    }

    public static boolean isHigherEquals(MCDetailedVersion otherVersion) {
        return MCDetailedVersion.getCurrent().isHigherEquals(otherVersion);
    }

}
