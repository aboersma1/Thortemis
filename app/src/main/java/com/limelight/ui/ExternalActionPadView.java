package com.limelight.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.ui.ActionPadKeyMap.Page;
import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A second-screen button pad for MMO-style play: tabs of interface, target, utility and chat shortcuts,
 * Shift / Ctrl / Alt plus the action-bar number keys, a touchpad and an on-screen keyboard. Players can
 * change every button from the Edit button ({@link PadEditor}) and choose theme, tabs and layout in settings
 * ({@link ActionPadSettings}).
 *
 * Every touch is exactly one key press and one key release, sent through {@link KeySink}, so several keys
 * can be held at once. The state behind them lives in {@link PadInput}.
 */
public class ExternalActionPadView extends FrameLayout {

    /** Where key and mouse events go. {@code modifiers} is the KeyboardPacket modifier flags in effect for the key. */
    public interface KeySink {
        void sendKey(short vk, boolean down, byte modifiers);

        /** Moves the host's mouse by a relative amount, in host pixels. */
        default void sendMouseMove(short dx, short dy) {}

        /** Presses or releases a mouse button ({@link ActionPadKeyMap#MOUSE_LEFT} or {@link ActionPadKeyMap#MOUSE_RIGHT}). */
        default void sendMouseButton(byte button, boolean down) {}

        /** Turns the mouse wheel: positive scrolls up, negative scrolls down, one unit per notch. */
        default void sendMouseScroll(byte notches) {}
    }

    /** Pages with more buttons than this split into two columns. */
    private static final int SINGLE_COLUMN_MAX = 8;
    private static final int EXTRAS_PER_ROW = 5;

    private enum Kind { KEYS, MOUSE, KEYBOARD }

    /** A tab: one or more pages of key buttons (several when shown side by side), the touchpad, or the keyboard. */
    private static final class Tab {
        final String id;
        final String title;
        final Kind kind;
        final List<Page> pages;

        Tab(String id, String title, Kind kind, List<Page> pages) {
            this.id = id;
            this.title = title;
            this.kind = kind;
            this.pages = pages;
        }
    }

    private final ActionPadSettings settings;
    private final PadInput input;
    private final PadKit kit;
    private final PadEditor editor;
    private final PadDimmer dimmer;
    /** Solid black cover shown by the screen saver. */
    private final PadSaverView saver;
    /** True from the touch that woke the saver until it lifts, so the waking touch presses nothing. */
    private boolean swallowing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Tab> tabs = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final LinearLayout content;
    private LinearLayout pageHost;
    private LinearLayout rightColumn;
    /** Holds another page's buttons in place of the number block, when the player chose one for the tab. */
    private LinearLayout sideHost;
    private int tabIndex = 0;
    /** The tab to go back to when the keyboard, opened for chat, is done. -1 when it was not opened for chat. */
    private int chatReturnTab = -1;

    public ExternalActionPadView(Context context, KeySink sink) {
        super(context);
        settings = ActionPadSettings.load(context);
        dimmer = new PadDimmer(this, settings.dimMs);
        saver = new PadSaverView(context);
        saver.setVisibility(GONE);
        input = new PadInput(sink, new PadInput.Scheduler() {
            @Override
            public void post(Runnable task, long delayMs) {
                handler.postDelayed(task, delayMs);
            }

            @Override
            public void cancelAll() {
                handler.removeCallbacksAndMessages(null);
            }
        });
        kit = new PadKit(context, settings, input);
        // The editor has its own kit, so its screens never disturb the pad's buttons. Nothing it builds sends keys.
        PadInput silent = new PadInput((vk, down, modifiers) -> { }, new PadInput.Scheduler() {
            @Override
            public void post(Runnable task, long delayMs) { }

            @Override
            public void cancelAll() { }
        });
        editor = new PadEditor(new PadKit(context, settings, silent), this::rebuild, this::closeEditor);

        // Swallow touches in the gaps, so they do not move the host's mouse.
        setClickable(true);
        setMotionEventSplittingEnabled(true);
        int pad = kit.dp(8);
        setPadding(pad, pad, pad, pad);

        content = kit.box(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        rebuild();
    }

    private void buildTabs() {
        // Side by side puts the ordinary pages in one tab. A full-screen page keeps a tab of its own.
        List<Page> shown = new ArrayList<>();
        List<Page> full = new ArrayList<>();
        for (Page page : ActionPadKeyMap.PAGES) {
            if (!settings.shownTabs.contains(page.id)) continue;
            if (page.fullWidth) full.add(page); else shown.add(page);
        }
        if (settings.sideBySide && !shown.isEmpty()) {
            tabs.add(new Tab("pages", "Pages", Kind.KEYS, shown));
        } else {
            for (Page page : shown) tabs.add(new Tab(page.id, named(page.id, page.title), Kind.KEYS, Collections.singletonList(page)));
        }
        for (Page page : full) tabs.add(new Tab(page.id, named(page.id, page.title), Kind.KEYS, Collections.singletonList(page)));
        if (settings.shownTabs.contains(ActionPadKeyMap.MOUSE_TAB_ID)) {
            tabs.add(new Tab(ActionPadKeyMap.MOUSE_TAB_ID, named(ActionPadKeyMap.MOUSE_TAB_ID, "Mouse"), Kind.MOUSE, Collections.<Page>emptyList()));
        }
        if (settings.shownTabs.contains(ActionPadKeyMap.KEYBOARD_TAB_ID)) {
            tabs.add(new Tab(ActionPadKeyMap.KEYBOARD_TAB_ID, named(ActionPadKeyMap.KEYBOARD_TAB_ID, "Keyboard"), Kind.KEYBOARD, Collections.<Page>emptyList()));
        }
    }

    private void applyBackground() {
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{settings.theme.bgTop, settings.theme.bg}));
    }

    /** The tab's name: the player's own if they gave it one. */
    private String named(String id, String fallback) {
        return settings.tabOptions.name(id, fallback);
    }

    // --- Building the UI ---

    /** Builds everything again, after the player changed a button. Keeps the current tab. */
    private void rebuild() {
        // The editor may have changed the theme or the fade time.
        kit.applyTheme();
        applyBackground();
        dimmer.setIdleMs(settings.dimMs);
        dimmer.setSaver(settings.saverMs, this::showSaver, this::hideSaver);
        kit.forget();
        input.releaseKeys();
        input.releaseMouse();
        content.removeAllViews();
        tabViews.clear();
        // The editor may have turned tabs on or off. Stay on the same tab if it is still there.
        String current = tabs.isEmpty() ? null : tabKey(tabs.get(Math.min(tabIndex, tabs.size() - 1)));
        tabs.clear();
        buildTabs();
        tabIndex = Math.min(tabIndex, Math.max(0, tabs.size() - 1));
        for (int i = 0; i < tabs.size(); i++) if (tabKey(tabs.get(i)).equals(current)) tabIndex = i;
        if (chatReturnTab >= tabs.size()) chatReturnTab = -1;

        pageHost = kit.box(LinearLayout.HORIZONTAL);
        rightColumn = kit.box(LinearLayout.VERTICAL);
        sideHost = kit.box(LinearLayout.HORIZONTAL);
        LinearLayout body = kit.box(LinearLayout.HORIZONTAL);
        View[] sides = settings.swapSides ? new View[]{sideHost, rightColumn, pageHost} : new View[]{pageHost, rightColumn, sideHost};
        for (View side : sides) body.addView(side, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        buildRightColumn();

        // The tab bar is above the buttons, or below them if the player prefers.
        View tabBar = buildTabBar();
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        if (settings.tabsBottom) {
            content.addView(body, bodyParams);
            content.addView(tabBar, barParams);
        } else {
            content.addView(tabBar, barParams);
            content.addView(body, bodyParams);
        }

        showTab(tabIndex);
    }

    /** Identifies a tab across a rebuild: its kind and, for key pages, the first page. */
    private static String tabKey(Tab tab) {
        return tab.kind + ":" + (tab.pages.isEmpty() ? "" : tab.pages.get(0).id);
    }

    private LinearLayout buildTabBar() {
        LinearLayout bar = kit.box(LinearLayout.HORIZONTAL);
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TextView tab = kit.text(tabs.get(i).title, 13);
            tab.setSingleLine(true);
            tab.setOnClickListener(v -> {
                chatReturnTab = -1;
                showTab(index);
            });
            tabViews.add(tab);
            bar.addView(tab, kit.weighted(bar, 1f));
        }
        if (settings.hasAnyProfile()) {
            // One tap swaps to the next saved profile, e.g. another character or spec.
            String label = settings.activeProfile() == 0 ? "Profile" : "Profile " + settings.activeProfile();
            bar.addView(kit.tapButton(label, 13, () -> {
                input.releaseAll();
                settings.loadNextProfile();
                rebuild();
            }), kit.weighted(bar, 1.2f));
        }
        TextView edit = kit.tapButton("Edit", 13, this::openEditor);
        bar.addView(edit, kit.weighted(bar, 0.8f));
        return bar;
    }

    /** Shift / Ctrl / Alt, the number block, and the extra buttons when the player turned them on. */
    private void buildRightColumn() {
        LinearLayout mods = kit.box(LinearLayout.HORIZONTAL);
        for (int vk : ActionPadKeyMap.MODIFIER_KEYS) mods.addView(kit.modifierButton(vk), kit.weighted(mods, 1f));
        rightColumn.addView(mods, kit.weighted(rightColumn, 0.7f));

        List<Slot> numbers = settings.config.resolve("numbers", ActionPadKeyMap.NUMBERS, false);
        rightColumn.addView(keyGrid(numbers, 3, 26), kit.weighted(rightColumn, 4f));

        if (settings.extrasOn) {
            List<Slot> extras = settings.config.resolve("extras", ActionPadKeyMap.EXTRAS, false);
            extras = extras.subList(0, Math.min(settings.extrasCount, extras.size()));
            if (!extras.isEmpty()) {
                int rows = (extras.size() + EXTRAS_PER_ROW - 1) / EXTRAS_PER_ROW;
                rightColumn.addView(keyGrid(extras, (extras.size() + rows - 1) / rows, 16), kit.weighted(rightColumn, 0.8f * rows));
            }
        }
    }

    /** Buttons in rows of {@code perRow}. These take Shift, Ctrl and Alt armed on the pad. */
    private LinearLayout keyGrid(List<Slot> slots, int perRow, float sp) {
        LinearLayout grid = kit.box(LinearLayout.VERTICAL);
        for (int start = 0; start < slots.size(); start += perRow) {
            LinearLayout row = kit.box(LinearLayout.HORIZONTAL);
            for (Slot slot : slots.subList(start, Math.min(start + perRow, slots.size()))) {
                row.addView(keyButton(slot, slot.label, sp, true), kit.weighted(row, 1f));
            }
            grid.addView(row, kit.weighted(grid, 1f));
        }
        return grid;
    }

    private TextView keyButton(Slot slot, String label, float sp, boolean modified) {
        Runnable after = slot.opensKeyboard ? this::openChatKeyboard : null;
        return kit.keyButton(slot, label, sp, modified, after);
    }

    /** Shows tab {@code index}. Lets go of any pressed button first, since its view is about to go. */
    private void showTab(int index) {
        if (tabs.isEmpty()) return;
        input.releaseKeys();
        input.releaseMouse();
        tabIndex = Math.max(0, Math.min(index, tabs.size() - 1));
        for (int i = 0; i < tabViews.size(); i++) styleTab(tabViews.get(i), i == tabIndex);

        Tab tab = tabs.get(tabIndex);
        pageHost.removeAllViews();
        // The touchpad, keyboard and full-screen pages take the whole width, so the number block steps aside.
        boolean ordinary = tab.kind == Kind.KEYS && !tab.pages.get(0).fullWidth;
        String side = ordinary && tab.pages.size() == 1 ? settings.tabOptions.side(tab.id) : PadTabOptions.NUMBERS;
        rightColumn.setVisibility(ordinary && side.equals(PadTabOptions.NUMBERS) ? VISIBLE : GONE);
        sideHost.removeAllViews();
        sideHost.setVisibility(GONE);
        if (ordinary && !side.equals(PadTabOptions.NUMBERS) && !side.equals(PadTabOptions.HIDDEN)) {
            for (Page other : ActionPadKeyMap.PAGES) if (other.id.equals(side)) fillSide(other);
        }
        if (tab.kind == Kind.MOUSE) {
            input.releaseModifiers();
            pageHost.addView(new PadTouchpad(kit), kit.weighted(pageHost, 1f));
        } else if (tab.kind == Kind.KEYBOARD) {
            Runnable done = chatReturnTab >= 0 ? this::closeChatKeyboard : null;
            pageHost.addView(new PadKeyboard(kit, null, done), kit.weighted(pageHost, 1f));
        } else {
            for (Page page : tab.pages) addPage(page, ordinary && side.equals(PadTabOptions.HIDDEN));
        }
    }

    /** Another page's buttons where the number block would be: columns of at most seven. */
    private void fillSide(Page page) {
        List<Slot> slots = settings.config.resolve(page.id, page.slots, false);
        if (slots.isEmpty()) return;
        sideHost.setVisibility(VISIBLE);
        int columns = (slots.size() + 6) / 7;
        int perColumn = (slots.size() + columns - 1) / columns;
        for (int c = 0; c < columns; c++) {
            LinearLayout column = kit.box(LinearLayout.VERTICAL);
            for (Slot slot : slots.subList(c * perColumn, Math.min((c + 1) * perColumn, slots.size()))) {
                column.addView(keyButton(slot, wrapLabel(slot.label), 13, false), kit.weighted(column, 1f));
            }
            sideHost.addView(column, kit.weighted(sideHost, 1f));
        }
    }

    private void addPage(Page page, boolean wholeWidth) {
        List<Slot> slots = settings.config.resolve(page.id, page.slots, false);
        boolean single = tabs.get(tabIndex).pages.size() == 1;
        boolean wide = single && slots.size() > SINGLE_COLUMN_MAX;
        // With nothing on the right the page has the whole width, so it spreads over more columns.
        int columns = page.fullWidth
                ? Math.max(1, (slots.size() + ActionPadKeyMap.FULL_PAGE_ROWS - 1) / ActionPadKeyMap.FULL_PAGE_ROWS)
                : wholeWidth && single ? Math.min(4, Math.max(2, (slots.size() + 5) / 6))
                : wide ? 2 : 1;
        int perColumn = (slots.size() + columns - 1) / columns;
        for (int c = 0; c < columns; c++) {
            LinearLayout column = kit.box(LinearLayout.VERTICAL);
            for (Slot slot : slots.subList(Math.min(c * perColumn, slots.size()), Math.min((c + 1) * perColumn, slots.size()))) {
                float sp = !single ? 12 : page.fullWidth || wholeWidth ? 13 : wide ? 14 : 18;
                column.addView(keyButton(slot, wrapLabel(slot.label), sp, false), kit.weighted(column, 1f));
            }
            pageHost.addView(column, kit.weighted(pageHost, 1f));
        }
    }

    private void styleTab(TextView tab, boolean selected) {
        ActionPadTheme t = settings.theme;
        tab.setBackground(kit.rect(selected ? t.facePressed : t.bg, selected ? t.accent : t.border));
        tab.setTextColor(selected ? t.accent : t.text);
    }

    /** A long, multi-word label drops its last word onto a second line. */
    private static String wrapLabel(String label) {
        int cut = label.lastIndexOf(' ');
        return label.length() > 9 && cut > 0 && label.indexOf('/') < 0
                ? label.substring(0, cut) + "\n" + label.substring(cut + 1)
                : label;
    }

    // --- Chat keyboard ---

    /** Chat buttons send their key, then bring up the keyboard to type the message. Enter or Esc returns. */
    private void openChatKeyboard() {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).kind == Kind.KEYBOARD) {
                chatReturnTab = tabIndex;
                final int keyboard = i;
                post(() -> showTab(keyboard));
                return;
            }
        }
    }

    private void closeChatKeyboard() {
        final int back = chatReturnTab;
        chatReturnTab = -1;
        post(() -> showTab(back));
    }

    // --- Editor ---

    private void openEditor() {
        input.releaseAll();
        editor.show();
    }

    private void closeEditor() {
        editor.setVisibility(GONE);
    }

    // --- Letting go ---

    /** Lets go of everything the pad holds: pressed keys and armed or locked modifiers. */
    public void releaseAll() {
        input.releaseAll();
    }

    /** Pressed state belongs to each button: this view is clickable only to swallow touches in the gaps. */
    @Override
    protected void dispatchSetPressed(boolean pressed) {
    }

    // --- Dimming ---

    /** Turns idle dimming on or off. Off when the host screen already dims itself. */
    public void setDimmingEnabled(boolean enabled) {
        dimmer.setEnabled(enabled);
    }

    /** Any touch brings the pad back to full brightness and still counts as a normal press. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean wasAsleep = dimmer.isAsleep();
        dimmer.onTouch(ev);
        int action = ev.getActionMasked();
        if (wasAsleep) swallowing = true;
        if (swallowing) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallowing = false;
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showSaver() {
        // Keys held down when the screen goes black must not stay pressed on the host.
        input.releaseAll();
        if (saver.getParent() == null) {
            addView(saver, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        // The pad has padding, so the cover alone would leave a lit border.
        setBackgroundColor(0xFF000000);
        saver.setVisibility(VISIBLE);
        saver.bringToFront();
        saver.run(true, settings.saverLogo);
    }

    private void hideSaver() {
        saver.run(false, false);
        saver.setVisibility(GONE);
        applyBackground();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dimmer.start();
    }

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    @Override
    protected void onDetachedFromWindow() {
        dimmer.stop();
        input.releaseAll();
        super.onDetachedFromWindow();
    }
}
