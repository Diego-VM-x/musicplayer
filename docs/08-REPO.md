# 08 — Repositorio Git

## Estado

- Repositorio inicializado en `lark-clean/` (`git init`).
- `user.name` = `LarkClean Dev`, `user.email` = `dev@larkclean.local` (local al repo).
- Se hará el **commit inicial** una vez el build `assembleDebug` pase.

## Ignorados (.gitignore)

`.gradle/`, `build/`, `.kotlin/`, `.idea/`, `local.properties`, `*.apk`, `*.aab`, `build_debug.log`.

## Comandos

```bash
git status
git add .
git commit -m "feat: initial LarkClean native player (Fase 0-3)"
git log --oneline
```

## Control remoto (cuando lo decidas)

```bash
git remote add origin <URL>
git branch -M main
git push -u origin main
```

> No se añade remoto automáticamente: elige GitHub / GitLab / Codeberg y ejecuta los comandos anteriores.

## Estructura del primer commit

- `README.md`, `docs/00…08`
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`
- `core/*` (5 módulos), `feature/*` (7 módulos), `app/`
- `ANALISIS.md` está en la carpeta padre (`../ANALISIS.md`), fuera del repo — cópialo a `docs/ANALISIS-ORIGINAL.md` si quieres incluirlo.
