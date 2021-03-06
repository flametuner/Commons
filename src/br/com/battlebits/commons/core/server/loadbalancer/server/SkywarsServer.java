package br.com.battlebits.commons.core.server.loadbalancer.server;

import java.util.Set;
import java.util.UUID;

import br.com.battlebits.commons.core.server.ServerType;

public class SkywarsServer extends MinigameServer {

	public SkywarsServer(String serverId, Set<UUID> onlinePlayers, boolean joinEnabled) {
		super(serverId, ServerType.SKYWARS, onlinePlayers, 12, joinEnabled);
		setState(MinigameState.PREGAME);
	}

	@Override
	public boolean canBeSelected() {
		return super.canBeSelected() && (getState() == MinigameState.PREGAME && getTime() > 5);
	}

	@Override
	public boolean isInProgress() {
		return getState() != MinigameState.PREGAME;
	}

}
