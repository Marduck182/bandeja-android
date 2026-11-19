# Bandeja

Tu bandeja personal de contenido. Guarda cualquier enlace que encuentres (YouTube, Instagram, TikTok, X, Reddit,
el navegador…) con **Compartir → Bandeja** y organízalo en tus propias categorías.

```
Estoy viendo algo interesante → Compartir → Bandeja → elijo categoría → Guardar
Después: Inicio → categoría (o Buscar) → abrir el original
```

## Qué incluye (MVP)
- **Compartir de Android**: recibe `ACTION_SEND` de texto; extrae la URL aunque venga con comentario, título o puntuación.
  Se abre como hoja sobre la app de origen y vuelve sola a ella.
- **Categorías propias**: nombre, icono (emoji) y color; crear, editar, eliminar (su contenido pasa a "Sin categoría").
- **Guardado rápido**: título y miniatura se completan solos (sin bloquear el guardado ni sin conexión), categoría
  recordada, notas y etiquetas opcionales, favorito.
- **Duplicados**: por URL normalizada (misma página desde otra app = mismo contenido): abrir existente / guardar otra vez.
- **Búsqueda global** por título, URL, dominio, notas, categoría y etiquetas, con filtros (categoría, favoritos, fecha, fuente).
- **Offline-first**: todo en Room; la red solo enriquece.
- Tema claro/oscuro/sistema y colores dinámicos opcionales.

## Requisitos
- JDK 17 o superior (probado con 21), Android SDK con plataforma **36** (`compileSdk 36`), Android 8.0+ (`minSdk 26`).
- `local.properties` con `sdk.dir=...` (Android Studio lo crea; ya existe en este equipo).
- Gradle se descarga solo con el wrapper (`gradlew`).

## Ejecutar
Desde Android Studio: abrir la carpeta `bandeja/` y ejecutar la configuración `app`.

Desde terminal (PowerShell, en `bandeja/`):
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\gradlew.bat :app:assembleDebug          # APK en app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:installDebug           # instala en el teléfono conectado (USB o adb inalámbrico)
.\gradlew.bat :app:assembleRelease        # APK minificado (R8), ~2 MB, sin firmar
```
Probar Compartir sin otra app, con un teléfono conectado:
```powershell
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://youtu.be/dQw4w9WgXcQ" com.bandeja.app/.ShareReceiverActivity
```

## Pruebas
```powershell
.\gradlew.bat :app:testDebugUnitTest      # 176 pruebas, ~1 min, sin dispositivo
```
Genera además capturas de las pantallas reales en `app\build\screenshots\`. Detalle en [docs/TESTING.md](docs/TESTING.md).

## Estructura
```
app/src/main/java/com/bandeja/app/
  domain/   modelos, interfaces de repositorio, URL (validar/normalizar), parser de Share, SaveContent   (Kotlin puro)
  data/     Room (entidades, DAOs, búsqueda), DataStore, red (Open Graph, guardia de hosts), repositorios
  ui/       theme/ (Design System) · components/ · navigation/ · una carpeta por pantalla (Screen + ViewModel)
  di/       AppContainer (composición manual)
docs/       01 discovery · 02 UX y Design System · 03 arquitectura · DECISIONS · TESTING · ROADMAP
CHANGELOG.md
```

## Documentación
| Documento | Contenido |
|---|---|
| [01-discovery](docs/01-discovery.md) | objetivo, usuarios, flujo principal, casos de uso, alcance del MVP |
| [02-ux-design-system](docs/02-ux-design-system.md) | navegación, flujos, pantallas, Design System, accesibilidad |
| [03-architecture](docs/03-architecture.md) | capas, modelo de datos, búsqueda, Share, offline-first, seguridad |
| [DECISIONS](docs/DECISIONS.md) | decisiones técnicas y su motivo |
| [TESTING](docs/TESTING.md) | qué se prueba, cómo, y qué falta probar en un teléfono real |
| [ROADMAP](docs/ROADMAP.md) | lo que sigue, y dónde engancha en la arquitectura |
| [CHANGELOG](CHANGELOG.md) | cambios importantes: fecha, motivo, archivos, resultado de pruebas |
