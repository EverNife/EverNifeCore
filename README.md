[![Discord](https://img.shields.io/discord/899151012290498620.svg?label=discord&logo=discord)](https://discord.petrus.dev/)
[![Spigot](https://img.shields.io/spiget/downloads/97739?label=Spigot%20Downloads&logo=data%3Aimage%2Fpng%3Bbase64%2CiVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAABGdBTUEAALGPC%2FxhBQAAACBjSFJNAAB6JgAAgIQAAPoAAACA6AAAdTAAAOpgAAA6mAAAF3CculE8AAAAElBMVEUAAAAAAAD%2F0ADi6D86Ruj%2F%2F%2F%2BilASZAAAAAXRSTlMAQObYZgAAAAFiS0dEBfhv6ccAAAAHdElNRQfmBRoTHQ%2BKPgYQAAAAAW9yTlQBz6J3mgAAAFBJREFUCNdljdERgDAIQ8OdAxicgHQCZQH3n0raWn98P7y7QADAYkfHiInFK4yZwLWkTaHy7IPeMupOUkvVFiu5XL3hyLAhRsK%2FzvVlu%2F%2FyAL5yBqQb5SdrAAAAJXRFWHRkYXRlOmNyZWF0ZQAyMDIyLTA1LTI2VDE5OjI5OjEzKzAwOjAw2Eq4fQAAACV0RVh0ZGF0ZTptb2RpZnkAMjAyMi0wNS0yNlQxOToyOToxMyswMDowMKkXAMEAAAAASUVORK5CYII%3D)](https://www.spigotmc.org/resources/97739/)

<p align="center">
  <img src="icon/evernifecore.png" alt="EverNifeCore Logo" />
  <br>
  <img src="https://bstats.org/signatures/bukkit/EverNifeCore.svg" alt="bStats" />
</p>

# EverNifeCore

> A comprehensive Java Framework for Bukkit/Spigot/Paper plugin development!

EverNifeCore is a powerful, feature-rich framework designed to accelerate Minecraft plugin development. Originally created as a private foundation for my plugin ecosystem, it's now available to the community, providing developers with robust APIs, utilities, and ready-to-use systems that enforce best practices and reduce boilerplate code.

## 🚀 Quick Start

### For Server Owners
1. Download the latest JAR from [Releases](https://github.com/EverNife/EverNifeCore/releases)
2. Place in your `plugins/` folder
3. Restart your server (don't reload it)

### For Developers
```groovy
repositories {
    maven { url = 'https://maven.petrus.dev/public' }
}

dependencies {
    compileOnly 'br.com.finalcraft:EverNifeCore:2.0.4'
}
```

## 📋 Table of Contents

- [🌟 Key Features](#-key-features)
- [🏗️ Architecture](#️-architecture)
- [📚 Core Systems](#-core-systems)
  - [Command Framework](#command-framework)
  - [Configuration System](#configuration-system)
  - [FancyText & Messaging](#fancytext--messaging)
  - [GUI Framework](#gui-framework)
  - [Scheduler & Threading](#scheduler--threading)
  - [Player Data Management](#player-data-management)
  - [Database Integration](#database-integration)
  - [Localization System](#localization-system)
  - [Economy Integration](#economy-integration)
  - [Protection Systems](#protection-systems)
- [🔧 Utilities](#-utilities)
- [🔌 Integrations](#-integrations)
- [📖 Examples](#-examples)
- [🤝 Contributing](#-contributing)
- [📞 Support](#-support)

## 🌟 Key Features

### 🎯 **Multi-Platform Support**
- **Bukkit/Spigot/Paper** compatibility (1.7.10 - 1.21)
- **Forge Integration** for hybrid servers (1.7.10, 1.12.2, 1.16.5, 1.20.1, 1.21)
- **Version-specific NMS** handling with automatic fallbacks
- **Java Support** Java8 to Java25

### ⚡ **Developer Experience**
- **Virtual Thread Scheduler** (Java 21+) with fallback thread pools
- **Smart Configuration Caching** with memory optimization
- **Annotation-driven** Command System

## 📚 Core Systems

### Command Framework

Powerful annotation-based command system with automatic argument parsing, permission handling, and localization.

```java
@FinalCMD(
    aliases = {"teleport", "tp"},
    permission = "myplugin.teleport",
    locales = {
        @FCLocale(lang = LocaleType.EN_US, text = "Teleport to a player or location")
    }
)
public void teleportCommand(
        CommandSender sender, 
        @Arg(name = "<player>") Player target, // <> means notNull
        @Arg(name = "[destination]") Player destination) { // [] means 'nullable'
    
    if (destination != null) {
        target.teleport(destination.getLocation());
        FancyText.of("§aTeleported §e" + target.getName() + " §ato §e" + destination.getName())
                .send(sender);
    }
}
```

**Features:**
- Automatic argument parsing and validation
- Built-in help system generation
- Permission and context validation
- Multi-language support
- Tab completion
- Subcommand support

### Configuration System

Advanced YAML configuration with smart caching, comments, and type-safe access.

```java
// Basic usage
Config config = new Config(pluginInstance, "config.yml");

// Type-safe getters with defaults
String serverName = config.getOrSetDefaultValue("server.name", "MyServer", 
    "The display name of your server");

boolean enableFeature = config.getOrSetDefaultValue("features.teleport", true);
```

```java
// Complex objects with @Loadable/@Salvable
public class TeleportLocation implements Salvable {
  //var x, y, z, world;
  @Override
  public void onConfigSave(ConfigSection section) {
      section.setValue("world", this.world);
      section.setValue("x", this.x);
      section.setValue("y", this.y);
      section.setValue("z", this.z);
  }
  
  @Loadable @Salvable
  public static TeleportLocation onConfigLoad(ConfigSection section) {
      return new TeleportLocation(
          section.getString("world"),
          section.getDouble("x"),
          section.getDouble("y"),
          section.getDouble("z")
      );
  }
}

List<TeleportLocation> warps = config.getLoadableList("warps", TeleportLocation.class);
```

**Features:**
- Smart memory caching with automatic cleanup
- Comment preservation and generation
- Type-safe access methods
- Custom object serialization
- Async saving

### FancyText & Messaging

Rich text formatting with click/hover events and component chaining.

```java
// Simple usage
FancyText.of("§aClick here to teleport!")
    .setHoverText("§7Teleports you to spawn")
    .setRunCommandAction("/spawn")
    .send(player);

// Complex formatting with chaining
FancyFormatter formatter = FancyText.of("§6[Server] ")
    .append("§fWelcome ", "§7You joined the server!")
    .append("§e" + player.getName(), "§7Click to view profile", "/profile " + player.getName())
    .append("§f to our server!");
    
formatter.send(player);

// Item display in hover
FancyText.of("§6Legendary Sword")
    .setHoverText(player.getItemInHand())
    .send(player);
```

### Player Data Management

Persistent player data with automatic saving and caching.

```java
public class MyPlayerData extends PlayerData {
    
    @Override
    public void onPlayerLogin() {
        // Called when player joins
    }
    
    public int getCoins() {
        return getPDSection().getOrSetDefaultValue("coins", 0);
    }
    
    public void addCoins(int amount) {
        int current = getCoins();
        getPDSection().setValue("coins", current + amount);
        markAsModified(); // Schedule async save
    }
}

// Usage
MyPlayerData data = PlayerController.getPlayerData(player, MyPlayerData.class);
data.addCoins(100);
```

### Database Integration

Hibernate-based database abstraction with SQLite and MySQL support.

```java
@Entity
@Table(name = "player_stats")
public class PlayerStats {
    @Id
    private String uuid;
    
    private int kills;
    private int deaths;
    
    // Getters/Setters
}

// Usage
HibernateConnection connection = DatabaseFactory.createSQLiteConnection("stats.db");
connection.registerEntity(PlayerStats.class);

// Query
PlayerStats stats = connection.findByPrimaryKey(PlayerStats.class, player.getUniqueId().toString());
```

### Localization System

Multi-language support with automatic message formatting.

```java
@FCLocale(lang = LocaleType.EN_US, text = "Welcome {player}!")
@FCLocale(lang = LocaleType.PT_BR, text = "Bem-vindo {player}!")
public static LocaleMessage WELCOME_MESSAGE;

// Usage
WELCOME_MESSAGE.send(player, "{player}", player.getName());
```

## 🔧 Utilities

### ItemStack Builder
```java
ItemStack sword = FCItemFactory.from(Material.DIAMOND_SWORD)
    .displayName("§6Legendary Blade")
    .lore("§7A powerful weapon", "§7forged by ancient smiths")
    .addEnchant(Enchantment.FIRE_ASPECT, 5)
    .build();
```

### NBT Manipulation with Item-NBT-API
Read more at https://github.com/tr7zw/Item-NBT-API Item-NBT-API
```java
FCItemFactory.from(itemStack)
    .setNbt(nbtCompound -> {
        nbtCompound.setBoolean("Unbreakable", true);
        nbtCompound.setInteger("HideFlags", 1);
        nbtCompound.setString("teste", "teste");
    })
   .build();

```

### Reflection Utilities
```java
MethodInvoker method = FCReflectionUtil.getMethod(Player.class, "getHandle");
Object nmsPlayer = method.invoke(player);
```

## 🔌 Integrations

- **WorldGuard/WorldEdit** - Region and selection utilities
- **Vault** - Economy and permission integration
- **PlaceholderAPI** - Custom placeholder support
- **LuckPerms** - Advanced permission handling
- **ProtocolLib** - Packet manipulation
- **BossShopPro** - Shop integration
- **FeatherBoard** - Scoreboard utilities

## 📖 Examples

## 🤝 Contributing

Contributions are welcome! Please note that this project requires several private dependencies for full compilation:

- Various CraftBukkit versions for NMS support
- Some paid plugins for integration features
- Private repository access for certain dependencies

## 📞 Support

- **Discord**: [Join our community](https://discord.petrus.dev/)
- **SpigotMC**: [Plugin page](https://www.spigotmc.org/resources/97739/)
- **Issues**: [GitHub Issues](https://github.com/EverNife/EverNifeCore/issues)

---

<p align="center">
  <strong>Developed with ❤️ by <a href="https://github.com/EverNife">EverNife</a></strong>
  <br>
  <em>Empowering Minecraft plugin development since 2016</em>
</p>
