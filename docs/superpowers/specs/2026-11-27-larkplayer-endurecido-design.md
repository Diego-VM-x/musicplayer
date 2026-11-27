# Diseño: endurecimiento y paridad de LarkClean frente al Lark Player original

## Intención y por qué

El usuario pidió escanear todo el proyecto (`lark-clean/`) y el código original de Lark Player
(`jadx_full/sources`, `apk_extracted`, `ANALISIS.md`), y usar las skills disponibles para
corregir bugs, pulir funciones y añadir las que faltan. El objetivo no es reescribir la app:
es una réplica Kotlin/Compose ya publicada (versionCode 55, 1.1.3) con una base sólida y mucha
documentación, parte de la cual está desactualizada frente al código real.

## Hallazgos de Recon (evidencia)

- El proyecto es multi-módulo Gradle/Kotlin: `app` + `core:{model,common,database,datastore,designsystem,update}` +
  `feature:{player,library,lyrics,equalizer,backup,settings}`. ~100 archivos Kotlin.
- El original es `com.dywx.larkplayer` v2027.1.8, decompilado en `jadx_full/sources` (25k+ clases Java),
  con iJKPlayer, 13 redes de publicidad, analytics y recursos ofuscados (AndResGuard).
- `ANALISIS.md` y `docs/11-REA-ORIGINAL.md` listan funciones "faltantes", pero el código ya implementa
  varias de ellas: velocidad de reproducción, temporizador de sueño, visualizador PCM,
  `stopWithTask="false"`, wake lock, notificación y backup compatible. La documentación está desincronizada.
- El working tree tiene cambios sin commitear: `AlbumColorRepository.kt` eliminado (sin referencias en código,
  solo en docs), dependencias de tests eliminadas del version catalog, y flags de incremental Gradle añadidos.

## Objetivos

1. Corregir bugs concretos y verificables encontrados por auditoría estática de los módulos.
2. Añadir las funciones ausentes del original que sean de bajo riesgo y alto valor, sin reintroducir
   anuncios, analytics ni librerías nativas.
3. Añadir tests JVM para la lógica pura (parser LRC, cola/shuffle) que hoy no tiene ninguno.
4. Sincronizar la documentación con el código real.
5. Crear una skill reutilizable de auditoría LarkClean↔original para futuras iteraciones.

## No-objetivos / anti-objetivos

- No reintroducir iJKPlayer, anuncios, Firebase, MMKV ni permisos `AD_ID`.
- No replicar el proceso aislado `:playback` (exigiría IPC; el foreground service ya cubre el caso).
- No reescribir la UI; los cambios visuales deben ser incrementales y compatibles con la línea base.
- No tocar archivos generados (`build/`, `.gradle/`) ni los zips de WhatsApp.

## Decisiones de implementación (calls del builder)

- **Stack:** se mantiene Kotlin + Compose + Media3 + Room + DataStore + Hilt + Coil + WorkManager.
- **Enfoque:** endurecimiento incremental, no migración de arquitectura. Se priorizan cambios
  verificables con `gradlew testDebugUnitTest` y `assembleDebug`.
- **Tests:** JUnit4 puro para lógica JVM (sin Robolectric); se restauran las entradas del version catalog
  que se habían eliminado y se añaden al módulo correspondiente.
- **Docs:** `03-FUNCIONES.md` y `11-REA-ORIGINAL.md` se corrigen con el estado real; cada cambio se
  registra en `05-CAMBIOS.md`.

## Enfoques considerados

1. **Endurecimiento incremental (elegido).** Auditar módulos en paralelo, corregir bugs con TDD,
   añadir funciones pequeñas, verificar con build y tests. Bajo riesgo, alta trazabilidad.
2. **Réplica completa de funciones faltantes** (vídeo, lock screen overlay, transferencia web,
   búsqueda de letras online). Alto riesgo, requiere permisos/APIs nueva y no es verificable sin dispositivo.
   Descartado por ahora.
3. **Reescritura arquitectural** (startup por procesos, MMKV→DataStore ya hecho, etc.). Innecesario:
   la app ya está publicada y estable.

## Estado

A la espera de los informes de auditoría para fijar el alcance concreto en el plan.
