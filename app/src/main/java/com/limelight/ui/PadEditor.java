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

    private void showMain() {
        confirmReset = false;
        LinearLayout list = screen("Customize buttons", "Close", onClose, true);
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
        }

        list.addView(heading("Name"));
        List<View> name = new ArrayList<>();
        name.add(kit.tapButton("Rename", 14, () -> showRename(group, slot)));
        name.add(kit.tapButton("Default name", 14, () -> {
            config.setName(id, null);
            changedAndReopen(group, id);
        }));
        list.addView(chipRows(name, 2));

        list.addView(heading("Outline colour"));
        List<View> colors = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) colors.add(colorChip(group, id, slot, i));
        list.addView(chipRows(colors, 4));

        list.addView(heading("Place"));
        List<View> place = new ArrayList<>();
        place.add(kit.tapButton("Move earlier", 14, () -> {
            config.move(group.id, group.defaults, id, -1);
            changedAndReopen(group, id);
        }));
        place.add(kit.tapButton("Move later", 14, () -> {
            config.move(group.id, group.defaults, id, 1);
            changedAndReopen(group, id);
        }));
        place.add(kit.tapButton(config.isHidden(id) ? "Show" : "Hide", 14, () -> {
            config.setHidden(id, !config.isHidden(id));
            changedAndReopen(group, id);
        }));
        place.add(kit.tapButton("Reset button", 14, () -> {
            config.resetButton(id);
            changedAndReopen(group, id);
        }));
        list.addView(chipRows(place, 2));
    }

    private View modifierChips(final Group group, final Slot slot) {
        List<View> chips = new ArrayList<>();
        final int mask = ActionPadKeyMap.maskOf(slot.mods);
        for (final int mod : ActionPadKeyMap.MODIFIER_KEYS) {
            TextView chip = kit.tapButton(ActionPadKeyMap.modifierLabel(mod), 14, () -> {
                config.setKey(slot.id, slot.vk, mask ^ ActionPadKeyMap.modifierFlag(mod));
                changedAndReopen(group, slot.id);
            });
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
            TextView chip = kit.tapButton(key.label, 12, () -> {
                config.setKey(slot.id, key.vk, ActionPadKeyMap.maskOf(slot.mods));
                changedAndReopen(group, slot.id);
            });
            chip.setActivated(key.vk == slot.vk);
            chips.add(chip);
        }
        grid.addView(chipRows(chips, 8));
        return grid;
    }

    private View colorChip(final Group group, final String id, Slot slot, final int index) {
        TextView chip = kit.tapButton(COLOR_NAMES[index], 13, () -> {
            config.setColor(id, COLORS[index]);
            changedAndReopen(group, id);
        });
        int color = COLORS[index] != 0 ? COLORS[index] : kit.theme.border;
        chip.setBackground(kit.rect(kit.theme.face, color));
        chip.setTextColor(color);
        chip.setActivated(slot.color == COLORS[index]);
        if (slot.color == COLORS[index]) chip.setBackground(kit.rect(kit.theme.facePressed, color));
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
                changedAndReopen(group, slot.id);
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
            ScrollView scrollView = new ScrollView(getContext());
            scrollView.addView(content);
            addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
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

    private void changed() {
        kit.settings.saveConfig();
        onChanged.run();
    }

    private void changedAndReopen(Group group, String id) {
        changed();
        showButton(group, id);
    }
}
