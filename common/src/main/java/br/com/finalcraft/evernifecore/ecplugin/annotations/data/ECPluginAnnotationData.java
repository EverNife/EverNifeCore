package br.com.finalcraft.evernifecore.ecplugin.annotations.data;

import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The mutable form of an {@link ECPlugin} declaration. The accessors are fluent on purpose: this
 * type mirrors the annotation element for element, so reading it looks the same as reading the
 * annotation it was built from.
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class ECPluginAnnotationData {

    private String spigotID;
    private String bstatsID;

    public ECPluginAnnotationData(ECPlugin ecPlugin) {
        this.spigotID = ecPlugin.spigotID();
        this.bstatsID = ecPlugin.bstatsID();
    }

}
