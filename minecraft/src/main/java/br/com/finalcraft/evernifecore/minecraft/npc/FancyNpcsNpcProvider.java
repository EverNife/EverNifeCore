package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.waypoint.Reflect;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * FancyNpcs: packet NPCs with no server entity, {@code fancynpcs:<npc id>} - the id of its {@code NpcData}, a UUID.
 * Only the click is heard of them: {@code de.oliver.fancynpcs.api.events.NpcInteractEvent}, left and right.
 */
public final class FancyNpcsNpcProvider extends ReflectedNpcProvider {

    public static final String ID = "fancynpcs";

    private static final String API = "de.oliver.fancynpcs.api.FancyNpcsPlugin";

    public FancyNpcsNpcProvider(Plugin core) {
        super(ID, "FancyNpcs", API, core, "de.oliver.fancynpcs.api.events.NpcInteractEvent");
    }

    private static @Nullable Object data(NpcRef ref) {
        Object manager = Reflect.call(Reflect.callStatic(Reflect.type(API), "get"), "getNpcManager");
        return call(Reflect.call(manager, "getNpcById", ref.getId()), "getData");
    }

    @Override
    public Optional<WorldLocPos> getLocation(NpcRef ref) {
        return position((Location) call(data(ref), "getLocation"));
    }

    @Override
    public Optional<String> getName(NpcRef ref) {
        Object data = data(ref);
        Object shown = call(data, "getDisplayName");
        return Optional.ofNullable((String) (shown == null ? call(data, "getName") : shown));
    }

    @Override
    @Nullable Click click(Event event) {
        EntityContext.ClickType clickType = clickTypeOf(call(event, "getInteractionType"));
        if (clickType == null) {
            return null;
        }
        Object data = call(call(event, "getNpc"), "getData");
        Location at = (Location) call(data, "getLocation");
        return at == null ? null : new Click((Player) call(event, "getPlayer"), packetNpc(NpcRef.of(ID, (String) call(data, "getId"))), at, clickType);
    }
}
