package br.com.finalcraft.evernifecore.worldedit.clipboard.format;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class IFCClipboardManager {

    public final IFCClipboardFormat SCHEMATIC;
    public final IFCClipboardFormat SCHEM; //Only present on 1.13 and above

    public IFCClipboardManager(IFCClipboardFormat SCHEMATIC, @Nullable IFCClipboardFormat SCHEM) {
        this.SCHEMATIC = SCHEMATIC;
        this.SCHEM = SCHEM;
    }

    public @Nullable IFCClipboardFormat getFormat(File file) throws IOException {
        if (file.getName().endsWith(".schematic")) return SCHEMATIC;//On 1.7.10 if the file does not exist, it returns null
        if (SCHEM != null && SCHEM.isFormat(file)) return SCHEM;
        if (SCHEMATIC.isFormat(file)) return SCHEMATIC;
        return null;
    }

    public @Nullable IFCClipboardFormat findByAlias(String alias){
        if (SCHEM != null && SCHEM.getAliases().contains(alias)) return SCHEM;
        if (SCHEMATIC.getAliases().contains(alias)) return SCHEMATIC;
        return null;
    }
}
