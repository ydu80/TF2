package com.chaseoes.tf2;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;

import net.gravitydevelopment.updater.Updater;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.chaseoes.tf2.capturepoints.CapturePointUtilities;
import com.chaseoes.tf2.classes.ClassUtilities;
import com.chaseoes.tf2.commands.CommandManager;
import com.chaseoes.tf2.commands.CreateCommand;
import com.chaseoes.tf2.commands.DebugCommand;
import com.chaseoes.tf2.commands.DeleteCommand;
import com.chaseoes.tf2.commands.DisableCommand;
import com.chaseoes.tf2.commands.EnableCommand;
import com.chaseoes.tf2.commands.JoinCommand;
import com.chaseoes.tf2.commands.LeaveCommand;
import com.chaseoes.tf2.commands.ListCommand;
import com.chaseoes.tf2.commands.RedefineCommand;
import com.chaseoes.tf2.commands.ReloadCommand;
import com.chaseoes.tf2.commands.SetCommand;
import com.chaseoes.tf2.commands.StartCommand;
import com.chaseoes.tf2.commands.StopCommand;
import com.chaseoes.tf2.listeners.*;
import com.chaseoes.tf2.lobbywall.LobbyWall;
import com.chaseoes.tf2.lobbywall.LobbyWallUtilities;
import com.chaseoes.tf2.utilities.*;

public class TF2 extends JavaPlugin {

    public HashMap<String, Map> maps = new HashMap<String, Map>();
    public HashMap<String, String> usingSetSpawnMenu = new HashMap<String, String>();
    public HashMap<String, StatCollector> stats = new HashMap<String, StatCollector>();
    public IconMenu setSpawnMenu;
    private static TF2 instance;
    public boolean enabled;
    public boolean isDisabling;

    public static TF2 getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        isDisabling = false;
        getServer().getScheduler().cancelTasks(this);

        setupClasses();

        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().log(Level.SEVERE, pluginRequiredMessage("WorldEdit"));
            getServer().getPluginManager().disablePlugin(this);
            enabled = false;
            return;
        }

        getCommand("tf2").setExecutor(new CommandManager());
        getConfig().options().copyDefaults(true);
        saveConfig();
        DataConfiguration.getData().reloadData();

        for (String map : MapUtilities.getUtilities().getEnabledMaps()) {
            addMap(map, GameStatus.WAITING);
        }

        for (String map : MapUtilities.getUtilities().getDisabledMaps()) {
            addMap(map, GameStatus.DISABLED);
        }

        if (MessagesFile.getMessages().reloadMessages()) {
            MessagesFile.getMessages().getMessagesFile().options().copyDefaults(true);
            MessagesFile.getMessages().saveMessages();
        } else {
            getLogger().severe("Error parsing messages.yml, disabling plugin...");
            setEnabled(false);
            return;
        }

        Schedulers.getSchedulers().startAFKChecker();

        LobbyWall.getWall().startTask();

        setSpawnMenu = new IconMenu(Localizer.getLocalizer().loadMessage("SETSPAWN-TITLE"), 9, new IconMenu.OptionClickEventHandler() {
            @Override
            public void onOptionClick(IconMenu.OptionClickEvent event) {
                String map = usingSetSpawnMenu.get(event.getPlayer().getName());
                String name = ChatColor.stripColor(event.getName());
                if (name.equalsIgnoreCase(Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-LOBBY"))) {
                    MapUtilities.getUtilities().setTeamLobby(map, Team.BLUE, event.getPlayer().getLocation());
                    event.getPlayer().sendMessage(Localizer.getLocalizer().loadPrefixedMessage("SETSPAWN-BLUE-LOBBY-DESC"));
                    usingSetSpawnMenu.remove(event.getPlayer().getName());
                } else if (name.equalsIgnoreCase(Localizer.getLocalizer().loadMessage("SETSPAWN-RED-LOBBY"))) {
                    MapUtilities.getUtilities().setTeamLobby(map, Team.RED, event.getPlayer().getLocation());
                    event.getPlayer().sendMessage(Localizer.getLocalizer().loadPrefixedMessage("SETSPAWN-RED-LOBBY-DESC"));
                    usingSetSpawnMenu.remove(event.getPlayer().getName());
                } else if (name.equalsIgnoreCase(Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-SPAWN"))) {
                    MapUtilities.getUtilities().setTeamSpawn(map, Team.BLUE, event.getPlayer().getLocation());
                    event.getPlayer().sendMessage(Localizer.getLocalizer().loadPrefixedMessage("SETSPAWN-BLUE-SPAWN-DESC"));
                    usingSetSpawnMenu.remove(event.getPlayer().getName());
                } else if (name.equalsIgnoreCase(Localizer.getLocalizer().loadMessage("SETSPAWN-RED-SPAWN"))) {
                    MapUtilities.getUtilities().setTeamSpawn(map, Team.RED, event.getPlayer().getLocation());
                    event.getPlayer().sendMessage(Localizer.getLocalizer().loadPrefixedMessage("SETSPAWN-RED-SPAWN-DESC"));
                    usingSetSpawnMenu.remove(event.getPlayer().getName());
                }
                event.setWillClose(true);
            }
        }, this).setOption(2, new ItemStack(Material.REDSTONE, 1), ChatColor.DARK_RED + "" + ChatColor.BOLD + Localizer.getLocalizer().loadMessage("SETSPAWN-RED-LOBBY") + ChatColor.RESET, ChatColor.WHITE + Localizer.getLocalizer().loadMessage("SETSPAWN-RED-LOBBY-DESC")).setOption(3, new ItemStack(Material.INK_SACK, 1, (short) 4), ChatColor.AQUA + "" + ChatColor.BOLD + Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-LOBBY") + ChatColor.RESET, ChatColor.WHITE + Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-LOBBY-DESC")).setOption(4, new ItemStack(Material.WOOL, 1, (short) 14), ChatColor.DARK_RED + "" + ChatColor.BOLD + Localizer.getLocalizer().loadMessage("SETSPAWN-RED-SPAWN") + ChatColor.RESET, ChatColor.WHITE + Localizer.getLocalizer().loadMessage("SETSPAWN-RED-SPAWN-DESC")).setOption(5, new ItemStack(Material.WOOL, 1, (short) 11), ChatColor.AQUA + "" + ChatColor.BOLD + Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-SPAWN") + ChatColor.RESET, ChatColor.WHITE + Localizer.getLocalizer().loadMessage("SETSPAWN-BLUE-SPAWN-DESC")).setOption(6, new ItemStack(Material.BEDROCK, 1), ChatColor.RED + "" + ChatColor.BOLD + Localizer.getLocalizer().loadMessage("SETSPAWN-EXIT") + ChatColor.RESET, ChatColor.RED + Localizer.getLocalizer().loadMessage("SETSPAWN-EXIT-DESC"));

        if (getConfig().getBoolean("auto-update")) {
            if (!getDescription().getVersion().contains("SNAPSHOT")) {
                new Updater(this, 46264, this.getFile(), Updater.UpdateType.DEFAULT, false);
            }
        }

        try {
            Metrics metrics = new Metrics(this);
            metrics.start();
        } catch (IOException e) {
            // Failed to submit Metrics!
        }

        // Connect to database after everything else has loaded.
        if (getConfig().getBoolean("stats-database.enabled")) {
            SQLUtilities.getUtilities().setup(this);
        }

        enabled = true;
    }

    @Override
    public void onDisable() {
        isDisabling = true;
        if (enabled) {
            reloadConfig();
            saveConfig();
            for (Map map : MapUtilities.getUtilities().getMaps()) {
                if (GameUtilities.getUtilities().getGame(map).getStatus() != GameStatus.WAITING && GameUtilities.getUtilities().getGame(map).getStatus() != GameStatus.DISABLED) {
                    GameUtilities.getUtilities().getGame(map).stopMatch(false);
                }
            }
            instance = null;
        }
        getServer().getScheduler().cancelTasks(this);
        enabled = false;
    }

    public void setupClasses() {
        MapUtilities.getUtilities().setup(this);
        WorldEditUtilities.getWEUtilities().setup(this);
        CreateCommand.getCommand().setup(this);
        RedefineCommand.getCommand().setup(this);
        LobbyWall.getWall().setup(this);
        DataConfiguration.getData().setup(this);
        LobbyWallUtilities.getUtilities().setup(this);
        WorldEditUtilities.getWEUtilities().setupWorldEdit(getServer().getPluginManager());
        ClassUtilities.getUtilities().setup(this);
        GameUtilities.getUtilities().setup(this);
        CapturePointUtilities.getUtilities().setup(this);
        Schedulers.getSchedulers().setup(this);
        CreateCommand.getCommand().setup(this);
        DeleteCommand.getCommand().setup(this);
        DisableCommand.getCommand().setup(this);
        EnableCommand.getCommand().setup(this);
        JoinCommand.getCommand().setup(this);
        LeaveCommand.getCommand().setup(this);
        ListCommand.getCommand().setup(this);
        ReloadCommand.getCommand().setup(this);
        SetCommand.getCommand().setup(this);
        DebugCommand.getCommand().setup(this);
        StartCommand.getCommand().setup(this);
        StopCommand.getCommand().setup(this);
        MessagesFile.getMessages().setup(this);

        // Register Events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockPlaceListener(), this);
        pm.registerEvents(new FoodLevelChangeListener(), this);
        pm.registerEvents(new PlayerInteractListener(), this);
        pm.registerEvents(new PlayerCommandPreprocessListener(), this);
        pm.registerEvents(new PlayerDamageByEntityListener(), this);
        pm.registerEvents(new PlayerDeathListener(), this);
        pm.registerEvents(new PlayerDropItemListener(), this);
        pm.registerEvents(new PlayerJoinListener(), this);
        pm.registerEvents(new PlayerMoveListener(), this);
        pm.registerEvents(new PlayerQuitListener(), this);
        pm.registerEvents(new PotionSplashListener(), this);
        pm.registerEvents(new ProjectileLaunchListener(), this);
        pm.registerEvents(new SignChangeListener(), this);
        pm.registerEvents(new TF2DeathListener(), this);
        pm.registerEvents(new BlockBreakListener(), this);
        pm.registerEvents(new EntityDamageListener(), this);
        pm.registerEvents(new EntityShootBowListener(), this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new PlayerRespawnListener(), this);
    }

    public Map getMap(String map) {
        return maps.get(map);
    }

    public void addMap(String map, GameStatus status) {
        Map m = new Map(this, map);
        Game g = new Game(m, this);
        maps.put(map, m);
        GameUtilities.getUtilities().addGame(m, g);
        m.load();
        GameUtilities.getUtilities().getGame(m).redHasBeenTeleported = false;
        GameUtilities.getUtilities().getGame(m).setStatus(status);
        if (status == GameStatus.DISABLED) {
            String[] creditlines = new String[4];
            creditlines[0] = " ";
            creditlines[1] = "--------------------------";
            creditlines[2] = "--------------------------";
            creditlines[3] = " ";
            LobbyWall.getWall().setAllLines(map, null, creditlines, false, false);
        }
    }

    public Collection<Map> getMaps() {
        return maps.values();
    }

    public void removeMap(String map) {
        Map m = maps.remove(map);
        Game game = GameUtilities.getUtilities().removeGame(m);
        game.stopMatch(false);
        LobbyWall.getWall().unloadCacheInfo(map);
        MapUtilities.getUtilities().destroyMap(m);
    }

    public boolean mapExists(String map) {
        return maps.containsKey(map);
    }

    public String pluginRequiredMessage(String plugin) {
        return "\n------------------------------ [ ERROR ] ------------------------------\n-----------------------------------------------------------------------\n\n" + plugin + " is REQUIRED to run TF2!\nPlease download " + plugin + ", or TF2 will NOT work!\nDownload at: " + getPluginURL(plugin) + "\nTF2 is now being disabled...\n\n-----------------------------------------------------------------------\n-----------------------------------------------------------------------";
    }

    public String getPluginURL(String plugin) {
        if (plugin.equalsIgnoreCase("WorldEdit")) {
            return "http://dev.bukkit.org/server-mods/worldedit/";
        }
        return "";
    }
}