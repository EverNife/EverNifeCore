package br.com.finalcraft.evernifecore.region.shape;

import java.util.Locale;

/** The kinds of {@link Shape}; {@link #getId()} is the {@code type} value a stored shape carries. */
public enum ShapeType {
    CUBOID,
    POLYGON,
    WORLD;

    /** The lowercase id written in the {@code type} field of a stored shape. */
    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
