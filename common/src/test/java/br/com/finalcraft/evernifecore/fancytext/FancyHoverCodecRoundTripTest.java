package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverRegistry;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverType;
import br.com.finalcraft.everyconfig.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A {@link FancyHover} type a plugin integrator registers at runtime, with a codec, survives a
 * save -&gt; load -&gt; save -&gt; load cycle: the codec persists it through the registry instead of
 * dropping it. Compared field by field through the {@link FancyHover} interface, because
 * EveryConfig's binder swallows a decode exception and would let a silent data loss pass otherwise.
 */
@ECoreTest
public class FancyHoverCodecRoundTripTest {

    private static final String TYPE_ID = "custom_roundtrip";

    @BeforeAll
    static void setUp() {
        if (!FancyHoverRegistry.registeredIds().contains(TYPE_ID)) {
            FancyHoverRegistry.register(FancyHoverType.<CustomHover>of(TYPE_ID,
                            custom -> HoverEvent.showText(Component.text(custom.payload())))
                    .withCodec(CustomHover::payload, CustomHover::new));
        }
    }

    private Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("data.yml"));
    }

    @Test
    void customHoverTypeRoundTripsThroughTheCodec(@TempDir Path dir) {
        FancySegment original = new FancySegment("§aHover me");
        original.setHover(new CustomHover("secret-payload-42"));

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        Config reopened = open(dir);
        FancyText firstRead = reopened.getValue("msg", FancyText.class);
        FancyHover firstHover = firstRead.getHover();
        assertNotNull(firstHover, "the custom hover must survive the first read, not be dropped");
        assertEquals(TYPE_ID, firstHover.typeId(), "the hover type id must survive");
        assertInstanceOf(CustomHover.class, firstHover, "the hover must decode back to its own type");
        assertEquals("secret-payload-42", ((CustomHover) firstHover).payload(), "the payload must survive");

        // write back what was read, read again: a second cycle must keep the same value.
        reopened.setValue("msg", firstRead);
        reopened.save();
        FancyHover secondHover = open(dir).getValue("msg", FancyText.class).getHover();
        assertEquals(TYPE_ID, secondHover.typeId());
        assertEquals("secret-payload-42", ((CustomHover) secondHover).payload(),
                "the payload must survive a second save->load cycle");
    }

    /** A plugin-owned hover value: opaque payload, its own type id, reconstructed by the codec. */
    static final class CustomHover implements FancyHover {
        private final String payload;

        CustomHover(String payload) {
            this.payload = payload;
        }

        String payload() {
            return payload;
        }

        @Override
        public String typeId() {
            return TYPE_ID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomHover)) return false;
            return Objects.equals(payload, ((CustomHover) o).payload);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(payload);
        }
    }
}
