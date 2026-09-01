package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class KharedstMemoryDefinition
{
	@Getter
	static private final int width = 36;
	@Getter
	static private final int height = 32;

	private String name;
	private String memory;
	private int x;
	private int y;
	private HotKeyDefinition hotkey;
}
