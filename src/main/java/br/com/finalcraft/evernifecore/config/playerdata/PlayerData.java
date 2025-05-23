package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.util.*;

public class PlayerData implements IPlayerData{

    //PlayerData
    protected final Config config;
    protected final String playerName;
    protected final UUID uuid;
    protected final Map<String, PlayerCooldown> cooldownHashMap = new HashMap<>();
    protected long lastSeen;
    protected long lastSaved;

    //Transient Data
    protected transient Player player = null;
    protected transient boolean recentChanged = false;

    // PDSection Controller
    private final Map<Class<? extends PDSection>, PDSection> MAP_OF_PDSECTIONS = new HashMap();

    public Map<Class<? extends PDSection>, PDSection> getMapOfPDSections() {
        return MAP_OF_PDSECTIONS;
    }

    @Override
    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass){
        PDSection pdSection = MAP_OF_PDSECTIONS.get(pdSectionClass);
        if (pdSection == null){
            pdSection = createPDSection(pdSectionClass);
        }
        return (T)pdSection;
    }

    private  <T extends PDSection> T createPDSection(Class<? extends T> pdSectionClass){
        try {
            Constructor<?> constructor = pdSectionClass.getConstructor(PlayerData.class);
            PDSection pdSection = (PDSection) constructor.newInstance(this);
            MAP_OF_PDSECTIONS.put(pdSectionClass, pdSection);
            return (T)pdSection;
        }catch (Exception e){
            EverNifeCore.warning("Failed to instantiate PDSection [" + pdSectionClass.getName() + "]!");
            throw new RuntimeException(e);
        }
    }

    public PlayerData(Config config) {
        this.config = Objects.requireNonNull(config,"PlayConfig cannot be null!");
        this.playerName = Objects.requireNonNull(config.getString("PlayerData.Username"),"PlayerName cannot be null!");
        this.uuid = Objects.requireNonNull(config.getUUID("PlayerData.UUID"),"PlayerUUID cannot be null!");
        this.lastSeen = config.getLong("PlayerData.lastSeen",0L);
        this.lastSaved = config.getLong("PlayerData.lastSaved", this.lastSeen);

        for (String cooldownID : config.getKeys("Cooldown")) {
            Cooldown cooldown = config.getLoadable("Cooldown." + cooldownID, Cooldown.class);
            PlayerCooldown playerCooldown = new PlayerCooldown(cooldown, this.uuid);
            cooldownHashMap.put(playerCooldown.getIdentifier(), playerCooldown);
        }
    }

    public PlayerData(Config config, String playerName, UUID uuid) {
        this.config = config;
        this.playerName = playerName;
        this.uuid = uuid;
        this.lastSeen = System.currentTimeMillis();
        this.lastSaved = 0L;

        this.getConfig().enableSmartCache(); //Cache config for only 3 minutes between uses
    }

    public PlayerData hotLoadPDSections(){
        for (PDSectionConfiguration configuration : PlayerController.getConfiguredPDSections().values()) {
            if (configuration.shouldHotLoad()){
                this.getPDSection(configuration.getPdSectionClass());//This will hot-load a PDSection
                //TODO in the future would be nice to have more configurations for these hot-loaded PDSections;
            }
        }
        return this;
    }

    public void setRecentChanged(){
        if (this.recentChanged == false){
            this.recentChanged = true;
        }
    }

    //Save Single PlayerData into YML
    public boolean forceSavePlayerData(){
        setRecentChanged();
        return savePlayerData();
    }

    //Save Single PlayerData into YML
    public boolean savePlayerData(){
        if (this.recentChanged == false){
            return false;
        }
        this.recentChanged = false;
        this.lastSaved = System.currentTimeMillis();

        //Player Data
        config.setValue("PlayerData.Username",this.playerName);
        config.setValue("PlayerData.UUID",this.uuid);
        config.setValue("PlayerData.lastSeen",this.lastSeen);
        config.setValue("PlayerData.lastSaved",this.lastSaved);

        // Loop all PDSections and save them if needed
        ArrayList<Map.Entry<Class<? extends PDSection>, PDSection>> entries = new ArrayList<>(MAP_OF_PDSECTIONS.entrySet());
        for (Map.Entry<Class<? extends PDSection>, PDSection> entry : entries) {
            Class<? extends PDSection> key = entry.getKey();
            PDSection pDSection = entry.getValue();
            try {
                if (pDSection.recentChanged){
                    pDSection.savePDSection();
                    pDSection.recentChanged = false;
                }
            }catch (Throwable e){
                EverNifeCore.warning("Failed to save PDSection {" + key.getName() + "} at [" + this.getConfig().getTheFile().getAbsolutePath() + "]");
                e.printStackTrace();
            }
        }

        config.saveAsync();
        return true;
    }

    public void setPlayer(Player player){
        this.player = player;
        this.lastSeen = System.currentTimeMillis();
        this.setRecentChanged();
    }

    public Map<String, PlayerCooldown> getCooldownHashMap() {
        return cooldownHashMap;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public long getLastSeen(){
        return player != null ? System.currentTimeMillis() : lastSeen;
    }

    @Override
    public long getLastSaved() {
        return lastSaved;
    }

    @Override
    public Player getPlayer(){
        return player;
    }

    @Override
    public boolean isPlayerOnline(){
        return player != null && player.isOnline();
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public PlayerCooldown getCooldown(String identifier){
        return cooldownHashMap.computeIfAbsent(identifier, s -> new PlayerCooldown(identifier, this.getUniqueId()));
    }

    @Override
    public PlayerData getPlayerData() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;//Only equals when the same object, otherwise different
        return false;
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();//Use UUID as hashcode
    }
}
