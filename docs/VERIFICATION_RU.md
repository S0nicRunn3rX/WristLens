> Исторический документ. Актуальные изменения: [RELEASE_1.8_RU.md](RELEASE_1.8_RU.md), использование: [INSTALL_RU.md](INSTALL_RU.md).

> Исторический отчёт 0.2.0. Актуальный релиз: [RELEASE_1.0_RU.md](RELEASE_1.0_RU.md).

# Проверка сборки WristLens 0.2.0

Дата: 22 октября 2026. Сборка из исходников этого архива.

## Выполнено

- Gradle 8.13, Android Gradle Plugin 8.13.2, JDK 17; SDK 36 / Build Tools 35.0.0.
- Чистая сборка `:phone:assembleDebug :wear:assembleDebug` завершена успешно.
- `:core:test`: 20 тестов, 0 ошибок, 0 пропусков (ProtocolTest: 7; UpgradeTest: 13).
- `:phone:lintDebug`, `:wear:lintDebug`: `No issues found.`
- `apksigner verify --verbose --print-certs`: оба APK прошли проверку подписи.
- `aapt2 dump badging`: applicationId обоих APK `com.daniil.wristlens`, versionName `0.2.0`, versionCode телефона 200, часов 201.
- Оба APK подписаны прежним development-ключом из версии 0.1.0. SHA-256 сертификата: `1c8dee161a0c28fca4629da32fa8342006840918b2459a615ead005337156a73`.

| Файл | Размер, байт | SHA-256 APK |
|---|---:|---|
| WristLens-phone-0.2.0-debug.apk | 2656752 | `f580add4014f6c6e3d43e94c3b9596b74b98e74a8c2835d8ac59fce9fdd6d391` |
| WristLens-wear-0.2.0-debug.apk | 6317426 | `e872019e58e9dbcea205b9c95572eb8a5a315613d16c334c311c2d7866c36a4d` |

Полные выводы, XML-результаты тестов, данные пакетов и хеши находятся в `docs/verification/`. Исторический отчёт 0.1 перенесён в `docs/previous-0.1/`.

## Что не проверено

Физические Infinix X678B и Galaxy Watch Ultra не подключены. Не выполнены инструментальные тесты камеры, фото/видео на реальном сенсоре, удалённого запуска на конкретной оболочке, EGL, фоновых сервисов, MediaStore/SAF, потока и передачи по Bluetooth/Wi-Fi. Производительность, нагрев, расход батареи, 108 Мп и 60 FPS не подтверждены этой сборкой.

Автотесты core проверяют логику и формат передачи, а не работу Android-драйверов и Google Play services. Отсутствие ошибок Lint и успешная компиляция не являются физической приёмкой. Сценарии следующей проверки перечислены в TEST_PLAN_RU.md.
