package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

public class Argumento {

    public final static Argumento EMPTY_ARG = new Argumento("");

    private final String argumento;

    public Argumento(String argumento) {
        this.argumento = argumento;
    }

    public int length(){return this.argumento.length();}

    public boolean isEmpty(){
        return this.argumento.isEmpty();
    }

    public String colorize(){
        return FCColorUtil.colorfy(argumento);
    }

    public boolean equals(String anotherString){
        return this.argumento.equals(anotherString);
    }

    public boolean equalsIgnoreCase(String anotherString){
        return argumento.equalsIgnoreCase(anotherString);
    }

    public boolean equalsIgnoreCase(String... possibilities){
        for (String possibility : possibilities){
            if (possibility.equalsIgnoreCase(argumento)){
                return true;
            }
        }
        return false;
    }

    public String toLowerCase(){
        return this.argumento.toLowerCase();
    }

    public String toUpperCase(){
        return this.argumento.toUpperCase();
    }

    public FPlayer getPlayer(){

        if (argumento.isEmpty()){
            return null;
        }

        PlayerRef playerRef = null;

        for(World world : Universe.get().getWorlds().values()) {

            playerRef = NameMatching.EXACT_IGNORE_CASE.find(world.getPlayerRefs(), argumento, PlayerRef::getUsername);

            if (playerRef != null) {
                break;
            }
        }

        if (playerRef == null){
            return null;
        }

        return FCHytaleUtil.wrap(playerRef);
    }

    public PlayerData getPlayerData(){
        if (argumento.isEmpty()) return null;

        UUID uuid = getUUID();
        if (uuid != null){
            return PlayerController.getPlayerData(uuid);
        }

        return PlayerController.getPlayerData(argumento);
    }

    public <T extends PDSection> T getPDSection(Class<? extends T> pdClass){
        PlayerData playerData = getPlayerData();
        return playerData == null ? null : playerData.getPDSection(pdClass);
    }

    public JavaPlugin getPlugin(){
        return (JavaPlugin) PluginManager.get().getPlugins().stream()
                .filter(pluginBase -> pluginBase.getIdentifier().toString().equalsIgnoreCase(argumento))
                .findFirst()
                .orElse(null);
    }

    public Integer getInteger(){
        if (argumento.isEmpty()) return null;
        try {
            return Integer.parseInt(argumento);
        }catch (Exception ignored){
            return null;
        }
    }

    public Float getFloat(){
        if (argumento.isEmpty()) return null;
        try {
            return Float.parseFloat(argumento);
        }catch (Exception ignored){
            return null;
        }
    }

    public Long getLong(){
        if (argumento.isEmpty()) return null;
        try {
            return Long.parseLong(argumento);
        }catch (Exception ignored){
            return null;
        }
    }

    public Double getDouble(){
        if (argumento.isEmpty()) return null;
        try {
            return Double.parseDouble(argumento);
        }catch (Exception ignored){
            return null;
        }
    }

    public <T extends Number> NumberWrapper<T> getNumberWrapper(Class<T> clazz){
        if (argumento.isEmpty()) return null;

        Number number = null;

        if (clazz == Integer.class) {
            number = getInteger();
        } else if (clazz == Long.class) {
            number = getLong();
        } else if (clazz == Float.class) {
            number = getFloat();
        } else if (clazz == Double.class) {
            number = getDouble();
        } else if (clazz == Byte.class) {
            number = Byte.valueOf((byte)(int)getInteger());
        }

        if (number == null) return null;
        return (NumberWrapper<T>) NumberWrapper.of(number);
    }

    public <T extends Number> NumberWrapper<T> getNumberWrapper(Class<T> clazz, T def){
        NumberWrapper numberWrapper = getNumberWrapper(clazz);
        return numberWrapper != null ? numberWrapper : def == null ? null : NumberWrapper.of(def);
    }

    public World getWorld(){
        return argumento.isEmpty() ? null : Universe.get().getWorld(argumento);
    }

    public Boolean getBoolean(){
        if (argumento.isEmpty()) return null;
        switch (argumento.toLowerCase()){
            case "on":
            case "true":
            case "verdadeiro":
            case "yes":
            case "sim":
            case "y":
            case "s":
                return true;
            case "off":
            case "false":
            case "falso":
            case "não":
            case "nao":
            case "n":
                return false;
        }
        return null;
    }

    public UUID getUUID(){
        if (argumento.isEmpty() || argumento.length() != 36) return null;
        try {
            return UUID.fromString(argumento);
        }catch (Exception ignored){
            return null;
        }
    }

    public String replaceAll(String regex, String replacement){
        return this.argumento.replaceAll(regex, replacement);
    }

    public String replace(CharSequence target, CharSequence replacement){
        return this.argumento.replace(target, replacement);
    }

    @Override
    public String toString() {
        return argumento;
    }
}

