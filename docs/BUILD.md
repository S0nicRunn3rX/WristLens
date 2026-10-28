# Build and signing / Сборка и подпись

Use JDK 17 and Android SDK 36. The wrapper pins Gradle 8.13; AGP is 8.13.2.

```sh
chmod +x gradlew
./gradlew :phone:assembleDebug :wear:assembleDebug
./gradlew :core:test :phone:testDebugUnitTest :wear:testDebugUnitTest :phone:lintDebug :wear:lintDebug
```

Without custom configuration, both debug modules use the standard local Android debug keystore. For signed release builds, set these environment variables locally, outside Git:

| Variable | Meaning / Назначение |
|---|---|
| `WRISTLENS_KEYSTORE` | Absolute path to your private keystore / Путь к закрытому хранилищу |
| `WRISTLENS_STORE_PASSWORD` | Store password / Пароль хранилища |
| `WRISTLENS_KEY_ALIAS` | Signing key alias / Псевдоним ключа |
| `WRISTLENS_KEY_PASSWORD` | Key password / Пароль ключа |

```sh
./gradlew :phone:assembleRelease :wear:assembleRelease
```

With no keystore configured, release outputs are unsigned. Sign both applications with the same certificate: the Wear OS data layer requires matching package/signature identities. Both apps use `com.daniil.wristlens`.

The public export removes the private development keystore and hardcoded signing password from the original archive. The application code and version remain 1.8.0; published APKs are the original binaries. Historical reports mentioning an included key describe their original private archives, not this public tree.

A newly generated key cannot update an already-installed APK with another certificate. Keep your key private and backed up. The retained historical signing identity was used for personal development and is not presented as a protected production signing setup.

Русский: без переменных окружения debug использует стандартный локальный ключ Android, release остаётся неподписанным. Для release задайте четыре переменные выше. Оба APK нужно подписать одним сертификатом. Публичный экспорт исключает закрытый ключ и пароль; код приложения и версия не менялись. Старые отчёты об «included development key» относятся к исходным личным архивам. APK релиза сохранены без изменений. С другим сертификатом обновление поверх прежней установки невозможно.
