package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    //PlayerData
    protected Config config;
    protected String playerName;
    protected UUID uuid;
    protected long lastSeen;
    protected HashMap<String, PlayerCooldown> cooldownHashMap = new HashMap<>();

    protected transient Player player = null;
    protected transient boolean recentChanged = false;

    // PDSection Controller
    private Map<Class<? extends PDSection>, PDSection> mapOfPDSections = new HashMap();

    public Map<Class<? extends PDSection>, PDSection> getMapOfPDSections() {
        return mapOfPDSections;
    }

    public <T extends PDSection> T getPDSection(Class<? extends T> pdSectionClass){
        PDSection pdSection = mapOfPDSections.get(pdSectionClass);
        if (pdSection == null){
            pdSection = createPDSection(pdSectionClass);
        }
        return (T)pdSection;
    }

    private  <T extends PDSection> T createPDSection(Class<? extends T> pdSectionClass){
        try {
            Constructor<?> constructor = pdSectionClass.getConstructor(PlayerData.class);
            PDSection pdSection = (PDSection) constructor.newInstance(this);
            mapOfPDSections.put(pdSectionClass, pdSection);
            return (T)pdSection;
        }catch (Exception e){
            EverNifeCore.warning("Failed to instantiate PDSection [" + pdSectionClass.getName() + "]!");
            throw new RuntimeException(e);
        }
    }

    protected PlayerData() {
    }

    public PlayerData(Config config) {
        this.config = config;
        this.playerName = config.getString("PlayerData.Username");
        this.uuid = config.getUUID("PlayerData.UUID");
        this.lastSeen = config.getLong("PlayerData.lastSeen",0);

        for (String cooldownKey : config.getKeys("Cooldowns")) {

        }
    }

    public PlayerData(Config config, String playerName, UUID uuid) {
        this.config = config;
        this.playerName = playerName;
        this.uuid = uuid;
        this.lastSeen = 0;
    }

    public void setRecentChanged(){
        if (this.recentChanged == false)
            this.recentChanged = true;
    }

    //Save Single PlayerData into YML
    public boolean forceSavePlayerData(){
        setRecentChanged();
        return savePlayerData();
    }

    //Save Single PlayerData into YML
    public boolean savePlayerData(){
        if (this.recentChanged){
            //Player Data
            config.setValue("PlayerData.Username",this.playerName);
            config.setValue("PlayerData.UUID",this.uuid);
            config.setValue("PlayerData.lastSeen",this.lastSeen);

            // Faz um loop em todas as configSections dos side-plugins e salva elas tambem!
            for (PDSection pDSection : mapOfPDSections.values()){
                try {
                    if (pDSection.recentChanged){
                        pDSection.savePDSection();
                        pDSection.recentChanged = false;
                    }
                }catch (Exception e){
                    EverNifeCore.warning("Failed to save PDSection {" + pDSection.getClass().getName() + "} at [" + this.getConfig().getTheFile().getAbsolutePath() + "]");
                    e.printStackTrace();
                }
            }

            this.recentChanged = false;
            config.saveAsync();
            return true;
        }
        return false;
    }

    public Config getConfig() {
        return config;
    }

    public void setPlayer(Player player){
        this.player = player;
        lastSeen = System.currentTimeMillis();
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getLastSeen(){
        return player != null ? System.currentTimeMillis() : lastSeen;
    }

    public Player getPlayer(){
        return player;
    }

    public boolean isPlayerOnline(){
        return player != null && player.isOnline();
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public PlayerCooldown getCooldown(String identifier){
        PlayerCooldown cooldown = cooldownHashMap.get(identifier);
        if (cooldown == null){
            cooldown = new PlayerCooldown(identifier, this.getUniqueId());
            cooldownHashMap.put(identifier, cooldown);
        }
        return cooldown;
    }

    public HashMap<String, PlayerCooldown> getCooldownHashMap() {
        return cooldownHashMap;
    }
}
