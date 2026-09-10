package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class DigsitePendantDefinition
{
	@Getter
	static private final int width = 36;
	@Getter
	static private final int height = 33;

	private String name;
	private String option;
	private int x;
	private int y;
	private HotKeyDefinition hotkey;
}
