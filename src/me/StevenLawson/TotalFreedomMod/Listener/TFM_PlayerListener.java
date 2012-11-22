package me.StevenLawson.TotalFreedomMod.Listener;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import me.StevenLawson.TotalFreedomMod.*;
import net.minecraft.server.BanEntry;
import net.minecraft.server.BanList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerConfigurationManagerAbstract;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class TFM_PlayerListener implements Listener
{
    private static final SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd \'at\' HH:mm:ss z");
    private static final List<String> BLOCKED_MUTED_CMDS = Arrays.asList(StringUtils.split("say,me,msg,m,tell,r,reply", ","));

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();

        switch (event.getAction())
        {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
            {
                switch (event.getMaterial())
                {
                    case WATER_BUCKET:
                    {
                        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), new ItemStack(Material.COOKIE, 1));
                        player.sendMessage(ChatColor.GRAY + "Water buckets are currently disabled.");
                        event.setCancelled(true);
                        return;
                    }
                    case LAVA_BUCKET:
                    {
                        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), new ItemStack(Material.COOKIE, 1));
                        player.sendMessage(ChatColor.GRAY + "Lava buckets are currently disabled.");
                        event.setCancelled(true);
                        return;
                    }
                }
                break;
            }
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
            {
                switch (event.getMaterial())
                {
                    case STICK:
                    {
                        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(player);
                        if (playerdata.mobThrowerEnabled())
                        {
                            Location player_pos = player.getLocation();
                            Vector direction = player_pos.getDirection().normalize();

                            LivingEntity rezzed_mob = (LivingEntity) player.getWorld().spawnEntity(player_pos.add(direction.multiply(2.0)), playerdata.mobThrowerCreature());
                            rezzed_mob.setVelocity(direction.multiply(playerdata.mobThrowerSpeed()));
                            playerdata.enqueueMob(rezzed_mob);

                            event.setCancelled(true);
                            return;
                        }
                        break;
                    }
                    case SULPHUR:
                    {
                        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(player);
                        if (playerdata.isMP44Armed())
                        {
                            if (playerdata.toggleMP44Firing())
                            {
                                playerdata.startArrowShooter(TotalFreedomMod.plugin);
                            }
                            else
                            {
                                playerdata.stopArrowShooter();
                            }

                            event.setCancelled(true);
                            return;
                        }
                        break;
                    }
                    case BLAZE_ROD:
                    {
                        if (TotalFreedomMod.allowExplosions && TFM_SuperadminList.isSeniorAdmin(player))
                        {
                            Block target_block = null;

                            if (event.getAction().equals(Action.LEFT_CLICK_AIR))
                            {
                                target_block = player.getTargetBlock(null, 120);
                            }
                            else
                            {
                                target_block = event.getClickedBlock();
                            }

                            if (target_block != null)
                            {
                                player.getWorld().createExplosion(target_block.getLocation(), 4F, true);
                                player.getWorld().strikeLightning(target_block.getLocation());
                            }
                            else
                            {
                                player.sendMessage("Can't resolve target block.");
                            }

                            event.setCancelled(true);
                            return;
                        }

                        break;
                    }
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event)
    {
        Player p = event.getPlayer();
        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(p);

        for (Entry<Player, Double> fuckoff : TotalFreedomMod.fuckoffEnabledFor.entrySet())
        {
            Player fuckoff_player = fuckoff.getKey();

            if (fuckoff_player.equals(p) || !fuckoff_player.isOnline())
            {
                continue;
            }

            double fuckoff_range = fuckoff.getValue().doubleValue();

            Location mover_pos = p.getLocation();
            Location fuckoff_pos = fuckoff_player.getLocation();

            double distanceSquared;
            try
            {
                distanceSquared = mover_pos.distanceSquared(fuckoff_pos);
            }
            catch (IllegalArgumentException ex)
            {
                continue;
            }

            if (distanceSquared < (fuckoff_range * fuckoff_range))
            {
                event.setTo(fuckoff_pos.clone().add(mover_pos.subtract(fuckoff_pos).toVector().normalize().multiply(fuckoff_range * 1.1)));
                break;
            }
        }

        boolean do_freeze = false;
        if (TotalFreedomMod.allPlayersFrozen)
        {
            if (!TFM_SuperadminList.isUserSuperadmin(p))
            {
                do_freeze = true;
            }
        }
        else
        {
            if (playerdata.isFrozen())
            {
                do_freeze = true;
            }
        }

        if (do_freeze)
        {
            Location from = event.getFrom();
            Location to = event.getTo().clone();

            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());

            event.setTo(to);
        }

        if (playerdata.isCaged())
        {
            Location target_pos = p.getLocation().add(0, 1, 0);

            boolean out_of_cage = false;
            if (!target_pos.getWorld().equals(playerdata.getCagePos().getWorld()))
            {
                out_of_cage = true;
            }
            else
            {
                out_of_cage = target_pos.distanceSquared(playerdata.getCagePos()) > (2.5 * 2.5);
            }

            if (out_of_cage)
            {
                playerdata.setCaged(true, target_pos, playerdata.getCageMaterial(TFM_UserInfo.CageLayer.OUTER), playerdata.getCageMaterial(TFM_UserInfo.CageLayer.INNER));
                playerdata.regenerateHistory();
                playerdata.clearHistory();
                TFM_Util.buildHistory(target_pos, 2, playerdata);
                TFM_Util.generateCube(target_pos, 2, playerdata.getCageMaterial(TFM_UserInfo.CageLayer.OUTER));
                TFM_Util.generateCube(target_pos, 1, playerdata.getCageMaterial(TFM_UserInfo.CageLayer.INNER));
            }
        }

        if (playerdata.isOrbiting())
        {
            if (p.getVelocity().length() < playerdata.orbitStrength() * (2.0 / 3.0))
            {
                p.setVelocity(new Vector(0, playerdata.orbitStrength(), 0));
            }
        }
        if (TotalFreedomMod.landminesEnabled && TotalFreedomMod.allowExplosions)
        {
            Iterator<TFM_LandmineData> landmines = TFM_LandmineData.landmines.iterator();
            while (landmines.hasNext())
            {
                TFM_LandmineData landmine = landmines.next();

                Location landmine_pos = landmine.landmine_pos;
                if (landmine_pos.getBlock().getType() != Material.TNT)
                {
                    landmines.remove();
                    continue;
                }

                if (!landmine.player.equals(p))
                {
                    if (p.getWorld().equals(landmine_pos.getWorld()))
                    {
                        if (p.getLocation().distanceSquared(landmine_pos) <= (landmine.radius * landmine.radius))
                        {
                            landmine.landmine_pos.getBlock().setType(Material.AIR);

                            TNTPrimed tnt1 = landmine_pos.getWorld().spawn(landmine_pos, TNTPrimed.class);
                            tnt1.setFuseTicks(40);
                            tnt1.setPassenger(p);
                            tnt1.setVelocity(new Vector(0.0, 2.0, 0.0));

                            TNTPrimed tnt2 = landmine_pos.getWorld().spawn(p.getLocation(), TNTPrimed.class);
                            tnt2.setFuseTicks(1);

                            p.setGameMode(GameMode.SURVIVAL);
                            landmines.remove();
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeavesDecay(LeavesDecayEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncPlayerChatEvent event)
    {
        try
        {
            final Player p = event.getPlayer();

            TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(p);
            playerdata.incrementMsgCount();

            // check for spam
            if (playerdata.getMsgCount() > 10)
            {
                TFM_Util.bcastMsg(p.getName() + " was automatically kicked for spamming chat.", ChatColor.RED);
                TFM_Util.autoEject(p, "Kicked for spamming chat.");

                playerdata.resetMsgCount();

                event.setCancelled(true);
                return;
            }

            // check for muted
            if (playerdata.isMuted())
            {
                if (!TFM_SuperadminList.isUserSuperadmin(p))
                {
                    p.sendMessage(ChatColor.RED + "You are muted, STFU!");
                    event.setCancelled(true);
                    return;
                }
                else
                {
                    playerdata.setMuted(false);
                }
            }

            String message = event.getMessage().trim();

            // strip color from messages
            message = ChatColor.stripColor(message);

            // truncate messages that are too long - 100 characters is vanilla client max
            if (message.length() > 100)
            {
                message = message.substring(0, 100);
                TFM_Util.playerMsg(p, "Message was shortened because it was too long to send.");
            }

            // check for caps
            if (message.length() >= 6)
            {
                int caps = 0;
                for (char c : message.toCharArray())
                {
                    if (Character.isUpperCase(c))
                    {
                        caps++;
                    }
                }
                if (((float) caps / (float) message.length()) > 0.75) //Compute a ratio so that longer sentences can have more caps.
                {
                    message = message.toLowerCase();
                }
            }

            // finally, set message
            event.setMessage(message);
        }
        catch (Exception ex)
        {
            TFM_Log.severe(ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        String command = event.getMessage();
        Player p = event.getPlayer();

        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(p);
        playerdata.incrementMsgCount();

        if (playerdata.getMsgCount() > 10)
        {
            TFM_Util.bcastMsg(p.getName() + " was automatically kicked for spamming commands.", ChatColor.RED);
            TFM_Util.autoEject(p, "Kicked for spamming commands.");

            playerdata.resetMsgCount();

            TFM_Util.wipeEntities(true, true);

            event.setCancelled(true);
            return;
        }

        if (TotalFreedomMod.preprocessLogEnabled)
        {
            TFM_Log.info(String.format("[PREPROCESS_COMMAND] %s(%s): %s", p.getName(), ChatColor.stripColor(p.getDisplayName()), command), true);
        }

        command = command.toLowerCase().trim();

        boolean block_command = false;

        //Commands that will auto-kick the user:
        if (Pattern.compile("^/stop").matcher(command).find())
        {
            if (!TFM_SuperadminList.isUserSuperadmin(p))
            {
                block_command = true;
            }
        }
        else if (Pattern.compile("^/reload").matcher(command).find())
        {
            if (!TFM_SuperadminList.isUserSuperadmin(p))
            {
                block_command = true;
            }
        }
        else if (Pattern.compile("^/save-").matcher(command).find())
        {
            if (!TFM_SuperadminList.isUserSuperadmin(p))
            {
                block_command = true;
            }
        }

        if (block_command)
        {
            TFM_Util.autoEject(p, "You used a prohibited command: " + command);
            TFM_Util.bcastMsg(p.getName() + " was automatically kicked for using harmful commands.", ChatColor.RED);
        }
        else
        {
            // commands that will not auto-kick the user, but still deny:
            if (Pattern.compile("^/time").matcher(command).find())
            {
                p.sendMessage(ChatColor.GRAY + "Server-side time changing is disabled. Please use /ptime to set your own personal time.");
                block_command = true;
            }
            else if (Pattern.compile("^/md").matcher(command).find())
            {
                p.sendMessage(ChatColor.GRAY + "This server now uses DisguiseCraft instead of MobDisguise. Type /d to disguise and /u to undisguise.");
                block_command = true;
            }
            else if (Pattern.compile("^/gamemode").matcher(command).find())
            {
                p.sendMessage(ChatColor.GRAY + "Use /creative and /survival to set your gamemode.");
                block_command = true;
            }
            else if (Pattern.compile("^/ban").matcher(command).find())
            {
                if (!Pattern.compile("^/banlist").matcher(command).find())
                {
                    block_command = true;
                }
            }
            else if (Pattern.compile("^/kick").matcher(command).find())
            {
                if (!TFM_SuperadminList.isUserSuperadmin(p))
                {
                    block_command = true;
                }
            }
            else if (Pattern.compile("^/kill").matcher(command).find())
            {
                if (!TFM_SuperadminList.isUserSuperadmin(p))
                {
                    block_command = true;
                }
            }
            else if (Pattern.compile("^/e?socialspy").matcher(command).find())
            {
                if (!TFM_SuperadminList.isUserSuperadmin(p))
                {
                    block_command = true;
                }
            }
            else if (Pattern.compile("^/pardon").matcher(command).find())
            {
                block_command = true;
            }
            else if (Pattern.compile("^/toggledownfall").matcher(command).find())
            {
                block_command = true;
            }
        }

        if (block_command)
        {
            p.sendMessage(ChatColor.GRAY + "That command is blocked.");
            event.setCancelled(true);
            return;
        }

        if (playerdata.isMuted())
        {
            if (!TFM_SuperadminList.isUserSuperadmin(p))
            {
                for (String test_command : BLOCKED_MUTED_CMDS)
                {
                    if (Pattern.compile("^/" + test_command.toLowerCase() + " ").matcher(command.toLowerCase()).find())
                    {
                        p.sendMessage(ChatColor.RED + "That command is blocked while you are muted.");
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            else
            {
                playerdata.setMuted(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(PlayerDropItemEvent event)
    {
        if (TotalFreedomMod.autoEntityWipe)
        {
            if (event.getPlayer().getWorld().getEntities().size() > 750)
            {
                event.setCancelled(true);
            }
            else
            {
                event.getItemDrop().remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event)
    {
        Player p = event.getPlayer();
        if (TotalFreedomMod.fuckoffEnabledFor.containsKey(p))
        {
            TotalFreedomMod.fuckoffEnabledFor.remove(p);
        }
        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(p);
        playerdata.disarmMP44();
        if (playerdata.isCaged())
        {
            playerdata.regenerateHistory();
            playerdata.clearHistory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player p = event.getPlayer();
        if (TotalFreedomMod.fuckoffEnabledFor.containsKey(p))
        {
            TotalFreedomMod.fuckoffEnabledFor.remove(p);
        }
        TFM_UserInfo playerdata = TFM_UserInfo.getPlayerData(p);
        playerdata.disarmMP44();
        if (playerdata.isCaged())
        {
            playerdata.regenerateHistory();
            playerdata.clearHistory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        try
        {
            final Player p = event.getPlayer();

            TFM_UserList.getInstance(TotalFreedomMod.plugin).addUser(p);

            boolean superadmin_impostor = TFM_SuperadminList.isSuperadminImpostor(p);

            if (superadmin_impostor || TFM_SuperadminList.isUserSuperadmin(p))
            {
                TFM_Util.bcastMsg(ChatColor.AQUA + p.getName() + " is " + TFM_Util.getRank(p));

                if (superadmin_impostor)
                {
                    p.getInventory().clear();
                    p.setOp(false);
                    p.setGameMode(GameMode.SURVIVAL);
                    TFM_Util.bcastMsg("Warning: " + p.getName() + " has been flagged as an impostor!", ChatColor.RED);
                }
                else
                {
                    p.setOp(true);

                    TFM_SuperadminList.updateLastLogin(p);
                }
            }

            if (TotalFreedomMod.adminOnlyMode)
            {
                TotalFreedomMod.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(TotalFreedomMod.plugin, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        p.sendMessage(ChatColor.RED + "Server is currently closed to non-superadmins.");
                    }
                }, 60L);
            }
        }
        catch (Throwable ex)
        {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event)
    {
        // this should supersede all other onPlayerLogin authentication on the TFM server.
        // when using the TFM CraftBukkit, CraftBukkit itself should not do any of its own authentication.

        final Server server = TotalFreedomMod.plugin.getServer();

        final ServerConfigurationManagerAbstract scm = MinecraftServer.getServer().getServerConfigurationManager();
        final BanList banByIP = scm.getIPBans();
        final BanList banByName = scm.getNameBans();

        final Player p = event.getPlayer();

        final String player_name = p.getName();
        final String player_ip = event.getAddress().getHostAddress().trim().toLowerCase();

        if (player_name.trim().length() <= 2)
        {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Your username is too short (must be at least 3 characters long).");
            return;
        }
        else if (Pattern.compile("[^a-zA-Z0-9\\-\\.\\_]").matcher(player_name).find())
        {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Your username contains invalid characters.");
            return;
        }

        // not safe to use TFM_Util.isUserSuperadmin for player logging in because p.getAddress() will return a null until after player login.
        boolean is_superadmin;
        if (server.getOnlineMode())
        {
            is_superadmin = TFM_SuperadminList.getSuperadminNames().contains(player_name.toLowerCase());
        }
        else
        {
            is_superadmin = TFM_SuperadminList.checkPartialSuperadminIP(player_ip);
        }

        if (!is_superadmin)
        {
            BanEntry ban_entry = null;

            if (banByName.isBanned(player_name.toLowerCase()))
            {
                ban_entry = (BanEntry) banByName.getEntries().get(player_name.toLowerCase());

                String kick_message = "You are banned from this server.";
                if (ban_entry != null)
                {
                    kick_message = kick_message + "\nReason: " + ban_entry.getReason();
                    if (ban_entry.getExpires() != null)
                    {
                        kick_message = kick_message + "\nYour ban will be removed on " + date_format.format(ban_entry.getExpires());
                    }
                }

                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kick_message);
                return;
            }

            boolean is_ip_banned = false;

            @SuppressWarnings("rawtypes")
            Iterator ip_bans = banByIP.getEntries().keySet().iterator();
            while (ip_bans.hasNext())
            {
                String test_ip = (String) ip_bans.next();

                if (!test_ip.matches("^\\d{1,3}\\.\\d{1,3}\\.(\\d{1,3}|\\*)\\.(\\d{1,3}|\\*)$"))
                {
                    continue;
                }

                if (player_ip.equals(test_ip))
                {
                    ban_entry = (BanEntry) banByIP.getEntries().get(test_ip);
                    is_ip_banned = true;
                    break;
                }

                String[] test_ip_parts = test_ip.split("\\.");
                String[] player_ip_parts = player_ip.split("\\.");

                boolean is_match = false;

                for (int i = 0; i < test_ip_parts.length && i < player_ip_parts.length; i++)
                {
                    if (test_ip_parts[i].equals("*") && i >= 2)
                    {
                        is_match = true;
                    }
                    else if (test_ip_parts[i].equals(player_ip_parts[i]))
                    {
                        is_match = true;
                    }
                    else
                    {
                        is_match = false;
                    }

                    if (!is_match)
                    {
                        break;
                    }
                }

                if (is_match)
                {
                    ban_entry = (BanEntry) banByIP.getEntries().get(test_ip);
                    is_ip_banned = true;
                    break;
                }
            }

            if (is_ip_banned)
            {
                String kick_message = "Your IP address is banned from this server.";
                if (ban_entry != null)
                {
                    kick_message = kick_message + "\nReason: " + ban_entry.getReason();
                    if (ban_entry.getExpires() != null)
                    {
                        kick_message = kick_message + "\nYour ban will be removed on " + date_format.format(ban_entry.getExpires());
                    }
                }

                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kick_message);
                return;
            }

            for (String test_player : TotalFreedomMod.permbanned_players)
            {
                if (test_player.equalsIgnoreCase(player_name))
                {
                    event.disallow(PlayerLoginEvent.Result.KICK_BANNED, ChatColor.RED + "Your username is permanently banned from this server.");
                    return;
                }
            }

            for (String test_ip : TotalFreedomMod.permbanned_ips)
            {
                //TODO: Add support for wildcards in permbanned_ips list.
                //TODO: Create generic wildcard IP matching method since we do this several times already in this project.
                if (test_ip.equalsIgnoreCase(player_ip))
                {
                    event.disallow(PlayerLoginEvent.Result.KICK_BANNED, ChatColor.RED + "Your IP address is permanently banned from this server.");
                    return;
                }
            }

            if (server.getOnlinePlayers().length >= server.getMaxPlayers())
            {
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, "Sorry, but this server is full.");
                return;
            }

            if (TotalFreedomMod.adminOnlyMode)
            {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Server is temporarily open to admins only.");
                return;
            }

            if (scm.hasWhitelist)
            {
                if (!scm.getWhitelisted().contains(player_name.toLowerCase()))
                {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "You are not whitelisted on this server.");
                    return;
                }
            }

            for (Player test_player : server.getOnlinePlayers())
            {
                if (test_player.getName().equalsIgnoreCase(player_name))
                {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Your username is already logged into this server.");
                    return;
                }
            }
        }
        else
        {
            for (Player test_player : server.getOnlinePlayers())
            {
                if (test_player.getName().equalsIgnoreCase(player_name))
                {
                    test_player.kickPlayer("An admin just logged in with the username you are using.");
                }
            }

            boolean can_kick = true; // if the server is full of superadmins, however unlikely that might be, this will prevent an infinite loop.
            while (server.getOnlinePlayers().length >= server.getMaxPlayers() && can_kick)
            {
                can_kick = false;
                for (Player test_player : server.getOnlinePlayers())
                {
                    if (!TFM_SuperadminList.isUserSuperadmin(test_player))
                    {
                        can_kick = true;
                        test_player.kickPlayer("You have been kicked to free up room for an admin.");
                        break;
                    }
                }
            }
        }
    }
}
