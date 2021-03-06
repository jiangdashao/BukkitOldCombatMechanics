package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.ModuleLoader;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.module.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by Rayzr522 on 6/14/16.
 */

public class Config {

    private static OCMMain plugin;
    private static FileConfiguration config;
    private static List<Material> interactive = new ArrayList<>();

    public static void Initialise(OCMMain plugin) {
        Config.plugin = plugin;
        config = plugin.getConfig();

        reload();
    }

    /**
     * @return Whether config was changed or not
     */
    private static boolean checkConfigVersion() {
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml")));

        if (config.getInt("config-version") != defaultConfig.getInt("config-version")) {
            plugin.getLogger().warning("Config version does not match, backing up old config and creating a new one");
            plugin.upgradeConfig();
            reload();
            return true;
        }

        return false;
    }


    public static void reload() {
        if (plugin.doesConfigymlExist()) {
            plugin.reloadConfig();
            config = plugin.getConfig();
        } else
            plugin.upgradeConfig();

        if (checkConfigVersion()) {
            // checkConfigVersion will call #reload() again anyways
            return;
        }

        //plugin.restartTask(); //Restart no-collision check
        plugin.restartSweepTask(); //Restart sword sweep check

        Messenger.DEBUG_ENABLED = config.getBoolean("debug.enabled");

        if (Config.moduleEnabled("old-tool-damage"))
            WeaponDamages.Initialise(plugin); //Reload weapon damages from config

        //if(Config.moduleEnabled("old-armour-strength"))
        ArmourValues.Initialise(plugin); //Reload armour values from config

        //Setting correct attack speed and armour values for online players
        for (World world : Bukkit.getWorlds()) {

            List<Player> players = world.getPlayers();

            double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.generic-attack-speed");

            if (!Config.moduleEnabled("disable-attack-cooldown", world))
                GAS = 4; //If module is disabled, set attack speed to 1.9 default

            boolean isArmourEnabled = Config.moduleEnabled("old-armour-strength", world);

            for (Player player : players) {
                //Setting attack speed
                AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
                double baseValue = attribute.getBaseValue();

                if (baseValue != GAS) {
                    attribute.setBaseValue(GAS);
                    player.saveData();
                }

                //Setting armour values
                ModuleOldArmourStrength.setArmourAccordingly(player, isArmourEnabled);
            }
        }

        ModuleLoader.ToggleModules();

        if (Config.moduleEnabled("disable-offhand"))
            ModuleDisableOffHand.INSTANCE.reloadList();
        if (Config.moduleEnabled("old-golden-apples")) {
            ModuleGoldenApple.INSTANCE.reloadRecipes();
            ModuleGoldenApple.INSTANCE.registerCrafting();
        }
        if (Config.moduleEnabled("sword-blocking") || Config.moduleEnabled("disable-elytra"))
            reloadInteractiveBlocks();
        if (Config.moduleEnabled("sword-blocking"))
            ModuleSwordBlocking.INSTANCE.reload();
        if (moduleEnabled("disable-crafting"))
            ModuleDisableCrafting.INSTANCE.reload();
    }

    public static boolean moduleEnabled(String name, World world) {
        ConfigurationSection section = config.getConfigurationSection(name);

        if (section == null) {
            plugin.getLogger().warning("Tried to check module '" + name + "', but it didn't exist!");
            return false;
        }

        if (section.getBoolean("enabled")) {
            if (world == null) {
                return true;
            }

            List<String> list = section.getStringList("worlds");

            return list == null || list.size() <= 0 || list.stream().anyMatch(entry -> entry.equalsIgnoreCase(world.getName()));
        }

        return false;
    }

    public static boolean moduleEnabled(String name) {
        return moduleEnabled(name, null);
    }

    public static boolean debugEnabled() {
        return moduleEnabled("debug", null);
    }

    public static List<?> getWorlds(String moduleName) {
        return config.getList(moduleName + ".worlds");
    }

    public static boolean moduleSettingEnabled(String moduleName, String moduleSettingName) {
        return config.getBoolean(moduleName + "." + moduleSettingName);
    }

    public static void setModuleSetting(String moduleName, String moduleSettingName, boolean value) {
        config.set(moduleName + "." + moduleSettingName, value);
        plugin.saveConfig();
    }

    public static void reloadInteractiveBlocks() {
        List<String> list = config.getStringList("interactive");
        if (list == null) return;

        interactive = list.stream().map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<Material> getInteractiveBlocks() {
        return interactive;
    }

    /**
     * Only use if you can't access config through plugin instance
     *
     * @return config.yml instance
     */
    public static FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
