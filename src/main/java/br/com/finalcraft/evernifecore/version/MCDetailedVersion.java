package br.com.finalcraft.evernifecore.version;

import org.bukkit.Bukkit;

import java.util.Arrays;

public enum MCDetailedVersion {

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

    private static final MCDetailedVersion currentVersion;

    static {
        String[] svPackage = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        String svPackageVersionName = svPackage[svPackage.length - 1];
        currentVersion = Arrays.stream(MCDetailedVersion.values())
                .filter(mcDetailedVersion -> mcDetailedVersion.name().equalsIgnoreCase(svPackageVersionName))
                .findFirst()
                .orElse(MCDetailedVersion.v1_19_R3); //Assume it's a newer version!
    }

    // Operations
    private int value;
    private String shortVersion;

    MCDetailedVersion(Integer value, String ShortVersion) {
        this.value = value;
        this.shortVersion = ShortVersion;
    }

    public Integer getValue() {
        return value;
    }

    public String getShortVersion() {
        return shortVersion;
    }

    public static MCDetailedVersion getCurrent() {
        return currentVersion;
    }

    public boolean isLower(MCDetailedVersion otherVersion) {
        return this.getValue() < otherVersion.getValue();
    }

    public boolean isLowerEquals(MCDetailedVersion otherVersion) {
        return this.getValue() <= otherVersion.getValue();
    }

    public boolean isEqual(MCDetailedVersion otherVersion) {
        return this.getValue() == otherVersion.getValue();
    }

    public boolean isHigher(MCDetailedVersion otherVersion) {
        return this.getValue() > otherVersion.getValue();
    }

    public boolean isHigherEquals(MCDetailedVersion otherVersion) {
        return this.getValue() >= otherVersion.getValue();
    }

}
