# Changelog

[Русский](CHANGELOG_RU.md) · [Version archive](RELEASES.md)

Based on the original development reports. Dates are retained in source reports where recorded. Test totals below are historical automated checks, not new physical-device validation.

## 0.1.0 — Initial camera remote

Debug · 7 archived tests · [Full entry](docs/changelog/0.1.0.md)

- Initial paired Android/Wear OS camera remote with live preview, photo/video controls, timers, flash, focus and zoom.
- Command deduplication, frame limits, focus-coordinate mapping and permission checks.
- Both debug APK signatures and package identity verified; hardware and visual acceptance had not been performed.

## 0.2.0 — Round controls and media settings

Debug · 20 archived tests · [Full entry](docs/changelog/0.2.0.md)

- Expanded round-screen controls, hold-to-burst and on-watch gallery/copy workflow, as documented by the development requests and upgrade tests.
- Configurable saving folder, naming and file formats; expanded camera capability selection and watch-sized preview.
- Both apps used versionName 0.2.0 (phone 200 / wear 201). A watch layout regression was subsequently fixed in 0.2.1.

## 0.2.1 — Watch layout hotfix

Debug · watch only · 29 archived tests · [Full entry](docs/changelog/0.2.1.md)

- Assign button dimensions before child measurement, fixing oversized overlapping icons on first layout.
- Centre the curved status text once; separate button hit areas and scale for small displays.
- Fix offline initial state, stale recording state, countdown and large-font text.
- Only the watch changed to 0.2.1 (code 202); the matching phone remains the original 0.2.0 APK.

## 1.0.0 — Background capture and watermark

Release · 44 archived tests · [Full entry](docs/changelog/1.0.0.md)

- First release builds with debugging disabled; transparent status arc, clock in menus and in-app instructions.
- Automatic phone launch/session request, explicit disconnect and ordered watch presence messages.
- Configurable background video/timers; hold bursts stop on exit.
- Video date/time watermark composed through GLES; constrained high-speed watermark rejected explicitly.
- Optional platform primary gesture support was conditional on firmware; later versions replaced and then removed it.

## 1.1.0 — Reliable gallery and saved watermark

Release · 63 archived tests · [Full entry](docs/changelog/1.1.0.md)

- Refresh gallery after deletion and preserve settings page/scroll while changing values.
- Automatic original-copy queue, list/grid, multiple selection and system sharing.
- English default and Russian localization; curved transparent menu time.
- Watermark baked into saved photos/video; EXIF-aware photo processing and combined video shader.
- Burst default/limit raised to 50 frames with a 250 ms post-save interval at this version.

## 1.2.0 — Gallery layout and experimental gesture

Release · 85 archived tests · [Full entry](docs/changelog/1.2.0.md)

- Round menus use an inscribed scroll area and wrapped text, including nested settings and confirmation dialogs.
- List rows use a small thumbnail with filename/properties; grid uses two larger thumbnail columns.
- Show media type, date, size, available dimensions and duration.
- Experimental app-owned accelerometer/gyroscope double-pinch recognition with personal training and a test mode.
- This experimental gesture was not hardware-validated and was removed in 1.4.0.

## 1.3.0 — Navigation, select all and bezel

Release · 100 archived tests · [Full entry](docs/changelog/1.3.0.md)

- Full gallery count and Select all across pages; stable indexing during deletion.
- Back closes one submenu level; confirmed settings display Saved.
- Remove rectangular scroll clipping, centre headings, add spacing and suppress overlapping state text.
- Long-press flash/timer/camera opens its settings; rotary and touch-bezel scrolling plus precise sliders.
- Persist three independent preview profiles; add a simpler experimental gesture mode (removed in 1.4.0).

## 1.4.0 — Configurable watch copies

Release · 95 archived tests · [Full entry](docs/changelog/1.4.0.md)

- Animated curved scroll thumb follows viewport fraction and position; hides on idle and non-scrollable pages.
- Watch folder and separate original/balanced/compact photo/video copy profiles.
- Compress on phone before transfer, verify copy SHA-256 and keep source originals unchanged.
- Download/delete status text, normal display timeout in menus and an action-vibration switch.
- Remove all air-pinch recognition, sensors, training and gesture menus.

## 1.5.0 — Unified menus and idle disconnect

Release · 110 archived tests · [Full entry](docs/changelog/1.5.0.md)

- Haptics for menu buttons/toggles and real bezel scroll/slider changes, respecting app/system settings.
- Merge camera/background/watermark settings and combine phone/watch saving on one page.
- Move long explanations into the guide; show version and credits.
- Disconnect watch-controlled camera sessions after 60 seconds of continuous background inactivity.
- Protect recording/paused video, timers and pending capture; keep copy jobs independent.

## 1.6.0 — Capture feedback

Release · 127 archived tests · [Full entry](docs/changelog/1.6.0.md)

- Suppress Saved for main-screen quick controls/bezel, retain settings-page confirmations.
- Use Timer cancelled wording. Add independently configurable capture sounds.
- Confirmed burst frames trigger haptics; stale/duplicate events are suppressed.
- Add a 180 ms shutter aperture animation beneath controls.
- Default burst interval becomes 200 ms after save; 50-frame default/limit remains.
- Menu footer temporarily shows only the installed version.

## 1.7.0 — Download progress and live audio

Release · 153 archived tests · [Full entry](docs/changelog/1.7.0.md)

- Durable WorkManager downloads with start message, gallery status, notification progress and cancellation.
- Keep completed copies on cancellation; clean unfinished files.
- Independent recorded phone microphone audio (on by default) and live watch monitoring (off by default).
- Replace camera-switch with microphone mute/unmute during recording, including pause, in the same output file.
- Share microphone capture and use MediaCodec/MediaMuxer with a pause-aware timeline; retain GLES watermark.
- Menu footer shows WristLens and installed version.

## 1.8.0 — Pinch, pan and fit

Release · 179 archived tests · [Full entry](docs/changelog/1.8.0.md)

- Pinch the watch preview from full-frame fit (all corners inside the circle) to 3× default fill.
- Drag to pan within image bounds; double-tap outside controls restores fill and centring.
- Preserve aspect ratio; camera zoom, transmitted frame size and saved media remain independent.
- Tap-to-focus uses the inverse displayed transform, including rotation/mirroring; black margins do not focus.
- Pinching across controls cancels pending clicks/holds safely and stops hold-burst without a stray photo.
- Keep view through frames/menus; reset on orientation/aspect/mirror changes or a new launch.
