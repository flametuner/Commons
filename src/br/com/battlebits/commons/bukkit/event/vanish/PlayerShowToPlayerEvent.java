package br.com.battlebits.commons.bukkit.event.vanish;

import org.bukkit.entity.Player;

import br.com.battlebits.commons.bukkit.event.PlayerCancellableEvent;
import lombok.Getter;

@Getter
public class PlayerShowToPlayerEvent extends PlayerCancellableEvent {
	private Player toPlayer;

	public PlayerShowToPlayerEvent(Player player, Player toPlayer) {
		super(player);
		this.toPlayer = toPlayer;
	}
}
