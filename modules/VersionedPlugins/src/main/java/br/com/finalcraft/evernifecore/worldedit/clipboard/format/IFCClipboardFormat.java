package br.com.finalcraft.evernifecore.worldedit.clipboard.format;

import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public abstract class IFCClipboardFormat {

    public abstract Set<String> getAliases();

    public abstract FCBlockArrayClipboard getReaderAndRead(InputStream inputStream) throws IOException;

    public abstract void getWriterAndWrite(OutputStream outputStream, Clipboard clipboard) throws IOException;

    public abstract boolean isFormat(File file);

}
