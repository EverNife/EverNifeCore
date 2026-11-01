package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.action.listener.McActionContexts;
import br.com.finalcraft.evernifecore.minecraft.waypoint.Reflect;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Citizens 2: NPCs are real entities, {@code citizens:<numeric id>}. The clicks heard are Citizens' own
 * {@code NPCRightClickEvent} and {@code NPCLeftClickEvent}: Citizens cancels the Bukkit interact of an NPC that runs
 * commands, so listening to the Bukkit events would miss those talks.
 */
public final class CitizensNpcProvider extends ReflectedNpcProvider {

    public static final String ID = "citizens";
    /** The metadata Citizens - and most NPC plugins after it - tag their entities with. */
    public static final String CITIZENS_NPC_METADATA = "NPC";

    private static final String API = "net.citizensnpcs.api.CitizensAPI";
    static final String LEFT_CLICK_EVENT = "net.citizensnpcs.api.event.NPCLeftClickEvent";

    public CitizensNpcProvider(Plugin core) {
        super(ID, "Citizens", API, core, "net.citizensnpcs.api.event.NPCRightClickEvent", LEFT_CLICK_EVENT);
    }

    /** Whether {@code entity} carries the {@code NPC} metadata: the cheap check, before any reflection. */
    public static boolean isTaggedNpc(Entity entity) {
        return entity.hasMetadata(CITIZENS_NPC_METADATA);
    }

    private static Object registry() {
        return Reflect.callStatic(Reflect.type(API), "getNPCRegistry");
    }

    private static @Nullable Object npc(NpcRef ref) {
        try {
            return Reflect.call(registry(), "getById", Integer.parseInt(ref.getId()));
        } catch (NumberFormatException notCitizens) {
            return null;
        }
    }

    @Override
    public Optional<NpcRef> resolve(NativeEntity entity) {
        Entity bukkit = entity.getEntity();
        if (!isTaggedNpc(bukkit)) {
            return Optional.empty();
        }
        Object npc = Reflect.call(registry(), "getNPC", bukkit);
        return npc == null ? Optional.empty() : Optional.of(NpcRef.of(ID, String.valueOf(call(npc, "getId"))));
    }

    @Override
    public Optional<NativeEntity> find(NpcRef ref) {
        Object npc = npc(ref);
        if (npc == null || !Boolean.TRUE.equals(call(npc, "isSpawned"))) {
            return Optional.empty();
        }
        Entity entity = (Entity) call(npc, "getEntity");
        return entity == null ? Optional.empty() : Optional.of(new NativeEntity(entity));
    }

    @Override
    public Optional<WorldLocPos> getLocation(NpcRef ref) {
        return position((Location) call(npc(ref), "getStoredLocation"));
    }

    @Override
    public Optional<String> getName(NpcRef ref) {
        return Optional.ofNullable((String) call(npc(ref), "getFullName"));
    }

    @Override
    @Nullable Click click(Event event) {
        Entity entity = (Entity) call(call(event, "getNPC"), "getEntity");
        if (entity == null) {
            return null;
        }
        EntityContext.ClickType clickType = LEFT_CLICK_EVENT.equals(event.getClass().getName())
                ? EntityContext.ClickType.LEFT : EntityContext.ClickType.RIGHT;
        return new Click((Player) call(event, "getClicker"), McActionContexts.actor(entity), entity.getLocation(), clickType);
    }
}
