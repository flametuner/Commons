package br.com.battlebits.commons.core.server;

import java.util.HashMap;
import java.util.Map;

import br.com.battlebits.commons.core.server.loadbalancer.BaseBalancer;
import br.com.battlebits.commons.core.server.loadbalancer.server.BattleServer;
import br.com.battlebits.commons.core.server.loadbalancer.server.HungerGamesServer;
import br.com.battlebits.commons.core.server.loadbalancer.server.MinigameServer;
import br.com.battlebits.commons.core.server.loadbalancer.server.MinigameState;
import br.com.battlebits.commons.core.server.loadbalancer.type.LeastConnection;
import br.com.battlebits.commons.core.server.loadbalancer.type.MostConnection;

public class ServerManager {

	private Map<String, BattleServer> activeServers;

	private HashMap<ServerType, BaseBalancer<BattleServer>> balancers;

	public ServerManager() {
		balancers = new HashMap<>();

		balancers.put(ServerType.LOBBY, new LeastConnection<>());
		balancers.put(ServerType.PVP_FULLIRON, new MostConnection<>());
		balancers.put(ServerType.PVP_SIMULATOR, new MostConnection<>());

		balancers.put(ServerType.CUSTOMHG, new MostConnection<>());
		balancers.put(ServerType.DOUBLEKITHG, new MostConnection<>());
		balancers.put(ServerType.HUNGERGAMES, new MostConnection<>());

		activeServers = new HashMap<>();
	}

	public BaseBalancer<BattleServer> getBalancer(ServerType type) {
		return balancers.get(type);
	}

	public void addActiveServer(String serverAddress, String serverIp, int maxPlayers) {
		updateActiveServer(serverIp, 0, maxPlayers, true);
	}

	public void updateActiveServer(String serverId, int onlinePlayers, int maxPlayers, boolean canJoin) {
		updateActiveServer(serverId, onlinePlayers, maxPlayers, canJoin, 0, null);
	}

	public void updateActiveServer(String serverId, int onlinePlayers, int maxPlayers, boolean canJoin, int tempo,
			MinigameState state) {
		serverId = serverId.toLowerCase();
		BattleServer server = activeServers.get(serverId);
		if (server == null) {
			if (serverId.endsWith("battle-hg.com")) {
				server = new HungerGamesServer(serverId, onlinePlayers, true);
			} else {
				server = new BattleServer(serverId, onlinePlayers, maxPlayers, true);
			}
			activeServers.put(serverId, server);
		}
		server.setOnlinePlayers(onlinePlayers);
		server.setJoinEnabled(canJoin);
		if (state != null && server instanceof MinigameServer) {
			((MinigameServer) server).setState(state);
			((MinigameServer) server).setTime(tempo);
		}
		addToBalancers(serverId, server);
	}

	public BattleServer getServer(String str) {
		return activeServers.get(str.toLowerCase());
	}

	public void removeActiveServer(String str) {
		activeServers.remove(str.toLowerCase());
		removeFromBalancers(str);
	}

	public void addToBalancers(String serverId, BattleServer server) {
		serverId = serverId.toLowerCase();
		BaseBalancer<BattleServer> balancer = getBalancer(ServerType.getServerType(serverId));
		if (balancer == null)
			return;
		if (serverId.endsWith("battle-hg.com")) {
			balancer.add(serverId, (HungerGamesServer) server);
		} else {
			balancer.add(serverId, server);
		}
	}

	public void removeFromBalancers(String serverId) {
		serverId = serverId.toLowerCase();
		BaseBalancer<BattleServer> balancer = getBalancer(ServerType.getServerType(serverId));
		if (balancer != null)
			balancer.remove(serverId);
	}

}
