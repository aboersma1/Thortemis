# Artemis Android

Previously named Moonlight Noir

An open source client for [Apollo](https://github.com/ClassicOldSong/Apollo)/[Sunshine](https://github.com/LizardByte/Sunshine).

Artemis Android will allow you to stream your collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Artemis is currently the best fork of Moonlight with loads of optimizations for office usage.

A more seamless experience with virtual display will be Artemis paired with [Apollo](https://github.com/ClassicOldSong/Apollo).

# Thortemis (WoW action pad)

Thortemis is a fork of Artemis that adds an **action pad** for two-screen handhelds such as the AYN Thor. While you stream a game from your PC with Apollo or Sunshine, the second (bottom) screen becomes a touch panel of keys, so you can reach your number bar, interface shortcuts and mouse without a keyboard. It was built for World of Warcraft but sends ordinary Windows key codes, so it works for any game or app.

> **Status:** early release. The pad's logic has automated tests (79 unit tests), but the screens have so far only been checked by building them, not on many devices. Please report problems with your device model and a description of what you saw.

## What you get

- **Key pages:** Interface, Target, Utility, Chat, Replies and F-Keys, plus a number row. F-Keys is hidden until you turn it on.
- **Tabs on or off:** in Edit, under Tabs, tap a tab to hide or show it, so the pad keeps only what you use. The last tab stays on.
- **Chat tab:** two columns. On the left, ways to open chat with the on-screen keyboard up: Open chat, Reply, and one-tap Party, Guild, Raid, Say and Yell (each types `/p `, `/g `, `/raid `, `/s ` or `/y ` and waits for your message). On the right, a slash command opener and one-tap commands (Sit, Dance, Follow, Assist, Dismount, Wave). These openers need the Keyboard tab turned on.
- **Replies tab:** a full-screen grid of 24 one-tap chat lines (Thanks, On my way, Be right back, Need heals, Need rez, LFG, AFK and more), with no number block beside it. Each types into WoW's chat box in whatever channel it opens in. To change the words, tap Edit, pick the button, then Edit message. Start a line with `/p ` or `/s ` to pick a channel.
- **Modifiers:** Shift, Ctrl and Alt can be tapped once for the next key or locked on, and combined with any button.
- **Mouse tab:** touchpad with tap-to-click, two-finger right-click, Left/Hold/Right buttons and a scroll strip.
- **Keyboard tab:** an on-screen keyboard with three layouts you can switch between from Edit: Two layers (the default, big keys with a `/` on the letters layer and a `#+=` key for symbols and arrows), Square grid (everything on one layer) and Classic.
- **Extra buttons:** an optional panel of up to 10 more keys (F8 to F12, then Shift plus F8 to F12).
- **Customize:** change any button's key, name and outline colour, hide buttons, reorder them, and save up to three profiles. Reset one button or everything.
- **Looks:** change theme and fade time from the Edit screen, even mid-stream. 15 themes (Alliance Blue default, Classic, TBC, Wrath, Cata, MoP, WoD, Legion, BfA, Shadowlands, Dragonflight, TWW, Midnight, OLED Black, Cyberpunk).
- **Layout options:** show pages side by side, swap sides, put the tab bar at the bottom, change text size.
- **Profiles:** save up to three layouts (for example one per character or spec) and swap between them with the Profile button in the tab bar.
- **Comfort:** the pad appears once the stream has started, dims when idle (3, 5, 10, 20 or 60 seconds, or never; 10 by default) and can vibrate on press (a setting, on by default).
- **What's new:** a short notes screen shows once after each update, and again from Settings, What's new.

## Using it

1. Install the APK (it installs next to Moonlight or Artemis, as its own app, `com.thortemis`) and connect to your PC as usual.
2. Open Artemis settings and make sure **Action pad on second screen** is on (it is by default). The tabs, theme, dimming, touchpad, scroll and extras options are in the same section. Settings apply the next time the pad opens.
3. Start a stream. The pad appears on the second screen once the stream is running. Tap **Edit** in its tab bar to customize buttons.

Not used in Fully External Display Mode, which has its own pad.

## Known limits

- Scroll wheel direction may need the **Reverse scroll direction** setting on some setups.
- Saved button changes are tied to each button's default label, so a future version that renames defaults could reset those buttons.
- The release build is shrunk with R8 and has had no device testing yet; if something crashes that works in a debug build, report it.

## For developers

See [docs/ACTION_PAD.md](docs/ACTION_PAD.md) for how the pad works: data flow, file map, a suggested reading order, design decisions, tests and risks.

## Licence and credit

Artemis is built on Moonlight Android and is licensed under the GNU GPL v3 (see `LICENSE.txt`); this fork keeps that licence and the original authors' credit (see Authors below). The action pad is an addition on top of [ClassicOldSong's Artemis](https://github.com/ClassicOldSong/moonlight-android), which is a fork of [Moonlight](https://github.com/moonlight-stream/moonlight-android).

# Features

If you switch back to the main stream version, you'll be missing the following awesome features which are very unlikely to be added there:

1. Custom virtual buttons with import and export support.
2. [Custom resolutions](https://github.com/moonlight-stream/moonlight-android/pull/1349).
3. Custom bitrates.
4. [Multiple mouse mode switching](https://github.com/moonlight-stream/moonlight-android/pull/1304) (normal mouse, [multi-touch](https://github.com/moonlight-stream/moonlight-android/pull/1364), touchpad, disabled, local cursor mode).
5. Optimized virtual gamepad skins and free joystick.
6. External monitor mode.
7. Joycon D-pad support.
8. Simplified performance information display.
9. [Game back menu](https://github.com/moonlight-stream/moonlight-android/pull/1171).
10. Custom shortcut commands.
11. Easy soft keyboard switching.
12. Portrait mode.
13. Display on top mode, useful for foldable phones.
14. [Virtual touchpad space and sensitivity adjustment](https://github.com/moonlight-stream/moonlight-android/issues/1348#issuecomment-2236344729) for playing right-click view games, such as Warcraft.
15. Force use device's own vibration motor (in case your gamepad's vibration is not effective).
16. Gamepad debugging page to view gamepad vibration and gyroscope information, as well as Android kernel version information.
17. Trackpad tap/scrolling support
18. Natural track pad mode with touch screen
19. Non-QWERTY keyboard layout support
20. Quick Meta key with physical BACK button
21. Frame rate lock fix for some devices
22. Video scale mode: Fit/Fill/Stretch
23. View pan/zoom support
24. Rotate screen in-game
25. Add option to quit app directly
26. Samsung DeX scrolling support
27. Proper click/scroll/right-click for trackpad on generic Android tablet when using local cursor
28. Virtual Display integration with [Apollo](https://github.com/ClassicOldSong/Apollo)
29. Server Command integration with [Apollo](https://github.com/ClassicOldSong/Apollo)
30. Clipboard sync (requires Apollo)
31. SBS 3D for external Displays (Using AI MiDaS v2 Lite)

# Disclaimer

This is the `go away` version of Moonlight Android.

I got kicked from Moonlight and Sunshine's Discord server literally for helping people out.

This is what I got for finding a bug, opened an issue, getting no response, troubleshoot myself, fixed the issue myself, shared it by PR to the main repo hoping my efforts can help someone else during the maintainance gap.

Yes, I'm going away. Fixes and improvements on this fork are not necessarily be merged to the main repo either. I have also started [a fork of Sunshine called Apollo](https://github.com/ClassicOldSong/Apollo) and will add useful features that will never get merged by the main repo shortly. [Apollo](https://github.com/ClassicOldSong/Apollo) and [Moonlight Noir](https://github.com/ClassicOldSong/moonlight-android) will no longer be compatible with OG Sunshine and OG Moonlight eventually, but they'll work even better with much more carefully designed features.

The main repo had stayed silent for 5 months, with nobody actually responding to issues, and people are getting totally no help besides the limited FAQ in their Discord server. I tried to answer issues and questions, solve problems within my ablilty but I got kicked out just for helping others.

**PRs for feature improvements are welcomed here unlike the main repo, your ideas are more likely to be appreciated and your efforts are actually being respected. We welcome people who can and willing to share their efforts, helping yourselves and other people in need.**

**Update**: They have contacted me and apologized for this incident, but the fact it **happened** still motivated me to start my own fork.

## Downloads
* [Download the latest Thortemis APK](https://github.com/aboersma1/Thortemis/releases/latest). Pick the `arm64-release` file for the AYN Thor.

## Reporting a problem
Open an [issue](https://github.com/aboersma1/Thortemis/issues) with your device model, the Thortemis version (Settings, What's new), and what happened. For a crash, install the `debug` APK from the same release and attach the output of `adb logcat -b crash`.

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
