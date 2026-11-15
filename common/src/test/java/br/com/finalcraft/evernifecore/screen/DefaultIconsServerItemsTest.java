package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The stock buttons embed only the vanilla item; the server's config puts its own items in front of it. */
class DefaultIconsServerItemsTest {

    @AfterEach
    void teardown() {
        DefaultIcons.setServerCandidates(Collections.emptyMap());
    }

    private static List<String> chainOf(Icon icon) {
        return Stream.concat(Stream.of(icon.getItem()), icon.getFallbacks().stream())
                .map(ItemSpec::getMaterialId)
                .collect(Collectors.toList());
    }

    @Test
    void withoutAnEntryAButtonDrawsTheVanillaItem() {
        assertEquals(Arrays.asList("minecraft:spectral_arrow", "minecraft:redstone"), chainOf(DefaultIcons.previousPage()));
        assertEquals(Collections.singletonList("minecraft:paper"), chainOf(DefaultIcons.info()));
    }

    @Test
    void theServersItemsComeBeforeTheVanillaOne() {
        DefaultIcons.setServerCandidates(Collections.singletonMap("previous-page", Arrays.asList("MYMOD_BACK", "MYMOD_ARROW")));

        assertEquals(Arrays.asList("MYMOD_BACK", "MYMOD_ARROW", "minecraft:spectral_arrow", "minecraft:redstone"),
                chainOf(DefaultIcons.previousPage()));
        assertEquals(Arrays.asList("minecraft:spectral_arrow", "minecraft:redstone"), chainOf(DefaultIcons.nextPage()),
                "a button the server named nothing for keeps the vanilla item");
    }
}
