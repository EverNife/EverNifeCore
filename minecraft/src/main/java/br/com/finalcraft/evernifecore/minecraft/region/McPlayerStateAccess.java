package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.playerstate.ActiveEffect;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Every capability, through the Bukkit player: {@code setPlayerTime} (ramped one step per tick), {@code setPlayerWeather},
 * flight, {@link GameMode} (only the modes this server version has), potion effects by their Bukkit name, health, food
 * and {@code Bukkit.dispatchCommand}.
 */
public final class McPlayerStateAccess implements IPlayerStateAccess {

    private static final long TICK_MILLIS = 50;

    private final BiPredicate<CommandSender, String> dispatcher;
    private final Map<UUID, BukkitTask> ramps = new ConcurrentHashMap<>();

    public McPlayerStateAccess() {
        this((sender, command) -> Bukkit.dispatchCommand(sender == null ? Bukkit.getConsoleSender() : sender, command));
    }

    /** @param dispatcher runs a command as the sender, or as the console when the sender is null */
    McPlayerStateAccess(BiPredicate<CommandSender, String> dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean supports(Capability capability) {
        return true;
    }

    private static Player bukkit(FPlayer player) {
        Player bukkit = ((MinecraftFPlayer<?>) player).getPlayer();
        if (bukkit == null) {
            throw new IllegalStateException(player.getName() + " is not online, so there is no Bukkit player to read or change.");
        }
        return bukkit;
    }

    // ---- TIME ----

    @Override
    public @Nullable PlayerTime getTime(FPlayer player) {
        Player bukkit = bukkit(player);
        if (bukkit.isPlayerTimeRelative() && bukkit.getPlayerTimeOffset() == 0) {
            return null;
        }
        return new PlayerTime(bukkit.getPlayerTimeOffset(), bukkit.isPlayerTimeRelative());
    }

    @Override
    public void setTime(FPlayer player, @Nullable PlayerTime time, Duration transition) {
        Player bukkit = bukkit(player);
        cancelRamp(bukkit.getUniqueId());
        if (time == null) {
            bukkit.resetPlayerTime();
            return;
        }
        int steps = (int) (transition.toMillis() / TICK_MILLIS);
        if (steps <= 0) {
            bukkit.setPlayerTime(time.getTicks(), time.isRelative());
            return;
        }
        //walk the client's clock from where it is to the target, one step per tick; the last step lands exactly on it
        long from = bukkit.getPlayerTime();
        long to = time.getTicks();
        int[] step = {0};
        ramps.put(bukkit.getUniqueId(), Bukkit.getScheduler().runTaskTimer(EverNifeCoreBukkitPlugin.instance, () -> {
            step[0]++;
            bukkit.setPlayerTime(from + (to - from) * step[0] / steps, time.isRelative());
            if (step[0] >= steps) {
                cancelRamp(bukkit.getUniqueId());
            }
        }, 1L, 1L));
    }

    private void cancelRamp(UUID player) {
        BukkitTask ramp = ramps.remove(player);
        if (ramp != null) {
            ramp.cancel();
        }
    }

    // ---- WEATHER ----

    @Override
    public @Nullable String getWeather(FPlayer player) {
        WeatherType weather = bukkit(player).getPlayerWeather();
        return weather == null ? null : weather == WeatherType.CLEAR ? CLEAR : RAIN;
    }

    @Override
    public void setWeather(FPlayer player, @Nullable String weather) {
        Player bukkit = bukkit(player);
        if (weather == null) {
            bukkit.resetPlayerWeather();
        } else {
            bukkit.setPlayerWeather(RAIN.equals(toWeatherId(weather)) ? WeatherType.DOWNFALL : WeatherType.CLEAR);
        }
    }

    /** {@code clear}, and {@code rain} - Bukkit's {@code downfall} is the same weather. */
    @Override
    public @Nullable String toWeatherId(String text) {
        String id = text.trim().toLowerCase(Locale.ROOT);
        return id.equals(CLEAR) ? CLEAR : id.equals(RAIN) || id.equals("downfall") ? RAIN : null;
    }

    // ---- FLIGHT ----

    @Override
    public boolean getAllowFlight(FPlayer player) {
        return bukkit(player).getAllowFlight();
    }

    @Override
    public void setAllowFlight(FPlayer player, boolean allow) {
        bukkit(player).setAllowFlight(allow); //Bukkit lands a flying player itself when flight is taken away
    }

    @Override
    public boolean isFlying(FPlayer player) {
        return bukkit(player).isFlying();
    }

    @Override
    public void setFlying(FPlayer player, boolean flying) {
        bukkit(player).setFlying(flying);
    }

    // ---- GAME_MODE ----

    @Override
    public boolean supportsGameMode(ECGameMode mode) {
        return gameModeOf(mode) != null;
    }

    private static @Nullable GameMode gameModeOf(ECGameMode mode) {
        try {
            return GameMode.valueOf(mode.name());
        } catch (IllegalArgumentException olderServer) {
            return null; //SPECTATOR came with 1.8
        }
    }

    @Override
    public ECGameMode getGameMode(FPlayer player) {
        return ECGameMode.valueOf(bukkit(player).getGameMode().name());
    }

    @Override
    public void setGameMode(FPlayer player, ECGameMode mode) {
        GameMode bukkitMode = gameModeOf(mode);
        if (bukkitMode == null) {
            throw new IllegalArgumentException("This server version has no " + mode + " game mode: pick one of the modes it has.");
        }
        bukkit(player).setGameMode(bukkitMode);
    }

    // ---- EFFECTS ----

    /** The Bukkit type {@code effect} names: {@code speed}, {@code minecraft:speed} or a legacy name like {@code increase_damage}. */
    private static @Nullable PotionEffectType typeOf(String effect) {
        String name = effect.trim();
        int colon = name.indexOf(':');
        return PotionEffectType.getByName((colon >= 0 ? name.substring(colon + 1) : name).toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isEffect(String effect) {
        return typeOf(effect) != null;
    }

    /** Matched by type, never by name: the names a type reports are not the ones every version reads back. */
    @Override
    public @Nullable ActiveEffect getEffect(FPlayer player, String effect) {
        PotionEffectType type = typeOf(effect);
        for (PotionEffect active : bukkit(player).getActivePotionEffects()) {
            if (active.getType().equals(type)) {
                return new ActiveEffect(EffectLevel.of(effect, active.getAmplifier() + 1), Duration.ofMillis(active.getDuration() * TICK_MILLIS));
            }
        }
        return null;
    }

    @Override
    public void addEffect(FPlayer player, EffectLevel effect, Duration duration) {
        PotionEffectType type = typeOf(effect.getEffect());
        if (type == null) {
            throw new IllegalArgumentException("'" + effect.getEffect() + "' is no potion effect on this server: use a Bukkit effect name"
                    + " such as speed or night_vision.");
        }
        Player bukkit = bukkit(player);
        bukkit.removePotionEffect(type); //addPotionEffect keeps a stronger one in place, and this replaces
        bukkit.addPotionEffect(new PotionEffect(type, (int) Math.min(Integer.MAX_VALUE, duration.toMillis() / TICK_MILLIS), effect.getLevel() - 1));
    }

    @Override
    public void removeEffect(FPlayer player, String effect) {
        PotionEffectType type = typeOf(effect);
        if (type != null) {
            bukkit(player).removePotionEffect(type);
        }
    }

    // ---- HEALTH ----

    @Override
    public double getHealth(FPlayer player) {
        return bukkit(player).getHealth();
    }

    @Override
    @SuppressWarnings("deprecation") //the max-health attribute does not exist before 1.9
    public double getMaxHealth(FPlayer player) {
        return bukkit(player).getMaxHealth();
    }

    @Override
    public void setHealth(FPlayer player, double health) {
        bukkit(player).setHealth(Math.max(0, Math.min(getMaxHealth(player), health)));
    }

    @Override
    public void damage(FPlayer player, double amount) {
        bukkit(player).damage(amount);
    }

    // ---- FOOD ----

    @Override
    public int getFood(FPlayer player) {
        return bukkit(player).getFoodLevel();
    }

    @Override
    public int getMaxFood(FPlayer player) {
        return 20;
    }

    @Override
    public void setFood(FPlayer player, int food) {
        bukkit(player).setFoodLevel(Math.max(0, Math.min(20, food)));
    }

    // ---- COMMAND ----

    @Override
    public boolean dispatchCommand(@Nullable FPlayer asPlayer, String command) {
        return dispatcher.test(asPlayer == null ? null : bukkit(asPlayer), command);
    }
}
