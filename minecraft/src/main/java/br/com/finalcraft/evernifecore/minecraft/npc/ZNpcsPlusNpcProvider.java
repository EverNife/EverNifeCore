package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.waypoint.Reflect;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;

/**
 * ZNPCsPlus 2: packet NPCs with no server entity, {@code znpcsplus:<entry id>} - the id an admin chose, which keeps its
 * case in the plugin, so a lookup falls back to comparing ignoring case. Its API has no display name: the name is the id.
 */
public final class ZNpcsPlusNpcProvider extends ReflectedNpcProvider {

    public static final String ID = "znpcsplus";

    private static final String API = "lol.pyr.znpcsplus.api.NpcApiProvider";

    public ZNpcsPlusNpcProvider(Plugin core) {
        super(ID, "ZNPCsPlus", API, core, "lol.pyr.znpcsplus.api.event.NpcInteractEvent");
    }

    private static @Nullable Object entry(NpcRef ref) {
        Object registry = Reflect.call(Reflect.callStatic(Reflect.type(API), "get"), "getNpcRegistry");
        Object entry = Reflect.call(registry, "getById", ref.getId());
        if (entry == null) {
            for (Object id : (Collection<?>) Reflect.call(registry, "getAllIds")) {
                if (ref.getId().equalsIgnoreCase(String.valueOf(id))) {
                    return Reflect.call(registry, "getById", id);
                }
            }
        }
        return entry;
    }

    private static @Nullable Location location(@Nullable Object npc) {
        Object at = call(npc, "getLocation");
        World world = npc == null ? null : Bukkit.getWorld((String) call(npc, "getWorldName"));
        return at == null || world == null ? null : (Location) call(at, "toBukkitLocation", world);
    }

    @Override
    public Optional<WorldLocPos> getLocation(NpcRef ref) {
        return position(location(call(entry(ref), "getNpc")));
    }

    @Override
    public Optional<String> getName(NpcRef ref) {
        return Optional.ofNullable((String) call(entry(ref), "getId"));
    }

    @Override
    @Nullable Click click(Event event) {
        EntityContext.ClickType clickType = clickTypeOf(call(event, "getClickType"));
        if (clickType == null) {
            return null;
        }
        Object entry = call(event, "getEntry");
        Location at = location(call(entry, "getNpc"));
        return at == null ? null : new Click((Player) call(event, "getPlayer"), packetNpc(NpcRef.of(ID, (String) call(entry, "getId"))), at, clickType);
    }
}
