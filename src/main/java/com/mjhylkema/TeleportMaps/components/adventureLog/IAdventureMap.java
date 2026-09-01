package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import net.runelite.api.widgets.Widget;

public interface IAdventureMap extends IMap
{
	boolean matchesTitle(String title);

	/**
	 * Replaces an open teleport menu with this map.
	 * @param container the widget the map is built into
	 * @param entryList the widget whose dynamic children are the menu's entry labels
	 */
	void buildInterface(Widget container, Widget entryList);
}
