package br.com.finalcraft.evernifecore.config.yaml.anntation;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

public interface Salvable {

    public void onConfigSave(ConfigSection section);

}
