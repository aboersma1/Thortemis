package com.limelight.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
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
        final String title;
        final Kind kind;
        final List<Page> pages;

        Tab(String title, Kind kind, List<Page> pages) {
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Tab> tabs = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final LinearLayout content;
    private LinearLayout pageHost;
    private LinearLayout rightColumn;
    private int tabIndex = 0;
    /** The tab to go back to when the keyboard, opened for chat, is done. -1 when it was not opened for chat. */
    private int chatReturnTab = -1;

    public ExternalActionPadView(Context context, KeySink sink) {
        super(context);
        settings = ActionPadSettings.load(context);
        dimmer = new PadDimmer(this, settings.dimMs);
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

        buildTabs();
        // Swallow touches in the gaps, so they do not move the host's mouse.
        setClickable(true);
        setMotionEventSplittingEnabled(true);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{settings.theme.bgTop, settings.theme.bg}));
        int pad = kit.dp(8);
        setPadding(pad, pad, pad, pad);

        content = kit.box(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        rebuild();
    }

    private void buildTabs() {
        List<Page> shown = new ArrayList<>();
        for (Page page : ActionPadKeyMap.PAGES) {
            if (settings.shownTabs.contains(page.id)) shown.add(page);
        }
        if (settings.sideBySide && !shown.isEmpty()) {
            tabs.add(new Tab("Pages", Kind.KEYS, shown));
        } else {
            for (Page page : shown) tabs.add(new Tab(page.title, Kind.KEYS, Collections.singletonList(page)));
        }
        if (settings.shownTabs.contains(ActionPadKeyMap.MOUSE_TAB_ID)) tabs.add(new Tab("Mouse", Kind.MOUSE, Collections.<Page>emptyList()));
        if (settings.shownTabs.contains(ActionPadKeyMap.KEYBOARD_TAB_ID)) tabs.add(new Tab("Keyboard", Kind.KEYBOARD, Collections.<Page>emptyList()));
    }

    // --- Building the UI ---

    /** Builds everything again, after the player changed a button. Keeps the current tab. */
    private void rebuild() {
        kit.forget();
        input.releaseKeys();
        input.releaseMouse();
        content.removeAllViews();
        tabViews.clear();

        pageHost = kit.box(LinearLayout.HORIZONTAL);
        rightColumn = kit.box(LinearLayout.VERTICAL);
        LinearLayout body = kit.box(LinearLayout.HORIZONTAL);
        View[] sides = settings.swapSides ? new View[]{rightColumn, pageHost} : new View[]{pageHost, rightColumn};
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
        // The touchpad and keyboard take the whole width, so the number block steps aside.
        rightColumn.setVisibility(tab.kind == Kind.KEYS ? VISIBLE : GONE);
        if (tab.kind == Kind.MOUSE) {
            input.releaseModifiers();
            pageHost.addView(new PadTouchpad(kit), kit.weighted(pageHost, 1f));
        } else if (tab.kind == Kind.KEYBOARD) {
            Runnable done = chatReturnTab >= 0 ? this::closeChatKeyboard : null;
            pageHost.addView(new PadKeyboard(kit, null, done), kit.weighted(pageHost, 1f));
        } else {
            for (Page page : tab.pages) addPage(page);
        }
    }

    private void addPage(Page page) {
        List<Slot> slots = settings.config.resolve(page.id, page.slots, false);
        boolean wide = tabs.get(tabIndex).pages.size() == 1 && slots.size() > SINGLE_COLUMN_MAX;
        int columns = wide ? 2 : 1;
        int perColumn = (slots.size() + columns - 1) / columns;
        for (int c = 0; c < columns; c++) {
            LinearLayout column = kit.box(LinearLayout.VERTICAL);
            for (Slot slot : slots.subList(Math.min(c * perColumn, slots.size()), Math.min((c + 1) * perColumn, slots.size()))) {
                float sp = tabs.get(tabIndex).pages.size() > 1 ? 12 : wide ? 14 : 18;
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
        dimmer.onTouch(ev);
        return super.dispatchTouchEvent(ev);
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
