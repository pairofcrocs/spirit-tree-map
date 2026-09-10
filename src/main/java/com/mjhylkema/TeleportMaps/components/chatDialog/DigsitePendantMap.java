package com.mjhylkema.TeleportMaps.components.chatDialog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.adventureLog.AdventureLogComposite;
import com.mjhylkema.TeleportMaps.components.adventureLog.IAdventureMap;
import com.mjhylkema.TeleportMaps.definition.DigsitePendantDefinition;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import lombok.extern.slf4j.Slf4j;

/**
 * An interactive map for the digsite pendant and its mounted form in the
 * player-owned house. Rubbing the pendant opens a chat dialog offering the
 * Digsite, Fossil Island and Lithkren teleports, while the mounted pendant
 * lists the same destinations in a scroll menu like the mounted Xeric's
 * talisman, so the map is built over either.
 */
@Slf4j
public class DigsitePendantMap extends BaseChatDialogMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_DESTINATIONS = "/DigsitePendantMap/DigsitePendantDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -20000;
	private static final int MAP_SPRITE_WIDTH = 512;
	private static final int MAP_SPRITE_HEIGHT = 334;
	private static final int PENDANT_SPRITE_ID = -20001;
	private static final int PENDANT_HIGHLIGHTED_SPRITE_ID = -20002;
	private static final int PENDANT_DISABLED_SPRITE_ID = -20003;
	private static final int CLOSE_BUTTON_X = 427;
	private static final int CLOSE_BUTTON_Y = 24;

	/* The mounted pendant's scroll menu */
	private static final String MENU_TITLE_MOUNTED = "(?i).*digsite pendant.*";
	private static final int MENU_CONTAINER_BACKGROUND = 0;
	private static final int MENU_CONTAINER_TITLE = 1;
	private static final int CLASSIC_MENU_CLOSE_BUTTON = 4;
	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final Pattern ENTRY_LABEL_PATTERN = Pattern.compile(AdventureLogComposite.ENTRY_LABEL_PATTERN);

	private DigsitePendantDefinition[] destinationDefinitions;

	@Inject
	public DigsitePendantMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showDigsitePendantMap());
		this.loadDefinitions();
	}

	private void loadDefinitions()
	{
		this.destinationDefinitions = this.plugin.loadDefinitionResource(DigsitePendantDefinition[].class, DEF_FILE_DESTINATIONS);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_DIGSITE_PENDANT_MAP:
				this.setActive(config.showDigsitePendantMap());
			default:
				super.onConfigChanged(e);
		}
	}

	/* --- Pendant item: chat dialog --- */

	@Override
	protected void buildInterface(List<DialogOption> travelChoices, DialogOption declineOption)
	{
		if (!this.isPendantDialog(travelChoices))
			return;

		Widget screen = this.getScreenContainer();
		if (screen == null)
			return;

		int mapX = (screen.getWidth() - MAP_SPRITE_WIDTH) / 2;
		int mapY = Math.max(0, (screen.getHeight() - MAP_SPRITE_HEIGHT) / 2);

		this.createMapWidget(screen, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID);
		this.clearTeleports();

		for (DigsitePendantDefinition definition : this.destinationDefinitions)
		{
			UITeleport teleport = this.createTeleport(screen, definition, mapX, mapY, true);
			DialogOption option = this.findDestinationOption(definition, travelChoices);

			if (option != null)
			{
				teleport.addAction(TRAVEL_ACTION, () -> this.triggerDialogOption(option));
				this.highlightOptionOnHover(teleport, option);
				this.bindOptionHotkey(teleport, option, screen, definition.getHotkey(), mapX, mapY);
			}
			else
			{
				this.lockTeleport(teleport, definition);
			}

			this.addTeleport(teleport);
		}

		this.createCloseButton(screen, () -> this.closeDialog(declineOption), mapX + CLOSE_BUTTON_X, mapY + CLOSE_BUTTON_Y);
	}

	/**
	 * The dialog belongs to the pendant when every one of its options is a
	 * defined destination; the three destination names don't appear
	 * together in any other dialog.
	 */
	private boolean isPendantDialog(List<DialogOption> travelChoices)
	{
		for (DialogOption choice : travelChoices)
		{
			if (this.findDestination(choice.getText()) == null)
				return false;
		}

		return true;
	}

	private DialogOption findDestinationOption(DigsitePendantDefinition definition, List<DialogOption> travelChoices)
	{
		for (DialogOption choice : travelChoices)
		{
			if (this.findDestination(choice.getText()) == definition)
				return choice;
		}
		return null;
	}

	private void closeDialog(DialogOption declineOption)
	{
		if (declineOption != null)
			this.triggerDialogOption(declineOption);
		else
			this.pressEscape();
	}

	/* --- Mounted pendant: scroll menu --- */

	@Override
	public boolean matchesTitle(String title)
	{
		return title.matches(MENU_TITLE_MOUNTED);
	}

	@Override
	public void buildInterface(Widget container, Widget entryList)
	{
		Widget background = container.getChild(MENU_CONTAINER_BACKGROUND);
		if (background != null)
			background.setHidden(true);

		Widget title = container.getChild(MENU_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);

		this.createSpriteWidget(container, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, 0, 0, MAP_SPRITE_ID);
		this.replaceClassicCloseButton(container);
		this.clearTeleports();

		for (DigsitePendantDefinition definition : this.destinationDefinitions)
		{
			UITeleport teleport = this.createTeleport(container, definition, 0, 0, false);
			MenuEntry entry = this.findMenuEntry(definition, entryList);

			if (entry != null)
			{
				teleport.addAction(TRAVEL_ACTION, () -> this.triggerMenuEntry(entry));
				UIHotkey hotkey = this.createHotKey(container, definition.getHotkey(), entry.shortcutKey);
				teleport.attachHotkey(hotkey);
			}
			else
			{
				this.lockTeleport(teleport, definition);
			}

			this.addTeleport(teleport);
		}
	}


	/**
	 * The classic menu's own close button is placed for the default scroll
	 * art, off the edge of this map's scroll, so hide it and draw one at
	 * the map position that clicks it. The modern menu's button is
	 * replaced by the composite instead.
	 */
	private void replaceClassicCloseButton(Widget container)
	{
		if (container.getId() >> 16 != InterfaceID.ADVENTURE_LOG)
			return;

		Widget nativeButton = this.client.getWidget(InterfaceID.ADVENTURE_LOG, CLASSIC_MENU_CLOSE_BUTTON);
		if (nativeButton == null)
			return;

		nativeButton.setHidden(true);

		Widget closeWidget = container.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(CLOSE_BUTTON_X, CLOSE_BUTTON_Y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.clickWidget(nativeButton));
		closeWidget.revalidate();
	}

	/**
	 * Restores the classic menu's close button for the other menus
	 * that share the interface
	 */
	@Override
	protected void onInterfaceClosed(int groupId)
	{
		if (groupId != InterfaceID.ADVENTURE_LOG)
			return;

		Widget nativeButton = this.client.getWidget(InterfaceID.ADVENTURE_LOG, CLASSIC_MENU_CLOSE_BUTTON);
		if (nativeButton != null)
			nativeButton.setHidden(false);
	}

	/**
	 * Performs the first op of a widget as if it were clicked, searching
	 * its children for the one carrying the op
	 */
	private void clickWidget(Widget widget)
	{
		Widget target = this.findOpWidget(widget);
		if (target == null)
		{
			log.debug("No op listener under widget {}", widget.getId());
			return;
		}

		this.clientThread.invokeLater(() -> this.client.menuAction(target.getIndex(), target.getId(), MenuAction.CC_OP, 1, -1, "Close", ""));
	}

	private Widget findOpWidget(Widget widget)
	{
		if (widget.getOnOpListener() != null)
			return widget;

		Widget[][] groups = {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()};
		for (Widget[] children : groups)
		{
			if (children == null)
				continue;

			for (Widget child : children)
			{
				Widget found = this.findOpWidget(child);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	/**
	 * An unlocked entry of the mounted pendant's scroll menu
	 */
	private static class MenuEntry
	{
		final Widget widget;
		final String shortcutKey;

		MenuEntry(Widget widget, String shortcutKey)
		{
			this.widget = widget;
			this.shortcutKey = shortcutKey;
		}
	}

	/**
	 * Finds the menu entry for a destination; locked entries are greyed
	 * out by the menu and treated as absent
	 */
	private MenuEntry findMenuEntry(DigsitePendantDefinition definition, Widget entryList)
	{
		for (Widget child : entryList.getDynamicChildren())
		{
			Matcher matcher = ENTRY_LABEL_PATTERN.matcher(child.getText());
			if (!matcher.matches() || matcher.group(2) != null)
				continue;

			if (this.findDestination(matcher.group(3)) == definition)
				return new MenuEntry(child, matcher.group(1));
		}
		return null;
	}

	private void triggerMenuEntry(MenuEntry entry)
	{
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, entry.widget.getId(), entry.widget.getIndex()));
	}

	/* --- Shared --- */

	/**
	 * Finds the destination an option or menu entry teleports to. The text
	 * is matched as a prefix so a suffix such as "Lithkren Dungeon" still
	 * resolves.
	 */
	private DigsitePendantDefinition findDestination(String text)
	{
		for (DigsitePendantDefinition definition : this.destinationDefinitions)
		{
			String option = definition.getOption();
			if (text.regionMatches(true, 0, option, 0, option.length()))
				return definition;
		}
		return null;
	}

	private UITeleport createTeleport(Widget parent, DigsitePendantDefinition definition, int mapX, int mapY, boolean onScreen)
	{
		Widget widgetContainer = parent.createChild(-1, WidgetType.GRAPHIC);
		Widget pendantWidget = parent.createChild(-1, WidgetType.GRAPHIC);

		if (onScreen)
		{
			this.trackScreenWidget(widgetContainer);
			this.trackScreenWidget(pendantWidget);
		}

		UITeleport teleport = new UITeleport(widgetContainer, pendantWidget);
		teleport.setPosition(mapX + definition.getX(), mapY + definition.getY());
		teleport.setSize(DigsitePendantDefinition.getWidth(), DigsitePendantDefinition.getHeight());
		teleport.setName(definition.getName());
		teleport.setTeleportSprites(PENDANT_SPRITE_ID, PENDANT_HIGHLIGHTED_SPRITE_ID, PENDANT_DISABLED_SPRITE_ID);

		return teleport;
	}

	private void lockTeleport(UITeleport teleport, DigsitePendantDefinition definition)
	{
		teleport.setLocked(true);
		teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
	}

	private void triggerLockedMessage(DigsitePendantDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The pendant hasn't been attuned to %s yet.", definition.getName()), null));
	}
}
