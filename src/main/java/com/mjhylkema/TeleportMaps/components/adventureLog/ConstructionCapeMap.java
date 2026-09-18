package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.ConstructionCapeDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * An interactive map for the Construction cape's teleport, which lists the
 * player's own house and every house portal location in a scroll menu.
 * The map is drawn on the spirit tree's world map, with the house sitting
 * in the same spot as the spirit tree map's house.
 */
public class ConstructionCapeMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_DESTINATIONS = "/ConstructionCapeMap/ConstructionCapeDefinitions.json";

	/* Sprite IDs, dimensions and positions; the map art is the spirit tree's */
	private static final int MAP_SPRITE_ID = -19000;
	private static final int MAP_SPRITE_WIDTH = 512;
	private static final int MAP_SPRITE_HEIGHT = 334;
	private static final int CLOSE_BUTTON_X = 428;
	private static final int CLOSE_BUTTON_Y = 22;
	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final Pattern ENTRY_LABEL_PATTERN = Pattern.compile(AdventureLogComposite.ENTRY_LABEL_PATTERN);

	private ConstructionCapeDefinition[] destinationDefinitions;

	@Inject
	public ConstructionCapeMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showConstructionCapeMap());
		this.loadDefinitions();
	}

	private void loadDefinitions()
	{
		this.destinationDefinitions = this.plugin.loadDefinitionResource(ConstructionCapeDefinition[].class, DEF_FILE_DESTINATIONS);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_CONSTRUCTION_CAPE_MAP:
				this.setActive(config.showConstructionCapeMap());
			default:
				super.onConfigChanged(e);
		}
	}

	/**
	 * The cape's menu shares its generic title with other teleport menus,
	 * so it's recognised by its entries instead
	 */
	@Override
	public boolean matchesTitle(String title)
	{
		return false;
	}

	/**
	 * The menu belongs to the cape when every one of its entries is a
	 * defined destination; the house portal locations don't appear
	 * together in any other menu.
	 */
	@Override
	public boolean matchesMenu(String title, List<String> entries)
	{
		if (entries == null || entries.isEmpty())
			return false;

		for (String entry : entries)
		{
			if (this.findDestination(entry) == null)
				return false;
		}

		return true;
	}

	@Override
	public void buildInterface(Widget container, Widget entryList)
	{
		Widget background = container.getChild(ADVENTURE_LOG_CONTAINER_BACKGROUND);
		if (background != null)
			background.setHidden(true);

		Widget title = container.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);

		this.createSpriteWidget(container, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, 0, 0, MAP_SPRITE_ID);
		this.moveExitWidget();
		this.clearTeleports();

		for (ConstructionCapeDefinition definition : this.destinationDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget destinationWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport teleport = new UITeleport(widgetContainer, destinationWidget);
			teleport.setPosition(definition.getX(), definition.getY());
			teleport.setSize(definition.getWidth(), definition.getHeight());
			teleport.setName(definition.getName());
			teleport.setTeleportSprites(definition.getSpriteEnabled(), definition.getSpriteHover(), definition.getSpriteDisabled());

			AdventureLogEntry<ConstructionCapeDefinition> entry = this.findMenuEntry(definition, entryList);

			if (entry != null)
			{
				teleport.addAction(TRAVEL_ACTION, () -> this.triggerMenuEntry(entry));
				UIHotkey hotkey = this.createHotKey(container, definition.getHotkey(), entry.getKeyShortcut());
				teleport.attachHotkey(hotkey);
			}
			else
			{
				teleport.setLocked(true);
				teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
			}

			this.addTeleport(teleport);
		}
	}

	/**
	 * Moves the classic menu's close button onto the map's scroll, as the
	 * spirit tree map does for the same art
	 */
	private void moveExitWidget()
	{
		Widget exitWidget = this.client.getWidget(InterfaceID.ADVENTURE_LOG, AdventureLogComposite.AdventureLog.CLOSE_BUTTON);
		if (exitWidget == null)
			return;

		exitWidget.setOriginalX(CLOSE_BUTTON_X);
		exitWidget.setOriginalY(CLOSE_BUTTON_Y);
		exitWidget.revalidate();
	}

	private ConstructionCapeDefinition findDestination(String text)
	{
		for (ConstructionCapeDefinition definition : this.destinationDefinitions)
		{
			if (definition.getName().equalsIgnoreCase(text.trim()))
				return definition;
		}
		return null;
	}

	/**
	 * Finds the menu entry for a destination; locked entries are greyed
	 * out by the menu and treated as absent
	 */
	private AdventureLogEntry<ConstructionCapeDefinition> findMenuEntry(ConstructionCapeDefinition definition, Widget entryList)
	{
		for (Widget child : entryList.getDynamicChildren())
		{
			Matcher matcher = ENTRY_LABEL_PATTERN.matcher(child.getText());
			if (!matcher.matches() || matcher.group(2) != null)
				continue;

			if (this.findDestination(matcher.group(3)) == definition)
				return new AdventureLogEntry<>(definition, child, matcher.group(1));
		}
		return null;
	}

	private void triggerMenuEntry(AdventureLogEntry<ConstructionCapeDefinition> entry)
	{
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, entry.getWidget().getId(), entry.getWidget().getIndex()));
	}

	private void triggerLockedMessage(ConstructionCapeDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You can't teleport to %s yet.", definition.getName()), null));
	}
}
