package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;
import net.runelite.client.game.SpriteOverride;

@Getter
public class SpriteDefinition implements SpriteOverride
{
	private int spriteId;
	private String fileName;

	/* Optional ids for variants generated from the base image at startup */
	private Integer hoverSpriteId;
	private Integer selectedSpriteId;
	private Integer disabledSpriteId;
}
