# Первичные технические источники

Проверены 21.10.2026. Изложение в документации проекта собственное; ссылки фиксируют основания выбора API и ограничений.

- [Wear OS Data Layer — назначение, зависимости, transport, одинаковые package/signature](https://developer.android.com/training/wearables/data/overview)
- [ChannelClient API](https://developers.google.com/android/reference/com/google/android/gms/wearable/ChannelClient)
- [Выбор клиента Data Layer](https://developer.android.com/training/wearables/data/client-types)
- [Camera2 capture sessions and requests](https://developer.android.com/media/camera/camera2/capture-sessions-requests)
- [Совместимость одновременных потоков камеры](https://developer.android.com/media/camera/camera2/multiple-camera-streams-simultaneously)
- [Перечисление камер и возможностей](https://developer.android.com/media/camera/camera2/camera-enumeration)
- [CameraCharacteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
- [MediaRecorder, включая pause/resume](https://developer.android.com/reference/android/media/MediaRecorder)
- [Screen flash: белый экран, яркость, AE/AWB precapture](https://developer.android.com/media/camera/camera2/screen-flash-implementation-guidelines)
- [Foreground service types и разрешения camera/microphone](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Ограничения запуска foreground service из фона](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [AGP 8.13.2: совместимость Gradle/JDK/SDK](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Wear OS: упаковка и одинаковый package](https://developer.android.com/training/wearables/packaging)
- [Wear OS: адаптация к размерам экрана](https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design)
- [Wear OS: беспроводная отладка и установка](https://developer.android.com/training/wearables/get-started/debug-wifi)
- [Android Debug Bridge](https://developer.android.com/tools/adb)

Проект использует Android SDK и Google Play services; их лицензии и исходные уведомления об авторских правах принадлежат соответствующим правообладателям. Gradle Wrapper — Gradle, лицензия Apache-2.0. JUnit 4.13.2 используется только для тестов, EPL-1.0.


## Дополнения для 0.2.0 (22.10.2026)

- [StreamConfigurationMap](https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap): high-resolution и high-speed размеры/диапазоны.
- [OutputConfiguration](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration): sensor pixel modes, зеркалирование.
- [RemoteActivityHelper](https://developer.android.com/reference/androidx/wear/remote/interactions/RemoteActivityHelper): ACTION_VIEW + CATEGORY_BROWSABLE + URI; остальные поля Intent не переносятся.
- [MediaStore shared storage](https://developer.android.com/training/data-storage/shared/media): собственные медиа, IS_PENDING и RELATIVE_PATH.
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): выбор папки и сохраняемое разрешение.
- [Infinix NOTE 30 Pro](https://www.infinixmobility.com/NOTE-30-Pro): камера 108 Мп.
- [Infinix NOTE 30 Pro specs](https://id.pre.infinixmobility.com/specs/note-30-pro): 2K30 и 1080p60 в спецификации. Спецификация штатной камеры не обещает доступ через сторонний Camera2 API.

Диагностика 0.1 от пользователя для Infinix X678B показывала rear JPEG 3968×2976, front 3264×2448, normal video до 2560×1440/30 и ошибочно выбранный default 1280×960/30. Это исходные данные пользователя, а не результаты тестирования версии 0.2.

## Дополнения для 1.0.0 (23.10.2026)

- [Wear SDK Sdk](https://developer.android.com/reference/com/google/wear/Sdk): optional manager и проверка API-функции.
- [GestureInputManager](https://developer.android.com/reference/com/google/wear/input/GestureInputManager): isActionSupported/isActionEnabled, регистрация на Window, отключение слушателя.
- [GestureEvent](https://developer.android.com/reference/com/google/wear/input/GestureEvent): ACTION_PRIMARY, тип движения и монотонное время события.
- [Foreground gesture params](https://developer.android.com/reference/com/google/wear/input/ForegroundGestureSubscriptionParams.Builder): новая API 37; реализация использует совместимый оконный API Wear SDK 36.1.

Поддержка этого API на конкретной прошивке Samsung не следует из наличия фирменного Double Pinch в штатных приложениях; проверяется в приложении во время выполнения.

## Аудио и передача 1.7

- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
- [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer)
- [Длительные задачи WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

## 1.8: preview touch gestures

- [Android drag and scale](https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale) — multi-pointer tracking and scale gestures.
- [GestureDetector.OnDoubleTapListener](https://developer.android.com/reference/android/view/GestureDetector.OnDoubleTapListener) — confirmed single taps versus double taps.
- [ScaleGestureDetector](https://developer.android.com/reference/android/view/ScaleGestureDetector) — scale factor, centroid and gesture cancellation.

Consulted 2026-10-27.
