package br.com.battlebits.commons.bukkit.listener;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;

import br.com.battlebits.commons.BattlebitsAPI;
import br.com.battlebits.commons.bukkit.account.BukkitPlayer;
import br.com.battlebits.commons.bukkit.event.account.PlayerUpdatedFieldEvent;
import br.com.battlebits.commons.bungee.BungeeMain;
import br.com.battlebits.commons.core.account.BattlePlayer;
import br.com.battlebits.commons.core.clan.Clan;
import br.com.battlebits.commons.core.data.DataPlayer;
import br.com.battlebits.commons.core.translate.T;
import br.com.battlebits.commons.core.translate.Translate;
import br.com.battlebits.commons.util.GeoIpUtils;
import br.com.battlebits.commons.util.GeoIpUtils.IpCityResponse;

public class AccountListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public synchronized void onAsync(AsyncPlayerPreLoginEvent event) {
		if (Bukkit.getPlayer(event.getUniqueId()) != null) {
			event.setLoginResult(Result.KICK_OTHER);
			event.setKickMessage("Already online");
			return;
		}
		String userName = event.getName();
		InetAddress ipAdress = event.getAddress();
		UUID uuid = event.getUniqueId();
		String countryCode = "-";
		String timeZoneCode = "0";
		BattlebitsAPI.debug("CONNECTION > STARTING");
		try {
			BukkitPlayer player = DataPlayer.getRedisBukkitPlayer(uuid);
			if (player == null) {
				try {
					IpCityResponse responde = GeoIpUtils.getIpStatus(ipAdress.getHostAddress());
					if (responde != null)
						countryCode = responde.getCountryCode();
					timeZoneCode = responde.getTimeZone();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				player = DataPlayer.createIfNotExistMongoBukkit(uuid, userName, ipAdress.getHostAddress(), countryCode,
						timeZoneCode);
				DataPlayer.saveRedisPlayer(player);
				player.setCacheOnQuit(true);
				BattlebitsAPI.debug("CONNECTION > TRYING MONGO");
			} else {
				if (DataPlayer.checkCache(uuid))
					player.setCacheOnQuit(true);
				BattlebitsAPI.debug("CONNECTION > REDIS FOUND");
			}
			player.setJoinData(userName, ipAdress.getHostAddress(), countryCode, timeZoneCode);
			BattlebitsAPI.getAccountCommon().loadBattlePlayer(uuid, player);
			if (player.getClanUniqueId() != null) {
				try {
					Clan clan = BungeeMain.getPlugin().getClanManager().loadClan(player.getClanUniqueId());
					if (clan == null) {
						clan = player.getClan();
					}
					if (!clan.isParticipant(player))
						player.setClanUniqueId(null);
					if (clan != null) {
						clan.updatePlayer(player);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			BattlebitsAPI.debug("ACCOUNT > CLOSE");
		} catch (Exception e) {
			event.setKickMessage(T.t(BattlebitsAPI.getDefaultLanguage(), "account-load-failed"));
			e.printStackTrace();
			return;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRemoveAccount(AsyncPlayerPreLoginEvent event) {
		if (BattlebitsAPI.getAccountCommon().getBattlePlayer(event.getUniqueId()) == null) {
			event.setLoginResult(Result.KICK_OTHER);
			event.setKickMessage(Translate.getTranslation(BattlebitsAPI.getDefaultLanguage(), "account-not-load"));
		}
		if (event.getLoginResult() != Result.ALLOWED) {
			BattlebitsAPI.getAccountCommon().unloadBattlePlayer(event.getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLeave(PlayerQuitEvent event) {
		if (((BukkitPlayer) BattlePlayer.getPlayer(event.getPlayer().getUniqueId())).isCacheOnQuit())
			DataPlayer.cacheRedisPlayer(event.getPlayer().getUniqueId());
		BattlebitsAPI.getAccountCommon().unloadBattlePlayer(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onUpdate(PlayerUpdatedFieldEvent event) {
		BukkitPlayer player = event.getBukkitPlayer();
		switch (event.getField()) {
		case "groups":
		case "ranks":
			player.loadTags();
			player.setTag(player.getDefaultTag());
			break;
		default:
			break;
		}
	}
}
