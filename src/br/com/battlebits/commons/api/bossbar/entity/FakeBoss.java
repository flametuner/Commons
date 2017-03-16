package br.com.battlebits.commons.api.bossbar.entity;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import us.myles.ViaVersion.api.boss.BossColor;
import us.myles.ViaVersion.api.boss.BossStyle;
import us.myles.ViaVersion.boss.ViaBossBar;

public class FakeBoss extends EntityBoss 
{
	private ViaBossBar bossBar;
	
	public FakeBoss(Player player)
	{
		super(player);
		spawn();
	}
	
	@Override
	public void spawn() 
	{
		if (bossBar == null)
		{
			bossBar = new ViaBossBar("", 1F, BossColor.PINK, BossStyle.SOLID);		
			bossBar.addPlayer(getPlayer());
		}
	}
	
	@Override
	public void remove()
	{
		if (bossBar != null)
		{
			bossBar.removePlayer(getPlayer());
			bossBar = null;
		}
	}
	
	@Override
	public boolean setTitle(String title) 
	{
		if (this.bossBar != null)
		{
			if (!Objects.equals(this.title, title))
			{
				this.bossBar.setTitle(title);
				this.title = title;
			}
		}
		
		return false;
	}
	
	@Override
	public boolean setHealth(float percent)
	{
		if (this.bossBar != null)
		{
			float health = Math.max(0F, (percent / 100F) * 1F);

			if (!Objects.equals(this.health, health))
			{
				this.bossBar.setHealth(health);
				this.health = health;
			}
		}
		
		return false;
	}

	@Override
	public void update() 
	{
		// Unnecessary for version 1.9+
	}
	
	
	@Override
	public void move(PlayerMoveEvent event)
	{
		// Unnecessary for version 1.9+
	}
}
