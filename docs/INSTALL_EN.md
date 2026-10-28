# WristLens 1.8.0 — installation and use

[Русская инструкция](INSTALL_RU.md) · [README](../README.md)

## Install both apps

Phone: Android 10 or newer. Watch: Wear OS with API 30 or newer. Pair the devices and ensure Google Play services is available. Download the phone and watch release APKs from the same version. Open the phone APK to install, or use ADB:

```sh
adb -s PHONE_SERIAL install -r WristLens-phone-1.8.0-release.apk
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:CONNECTION_PORT
adb -s WATCH_IP:CONNECTION_PORT install -r WristLens-wear-1.8.0-release.apk
```

Enable developer options and wireless debugging on the watch first. Pairing and connection ports differ. Replace placeholders with your device values; disable debugging after installation if no longer needed.

Open the phone app once. Grant camera permission and microphone permission for sound. Opening the watch app requests a phone session automatically. Android/OEM restrictions may require unlocking, granting permission or opening the phone app after a force stop. These controls are not bypassed.

## Capture and preview

Use the centre shutter for a photo or to start video in video mode. Hold photo for a burst after 350 ms, release to stop. The default and maximum count are 50; the default delay is 200 ms after the preceding frame is saved. Exposure/processing time is additional, and a 60-second cap applies. Loss of the hold heartbeat stops the burst. A photo already being captured may finish after release.

During video: bottom-left pauses/resumes; bottom-right stops; centre takes a photo. Top-right becomes a microphone toggle for the current file, including while paused. Long-press stop opens the menu. Flash, timer and camera icons support long-press for their settings.

Tap the image for focus/exposure metering where supported. The bezel changes **camera zoom**. Pinching and dragging the image change only the **watch view**: fit all frame corners inside the circle with black margins, magnify or pan. Double-tap outside the buttons resets the view. Touch gestures never change output resolution. Air-pinch recognition is not included.

Preview presets are independent of saved media: Economy (up to 320 px, JPEG 40, interval 330 ms), Smooth (watch-screen limit, JPEG 60, 67 ms) and Detail (watch-screen limit, JPEG 85, 125 ms). These intervals are targets, not guaranteed FPS. A video snapshot that falls back to preview quality is affected by this setting.

## Gallery and downloads

The gallery supports list/grid, file counts and multiple selection. Hold a file to select it; use Select all for the complete index. Delete, copy or share selected files. Sharing on phone opens Android's share sheet; you choose the recipient. Local watch sharing requires a compatible installed app.

Downloads show a start message, per-file gallery status and a notification with progress/cancel. Allow notifications on the watch. Jobs continue after leaving the gallery. Cancellation preserves completed files and removes unfinished copies. Automatic saving is separate and off by default; each file is limited to 4 GB. Transfers verify SHA-256 before publishing a copy. Retrying an existing copy with the same folder/profile does not duplicate it.

## Files and saving

Defaults: phone and watch folder `DCIM/Camera`; names `IMG_YYYYMMDD_HHMMSS` and `VID_YYYYMMDD_HHMMSS`. Choose the phone folder/name pattern/photo type/video codec in Files and saving. Available photo formats include JPEG/PNG/WebP; PNG/WebP are re-encoded camera JPEG, not RAW.

Watch copies can retain the original bytes or be compressed on the phone before transfer. Balanced photos: JPEG 85%, up to 1920 px long edge. Compact: JPEG 70%, up to 1280 px. Balanced video: MP4 H.264/AAC, up to 1280 px, target 2 Mbps. Compact: up to 854 px, target 1 Mbps. Video copies are capped at 30 fps; audio targets 96 kbps if present. Compression does not modify the phone original and may lose metadata. Actual file reduction depends on the source/encoder.

## Audio, watermark and background

Record microphone audio defaults on. Listen to phone microphone defaults off. The latter is separate live monitoring on the watch; watch media volume controls it. Headphones help avoid feedback near the phone. Monitoring stops when the watch app leaves the foreground. Recording follows the background setting. Grant microphone permission before starting a clip; an absent audio track cannot be added halfway through that clip.

Watermarks can include date/time in different formats, in any corner, black or white, with three sizes. They are baked into saved photos/video. Constrained high-speed capture does not support watermarks.

Background capture defaults on: single-shot timers and video continue after leaving the watch app. With it off, timers cancel and video stops/saves. Hold bursts always stop on exit. Explicit Disconnect ends the session. A watch-controlled session ends after 60 seconds of background inactivity; recordings (even paused), timers and pending captures are protected. Downloads continue independently.

Only the main camera view keeps the display awake. Menus follow the normal screen timeout. Sounds and action vibration have independent switches; feedback follows confirmed capture events. In menus, rotary input scrolls or adjusts a focused slider; the curved position indicator appears only during scrolling.

## Troubleshooting

- No connection: confirm pairing, both APK versions, Google Play services, permissions and phone app availability.
- Camera mode unavailable: the application can only use modes exposed through Camera2 and the encoder; stock-camera marketing specifications do not guarantee public API access.
- Poor preview: try Economy/Smooth, shorten the wireless distance, and compare connection conditions. Saved media quality is independent.
- Watermark/audio issue: record a short test clip on your actual device and review it before important capture.
- Hardware validation: archived automated tests do not verify the actual Infinix/Watch Ultra camera, radio or OEM power restrictions. Export diagnosis from the phone app when reporting a problem.

Interface language follows the primary system language on each device: Russian for ru, English otherwise. Use Back to return one menu level. Changing a setting preserves its page and scroll position.
