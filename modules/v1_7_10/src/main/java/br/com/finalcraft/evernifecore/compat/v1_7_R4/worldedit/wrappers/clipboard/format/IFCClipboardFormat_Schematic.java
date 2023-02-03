package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.clipboard.format;

import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.clipboard.ImpFCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.clipboard.format.IFCClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class IFCClipboardFormat_Schematic extends IFCClipboardFormat {

    @Override
    public Set<String> getAliases() {
        return ClipboardFormat.SCHEMATIC.getAliases();
    }

    @Override
    public FCBlockArrayClipboard getReaderAndRead(InputStream inputStream) throws IOException {
        return new ImpFCBlockArrayClipboard((BlockArrayClipboard) ClipboardFormat.SCHEMATIC.getReader(inputStream).read(null));
    }

    @Override
    public void getWriterAndWrite(OutputStream outputStream, Clipboard clipboard) throws IOException {
        ClipboardFormat.SCHEMATIC.getWriter(outputStream).write(clipboard, null);
    }

    @Override
    public boolean isFormat(File file) {
        return ClipboardFormat.SCHEMATIC.isFormat(file);
    }

}
