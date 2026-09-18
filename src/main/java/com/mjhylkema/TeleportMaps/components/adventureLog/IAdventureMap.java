package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import java.util.List;
import net.runelite.api.widgets.Widget;

public interface IAdventureMap extends IMap
{
	boolean matchesTitle(String title);

	/**
	 * Whether an opening teleport menu belongs to this map. Maps whose
	 * menu title is shared with other menus can recognise theirs by its
	 * entries instead.
	 * @param title the menu title
	 * @param entries the menu's entry names, or null when unknown
	 */
	default boolean matchesMenu(String title, List<String> entries)
	{
		return this.matchesTitle(title);
	}

	/**
	 * Replaces an open teleport menu with this map.
	 * @param container the widget the map is built into
	 * @param entryList the widget whose dynamic children are the menu's entry labels
	 */
	void buildInterface(Widget container, Widget entryList);
}
