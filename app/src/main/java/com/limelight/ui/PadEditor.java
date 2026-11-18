package com.limelight.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.ui.ActionPadKeyMap.KeyDef;
import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The customise screen, drawn over the pad: pick a button, then change its key (with Shift, Ctrl or Alt),
 * name, outline colour, place or visibility. Also saved profiles and a reset. Every change is saved at once
 * and shown on the pad behind it.
 */
final class PadEditor extends LinearLayout {
    private static final int[] COLORS = {0, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFEEEEEE};
    private static final String[] COLOR_NAMES = {"Default", "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "White"};
    private static final int MAX_NAME = 14;

    /** A set of buttons that share a place on the pad, and an order. */
    private static final class Group {
        final String id;
        final String title;
        final List<Slot> defaults;

        Group(String id, String title, List<Slot> defaults) {
            this.id = id;
            this.title = title;
            this.defaults = defaults;
        }
    }

    private final PadKit kit;
    private final PadConfig config;
    private final Runnable onChanged;
    private final Runnable onClose;
    private final List<Group> groups = new ArrayList<>();
    private boolean confirmReset;
    /** The scrolling list of the screen on show, so a change can redraw it without jumping back to the top. */
    private ScrollView scrolling;

    PadEditor(PadKit kit, Runnable onChanged, Runnable onClose) {
        super(kit.context);
        this.kit = kit;
        this.config = kit.settings.config;
        this.onChanged = onChanged;
        this.onClose = onClose;
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) groups.add(new Group(page.id, page.title, page.slots));
        groups.add(new Group("numbers", "Number block", ActionPadKeyMap.NUMBERS));
        groups.add(new Group("extras", "Extra buttons", ActionPadKeyMap.EXTRAS));

        setOrientation(VERTICAL);
        setClickable(true);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{kit.theme.bgTop, kit.theme.bg}));
        int pad = kit.dp(8);
        setPadding(pad, pad, pad, pad);
        setVisibility(GONE);
    }

    void show() {
        showMain();
        setVisibility(VISIBLE);
    }

    // --- Screens ---

    /** Redraws the main screen at the same scroll position. */
    private void showMainInPlace() {
        int y = scrolling == null ? 0 : scrolling.getScrollY();
        showMain();
        final ScrollView list = scrolling;
        if (list != null) list.post(() -> list.scrollTo(0, y));
    }

    private void showMain() {
        confirmReset = false;
        LinearLayout list = screen("Customize buttons", "Close", onClose, true);
        list.addView(heading("Tabs"));
        list.addView(note("Tap a tab to show or hide it. The last one stays on. Open chat needs the Keyboard tab."));
        List<View> tabChips = new ArrayList<>();
        for (final String id : ActionPadKeyMap.tabIds()) {
            tabChips.add(choice(ActionPadKeyMap.tabTitle(id), kit.settings.shownTabs.contains(id), () -> {
                kit.settings.toggleTab(id);
                onChanged.run();
                showMainInPlace();
            }));
        }
        list.addView(chipRows(tabChips, 4));
        list.addView(heading("Buttons"));
        list.addView(note("Tap a button to change its key, name or colour, or to hide or move it."));
        for (final Group group : groups) {
            list.addView(heading(group.title));
            List<View> chips = new ArrayList<>();
            for (final Slot slot : config.resolve(group.id, group.defaults, true)) {
                TextView chip = kit.tapButton(slot.label, 13, () -> showButton(group, slot.id));
                chip.setAlpha(config.isHidden(slot.id) ? 0.45f : 1f);
                chips.add(chip);
            }
            list.addView(chipRows(chips, 4));
        }
        list.addView(heading("Theme"));
        List<View> themes = new ArrayList<>();
        for (final ActionPadTheme theme : ActionPadTheme.ALL) {
            themes.add(choice(theme.name, theme == kit.settings.theme, () -> {
                kit.settings.saveTheme(theme);
                kit.applyTheme();
                setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{kit.theme.bgTop, kit.theme.bg}));
                onChanged.run();
                showMainInPlace();
            }));
        }
        list.addView(chipRows(themes, 3));
        list.addView(heading("Fade the pad after"));
        List<View> fades = new ArrayList<>();
        for (final int seconds : ActionPadSettings.DIM_SECONDS) {
            boolean selected = kit.settings.dimMs == seconds * 1000L;
            fades.add(choice(seconds == 0 ? "Never" : seconds + " s", selected, () -> {
                kit.settings.saveDimSeconds(seconds);
                onChanged.run();
                showMainInPlace();
            }));
        }
        list.addView(chipRows(fades, 3));
        list.addView(heading("Keyboard layout"));
        List<View> layouts = new ArrayList<>();
        for (int i = 0; i < PadKeyboardLayouts.IDS.length; i++) {
            final String id = PadKeyboardLayouts.IDS[i];
            layouts.add(choice(PadKeyboardLayouts.NAMES[i], PadKeyboardLayouts.NAMES[i].equals(PadKeyboardLayouts.name(kit.settings.keyboardLayout)), () -> {
                kit.settings.saveKeyboardLayout(id);
                onChanged.run();
                showMainInPlace();
            }));
        }
        list.addView(chipRows(layouts, 3));
        list.addView(heading("Swipe typing (beta)"));
        list.addView(note("Slide a finger across the letters to type a word. While on, letters type when you lift your finger. Off by default."));
        List<View> swipe = new ArrayList<>();
        for (final boolean on : new boolean[]{false, true}) {
            swipe.add(choice(on ? "On (beta)" : "Off", kit.settings.swipeTyping == on, () -> {
                kit.settings.saveSwipeTyping(on);
                onChanged.run();
                showMainInPlace();
            }));
        }
        list.addView(chipRows(swipe, 2));
        list.addView(heading("Profiles"));
        for (int i = 1; i <= ActionPadSettings.PROFILE_SLOTS; i++) list.addView(profileRow(i));
        list.addView(heading("Reset"));
        final TextView[] reset = {null};
        reset[0] = kit.tapButton("Reset all changes", 14, () -> {
            if (!confirmReset) {
                confirmReset = true;
                reset[0].setText("Tap again to reset everything");
                return;
            }
            config.clear();
            changed();
            showMain();
        });
        list.addView(reset[0], new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44)));
    }

    private LinearLayout profileRow(final int slot) {
        LinearLayout row = kit.box(HORIZONTAL);
        TextView name = kit.text("Profile " + slot, 14);
        row.addView(name, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        boolean used = kit.settings.hasProfile(slot);
        row.addView(kit.tapButton(used ? "Overwrite" : "Save", 13, () -> {
            kit.settings.saveProfile(slot);
            showMain();
        }), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        if (used) {
            row.addView(kit.tapButton("Load", 13, () -> {
                kit.settings.loadProfile(slot);
                onChanged.run();
                showMain();
            }), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44)));
        return row;
    }

    private void showButton(final Group group, final String id) {
        final Slot slot = find(group, id);
        LinearLayout list = screen(slot.label, "Back", this::showMain, true);

        if (slot.macro == null) {
            list.addView(heading("Sends: " + describe(slot)));
            list.addView(modifierChips(group, slot));
            list.addView(keyGrid(group, slot));
        } else if (slot.opensKeyboard) {
            list.addView(heading("Opens chat with " + slot.macro.trim()));
        } else {
            list.addView(heading("Types: " + slot.macro));
            List<View> message = new ArrayList<>();
            message.add(kit.tapButton("Edit message", 14, () -> showMessage(group, slot)));
            message.add(edit("Default message", group, id, () -> config.setMacro(id, null)));
            list.addView(chipRows(message, 2));
        }

        list.addView(heading("Name"));
        List<View> name = new ArrayList<>();
        name.add(kit.tapButton("Rename", 14, () -> showRename(group, slot)));
        name.add(edit("Default name", group, id, () -> config.setName(id, null)));
        list.addView(chipRows(name, 2));

        list.addView(heading("Outline colour"));
        List<View> colors = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) colors.add(colorChip(group, id, slot, i));
        list.addView(chipRows(colors, 4));

        list.addView(heading("Place"));
        List<View> place = new ArrayList<>();
        place.add(edit("Move earlier", group, id, () -> config.move(group.id, group.defaults, id, -1)));
        place.add(edit("Move later", group, id, () -> config.move(group.id, group.defaults, id, 1)));
        place.add(edit(config.isHidden(id) ? "Show" : "Hide", group, id, () -> config.setHidden(id, !config.isHidden(id))));
        place.add(edit("Reset button", group, id, () -> config.resetButton(id)));
        list.addView(chipRows(place, 2));
    }

    private View modifierChips(final Group group, final Slot slot) {
        List<View> chips = new ArrayList<>();
        final int mask = ActionPadKeyMap.maskOf(slot.mods);
        for (final int mod : ActionPadKeyMap.MODIFIER_KEYS) {
            TextView chip = edit(ActionPadKeyMap.modifierLabel(mod), group, slot.id,
                    () -> config.setKey(slot.id, slot.vk, mask ^ ActionPadKeyMap.modifierFlag(mod)));
            chip.setActivated((mask & ActionPadKeyMap.modifierFlag(mod)) != 0);
            chips.add(chip);
        }
        return chipRows(chips, 3);
    }

    private View keyGrid(final Group group, final Slot slot) {
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(VERTICAL);
        String current = null;
        List<View> chips = new ArrayList<>();
        for (final KeyDef key : ActionPadKeyMap.KEYS) {
            if (!key.group.equals(current)) {
                if (!chips.isEmpty()) grid.addView(chipRows(chips, 8));
                chips = new ArrayList<>();
                current = key.group;
                grid.addView(note(current));
            }
            TextView chip = edit(key.label, group, slot.id, () -> config.setKey(slot.id, key.vk, ActionPadKeyMap.maskOf(slot.mods)));
            chip.setActivated(key.vk == slot.vk);
            chips.add(chip);
        }
        grid.addView(chipRows(chips, 8));
        return grid;
    }

    private View colorChip(final Group group, final String id, Slot slot, final int index) {
        TextView chip = edit(COLOR_NAMES[index], group, id, () -> config.setColor(id, COLORS[index]));
        int color = COLORS[index] != 0 ? COLORS[index] : kit.theme.border;
        boolean selected = slot.color == COLORS[index];
        chip.setBackground(kit.rect(selected ? kit.theme.facePressed : kit.theme.face, color));
        chip.setTextColor(color);
        return chip;
    }

    /** Typing a new name on the pad's own keyboard. Enter saves it. */
    private void showRename(final Group group, final Slot slot) {
        LinearLayout column = screen("Rename \"" + slot.label + "\"", "Cancel", () -> showButton(group, slot.id), false);
        final StringBuilder name = new StringBuilder(slot.label);
        final TextView preview = kit.text(name + "|", 20);
        column.addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(48)));
        PadKeyboard keyboard = new PadKeyboard(kit, new PadKeyboard.Capture() {
            @Override
            public void onChar(String text) {
                if (name.length() < MAX_NAME) name.append(text);
                preview.setText(name + "|");
            }

            @Override
            public void onBackspace() {
                if (name.length() > 0) name.deleteCharAt(name.length() - 1);
                preview.setText(name + "|");
            }

            @Override
            public void onDone() {
                config.setName(slot.id, name.toString());
                changed();
                showButton(group, slot.id);
            }
        }, null);
        column.addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Typing a new chat line on the pad's own keyboard. Enter saves it. */
    private void showMessage(final Group group, final Slot slot) {
        LinearLayout column = screen("Message for \"" + slot.label + "\"", "Cancel", () -> showButton(group, slot.id), false);
        final StringBuilder text = new StringBuilder(slot.macro);
        final TextView preview = kit.text(text + "|", 16);
        column.addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(64)));
        PadKeyboard keyboard = new PadKeyboard(kit, new PadKeyboard.Capture() {
            @Override
            public void onChar(String typed) {
                if (text.length() < ActionPadKeyMap.MAX_MACRO) text.append(typed);
                preview.setText(text + "|");
            }

            @Override
            public void onBackspace() {
                if (text.length() > 0) text.deleteCharAt(text.length() - 1);
                preview.setText(text + "|");
            }

            @Override
            public void onDone() {
                config.setMacro(slot.id, text.toString());
                changed();
                showButton(group, slot.id);
            }
        }, null);
        column.addView(keyboard, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    // --- Helpers ---

    /** Clears the screen and adds a title bar with a back button. Returns where the content goes. */
    private LinearLayout screen(String title, String backLabel, Runnable back, boolean scroll) {
        removeAllViews();
        kit.forget();
        LinearLayout bar = kit.box(HORIZONTAL);
        bar.addView(kit.tapButton(backLabel, 14, back), new LayoutParams(kit.dp(90), LayoutParams.MATCH_PARENT));
        TextView heading = kit.text(title, 16);
        bar.addView(heading, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44)));

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setMotionEventSplittingEnabled(true);
        if (scroll) {
            scrolling = new ScrollView(getContext());
            scrolling.addView(content);
            addView(scrolling, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            scrolling = null;
            addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return content;
    }

    private TextView heading(String text) {
        TextView view = kit.text(text, 15);
        view.setTextColor(kit.theme.accent);
        view.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(kit.dp(4), kit.dp(10), 0, kit.dp(2));
        return view;
    }

    private TextView note(String text) {
        TextView view = kit.text(text, 12);
        view.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(kit.dp(4), kit.dp(4), 0, 0);
        view.setAlpha(0.75f);
        return view;
    }

    /** Lays buttons out in rows of {@code perRow}, padding the last row so the buttons keep one size. */
    private LinearLayout chipRows(List<View> chips, int perRow) {
        LinearLayout rows = new LinearLayout(getContext());
        rows.setOrientation(VERTICAL);
        for (int start = 0; start < chips.size(); start += perRow) {
            LinearLayout row = kit.box(HORIZONTAL);
            for (int i = start; i < start + perRow; i++) {
                View chip = i < chips.size() ? chips.get(i) : new View(getContext());
                row.addView(chip, kit.weighted(row, 1f));
            }
            rows.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44)));
        }
        return rows;
    }

    private Slot find(Group group, String id) {
        for (Slot s : config.resolve(group.id, group.defaults, true)) if (s.id.equals(id)) return s;
        throw new IllegalStateException("No button " + id);
    }

    /** What the button sends, such as "Shift+V". */
    private static String describe(Slot slot) {
        StringBuilder sb = new StringBuilder();
        for (int mod : slot.mods) sb.append(ActionPadKeyMap.modifierLabel(mod)).append('+');
        return sb.append(ActionPadKeyMap.labelFor(slot.vk)).toString();
    }

    /** One option of a set: highlighted when it is the current one. */
    private TextView choice(String label, boolean selected, Runnable pick) {
        TextView chip = kit.tapButton(label, 13, pick);
        if (selected) chip.setBackground(kit.rect(kit.theme.accent, kit.theme.accent));
        if (selected) chip.setTextColor(kit.theme.bg);
        return chip;
    }

    private void changed() {
        kit.settings.saveConfig();
        onChanged.run();
    }

    /** A button that makes one change to a button's settings, saves it, and redraws the screen. */
    private TextView edit(String label, final Group group, final String id, final Runnable change) {
        return kit.tapButton(label, 14, () -> {
            change.run();
            changed();
            showButton(group, id);
        });
    }
}
