package br.com.finalcraft.evernifecore.version;

import org.bukkit.Bukkit;

public enum MCVersion {

    v1_7_R1(171, "v1_7"),
    v1_7_R2(172, "v1_7"),
    v1_7_R3(173, "v1_7"),
    v1_7_R4(174, "v1_7"),
    v1_8_R1(181, "v1_8"),
    v1_8_R2(182, "v1_8"),
    v1_8_R3(183, "v1_8"),
    v1_9_R1(191, "v1_9"),
    v1_9_R2(192, "v1_9"),
    v1_10_R1(1101, "v1_10"),
    v1_11_R1(1111, "v1_11"),
    v1_11_R2(1112, "v1_11"),
    v1_12_R1(1121, "v1_12"),
    v1_12_R2(1122, "v1_12"),
    v1_13_R1(1131, "v1_13"),
    v1_13_R2(1132, "v1_13"),
    v1_14_R1(1141, "v1_14"),
    v1_14_R2(1142, "v1_14"),
    v1_15_R1(1151, "v1_15"),
    v1_15_R2(1152, "v1_15"),
    v1_16_R1(1161, "v1_16"),
    v1_16_R2(1162, "v1_16"),
    v1_16_R3(1163, "v1_16"),
    v1_17_R1(1171, "v1_17"),
    v1_18_R1(1181, "v1_18"),
    v1_18_R2(1182, "v1_18"),
    v1_19_R1(1191, "v1_19"),
    v1_19_R2(1192, "v1_19"),
    v1_19_R3(1193, "v1_19"),
    ;

    private static MCVersion currentVersion = null;

    private static MCVersion calculateVersion() {
        String[] svPackage = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        String svPackageVersionName = svPackage[svPackage.length - 1];
        for (MCVersion version : MCVersion.values()) {
            if (version.name().equalsIgnoreCase(svPackageVersionName)) {
                return version;
            }
        }
        throw new IllegalStateException("Failed to calculate the Minecraft Version of this Server!");
    }

    public static boolean isLowerEquals1_7_10(){
        return isCurrentLowerEquals(MCVersion.v1_7_R4);
    }

    public static boolean isLowerEquals1_12(){
        return isCurrentLowerEquals(MCVersion.v1_12_R2);
    }

    public static boolean isLowerEquals1_16(){
        return isCurrentLowerEquals(MCVersion.v1_16_R3);
    }

    // Operations
    private int value;
    private String shortVersion;

    MCVersion(Integer value, String ShortVersion) {
        this.value = value;
        this.shortVersion = ShortVersion;
    }

    public Integer getValue() {
        return value;
    }

    public String getShortVersion() {
        return shortVersion;
    }

    public static MCVersion getCurrent() {
        return currentVersion != null
                ? currentVersion
                : (currentVersion = calculateVersion());
    }

    public boolean isLower(MCVersion otherVersion) {
        return this.getValue() < otherVersion.getValue();
    }

    public boolean isLowerEquals(MCVersion otherVersion) {
        return this.getValue() <= otherVersion.getValue();
    }

    public boolean isEqual(MCVersion otherVersion) {
        return this.getValue() == otherVersion.getValue();
    }

    public boolean isHigher(MCVersion otherVersion) {
        return this.getValue() > otherVersion.getValue();
    }

    public boolean isHigherEquals(MCVersion otherVersion) {
        return this.getValue() >= otherVersion.getValue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Static Utility Functions to make life easier!
    // -----------------------------------------------------------------------------------------------------------------

    public static boolean isCurrentLower(MCVersion otherVersion) {
        return getCurrent().isLower(otherVersion);
    }

    public static boolean isCurrentLowerEquals(MCVersion otherVersion) {
        return getCurrent().isLowerEquals(otherVersion);
    }

    public static boolean isCurrentEqual(MCVersion otherVersion) {
        return getCurrent().isEqual(otherVersion);
    }

    public static boolean isCurrentHigher(MCVersion otherVersion) {
        return getCurrent().isHigher(otherVersion);
    }

    public static boolean isCurrentHigherEquals(MCVersion otherVersion) {
        return getCurrent().isHigherEquals(otherVersion);
    }

}
