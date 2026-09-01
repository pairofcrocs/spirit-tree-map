package com.mjhylkema.TeleportMaps.components.chatDialog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.MagicCarpetDefinition;
import com.mjhylkema.TeleportMaps.definition.TravelOptionDefinition;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public class MagicCarpetMap extends BaseChatDialogMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_CARPETS = "/MagicCarpetMap/MagicCarpetDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19800;
	private static final int MAP_SPRITE_WIDTH = 512;
	private static final int MAP_SPRITE_HEIGHT = 334;
	private static final int CARPET_SPRITE_ID = -19801;
	private static final int CARPET_HIGHLIGHTED_SPRITE_ID = -19802;
	private static final int CARPET_SELECTED_SPRITE_ID = -19803;
	private static final int CARPET_DISABLED_SPRITE_ID = -19804;
	private static final int CLOSE_BUTTON_X = 427;
	private static final int CLOSE_BUTTON_Y = 24;


	/* The player must be standing beside a station's rug merchant for its
	   dialog to be open. Guards against unrelated dialogs that happen to
	   share option names, and resolves stations with identical menus. */
	private static final int MAX_STATION_DISTANCE = 35;

	private MagicCarpetDefinition[] carpetDefinitions;

	@Inject
	public MagicCarpetMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showMagicCarpetMap());
		this.loadDefinitions();
	}

	private void loadDefinitions()
	{
		this.carpetDefinitions = this.plugin.loadDefinitionResource(MagicCarpetDefinition[].class, DEF_FILE_CARPETS);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_MAGIC_CARPET_MAP:
				this.setActive(config.showMagicCarpetMap());
			default:
				super.onConfigChanged(e);
		}
	}

	@Override
	protected void buildInterface(List<DialogOption> travelChoices, DialogOption declineOption)
	{
		// Every carpet travel menu contains a cancel option
		if (declineOption == null)
			return;

		MagicCarpetDefinition currentStation = this.identifyStation(travelChoices);
		if (currentStation == null)
			return;

		// The scroll backdrop is part of the map sprite
		Widget screen = this.getScreenContainer();
		if (screen == null)
			return;

		int mapX = (screen.getWidth() - MAP_SPRITE_WIDTH) / 2;
		int mapY = Math.max(0, (screen.getHeight() - MAP_SPRITE_HEIGHT) / 2);

		this.createMapWidget(screen, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID);
		this.createCarpetWidgets(screen, currentStation, travelChoices, mapX, mapY);
		this.createCloseButton(screen, () -> this.triggerDialogOption(declineOption), mapX + CLOSE_BUTTON_X, mapY + CLOSE_BUTTON_Y);
	}

	/**
	 * Determines which carpet station the open dialog belongs to, if any.
	 * A station matches when every travel option in the dialog is one of the
	 * station's defined travel options. The destination names alone don't
	 * uniquely identify a station (e.g. "Pollnivneach" is offered both at
	 * Shantay Pass and at the southern terminals), and unrelated dialogs
	 * could share option names; the player must be standing beside the
	 * station's rug merchant, so the nearest in-range station with a
	 * matching menu wins.
	 * @param travelChoices the travel options present in the dialog
	 * @return the matching station, or null if this isn't a carpet travel dialog
	 */
	private MagicCarpetDefinition identifyStation(List<DialogOption> travelChoices)
	{
		WorldPoint playerLocation = this.client.getLocalPlayer().getWorldLocation();
		MagicCarpetDefinition nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (MagicCarpetDefinition definition : this.carpetDefinitions)
		{
			boolean allMatch = true;
			for (DialogOption choice : travelChoices)
			{
				if (this.findTravelOption(definition, choice.getText()) == null)
				{
					allMatch = false;
					break;
				}
			}

			if (!allMatch)
				continue;

			int distance = Math.max(
				Math.abs(definition.getWorldPointX() - playerLocation.getX()),
				Math.abs(definition.getWorldPointY() - playerLocation.getY()));

			if (distance <= MAX_STATION_DISTANCE && distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = definition;
			}
		}

		return nearest;
	}

	private TravelOptionDefinition findTravelOption(MagicCarpetDefinition definition, String optionText)
	{
		for (TravelOptionDefinition travelOption : definition.getTravelOptions())
		{
			if (travelOption.getOption().equalsIgnoreCase(optionText))
				return travelOption;
		}
		return null;
	}

	/**
	 * Maps each reachable destination name to the dialog option that travels there
	 */
	private HashMap<String, DialogOption> buildDestinationLookup(MagicCarpetDefinition currentStation, List<DialogOption> travelChoices)
	{
		HashMap<String, DialogOption> destinations = new HashMap<>();

		for (DialogOption option : travelChoices)
		{
			TravelOptionDefinition travelOption = this.findTravelOption(currentStation, option.getText());
			if (travelOption != null)
				destinations.put(travelOption.getDestination(), option);
		}

		return destinations;
	}

	private void createCarpetWidgets(Widget screen, MagicCarpetDefinition currentStation, List<DialogOption> travelChoices, int mapX, int mapY)
	{
		this.clearTeleports();

		HashMap<String, DialogOption> destinations = this.buildDestinationLookup(currentStation, travelChoices);

		for (MagicCarpetDefinition definition : this.carpetDefinitions)
		{
			Widget widgetContainer = screen.createChild(-1, WidgetType.GRAPHIC);
			Widget carpetWidget = screen.createChild(-1, WidgetType.GRAPHIC);
			this.trackScreenWidget(widgetContainer);
			this.trackScreenWidget(carpetWidget);

			UITeleport carpetTeleport = new UITeleport(widgetContainer, carpetWidget);

			carpetTeleport.setPosition(mapX + definition.getX(), mapY + definition.getY());
			carpetTeleport.setSize(MagicCarpetDefinition.getWidth(), MagicCarpetDefinition.getHeight());
			carpetTeleport.setName(definition.getName());

			DialogOption destinationOption = destinations.get(definition.getName());

			if (definition == currentStation)
			{
				// The station the player is standing at
				carpetTeleport.setTeleportSprites(CARPET_SELECTED_SPRITE_ID, CARPET_SELECTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.addAction(EXAMINE_ACTION, () -> this.triggerCurrentStationMessage(definition));
			}
			else if (destinationOption != null)
			{
				carpetTeleport.setTeleportSprites(CARPET_SPRITE_ID, CARPET_HIGHLIGHTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.addAction(TRAVEL_ACTION, () -> this.triggerDialogOption(destinationOption));
				this.highlightOptionOnHover(carpetTeleport, destinationOption);
				this.bindOptionHotkey(carpetTeleport, destinationOption, screen, definition.getHotkey(), mapX, mapY);
			}
			else
			{
				carpetTeleport.setTeleportSprites(CARPET_SPRITE_ID, CARPET_HIGHLIGHTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.setLocked(true);
				carpetTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
			}

			this.addTeleport(carpetTeleport);
		}
	}

	private void triggerCurrentStationMessage(MagicCarpetDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You are currently at the %s carpet station.", definition.getName()), null));
	}

	private void triggerLockedMessage(MagicCarpetDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The magic carpet cannot take you to %s from here.", definition.getName()), null));
	}
}
