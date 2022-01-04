package br.com.finalcraft.evernifecore.autoupdater;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class BukkitPluginJar {

    final File file;
    String pluginName;
    String version;

    public BukkitPluginJar(File file) throws IOException, IllegalArgumentException {
        this.file = file;

        try(ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            //Plugin Data
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isPluginYML(entry.getName())) {
                    InputStream stream = zipFile.getInputStream(entry);
                    BufferedReader buf = new BufferedReader(new InputStreamReader(stream));
                    String line = buf.readLine();
                    while(line != null){
                        if (line.startsWith("name:")){
                            pluginName = line.replace("name:","").replace(" ", "");
                        } else if (line.startsWith("version:")){
                            version = line.replace("version:","").replace(" ", "");
                        }
                        if (pluginName != null && version != null){
                            break;
                        }
                        line = buf.readLine();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (pluginName == null || version == null){
            throw new IllegalArgumentException("This jar does not have a proper plugin.yml file!");
        }
    }

    private boolean isPluginYML(String name) {
        String splitName = ((name.contains("/")) ? name.substring(name.lastIndexOf("/")).replace("/", "") : name); // Caso o zip esteja usando o "\" esse zip está quebrado, já que o padrão é o "/".
        return splitName.equals("plugin.yml");
    }

}
