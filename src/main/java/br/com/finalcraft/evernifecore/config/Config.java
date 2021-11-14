package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fcitemstack.FCItemStack;
import br.com.finalcraft.evernifecore.fcitemstack.iteminv.InvItem;
import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Config {
	
	protected final File theFile;
	protected FileConfiguration config;
	protected boolean newDefaultValueToSave = false;

	protected final static Random random = new Random();
	private static HashMap<Class, Method> MAP_OF_LOADABLE_METHODS = new HashMap<>();
	private static final ExecutorService SCHEDULER = new ThreadPoolExecutor(5, Integer.MAX_VALUE,
			1000L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue(),
			new ThreadFactoryBuilder()
					.setNameFormat("evernifecore-assyncsave-pool-%d")
					.setDaemon(true)
					.build());

	/**
	 * Creates a handlers Directory if doest not exist at the targed directory
	 *
	 * @param  assetName The asset name you want to copy
	 * @param  targetFolder The target folder you want to paste the theFile in
	 */
	public static File copyAsset(String assetName, File targetFolder, Plugin plugin) throws IOException {
		File file = new File(targetFolder, assetName);
		if (!file.exists()){
			InputStream inputStream = plugin.getResource(assetName);
			Files.copy(inputStream, file.getAbsoluteFile().toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
		}
		return file;
	}

	/**
	 * Gets all .yml files under a directory
	 *
	 * @param  directory The directory to search in
	 * @param  recursive Recursively search inside innerDirectorys
	 */
	public static List<Config> getAllConfings(File directory, boolean recursive){
		if (directory == null) throw new IllegalArgumentException("Directory to search can't be null!");
		if (directory.isFile()) throw new IllegalArgumentException("Directory to search must be a FOLDER not a FILE!");
		if (!directory.exists()) new ArrayList<>();

		List<Config> configList = new ArrayList<>();

		File[] files = directory.listFiles();
		if (files != null){
			for (File innerFile : files) {
				if (innerFile.isFile()){
					if (innerFile.getName().endsWith(".yml")){
						configList.add(new Config(innerFile));
					}
				}else if (recursive){
					configList.addAll(getAllConfings(directory, recursive));
				}
			}
		}

		return configList;
	}

	/**
	 * Creates a Config Object for the configName.yml File of
	 * the specified Plugin + copy default configs if asked to +
	 * a header information about EverNife Config Manager
	 *
	 * @param  plugin The Instance of the Plugin, the name.yml is referring to
	 */
	public Config(Plugin plugin, String configName, boolean copyDefaults, boolean header) {
		this(plugin, configName, copyDefaults, header ?
				// Site that i used to make this http://patorjk.com/software/taag/#p=display&f=Doom&t=FinalCraft
				"--------------------------------------------------------" +
				"\n      ______ _             _ _____            __ _   " +
				"\n      |  ___(_)           | /  __ \\          / _| |  " +
				"\n      | |_   _ _ __   __ _| | /  \\/_ __ __ _| |_| |_ " +
				"\n      |  _| | | '_ \\ / _` | | |   | '__/ _` |  _| __|" +
				"\n      | |   | | | | | (_| | | \\__/\\ | | (_| | | | |_ " +
				"\n      \\_|   |_|_| |_|\\__,_|_|\\____/_|  \\__,_|_|  \\__|" +
				"\n  " +
				"\n  " +
				"\n  " +
				"\n              EverNife's Config Manager" +
				"\n" +
				"\n Plugin: " + plugin.getName().replace("EverNife","") +
				"\n Author: " + (plugin.getDescription().getAuthors().size() > 0 ? plugin.getDescription().getAuthors().get(0) : "Desconhecido") +
				"\n" +
				"\n-------------------------------------------------------"
				: null);
	}

	/**
	 * Creates a handlers Config Object for the configName.yml File of
	 * the specified Plugin + copy default configs if asked to +
	 * a header information about EverNife Config Manager
	 *
	 * @param  plugin The Instance of the Plugin, the name.yml is referring to
	 */
	public Config(Plugin plugin, String configName, boolean copyDefaults, String header) {

		this.theFile = new File("plugins/" + plugin.getDescription().getName().replace(" ", "_") + "/" + configName);

		if (!theFile.exists()) {
			File theFileParentDir = theFile.getParentFile();
			theFileParentDir.mkdirs();
			if (copyDefaults){
				try {
					copyAsset(configName,theFileParentDir,plugin);
				}catch (IOException e){
					plugin.getLogger().warning("Failed to load Asset to the config!");
					e.printStackTrace();
				}
			}
		}

		this.config = YamlConfiguration.loadConfiguration(this.theFile);
		if (header != null){
			this.config.options().header(header);
		}
	}

	public Config(Plugin plugin, String configName, boolean copyDefaults) {
		this(plugin,configName,copyDefaults,true);
	}
	public Config(Plugin plugin, String configName) {
		this(plugin,configName,false);
	}
	public Config(Plugin plugin) {
		this(plugin, "config.yml");
	}
	
	/**
	 * Creates a handlers Config Object for the specified File
	 *
	 * @param  theFile The File for which the Config object is created for
	 */
	public Config(File theFile) {
		this.theFile = theFile;
		this.config = YamlConfiguration.loadConfiguration(this.theFile);
	}
	
	/**
	 * Creates a handlers Config Object for the specified File and FileConfiguration
	 *
	 * @param  theFile The File to save to
	 * @param  config The FileConfiguration
	 */
	public Config(File theFile, FileConfiguration config) {
		this.theFile = theFile;
		this.config=config;
	}
	
	/**
	 * Creates a handlers Config Object for the File with in
	 * the specified FCLocationController
	 *
	 * @param  path The Path of the File which the Config object is created for
	 */
	public Config(String path) {
		this.theFile = new File(path);
		this.config = YamlConfiguration.loadConfiguration(this.theFile);
	}
	
	/**
	 * Returns the File the Config is handling
	 *
	 * @return      The File this Config is handling
	 */ 
	public File getTheFile() {
		return this.theFile;
	}
	
	/**
	 * Converts this Config Object into a plain FileConfiguration Object
	 *
	 * @return      The converted FileConfiguration Object
	 */ 
	public FileConfiguration getConfiguration() {
		return this.config;
	}
	
	protected void store(String path, Object value) {
		this.config.set(path, value);
	}
	
	/**
	 * Sets the Value for the specified Path
	 *
	 * @param  path The path in the Config File
	 * @param  value The Value for that Path
	 */
	public void setValue(String path, Object value) {
		if (value == null) {
			this.store(path, value);
			this.store(path + "_extra", null);
		}
		else if (value instanceof Inventory) {
			for (int i = 0; i < ((Inventory) value).getSize(); i++) {
				setValue(path + "." + i, ((Inventory) value).getItem(i));
			}
		}
		else if (value instanceof Date) {
			this.store(path, String.valueOf(((Date) value).getTime()));
		}
		else if (value instanceof Long) {
			this.store(path, String.valueOf(value));
		}
		else if (value instanceof UUID) {
			this.store(path, value.toString());
		}
		else if (value instanceof Sound) {
			this.store(path, String.valueOf(value));
		}
		else if (value instanceof ItemStack) {
			this.store(path, new ItemStack((ItemStack) value));
		}
		else if (value instanceof NumberWrapper){
			setValue(path, ((NumberWrapper<?>) value).get());
		}
		else if (value instanceof FancyText && !(value instanceof FancyFormatter)) {
			FancyText fancyText = (FancyText) value;

			boolean hover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty();
			boolean action = fancyText.getClickActionText() != null && !fancyText.getClickActionText().isEmpty();

			if (hover || action){
				this.store(path + ".text", fancyText.getText());
				if (hover) this.store(path + ".hoverText", fancyText.getHoverText());
				if (action) {
					this.store(path + ".clickActionText", fancyText.getClickActionText());
					this.store(path + ".clickActionType", fancyText.getClickActionType());
				}
			}else {
				this.store(path, fancyText.getText());
			}

		}
		else if (value instanceof FCItemStack){
			FCItemStack fcItemStack = (FCItemStack) value;
			setValue(path + ".minecraftIdentifier", fcItemStack.getMinecraftIdentifier(false));
			if (fcItemStack.hasNBTTag()){
				InvItem invItem = InvItem.getInvItem(fcItemStack.getItemStack());
				if (invItem != null){
					setValue(path + ".invItem.name", invItem.name());
					for (ItemSlot itemSlot : invItem.getItemsFrom(fcItemStack.getItemStack())) {
						setValue(path + ".invItem.content." + itemSlot.getSlot(), itemSlot.getFcItemStack());
					}
					setValue(path + ".nbt", null);
					return;
				}
				List<String> list = Arrays.asList(
						Iterables.toArray(
								Splitter
										.fixedLength(100)
										.split(fcItemStack.getNBTtoString()),
								String.class
						)
				);
				setValue(path + ".nbt", list);
			}else {
				setValue(path + ".nbt", null);
			}
		}
		else if (value instanceof Location) {
			setValue(path + ".x", ((Location) value).getX());
			setValue(path + ".y", ((Location) value).getY());
			setValue(path + ".z", ((Location) value).getZ());
			setValue(path + ".pitch", ((Location) value).getPitch());
			setValue(path + ".yaw", ((Location) value).getYaw());
			setValue(path + ".worldName", ((Location) value).getWorld().getName());
		}
		else if (value instanceof Chunk) {
			setValue(path + ".x", ((Chunk) value).getX());
			setValue(path + ".z", ((Chunk) value).getZ());
			setValue(path + ".worldName", ((Chunk) value).getWorld().getName());
		}
		else if (value instanceof World) {
			this.store(path, ((World) value).getName());
		}
		else if (value instanceof Salvable){
			((Salvable) value).onConfigSave(this, path);
		}else if (value instanceof List && ((List) value).size() > 0 && ((List) value).get(0) instanceof Salvable){
			setValue(path,null);
			List<Salvable> salvableList = (List<Salvable>) value;
			for (int index = 0; index < salvableList.size(); index++) {
				setValue(path + "." + salvableList.get(index).getClass().getSimpleName() + "==" + index, salvableList.get(index));
			}
		}
		else this.store(path, value);
	}

	private final ReentrantLock lock = new ReentrantLock(true);
	/**
	 * Saves the Config Object to its File
	 */ 
	public void save() {
		lock.lock();
		try {
			config.save(theFile);
		} catch (IOException e) {
			EverNifeCore.warning("Failed to save file [" + theFile.getAbsolutePath() + "]");
			e.printStackTrace();
		}finally {
			lock.unlock();
		}
	}

	/**
	 * Saves the Config Object to its File, and ensure its assync state
	 */
	public void saveAsync() {
		SCHEDULER.submit(() -> {
			this.save();
		});
	}

	/**
	 * Saves the Config Object to its File if there is any handlers DefaultValue set
	 */
	public void saveIfNewDefaults() {
		if (newDefaultValueToSave){
			save();
			newDefaultValueToSave = false;
		}
	}
	
	/**
	 * Saves the Config Object to a File
	 * 
	 * @param  file The File you are saving this Config to
	 */ 
	public void save(File file) {
		try {
			config.save(file);
		} catch (IOException e) {
		}
	}


    public List getOrSetDefaultValue(String path, List def) {
		if (!contains(path)){
			setValue(path, def);
			if (!newDefaultValueToSave) newDefaultValueToSave = true;
			return def;
		}else {
			return getStringList(path);
		}
    }

    public Integer getOrSetDefaultValue(String path, Integer def) {
        return (Integer) getOrSetDefaultValue(path,(Object)def);
    }

	public Long getOrSetDefaultValue(String path, Long def) {
		return (Long) getOrSetDefaultValue(path,(Object)def);
	}

	public Double getOrSetDefaultValue(String path, Double def) {
        return (Double) getOrSetDefaultValue(path,(Object)def);
    }

    public Float getOrSetDefaultValue(String path, Float def) {
        return (Float) getOrSetDefaultValue(path,(Object)def);
    }

    public String getOrSetDefaultValue(String path, String def) {
        return (String) getOrSetDefaultValue(path,(Object)def);
    }

	public boolean getOrSetDefaultValue(String path, boolean def) {
		return (boolean) getOrSetDefaultValue(path,(Object)def);
	}

	public UUID getOrSetDefaultValue(String path, UUID def) {
		return (UUID) getOrSetDefaultValue(path,(Object)def);
	}

	public Object getOrSetDefaultValue(String path, Object value) {
		if (!contains(path)){
			setValue(path, value);
			if (!newDefaultValueToSave) newDefaultValueToSave = true;
			return value;
		}else {
			Object object = getValue(path);
			if (object.getClass() != value.getClass()){
				object = value;
				setValue(path,value);
				if (!newDefaultValueToSave) newDefaultValueToSave = true;
			}

			return object;
		}
	}

	/**
	 * Sets the Value for the specified Path 
	 * (IF the Path does not yet exist)
	 *
	 * @param  path The path in the Config File
	 * @param  value The Value for that Path
	 */
	public void setDefaultValue(String path, Object value) {
		if (!contains(path)){
			setValue(path, value);
			newDefaultValueToSave = true;
		}
	}

	/**
	 * Checks whether the Config contains the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      True/false
	 */ 
	public boolean contains(String path) {
		return config.contains(path);
	}
	
	/**
	 * Returns the Object at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Value at that Path
	 */ 
	public Object getValue(String path) {
		return config.get(path);
	}

	/**
	 * Returns the Object T at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @param  loadableClass Clas that has a Loadable function
	 * @return      The Value at that Path
	 */
	public <T> T getValue(String path, Class<? extends T> loadableClass){
		if (!contains(path)) return null;
		try {
			return (T) getLoadableMethodAndInvoke(loadableClass).invoke(null,this,path);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the ItemStack at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The ItemStack at that Path
	 */ 
	public ItemStack getItem(String path) {
		ItemStack item = config.getItemStack(path);
		if (item == null) return null;
		return item;
	}

	/**
	 * Returns the {@link FancyText} at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The FancyText at that Path
	 */
	public FancyText getFancyText(String path) {
		if (contains(path + ".text")){
			String text = getString(path + ".text");
			String hoverText = getString(path + ".hoverText", null);
			String actionText = getString(path + ".clickActionText", null);
			String actionTypeName = getString(path + ".clickActionType", null);
			ClickActionType actionType = actionTypeName != null && !actionTypeName.isEmpty() ? ClickActionType.valueOf(actionTypeName) : ClickActionType.NONE;
			return new FancyText(text, hoverText, actionText, actionType);
		}else if (contains(path)){
			return new FancyText(getString(path));
		}
		return null;
	}

	/**
	 * Returns the {@link FCItemStack} at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The ItemStack at that Path
	 */
	public FCItemStack getFCItem(String path) {
		FCItemStack fcItemStack = null;
		if (contains(path + ".minecraftIdentifier")){
			String minecraftIdentifier = config.getString(path + ".minecraftIdentifier");
			if (contains(path + ".nbt")){
				String nbt = " " + String.join("", config.getStringList(path + ".nbt"));
				fcItemStack = FCItemStack.fromMinecraftIdentifier(minecraftIdentifier + nbt);
			}else if (contains(path + ".invItem.name")){
				fcItemStack = FCItemStack.fromMinecraftIdentifier(minecraftIdentifier);
				String invItemName = config.getString(path + ".invItem.name");
				InvItem invItem = InvItem.valueOf(invItemName);
				if (!invItem.isEnabled()){
					EverNifeCore.warning("Found an InvItem [" + invItem.name()  + "] but it is not enabled! The content will be ignored!");
				}
				List<ItemSlot> itemSlots = new ArrayList<>();
				for (String slotString : getKeys(path + ".invItem.content")) {
					Integer slot = Integer.parseInt(slotString);
					itemSlots.add(new ItemSlot(slot, getFCItem(path + ".invItem.content." + slotString)));
				}
				invItem.setItemsTo(fcItemStack, itemSlots);
			}else {
				fcItemStack = FCItemStack.fromMinecraftIdentifier(minecraftIdentifier);
			}
		}
		return fcItemStack;
	}

	public FCItemStack getFCItem(String path, FCItemStack def) {
		FCItemStack fcItemStack = getFCItem(path);
		return fcItemStack != null ? fcItemStack : def;
	}

	/**
	 * Returns a randomly chosen String from an
	 * ArrayList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      A randomly chosen String from the ArrayList at that Path
	 */ 
	public String getRandomStringfromList(String path) {
		return getStringList(path).get(random.nextInt(getStringList(path).size()));
	}
	
	/**
	 * Returns a randomly chosen Integer from an
	 * ArrayList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      A randomly chosen Integer from the ArrayList at that Path
	 */ 
	public int getRandomIntfromList(String path) {
		return getIntList(path).get(random.nextInt(getIntList(path).size()));
	}
	
	/**
	 * Returns the String at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The String at that Path
	 */
	public String getString(String path) {
		return config.getString(path);
	}
	public String getString(String path, String def) {
		return config.getString(path,def);
	}

	/**
	 * Returns the String (Coloring it) at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The String at that Path
	 */
	public String getColoredString(String path) {
		return ChatColor.translateAlternateColorCodes('&',config.getString(path));
	}
	public String getColoredString(String path, String def) {
		return ChatColor.translateAlternateColorCodes('&',config.getString(path,def));
	}

	/**
	 * Returns the Integer at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Integer at that Path
	 */
	public int getInt(String path) {
		return config.getInt(path);
	}
	public int getInt(String path, int def) {
		return config.getInt(path,def);
	}

	/**
	 * Returns the Boolean at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Boolean at that Path
	 */
	public boolean getBoolean(String path) {
		return config.getBoolean(path);
	}
	public boolean getBoolean(String path,boolean def) {
		return config.getBoolean(path,def);
	}

	/**
	 * Returns the Loadable at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Boolean at that Path
	 */
	public <T> T getLoadable(String path, Class<? extends T> loadableClass) {
		if (contains(path)){
			try {
				Method method = getLoadableMethodAndInvoke(loadableClass);
				return (T) method.invoke(null, this, path);
			}catch (Exception e){
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	/**
	 * Returns the Loadable at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Boolean at that Path
	 */
	public <T> T getLoadableForced(String path, Class<? extends T> loadableClass) {
		try {
			Method method = getLoadableMethodAndInvoke(loadableClass);
			return (T) method.invoke(null, this, path);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	public <T> T getLoadable(String path, Class<? extends T> loadableClass, T def) {
		T loadaed = getLoadable(path, loadableClass);
		return  loadaed != null ? loadaed : def;
	}


	/**
	 * Returns the StringList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The StringList at that Path
	 */
	public List<Location> getLocationList(String path) {
		return (List<Location>) config.getList(path);
	}
	public List<Location> getLocationList(String path, List<Location> def) {
		if (contains(path)) return (List<Location>) config.getList(path);
		return def;
	}

	/**
	 * Returns the StringList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The StringList at that Path
	 */
	public List<String> getStringList(String path) {
		return config.getStringList(path);
	}
    public List<String> getStringList(String path, List<String> def) {
        if (contains(path)) return config.getStringList(path);
        return def;
    }

	/**
	 * Returns the StringList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The StringList at that Path
	 */
	public <T> List<T> getLoadableList(String path, Class<? extends T> loadableClass) {
		try {
			List<T> loadableList = new ArrayList<>();
			Method method = getLoadableMethodAndInvoke(loadableClass);
			for (String index : getKeys(path)) {
				T loadedValue = (T) method.invoke(null, this, path + "." + index);
				loadableList.add(loadedValue);
			}
			return loadableList;
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	public <T> List<T> getLoadableList(String path, Class<? extends T> loadableClass, List<T> def) {
		if (contains(path)) return getLoadableList(path, loadableClass);
		return def;
	}

	/**
	 * Returns the StringList (Coloring it) at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The StringList at that Path
	 */
	public List<String> getColoredStringList(String path) {
		List<String> coloredStringList = new ArrayList<String>();
		for (String stringLine : getStringList(path)) {
			coloredStringList.add(ChatColor.translateAlternateColorCodes('&',stringLine));
		}
		return coloredStringList;
	}
	public List<String> getColoredStringList(String path, List<String> def) {
		if (contains(path)) return getColoredStringList(path);
		return def;
	}

	/**
	 * Returns the ItemList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The ItemList at that Path
	 */ 
	public List<ItemStack> getItemList(String path) {
		List<ItemStack> list = new ArrayList<ItemStack>();
		for (String key: getKeys(path)) {
			if (!key.endsWith("_extra")) list.add(getItem(path + "." + key));
		}
		return list;
	}
	
	/**
	 * Returns the IntegerList at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The IntegerList at that Path
	 */ 
	public List<Integer> getIntList(String path) {
		return config.getIntegerList(path);
	}
	
	/**
	 * Recreates the File of this Config
	 */ 
	public void createFile() {
		try {
			this.theFile.createNewFile();
		} catch (IOException e) {
		}
	}
	
	/**
	 * Returns the Float at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Float at that Path
	 */ 
	public Float getFloat(String path) {
		return Float.valueOf(String.valueOf(getValue(path)));
	}
	
	/**
	 * Returns the Long at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Long at that Path
	 */ 
	public Long getLong(String path) {
		Object obj = getValue(path);
		return obj == null ? null : Long.valueOf(String.valueOf(obj));
	}
	public Long getLong(String path, long def) {
		if (!contains(path)){
			return def;
		}
		return getLong(path);
	}

	/**
	 * Returns the Sound at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Sound at that Path
	 */ 
	public Sound getSound(String path) {
		return Sound.valueOf(getString(path));
	}
	
	/**
	 * Returns the Date at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Date at that Path
	 */ 
	public Date getDate(String path) {
		if (contains(path)){
			return new Date(getLong(path));
		}
		return null;
	}
	
	/**
	 * Returns the Chunk at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Chunk at that Path
	 */ 
	public Chunk getChunk(String path) {
		return Bukkit.getWorld(getString(path + ".worldName")).getChunkAt(getInt(path + ".x"), getInt(path + ".z"));
	}
	
	/**
	 * Returns the UUID at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The UUID at that Path
	 */ 
	public UUID getUUID(String path) {
		if (contains(path)){
			return UUID.fromString(getString(path));
		}
		return null;
	}

	/**
	 * Returns the UUID at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The UUID at that Path
	 */
	public UUID getUUID(String path, UUID def) {
		UUID uuid = getUUID(path);
		return uuid != null ? uuid : def;
	}
	
	/**
	 * Returns the World at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The World at that Path
	 */ 
	public World getWorld(String path) {
		return Bukkit.getWorld(getString(path));
	}
	
	/**
	 * Returns the Double at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The Double at that Path
	 */
	public Double getDouble(String path) {
		return config.getDouble(path);
	}
	public Double getDouble(String path, double def) {
		return config.getDouble(path,def);
	}

	/**
	 * Returns the FCLocationController at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @return      The FCLocationController at that Path
	 */ 
	public Location getLocation(String path) {
		if (!this.contains(path)) return null;
		if (this.contains(path + ".pitch")) {
			return new Location(
				Bukkit.getWorld(
				getString(path + ".worldName")),
				getDouble(path + ".x"),
				getDouble(path + ".y"),
				getDouble(path + ".z"),
				getFloat(path + ".yaw"),
				getFloat(path + ".pitch")
			);
		}
		else {
			return new Location(
				Bukkit.getWorld(
				this.getString(path + ".worldName")),
				this.getDouble(path + ".x"),
				this.getDouble(path + ".y"),
				this.getDouble(path + ".z")
			);
		}
	}
	public Location getLocation(String path, Location def) {
		if (this.config.contains(path)){
			return getLocation(path);
		}
		return def;
	}
	
	@Deprecated
	public void setLocation(String path, Location location) {
		setValue(path + ".x", location.getX());
		setValue(path + ".y", location.getY());
		setValue(path + ".z", location.getZ());
		setValue(path + ".worldName", location.getWorld().getName());
	}
	
	@Deprecated
	public void setInventory(String path, Inventory inventory) {
		for (int i = 0; i < inventory.getSize(); i++) {
			setValue(path + "." + i, inventory.getItem(i));
		}
	}
	
	/**
	 * Gets the Contents of an Inventory at the specified Path
	 *
	 * @param  path The path in the Config File
	 * @param  size The Size of the Inventory
	 * @param  title The Title of the Inventory
	 * @return      The generated Inventory
	 */ 
	public Inventory getInventory(String path, int size, String title) {
		Inventory inventory = Bukkit.createInventory(null, size, title);
		for (int i = 0; i < size; i++) {
			inventory.setItem(i, getItem(path + "." + i));
		}
		return inventory;
	}
	
	/**
	 * Returns all Paths in this Config
	 *
	 * @return      All Paths in this Config
	 */ 
	public Set<String> getKeys() {
		return config.getKeys(false);
	}
	
	/**
	 * Returns all Sub-Paths in this Config
	 *
	 * @param  path The path in the Config File
	 * @return      All Sub-Paths of the specified Path
	 */ 
	public Set<String> getKeys(String path) {
		if (contains(path)){
			return config.getConfigurationSection(path).getKeys(false);
		}
		return Collections.emptySet();
	}

	/**
	 * Returns all Sub-Paths in this Config
	 *
	 * @param  path The path in the Config File
	 * @param  deep If to do a deep serach
	 * @return      All Sub-Paths of the specified Path
	 */
	public Set<String> getKeys(String path, boolean deep) {
		if (contains(path)){
			return config.getConfigurationSection(path).getKeys(deep);
		}
		return Collections.emptySet();
	}
	
	/**
	 * Reloads the Configuration File
	 */ 
	public void reload() {
		this.config = YamlConfiguration.loadConfiguration(this.theFile);
	}

	public ConfigurationSection getConfigurationSection(String path){
		return getConfiguration().getConfigurationSection(path);
	}

	public static void shutdownSaveScheduller(){
		if (!SCHEDULER.isShutdown() && !SCHEDULER.isTerminated()){
			try {
				SCHEDULER.shutdown();
				boolean success = SCHEDULER.awaitTermination(30, TimeUnit.SECONDS);
				if (!success){
					EverNifeCore.warning("Failed to close Config.class Scheduller, TimeOut of 30 seconds Reached, this is really bad! Terminating all of them now!");
					SCHEDULER.shutdownNow();
				}
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}

	//------------------------------------------------------------------------------------------------------------------

	public static interface Salvable{
		public void onConfigSave(Config config, String path);
	}

	//------------------------------------------------------------------------------------------------------------------

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Loadable{

	}

	public static Method getLoadableMethodAndInvoke(Class loadableClass){
		Method method = MAP_OF_LOADABLE_METHODS.get(loadableClass);
		if (method == null){
			try {
				for (Method declaredMethod : loadableClass.getDeclaredMethods()) {
					if (declaredMethod.isAnnotationPresent(Loadable.class)){
						method = declaredMethod;
						break;
					}
				}
				if (!method.isAccessible()){
					method.setAccessible(true);
				}
				MAP_OF_LOADABLE_METHODS.put(loadableClass,method);
			}catch (Exception e){
				EverNifeCore.warning("Fatal Error on LoadableClass Method Getter: " + loadableClass.getName());
				e.printStackTrace();
			}
		}
		return method;
	}

	//------------------------------------------------------------------------------------------------------------------

}
