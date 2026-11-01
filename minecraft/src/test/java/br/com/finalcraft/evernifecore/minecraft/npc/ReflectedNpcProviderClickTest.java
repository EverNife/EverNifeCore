package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Both clicks on an NPC raise {@code interact-npc}, each with its click type - read off the NPC plugin's event by reflection. */
public class ReflectedNpcProviderClickTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aFancyNpcsLeftClickRaisesInteractNpcAsLeftAndTheRightOneAsRight() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            FancyNpcsNpcProvider fancy = new FancyNpcsNpcProvider(null);
            Player alex = server.player("Alex", 0, 64, 0);
            Location at = server.at(3, 64, 3);

            EntityContext left = fancy.context(new FancyClick("LEFT_CLICK", alex, at));
            assertNotNull(left, "a punch raises interact-npc too");
            assertEquals(EntityContext.ClickType.LEFT, left.getClickType());
            assertEquals(NpcRef.of(FancyNpcsNpcProvider.ID, "guard"), left.getEntity().getNpc());

            EntityContext right = fancy.context(new FancyClick("RIGHT_CLICK", alex, at));
            assertNotNull(right);
            assertEquals(EntityContext.ClickType.RIGHT, right.getClickType());

            assertNull(fancy.context(new FancyClick("CUSTOM", alex, at)), "a trigger that is no click raises nothing");
        }
    }

    /** The shape of FancyNpcs' {@code NpcInteractEvent} that the provider reads. */
    public static final class FancyClick extends Event {

        private static final HandlerList HANDLERS = new HandlerList();

        private final String interactionType;
        private final Player player;
        private final Npc npc;

        FancyClick(String interactionType, Player player, Location at) {
            this.interactionType = interactionType;
            this.player = player;
            this.npc = new Npc(new Data(at));
        }

        public String getInteractionType() {
            return interactionType;
        }

        public Player getPlayer() {
            return player;
        }

        public Npc getNpc() {
            return npc;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class Npc {

        private final Data data;

        Npc(Data data) {
            this.data = data;
        }

        public Data getData() {
            return data;
        }
    }

    public static final class Data {

        private final Location location;

        Data(Location location) {
            this.location = location;
        }

        public Location getLocation() {
            return location;
        }

        public String getId() {
            return "guard";
        }
    }
}
