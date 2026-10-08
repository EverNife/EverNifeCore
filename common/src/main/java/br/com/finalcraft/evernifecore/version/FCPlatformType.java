package br.com.finalcraft.evernifecore.version;

import org.apache.commons.lang3.StringUtils;

public enum FCPlatformType {
    MINECRAFT,
    HYTALE;

    private static FCPlatformType CURRENT_PLATFORM = null;

    private final String name;

    FCPlatformType() {
        this.name = StringUtils.capitalize(this.name().toLowerCase());
    }

    public String getName() {
        return name;
    }

    public static FCPlatformType getCurrent() {
        if (CURRENT_PLATFORM == null) {
            CURRENT_PLATFORM = detectCurrentPlatform();
        }

        return CURRENT_PLATFORM;
    }

    public static boolean isMinecraft(){
        return getCurrent() == MINECRAFT;
    }

    public static boolean isHytale(){
        return getCurrent() == HYTALE;
    }

    //The resource path a Bukkit server is recognized by. It MUST carry the .class suffix: a jar
    //holds no entry for the bare binary name, so a suffix-less probe never matches and every
    //server, Bukkit included, gets detected as Hytale.
    static final String BUKKIT_MARKER = "org/bukkit/Bukkit.class";

    private static FCPlatformType detectCurrentPlatform() {
        return detectPlatform(FCPlatformType.class.getClassLoader());
    }

    static FCPlatformType detectPlatform(ClassLoader loader) {
        //Every loader up the chain is asked, not just our own: Bukkit's PluginClassLoader answers
        //getResource from the plugin's jar alone, and a server booted through a launcher (Paper's
        //paperclip) keeps its classes in a parent loader that the system classpath never sees.
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (current.getResource(BUKKIT_MARKER) != null) {
                return MINECRAFT;
            }
        }

        return ClassLoader.getSystemResource(BUKKIT_MARKER) != null ? MINECRAFT : HYTALE;
    }

}
