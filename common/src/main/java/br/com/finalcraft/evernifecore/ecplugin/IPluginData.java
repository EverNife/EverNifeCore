package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.api.common.IHasDelegate;

import java.io.File;

//TODO Rename to MetaInfo
public interface IPluginData extends IHasDelegate {

    public String getName();

    public String getVersion();

    public String getAuthor();

    public String getGroup();

    public File getDataFolder();

}
