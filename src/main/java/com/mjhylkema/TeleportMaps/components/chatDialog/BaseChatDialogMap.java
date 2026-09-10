package com.mjhylkema.TeleportMaps.components.chatDialog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.ui.MenuAction;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * Base for maps driven by the generic chat options dialog ("Select an option").
 * The dialog is too small to hold a map (its ancestor layers clip to the
 * chatbox), so these maps are built over the game view instead, in the same
 * modal layer the game opens screen-level interfaces such as the adventure
 * log into. Travel is performed by dispatching the key listener the game
 * attached to the chosen dialog option.
 */
@Slf4j
public abstract class BaseChatDialogMap extends BaseMap
{
	private static final int DIALOG_OPTION_GROUP_ID = 219;
	private static final int DIALOG_OPTION_CONTAINER_CHILD = 1;
	private static final String DECLINE_OPTION = "Cancel";
	protected static final String TRAVEL_ACTION = "Travel";
	protected static final String EXAMINE_ACTION = "Examine";

	/* The keyboard digit for the first dialog option; options natively respond
	   to the number keys matching their position in the dialog */
	private static final int OPTION_KEY_CHAR_BASE = '0';

	protected static final int CLOSE_BUTTON_SPRITE_ID = 537;
	protected static final int CLOSE_BUTTON_WIDTH = 26;
	protected static final int CLOSE_BUTTON_HEIGHT = 23;

	/* Widgets built on the top-level interface, removed when the dialog closes */
	private final List<Widget> screenWidgets = new ArrayList<>();

	/**
	 * A dialog option present in the currently open "Select an option" menu
	 */
	protected static class DialogOption
	{
		final Widget widget;
		final int childIndex;
		final String text;

		DialogOption(Widget widget, int childIndex, String text)
		{
			this.widget = widget;
			this.childIndex = childIndex;
			this.text = text;
		}

		String getText()
		{
			return this.text;
		}
	}

	protected BaseChatDialogMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread, boolean active)
	{
		super(plugin, config, client, clientThread, active);
	}

	/**
	 * Builds the map over the game view if the open dialog belongs to it.
	 * Implementations must ignore dialogs whose options they don't recognise;
	 * every chat options dialog in the game arrives here.
	 * @param travelChoices the dialog's options, excluding the decline option
	 * @param declineOption the dialog's decline ("Cancel") option, or null
	 *                      if the dialog doesn't offer one
	 */
	protected abstract void buildInterface(List<DialogOption> travelChoices, DialogOption declineOption);

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (!this.isActive())
			return;

		if (e.getGroupId() != DIALOG_OPTION_GROUP_ID)
			return;

		// The dialog options are populated after the interface loads,
		// so inspect the dialog on the next client cycle
		this.clientThread.invokeLater(this::tryBuildInterface);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (e.getGroupId() == DIALOG_OPTION_GROUP_ID)
			this.destroyInterface();

		this.onInterfaceClosed(e.getGroupId());
	}

	/**
	 * Hook for maps that also build over another interface
	 */
	protected void onInterfaceClosed(int groupId)
	{
	}

	private void tryBuildInterface()
	{
		// Remove any leftovers in case a new dialog replaced the
		// previous one without it closing
		this.destroyInterface();

		Widget container = this.client.getWidget(DIALOG_OPTION_GROUP_ID, DIALOG_OPTION_CONTAINER_CHILD);
		if (container == null)
			return;

		List<DialogOption> travelChoices = new ArrayList<>();
		DialogOption declineOption = null;

		for (DialogOption option : this.parseDialogOptions(container))
		{
			if (option.text.equalsIgnoreCase(DECLINE_OPTION))
				declineOption = option;
			else
				travelChoices.add(option);
		}

		if (travelChoices.isEmpty())
			return;

		this.buildInterface(travelChoices, declineOption);
	}

	/**
	 * Collects the option entries from the chat options dialog. Only the
	 * selectable options carry key listeners; the title widget does not,
	 * so it's naturally excluded.
	 */
	private List<DialogOption> parseDialogOptions(Widget container)
	{
		List<DialogOption> options = new ArrayList<>();

		Widget[] children = container.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			String text = child.getText();

			if (text == null || text.isEmpty() || child.getOnKeyListener() == null)
				continue;

			options.add(new DialogOption(child, i, text));
		}

		return options;
	}

	/**
	 * Finds the "mainmodal" layer for the current display mode: the layer
	 * over the game view that the game opens screen-level modals into
	 */
	protected Widget getScreenContainer()
	{
		int[][] containers = {
			{161, 16}, // toplevel_osrs_stretch:mainmodal (resizable classic)
			{164, 16}, // toplevel_pre_eoc:mainmodal (resizable modern)
			{548, 41}, // toplevel:mainmodal (fixed)
		};

		for (int[] componentId : containers)
		{
			Widget screen = this.client.getWidget(componentId[0], componentId[1]);
			if (screen != null)
				return screen;
		}

		return null;
	}

	protected void trackScreenWidget(Widget widget)
	{
		this.screenWidgets.add(widget);
	}

	/**
	 * Creates the map background sprite on the top-level interface
	 */
	protected Widget createMapWidget(Widget screen, int width, int height, int x, int y, int spriteId)
	{
		Widget map = this.createSpriteWidget(screen, width, height, x, y, spriteId);
		this.trackScreenWidget(map);

		// Set hasListener / noClickThrough to disallow click-through
		map.setHasListener(true);
		map.setNoClickThrough(true);

		return map;
	}

	/**
	 * Removes the widgets built on the top-level interface. Unlike the other
	 * maps, these aren't children of the travel dialog, so they outlive it
	 * and must be removed when the dialog closes.
	 */
	protected void destroyInterface()
	{
		if (this.screenWidgets.isEmpty())
			return;

		this.screenWidgets.clear();
		Objects.requireNonNull(getScreenContainer()).deleteAllChildren();
		this.clearTeleports();
	}

	/**
	 * Variant of {@link BaseMap#createHotKey} that positions the hotkey
	 * relative to the map and tracks its widgets for later removal
	 */
	protected UIHotkey createScreenHotKey(Widget screen, HotKeyDefinition hotKeyDefinition, String label, int mapX, int mapY)
	{
		Widget icon = screen.createChild(-1, WidgetType.GRAPHIC);
		icon.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
		Widget text = screen.createChild(-1, WidgetType.TEXT);
		this.trackScreenWidget(icon);
		this.trackScreenWidget(text);

		UIHotkey hotkey = new UIHotkey(icon, text);
		hotkey.setSize(hotKeyDefinition.getWidth(), hotKeyDefinition.getHeight());
		hotkey.setPosition(mapX + hotKeyDefinition.getX(), mapY + hotKeyDefinition.getY());
		hotkey.setText(label);
		hotkey.setVisibility(this.config.displayHotkeys());

		return hotkey;
	}

	/**
	 * Binds the dialog option's native number key to the teleport and
	 * attaches the matching hotkey label. The dialog options natively
	 * respond to their number key; child index lines up with the
	 * displayed option number.
	 */
	protected void bindOptionHotkey(UITeleport teleport, DialogOption option, Widget screen, HotKeyDefinition hotKeyDefinition, int mapX, int mapY)
	{
		int hotkeyDigit = option.childIndex;
		if (hotkeyDigit < 1 || hotkeyDigit > 9)
			return;

		teleport.getWidget().setOnKeyListener((JavaScriptCallback) ev ->
		{
			if (ev.getTypedKeyChar() == Character.forDigit(hotkeyDigit, 10))
				this.triggerDialogOption(option);
		});

		UIHotkey hotkey = this.createScreenHotKey(screen, hotKeyDefinition, String.valueOf(hotkeyDigit), mapX, mapY);
		teleport.attachHotkey(hotkey);
	}

	/**
	 * Highlights the dialog option's chat text while its teleport is hovered,
	 * tying the map back to the underlying dialog
	 */
	protected void highlightOptionOnHover(UITeleport teleport, DialogOption option)
	{
		teleport.addOnHoverListener((listener) -> option.widget.setTextColor(Color.WHITE.getRGB()));
		teleport.addOnLeaveListener((listener) -> option.widget.setTextColor(Color.BLACK.getRGB()));
	}

	protected void createCloseButton(Widget screen, MenuAction onClose, int x, int y)
	{
		Widget closeWidget = screen.createChild(-1, WidgetType.GRAPHIC);
		this.trackScreenWidget(closeWidget);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(x, y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", onClose);
		closeWidget.revalidate();
	}

	/**
	 * Dismisses the dialog the way the player would when it offers no
	 * decline option: with the escape key
	 */
	protected void pressEscape()
	{
		Canvas canvas = this.client.getCanvas();
		long now = System.currentTimeMillis();
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
	}

	/**
	 * Selects the given dialog option by dispatching the key listener the
	 * game attached to the option widget, with the event placeholders
	 * filled in as if the option's number key had been pressed
	 */
	protected void triggerDialogOption(DialogOption option)
	{
		this.clientThread.invokeLater(() ->
		{
			Object[] template = option.widget.getOnKeyListener();
			if (template == null)
			{
				log.debug("No key listener on dialog option '{}'", option.text);
				return;
			}

			int digit = option.childIndex;
			if (digit < 1 || digit > 9)
				return;

			Object[] listener = new Object[template.length];
			for (int i = 0; i < template.length; i++)
			{
				Object arg = template[i];
				if (arg instanceof Integer)
				{
					switch ((Integer) arg)
					{
						case ScriptEvent.KEY_CODE:
							arg = KeyCode.KC_1 + digit - 1;
							break;
						case ScriptEvent.KEY_CHAR:
							arg = OPTION_KEY_CHAR_BASE + digit;
							break;
						case ScriptEvent.WIDGET_ID:
							arg = option.widget.getId();
							break;
						case ScriptEvent.WIDGET_INDEX:
							arg = option.widget.getIndex();
							break;
						default:
							break;
					}
				}
				listener[i] = arg;
			}

			this.client.runScript(listener);
		});
	}
}
