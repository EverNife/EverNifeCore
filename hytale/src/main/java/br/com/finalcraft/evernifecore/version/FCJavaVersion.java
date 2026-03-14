package br.com.finalcraft.evernifecore.version;

public enum FCJavaVersion {
    JAVA_0(1.5f, "0.9"),
    JAVA_1(1.1f, "1"),
    JAVA_2(1.2f, "2"),
    JAVA_3(1.3f, "3"),
    JAVA_4(1.4f, "4"),
    JAVA_5(1.5f, "5"),
    JAVA_6(1.6f, "6"),
    JAVA_7(1.7f, "7"),
    JAVA_8(1.8f, "8"),
    JAVA_9(9.0f, "9"),
    JAVA_10(10.0f, "10"),
    JAVA_11(11.0f, "11"),
    JAVA_12(12.0f, "12"),
    JAVA_13(13.0f, "13"),
    JAVA_14(14.0f, "14"),
    JAVA_15(15.0f, "15"),
    JAVA_16(16.0f, "16"),
    JAVA_17(17.0f, "17"),
    JAVA_18(18.0f, "18"),
    JAVA_19(19.0f, "19"),
    JAVA_20(20, "20"),
    JAVA_21(21, "21"),
    JAVA_22(22, "22"),
    JAVA_23(23, "23"),
    JAVA_24(24, "24"),
    JAVA_25(25, "25"),

    UNKNOWN_JAVA_VERSION(999999, System.getProperty("java.version"));

    private static FCJavaVersion CURRENT_VERSION = null;

    private final float floatValue;
    private final String name;

    FCJavaVersion(final float floatValue, final String name) {
        this.floatValue = floatValue;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public float getFloatValue() {
        return floatValue;
    }

    @Override
    public String toString() {
        return name;
    }

    public static FCJavaVersion getCurrent() {
        if (CURRENT_VERSION == null) {
            CURRENT_VERSION = detectCurrentJavaVersion();
        }

        return CURRENT_VERSION;
    }

    private static FCJavaVersion detectCurrentJavaVersion() {
        String version = System.getProperty("java.version");

        float numeric;
        try {
            if (version.startsWith("1.")) {
                // old style, e.g., "1.8.0_402"
                numeric = Float.parseFloat(version.substring(0, 3)); // -> 1.8
            } else {
                // new style, e.g., "17.0.10" or "21"
                String[] parts = version.split("\\.");
                numeric = Float.parseFloat(parts[0]); // -> 17, 21, etc.
            }
        } catch (NumberFormatException e) {
            return UNKNOWN_JAVA_VERSION;
        }

        for (FCJavaVersion javaVersion : values()) {
            if (javaVersion.floatValue == numeric) {
                return javaVersion; // Found exact match
            }
        }

        return UNKNOWN_JAVA_VERSION;
    }

    public static boolean isLower(FCJavaVersion otherVersion) {
        return FCJavaVersion.getCurrent().getFloatValue() < otherVersion.getFloatValue();
    }

    public static boolean isLowerEquals(FCJavaVersion otherVersion) {
        return FCJavaVersion.getCurrent().getFloatValue() <= otherVersion.getFloatValue();
    }

    public static boolean isEqual(FCJavaVersion otherVersion) {
        return FCJavaVersion.getCurrent().getFloatValue() == otherVersion.getFloatValue();
    }

    public static boolean isHigher(FCJavaVersion otherVersion) {
        return FCJavaVersion.getCurrent().getFloatValue() > otherVersion.getFloatValue();
    }

    public static boolean isHigherEquals(FCJavaVersion otherVersion) {
        return FCJavaVersion.getCurrent().getFloatValue() >= otherVersion.getFloatValue();
    }
}
