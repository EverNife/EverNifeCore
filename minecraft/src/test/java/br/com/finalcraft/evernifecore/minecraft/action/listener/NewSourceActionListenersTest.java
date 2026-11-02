package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.BreedActionEvent;
import br.com.finalcraft.evernifecore.action.event.CommandActionEvent;
import br.com.finalcraft.evernifecore.action.event.ConsumeActionEvent;
import br.com.finalcraft.evernifecore.action.event.CraftActionEvent;
import br.com.finalcraft.evernifecore.action.event.EnchantActionEvent;
import br.com.finalcraft.evernifecore.action.event.ExploreActionEvent;
import br.com.finalcraft.evernifecore.action.event.FishActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.action.event.ShearActionEvent;
import br.com.finalcraft.evernifecore.action.event.SmeltActionEvent;
import br.com.finalcraft.evernifecore.action.event.TameActionEvent;
import br.com.finalcraft.evernifecore.action.event.XpGainActionEvent;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each of the twelve sources the regions never needed has its listener: it builds the context of its source from the
 * native event, posts the {@code Post} of what went through, and - where Bukkit can cancel the event - a cancelled
 * {@code Pre} cancels it.
 */
class NewSourceActionListenersTest {

    @TempDirNobodyCleans
    Path tempDir;

    private RegionServer server;
    private ActionListeners bridge;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();

    @BeforeEach
    void open() {
        server = RegionServer.open(tempDir);
        bridge = ActionListeners.start(server.server.getPluginData());
    }

    @AfterEach
    void close() {
        try {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
            bridge.stop();
        } finally {
            server.close();
        }
    }

    private <E extends ActionEvent<?>> List<E> hear(Class<E> type) {
        List<E> heard = new ArrayList<>();
        subscriptions.add(ECEventBus.global().subscribe(type, heard::add));
        return heard;
    }

    private <E extends ActionEvent<?>> void veto(Class<E> preType) {
        subscriptions.add(ECEventBus.global().subscribe(preType, event -> ((ECCancellable) event).setCancelled(true)));
    }

    private Item item(Material material, int amount, UUID id) {
        return RegionServer.entity(Item.class, EntityType.ITEM, server.at(50, 5, 5))
                .on("getItemStack", args -> new ItemStack(material, amount))
                .returning("getUniqueId", id)
                .build();
    }

    @Test
    void aDeathPostsTheKillWithTheCreditedKillerAndHasNoPre() {
        Player hunter = server.player("hunter", 50, 5, 5);
        Zombie zombie = RegionServer.entity(Zombie.class, EntityType.ZOMBIE, server.at(51, 5, 5)).returning("getKiller", hunter).build();
        List<KillActionEvent.Post> kills = hear(KillActionEvent.Post.class);

        server.call(new EntityDeathEvent(zombie, null, new ArrayList<>()));

        assertEquals(1, kills.size());
        assertEquals(Actor.getPlayerKey(), kills.get(0).getActor().getKey());
        assertEquals(TypeKey.parse("minecraft:zombie"), kills.get(0).getContext().getVictim().getKey());
        assertFalse(ActionListeners.PRE_SOURCES.contains(EventSource.KILL), "a death cannot be cancelled");
    }

    @Test
    void aDeathWithNoDamageRecordedIsTheEnvironmentsKill() {
        Cow cow = RegionServer.entity(Cow.class, EntityType.COW, server.at(51, 5, 5)).build();
        List<KillActionEvent.Post> kills = hear(KillActionEvent.Post.class);

        server.call(new EntityDeathEvent(cow, null, new ArrayList<>()));

        assertEquals(KillActionListener.UNKNOWN_DEATH, kills.get(0).getActor().getKey());
        assertNull(kills.get(0).getPlayer());
    }

    private CraftItemEvent craft(Player crafter, InventoryAction action, int resultAmount) {
        Recipe recipe = Doubles.of(Recipe.class).on("getResult", args -> new ItemStack(Material.STICK, resultAmount)).build();
        InventoryView view = Doubles.of(InventoryView.class).returning("getPlayer", crafter).build();
        return new CraftItemEvent(recipe, view, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, action);
    }

    @Test
    void aCraftPostsWhatTheClickProducedAndAClickThatCraftsNothingIsNoCraft() {
        Player crafter = server.player("crafter", 50, 5, 5);
        List<CraftActionEvent.Post> crafts = hear(CraftActionEvent.Post.class);

        server.call(craft(crafter, InventoryAction.PLACE_ONE, 4));
        assertTrue(crafts.isEmpty(), "placing an item on the result slot crafts nothing");

        server.call(craft(crafter, InventoryAction.PICKUP_ALL, 4));
        assertEquals(1, crafts.size());
        assertEquals(4, crafts.get(0).getContext().getAmount());
        assertEquals(TypeKey.parse("minecraft:stick"), crafts.get(0).getContext().getItemKey());
        assertNull(crafts.get(0).getContext().getItemEntityId());
    }

    @Test
    void aCraftCarriesTheResultWithItsNameAndLoreCopiedOnlyWhenAsked() {
        ItemStack result = new ItemStack(Material.STICK, 2);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName("§6Wand");
        meta.setLore(Arrays.asList("§7Crafted", "§7by hand"));
        result.setItemMeta(meta);
        Recipe recipe = Doubles.of(Recipe.class).returning("getResult", result).build();
        InventoryView view = Doubles.of(InventoryView.class).returning("getPlayer", server.player("crafter", 50, 5, 5)).build();
        List<CraftActionEvent.Post> crafts = hear(CraftActionEvent.Post.class);

        server.call(new CraftItemEvent(recipe, view, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        //the stack changed after the context was built: a context that copied it up front would not see this
        ItemMeta renamed = result.getItemMeta();
        renamed.setDisplayName("§6Wand of Ages");
        result.setItemMeta(renamed);
        ItemSpec item = crafts.get(0).getContext().getItem();

        assertEquals("§6Wand of Ages", item.getDisplayName());
        assertEquals(Arrays.asList("§7Crafted", "§7by hand"), item.getLore());
        ItemMeta carried = item.getNativeItem().getItemStack().getItemMeta();
        assertEquals("§6Wand of Ages", carried.getDisplayName(), "the native item is the result itself");
        assertEquals(Arrays.asList("§7Crafted", "§7by hand"), carried.getLore());
        assertNotSame(result, item.getNativeItem().getItemStack(), "copied on the first ask");
        assertSame(item, crafts.get(0).getContext().getItem(), "and only once");
    }

    @Test
    void aSmeltCarriesNoItemTheFurnaceEventNeverHad() {
        List<SmeltActionEvent.Post> smelts = hear(SmeltActionEvent.Post.class);

        server.call(new FurnaceExtractEvent(server.player("cook", 50, 5, 5), server.world.getBlockAt(52, 5, 5), Material.IRON_INGOT, 3, 1));

        assertNull(smelts.get(0).getContext().getItem());
    }

    @Test
    void aCancelledCraftPreCancelsTheCraft() {
        veto(CraftActionEvent.Pre.class);
        List<CraftActionEvent.Post> crafts = hear(CraftActionEvent.Post.class);

        assertTrue(server.call(craft(server.player("crafter", 50, 5, 5), InventoryAction.PICKUP_ALL, 1)).isCancelled());
        assertTrue(crafts.isEmpty());
    }

    @Test
    void aFurnaceExtractionPostsTheSmeltWhereTheFurnaceStands() {
        List<SmeltActionEvent.Post> smelts = hear(SmeltActionEvent.Post.class);

        server.call(new FurnaceExtractEvent(server.player("cook", 50, 5, 5), server.world.getBlockAt(52, 5, 5), Material.IRON_INGOT, 3, 1));

        assertEquals(1, smelts.size());
        assertEquals(3, smelts.get(0).getContext().getAmount());
        assertEquals(TypeKey.parse("minecraft:iron_ingot"), smelts.get(0).getContext().getItemKey());
        assertEquals(52, (int) smelts.get(0).getContext().getPosition().getX());
        assertFalse(ActionListeners.PRE_SOURCES.contains(EventSource.SMELT), "FurnaceExtractEvent is no Cancellable on any era");
    }

    @Test
    void aCatchIsFishedAndACastIsNot() {
        Player angler = server.player("angler", 50, 5, 5);
        UUID catchId = UUID.randomUUID();
        Item cod = item(Material.COD, 1, catchId);
        List<FishActionEvent.Post> catches = hear(FishActionEvent.Post.class);

        server.call(new PlayerFishEvent(angler, null, null, PlayerFishEvent.State.FISHING));
        assertTrue(catches.isEmpty(), "a cast catches nothing");

        server.call(new PlayerFishEvent(angler, cod, null, PlayerFishEvent.State.CAUGHT_FISH));
        assertEquals(1, catches.size());
        assertEquals(TypeKey.parse("minecraft:cod"), catches.get(0).getContext().getItemKey());
        assertEquals(catchId, catches.get(0).getContext().getItemEntityId(), "the catch flying to the player");
    }

    @Test
    void aCancelledConsumePreKeepsTheItemAndPostsNothing() {
        veto(ConsumeActionEvent.Pre.class);
        List<ConsumeActionEvent.Post> meals = hear(ConsumeActionEvent.Post.class);

        assertTrue(server.call(new PlayerItemConsumeEvent(server.player("eater", 50, 5, 5), new ItemStack(Material.BREAD))).isCancelled());
        assertTrue(meals.isEmpty());
    }

    @Test
    void aConsumePostsWhatWasEaten() {
        List<ConsumeActionEvent.Post> meals = hear(ConsumeActionEvent.Post.class);

        server.call(new PlayerItemConsumeEvent(server.player("eater", 50, 5, 5), new ItemStack(Material.BREAD)));

        assertEquals(TypeKey.parse("minecraft:bread"), meals.get(0).getContext().getItemKey());
    }

    @Test
    void experienceGainedIsPostedAndAChangeOfNothingIsNot() {
        Player player = server.player("learner", 50, 5, 5);
        List<XpGainActionEvent.Post> gains = hear(XpGainActionEvent.Post.class);

        server.call(new PlayerExpChangeEvent(player, 0));
        server.call(new PlayerExpChangeEvent(player, 7));

        assertEquals(1, gains.size());
        assertEquals(7, gains.get(0).getContext().getAmount());
    }

    @Test
    void anEnchantmentPostsTheItemWhereTheTableStands() {
        Player wizard = server.player("wizard", 50, 5, 5);
        InventoryView view = Doubles.of(InventoryView.class).returning("getPlayer", wizard).build();
        List<EnchantActionEvent.Post> enchants = hear(EnchantActionEvent.Post.class);

        server.call(new EnchantItemEvent(wizard, view, server.world.getBlockAt(53, 5, 5), new ItemStack(Material.DIAMOND_SWORD), 30,
                Collections.emptyMap(), null, 0, 0));

        assertEquals(1, enchants.size());
        assertEquals(TypeKey.parse("minecraft:diamond_sword"), enchants.get(0).getContext().getItemKey());
        assertEquals(53, (int) enchants.get(0).getContext().getPosition().getX());
    }

    @Test
    void aTamePostsTheTamerAndTheAnimal() {
        Player tamer = server.player("tamer", 50, 5, 5);
        Wolf wolf = RegionServer.entity(Wolf.class, EntityType.WOLF, server.at(51, 5, 5)).build();
        List<TameActionEvent.Post> tames = hear(TameActionEvent.Post.class);

        server.call(new EntityTameEvent(wolf, tamer));

        assertEquals(Actor.getPlayerKey(), tames.get(0).getActor().getKey());
        assertEquals(TypeKey.parse("minecraft:wolf"), tames.get(0).getContext().getEntity().getKey());
    }

    @Test
    void aBreedPostsTheNewbornWithWhoeverFedTheParents() {
        Player farmer = server.player("farmer", 50, 5, 5);
        Cow calf = RegionServer.entity(Cow.class, EntityType.COW, server.at(51, 5, 5)).build();
        Cow mother = RegionServer.entity(Cow.class, EntityType.COW, server.at(51, 5, 6)).build();
        List<BreedActionEvent.Post> births = hear(BreedActionEvent.Post.class);

        server.call(new EntityBreedEvent(calf, mother, mother, farmer, new ItemStack(Material.WHEAT), 3));
        server.call(new EntityBreedEvent(calf, mother, mother, null, null, 0));

        assertEquals(2, births.size(), "1.21 has the breed event: the listener is on");
        assertEquals(Actor.getPlayerKey(), births.get(0).getActor().getKey());
        assertEquals(BreedActionListener.NO_BREEDER, births.get(1).getActor().getKey(), "nobody fed them");
        assertNull(ActionListeners.servedSources(), "from 1.12 every core source is raised");
    }

    @Test
    void aShearPostsTheAnimalSheared() {
        Sheep sheep = RegionServer.entity(Sheep.class, EntityType.SHEEP, server.at(51, 5, 5)).build();
        List<ShearActionEvent.Post> shears = hear(ShearActionEvent.Post.class);

        server.call(new PlayerShearEntityEvent(server.player("shepherd", 50, 5, 5), sheep));

        assertEquals(TypeKey.parse("minecraft:sheep"), shears.get(0).getContext().getEntity().getKey());
    }

    @Test
    void aCommandPostsTheLabelTypedAndACancelledPreKeepsItFromRunning() {
        Player player = server.player("typist", 50, 5, 5);
        List<CommandActionEvent.Post> commands = hear(CommandActionEvent.Post.class);

        server.call(new PlayerCommandPreprocessEvent(player, "/Essentials:Spawn now", new HashSet<>()));
        assertEquals(1, commands.size());
        assertTrue(commands.get(0).getContext().getNames().contains("spawn"));
        assertEquals("/Essentials:Spawn now", commands.get(0).getContext().getCommandLine());

        veto(CommandActionEvent.Pre.class);
        assertTrue(server.call(new PlayerCommandPreprocessEvent(player, "/spawn", new HashSet<>())).isCancelled());
        assertEquals(1, commands.size(), "the cancelled command posted nothing");
    }

    @Test
    void aStepIntoAnotherChunkIsExploredAndOneInsideItIsNot() {
        Player walker = server.player("walker", 1, 5, 5);
        List<ExploreActionEvent.Post> steps = hear(ExploreActionEvent.Post.class);

        server.call(new PlayerMoveEvent(walker, server.at(1, 5, 5), server.at(14, 5, 5)));
        assertTrue(steps.isEmpty(), "still chunk 0");

        server.call(new PlayerMoveEvent(walker, server.at(14, 5, 5), server.at(17, 5, 5)));
        assertEquals(1, steps.size());
        assertEquals(ChunkPos.of(0, 0), steps.get(0).getContext().getFrom());
        assertEquals(ChunkPos.of(1, 0), steps.get(0).getContext().getTo());
    }
}
