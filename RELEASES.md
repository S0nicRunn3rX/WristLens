# Version archive / Архив версий

This inventory distinguishes the **documented development history** from the **binary and source artifacts actually uploaded**. The project archive retains release notes and verification evidence for all versions listed below. Earlier Library revisions still need to be exported before their original APKs/source snapshots can be attached. No earlier binary is recreated by renaming or rebuilding 1.8.0.

В таблице разделены подтверждённая история разработки и фактически опубликованные файлы. В исходном архиве сохранились отчёты всех перечисленных версий. Для публикации оригинальных старых APK и снимков исходников ещё нужно извлечь предыдущие редакции библиотеки. Версия 0.2.1 обновляла только часы: телефон оставался 0.2.0.

| Version / Версия | Build / Сборка | Archived tests / Тесты | Uploaded artifacts / Файлы |
|---|---|---:|---|
| [0.1.0](docs/changelog/0.1.0.md) | Debug | 7 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [0.2.0](docs/changelog/0.2.0.md) | Debug | 20 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [0.2.1](docs/changelog/0.2.1.md) | Debug · watch only | 29 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.0.0](docs/changelog/1.0.0.md) | Release | 44 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.1.0](docs/changelog/1.1.0.md) | Release | 63 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.2.0](docs/changelog/1.2.0.md) | Release | 85 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.3.0](docs/changelog/1.3.0.md) | Release | 100 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.4.0](docs/changelog/1.4.0.md) | Release | 95 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.5.0](docs/changelog/1.5.0.md) | Release | 110 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.6.0](docs/changelog/1.6.0.md) | Release | 127 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.7.0](docs/changelog/1.7.0.md) | Release | 153 | Awaiting historical Library revision export / Ожидается извлечение исторической редакции |
| [1.8.0](docs/changelog/1.8.0.md) | Release | 179 | [Original APKs + source archive](https://github.com/S0nicRunn3rX/WristLens/releases/tag/v1.8.0) |

## Provenance / Происхождение

- Source: the original `WristLens_1.8.0_project.zip` retained in this project's files. Earlier notes and test evidence are bundled under `docs/` in that archive.
- The original archive contains no `.git` directory. GitHub commits made during publication are import/documentation commits, not the original development commit history.
- The existing `v1.8.0` tag was created at the initial repository README commit. Its manually attached `WristLens-1.8.0-source.zip` is the actual project snapshot; GitHub's automatic source ZIP for that original tag reflects the tag commit. The main branch contains the public export.
- The public export excludes the private signing key and uses environment-based signing configuration. Original release APK bytes are preserved.
- Historical reports may refer to an included development key; this describes their original private archive. No private signing key is included in the public export.

Исходный архив не содержит `.git`, поэтому коммиты публикации нельзя выдавать за исходную историю разработки. Старый тег `v1.8.0` указывает на первоначальный README-коммит; полный проект находится в приложенном вручную архиве исходников. Закрытый ключ исключён из публичного экспорта. Исходные APK сохранены побайтово.
