package br.com.finalcraft.evernifecore.config.yaml.helper.smartloadable;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

public interface ISmartLoadSave<O> {

    public void onConfigSave(ConfigSection section, O value);

    public O onConfigLoad(ConfigSection section);

    public String onStringSerialize(O value);

    public O onStringDeserialize(String serializedValue);

    public boolean canSerializeToStringList();

}
