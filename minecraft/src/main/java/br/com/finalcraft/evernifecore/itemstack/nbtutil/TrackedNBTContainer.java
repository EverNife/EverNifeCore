package br.com.finalcraft.evernifecore.itemstack.nbtutil;

import de.tr7zw.changeme.nbtapi.NBTContainer;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In the FCItemFactory its sometimes necessary to remove the tags
 * AFTER the building process with merge() is done, to prevent some bugs.
 *
 * This class will keep track of tags that were removed from the original NBT
 */
public class TrackedNBTContainer extends NBTContainer {

    final Set<String> initialTags;
    final Set<String> manuallyRemoved = new HashSet<>();

    public TrackedNBTContainer() {
        initialTags = new HashSet<>();
    }

    public TrackedNBTContainer(Object nbt) {
        super(nbt);
        initialTags = this.getKeys();
    }

    public TrackedNBTContainer(InputStream inputsteam) {
        super(inputsteam);
        initialTags = this.getKeys();
    }

    public TrackedNBTContainer(String nbtString) {
        super(nbtString);
        initialTags = this.getKeys();
    }

    @Override
    public void removeKey(String key) {
        super.removeKey(key);
        manuallyRemoved.add(key);
    }

    public List<String> getRemovedTags() {
        return Stream.concat(initialTags.stream(), manuallyRemoved.stream())
                .filter(tag -> !this.hasKey(tag))
                .collect(Collectors.toList());
    }
}
