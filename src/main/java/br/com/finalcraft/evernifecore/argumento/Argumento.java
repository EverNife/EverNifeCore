package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
        return ChatColor.translateAlternateColorCodes('&',argumento);
    }

    public boolean equals(String anotherString){
        return this.argumento.equals(anotherString);
    }

    public boolean match(String... possibilities){
        return equalsIgnoreCase(possibilities);
    }

    public boolean equalsIgnoreCase(String anotherString){
        return argumento.equalsIgnoreCase(anotherString);
    }

    public boolean equalsIgnoreCase(String... possibilities){
        String lowerCaseArg = argumento.toLowerCase();
        for (String possibility : possibilities){
            if (possibility.toLowerCase().equals(lowerCaseArg)){
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

    public Player getPlayer(){
        return !argumento.isEmpty() ? Bukkit.getPlayer(argumento) : null;
    }

    public OfflinePlayer getOfflinePlayer(){
        return FCBukkitUtil.getOfflinePlayer(argumento);
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

        if (clazz == Integer.class)  {
            number = getInteger();
        } else if (clazz == Long.class)     {
            number = getLong();
        } else if (clazz == Float.class)    {
            number = getFloat();
        } else if (clazz == Double.class)   {
            number = getDouble();
        } else if (clazz == Byte.class)     {
            number = Byte.valueOf((byte)(int)getInteger());
        }

        if (number == null) return null;
        return (NumberWrapper<T>) NumberWrapper.of(number);
    }

    public <T extends Number> NumberWrapper<T> getNumberWrapper(Class<T> clazz, T def){
        NumberWrapper numberWrapper = getNumberWrapper(clazz);
        return numberWrapper != null ? numberWrapper : NumberWrapper.of(def);
    }

    public World getWorld(){
        return argumento.isEmpty() ? null : Bukkit.getWorld(argumento);
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

