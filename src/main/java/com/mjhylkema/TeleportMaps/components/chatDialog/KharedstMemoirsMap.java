package com.mjhylkema.TeleportMaps.components.chatDialog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.KharedstMemoryDefinition;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * An interactive map for the memories recited from Kharedst's memoirs and
 * its upgraded form, the Book of the Dead. Both items open the same
 * "Reminisce" chat dialog listing the unlocked memories; each memory
 * teleports to one of the five cities of Great Kourend.
 */
public class KharedstMemoirsMap extends BaseChatDialogMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_MEMORIES = "/KharedstMemoirsMap/KharedstMemoirsDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19900;
	private static final int MAP_SPRITE_WIDTH = 509;
	private static final int MAP_SPRITE_HEIGHT = 317;
	private static final int MEMORY_SPRITE_ID = -19901;
	private static final int MEMORY_HIGHLIGHTED_SPRITE_ID = -19902;
	private static final int MEMORY_DISABLED_SPRITE_ID = -19903;
	private static final int CLOSE_BUTTON_X = 447;
	private static final int CLOSE_BUTTON_Y = 24;


	private KharedstMemoryDefinition[] memoryDefinitions;

	@Inject
	public KharedstMemoirsMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showKharedstMemoirsMap());
		this.loadDefinitions();
	}

	private void loadDefinitions()
	{
		this.memoryDefinitions = this.plugin.loadDefinitionResource(KharedstMemoryDefinition[].class, DEF_FILE_MEMORIES);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_KHAREDST_MEMOIRS_MAP:
				this.setActive(config.showKharedstMemoirsMap());
			default:
				super.onConfigChanged(e);
		}
	}

	@Override
	protected void buildInterface(List<DialogOption> travelChoices, DialogOption declineOption)
	{
		if (!this.isMemoirsDialog(travelChoices))
			return;

		Widget screen = this.getScreenContainer();
		if (screen == null)
			return;

		int mapX = (screen.getWidth() - MAP_SPRITE_WIDTH) / 2;
		int mapY = Math.max(0, (screen.getHeight() - MAP_SPRITE_HEIGHT) / 2);

		this.createMapWidget(screen, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID);
		this.createMemoryWidgets(screen, travelChoices, mapX, mapY);
		this.createCloseButton(screen, this::pressEscape, mapX + CLOSE_BUTTON_X, mapY + CLOSE_BUTTON_Y);
	}

	/**
	 * The reminisce dialog belongs to the memoirs when every one of its
	 * options is a defined memory. Unlike the carpet stations there's no
	 * location to disambiguate; the memory titles don't appear in any
	 * other dialog. Memories whose page hasn't been added to the memoirs
	 * are simply absent from the dialog.
	 */
	private boolean isMemoirsDialog(List<DialogOption> travelChoices)
	{
		for (DialogOption choice : travelChoices)
		{
			if (this.findMemory(choice.getText()) == null)
				return false;
		}

		return true;
	}

	/**
	 * Finds the memory a dialog option recites. The options are worded
	 * "'&lt;memory&gt;' - &lt;city&gt;", so the title is matched as a
	 * prefix after the leading quote.
	 */
	private KharedstMemoryDefinition findMemory(String optionText)
	{
		String text = optionText.startsWith("'") ? optionText.substring(1) : optionText;

		for (KharedstMemoryDefinition definition : this.memoryDefinitions)
		{
			String memory = definition.getMemory();
			if (text.regionMatches(true, 0, memory, 0, memory.length()))
				return definition;
		}
		return null;
	}

	private DialogOption findMemoryOption(KharedstMemoryDefinition definition, List<DialogOption> travelChoices)
	{
		for (DialogOption choice : travelChoices)
		{
			if (this.findMemory(choice.getText()) == definition)
				return choice;
		}
		return null;
	}

	private void createMemoryWidgets(Widget screen, List<DialogOption> travelChoices, int mapX, int mapY)
	{
		this.clearTeleports();

		for (KharedstMemoryDefinition definition : this.memoryDefinitions)
		{
			Widget widgetContainer = screen.createChild(-1, WidgetType.GRAPHIC);
			Widget memoryWidget = screen.createChild(-1, WidgetType.GRAPHIC);
			this.trackScreenWidget(widgetContainer);
			this.trackScreenWidget(memoryWidget);

			UITeleport memoryTeleport = new UITeleport(widgetContainer, memoryWidget);

			memoryTeleport.setPosition(mapX + definition.getX(), mapY + definition.getY());
			memoryTeleport.setSize(KharedstMemoryDefinition.getWidth(), KharedstMemoryDefinition.getHeight());
			memoryTeleport.setName(definition.getName());
			memoryTeleport.setTeleportSprites(MEMORY_SPRITE_ID, MEMORY_HIGHLIGHTED_SPRITE_ID, MEMORY_DISABLED_SPRITE_ID);

			DialogOption memoryOption = this.findMemoryOption(definition, travelChoices);

			if (memoryOption != null)
			{
				memoryTeleport.addAction(TRAVEL_ACTION, () -> this.triggerDialogOption(memoryOption));
				this.highlightOptionOnHover(memoryTeleport, memoryOption);
				this.bindOptionHotkey(memoryTeleport, memoryOption, screen, definition.getHotkey(), mapX, mapY);
			}
			else
			{
				// The memory's page hasn't been added to the memoirs
				memoryTeleport.setLocked(true);
				memoryTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
			}

			this.addTeleport(memoryTeleport);
		}
	}

	private void triggerLockedMessage(KharedstMemoryDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The memoirs are missing the page for '%s'.", definition.getMemory()), null));
	}
}
