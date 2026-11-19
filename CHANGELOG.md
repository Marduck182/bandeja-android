# Registro de cambios

Cada entrada: **fecha · cambio · motivo · archivos principales · resultado de pruebas**.
Las pruebas son `.\gradlew.bat :app:testDebugUnitTest` salvo que se indique otra cosa.

## 2026-11-19 — Versión 0.1.0 (MVP)

### Fase 1–3 · Discovery, UX y arquitectura (solo documentos)
- **Cambio**: alcance del MVP, flujos, Design System y arquitectura por capas escritos antes de programar.
- **Motivo**: el brief pide analizar antes de implementar; fija decisiones que luego cuestan caro cambiar.
- **Archivos**: `docs/01-discovery.md`, `docs/02-ux-design-system.md`, `docs/03-architecture.md`, `docs/DECISIONS.md`.
- **Pruebas**: n/a.

### Fase 4 · Implementación del MVP
- **Cambio**: proyecto Android completo: dominio (URL, Share, `SaveContent`), datos (Room, DataStore, Open Graph),
  Design System y 11 pantallas, Android Share como hoja sobre la app de origen.
- **Motivo**: resolver el flujo principal del brief (§23) con arquitectura que admita sync después (§15).
- **Archivos**: `app/src/main/java/com/bandeja/app/{domain,data,ui,di}`, `ShareReceiverActivity.kt`, `MainActivity.kt`,
  `AndroidManifest.xml`, `res/`, `gradle/libs.versions.toml`.
- **Pruebas**: compila debug y release (R8); el release pesa 2.2 MB.
- **Incidencia**: `navigation-compose 2.10`, `coil 3.6` y lifecycle 2.11 exigen `compileSdk 37`/AGP 9.1 → se fijaron
  versiones compatibles con el SDK 36 instalado (D-018).

### Fase 5 · Pruebas (131 → ver cierre)
- **Cambio**: suite JVM con Room real (Robolectric) cubriendo todo lo del brief §17.
- **Motivo**: tests de las partes críticas y de regresión.
- **Archivos**: `app/src/test/java/com/bandeja/app/**`.
- **Resultado**: las pruebas encontraron **4 defectos reales**, ya corregidos:
  1. `SearchQueryBuilder`: `trimIndent()` quitaba un espacio y el SQL quedaba `WHERE 1 = 1AND (…)`. **Toda búsqueda con
     texto habría fallado en la app.** (`SearchQueryBuilder.kt`)
  2. `SharedContentParser`: un carácter de control (retroceso) en la expresión regular hacía que `javascript:`/`file:`
     se clasificaran como "sin enlace" en vez de "esquema no soportado". (`SharedContentParser.kt`)
  3. `YouTubeLinks.videoId` aceptaba cualquier sitio con `/watch?v=…` y le habría puesto miniatura de YouTube.
     (`UrlNormalizer.kt`)
  4. Expectativas de orden/fecha incorrectas en mis propias pruebas de búsqueda (corregidas en el test, no en el código).

### Fase 6 · Revisión de UI (con capturas)
- **Cambio**: harness que renderiza las pantallas reales y guarda PNG (`UiScreenshotsTest`); correcciones:
  tarjetas de categoría compactas (Recientes quedaba bajo el pliegue), cuadrícula de 1 columna con fuente grande
  (las palabras se partían al 160 %), vista previa de la hoja visible (mismo color que la hoja), `youtu.be` → `youtube.com`,
  etiqueta de categoría redundante dentro de la propia categoría, vista previa del editor sin doble recuadro,
  botón flotante en color primario, icono del filtro de favoritos coherente.
- **Motivo**: no había dispositivo; sin ver píxeles no se puede revisar un diseño.
- **Archivos**: `ui/components/*`, `ui/home/HomeScreen.kt`, `ui/save/SaveContentSheet.kt`, `ui/editor/*`,
  `ui/search/SearchScreen.kt`, `domain/url/UrlNormalizer.kt`.
- **Pruebas**: 26 capturas regeneradas; suite verde.

### Fase 7 · Revisión de código
Ver la sección siguiente.

Revisión independiente (agente revisor de solo lectura, informe verificado contra el código antes de actuar).
**Corregido**:
1. *Timeout de red no real*: el "8 s en total" no se cumplía (la lectura bloqueante seguía). Ahora hay plazo absoluto
   comprobado en cada lectura y desconexión al cancelar; además se cancela la petición anterior al cambiar de enlace.
   (`OpenGraphMetadataFetcher.kt`, `SaveViewModel.kt`)
2. *Regex cuadrático en el parser* (180–260 ms con 10 000 caracteres, en el hilo principal, con una actividad exportada):
   cuantificador acotado y tope de entrada 10 000 → 4 096. (`SharedContentParser.kt`, `ShareReceiverActivity.kt`)
3. *Rotar con la hoja manual abierta borraba el formulario*: el reinicio pasa al toque del botón, no a la composición.
   (`BandejaNavHost.kt`)
4. *Se podían guardar miniatura/título/duplicado del enlace anterior* durante los 450 ms de validación del nuevo:
   `canSave` ahora exige que los datos pertenezcan a la URL actual y que la comprobación de duplicados haya terminado.
   (`SaveViewModel.kt`)
5. *"Abrir" desde el aviso de duplicado volvía a navegar al rotar*: el extra se consume solo en un arranque limpio.
   (`MainActivity.kt`)
6. *Hoja de Compartir vacía tras muerte del proceso*: la lectura del Intent es idempotente y siempre se hace.
   (`ShareReceiverActivity.kt`)
7. Home pedía todos los favoritos para mostrar 10 (`LIMIT`); `YouTubeLinks` reutiliza la canonicalización del normalizador;
   comentarios/documentación que no coincidían con el código.
**No corregido (documentado en ROADMAP)**: búsqueda insensible a acentos en letras no ASCII, guardia de hosts para
miniaturas (Coil), `SubcomposeAsyncImage` en listas, rebinding DNS en la vista previa.

### Cierre de versión 0.1.0
- **Pruebas**: 155 pruebas JVM, 0 fallos; lint 0 errores; debug y release (R8, ~2.2 MB) compilan.
- **Instalada y comprobada en un Samsung SM-S948B**: arranca, el Compartir real (`ACTION_SEND`) abre la actividad, la
  vista previa trae título y miniatura reales de YouTube, el guardado y el detalle funcionan, tema oscuro correcto.
- **Pendiente**: checklist de [docs/TESTING.md](docs/TESTING.md) (Compartir desde cada app real, TalkBack, colores dinámicos).

## 2026-11-19 — Eliminar categorías y enlaces (petición del usuario)
- **Cambio**: (1) eliminar categoría desde Inicio (mantener pulsada una tarjeta → Editar/Eliminar), desde el editor
  (papelera en la barra superior y botón al final) y desde el menú ⋮ que ya existía; con confirmación que indica cuántos
  elementos pasarán a "Sin categoría". (2) Enlaces: deslizar a la izquierda **revela** una papelera; solo al presionarla se
  elimina, con barra *Deshacer* (Categoría, Favoritos y Buscar). Acción de accesibilidad "Eliminar" para TalkBack.
- **Motivo**: la opción de borrar categorías existía pero escondida (solo en el menú ⋮ dentro de cada categoría) y el
  usuario no la encontraba; el borrado rápido de enlaces faltaba. Primero se implementó el deslizar-para-borrar directo y
  se cambió a "revelar y presionar" a petición del usuario, para que deslizar sin querer no borre nada.
- **Archivos**: `ui/components/{SwipeToDelete,CategoryComponents,ContentList}.kt`, `ui/home/*`, `ui/editor/*`,
  `ui/category/*`, `ui/favorites/FavoritesScreen.kt`, `ui/search/*`, `data/repo/RoomContentRepository.kt` (`restore`),
  `domain/repository/Repositories.kt`, `res/values/strings.xml`.
- **Defectos hallados al probarlo y corregidos**: tras borrar una categoría desde su pantalla parpadeaba "Este contenido
  ya no existe" antes de cerrarse; en el editor el botón de borrar quedaba fuera de vista al final de una pantalla
  larga (se añadió la papelera arriba); estado transitorio en el que Guardar se habilitaba un instante antes de empezar
  la vista previa/comprobación de duplicados de un enlace nuevo (ahora es una sola actualización atómica).
- **Pruebas**: 164 pruebas, 0 fallos.

## 2026-11-19 — Miniaturas de más sitios (petición del usuario)
- **Cambio**: la vista previa ahora usa, en orden, (1) el oEmbed oficial del sitio (YouTube, TikTok, Vimeo,
  Dailymotion, Spotify, SoundCloud), (2) las etiquetas Open Graph de la página, (3) el oEmbed que la propia página
  anuncia. Los enlaces enviados como corto (`vm.tiktok.com`) se resuelven a su dirección final. Si el sitio no da imagen
  (Facebook, Instagram) la tarjeta se pinta con el color y la marca del sitio en vez de un gris genérico.
- **Motivo**: TikTok no entrega etiquetas Open Graph a una petición normal (su página la pinta JavaScript), y Facebook
  e Instagram bloquean la lectura sin sesión.
- **Límite, dicho con claridad**: **Facebook e Instagram no tendrán miniatura real** con este enfoque. Probado: Facebook
  devuelve 400 a un navegador normal y su oEmbed no incluye imagen; el de Instagram exige token de Meta. No se imita el
  rastreador de Facebook (suplantar su identidad) ni se usa un servicio de terceros (enviaría los enlaces del usuario
  fuera). Solución real en ROADMAP: backend propio con el token.
- **Archivos**: `data/remote/{OEmbed,OpenGraphParser,OpenGraphMetadataFetcher}.kt`, `ui/components/{PlatformBrand,ContentComponents}.kt`,
  `ui/detail/DetailScreen.kt`, `ui/save/SaveContentSheet.kt`, `app/build.gradle.kts` (`org.json` solo para pruebas).
- **Pruebas**: 176, 0 fallos. **Sin verificar con enlace real de TikTok**: el de ejemplo no devolvió datos.

## 2026-11-19 — Versión 0.2.0 (código 2)
- **Cambio**: versión subida de 0.1.0 (código 1) a 0.2.0 (código 2) con las miniaturas de más sitios y el borrado de
  categorías/enlaces.
- **Motivo**: que Android la ofrezca como **actualización** de la ya instalada: mismo paquete (`com.bandeja.app`), misma
  firma (certificado de depuración, SHA-256 `91906a2b…d6df`, comprobado) y código de versión mayor. Los datos guardados
  se conservan.
- **Archivos**: `app/build.gradle.kts`.
- **Pruebas**: 176, 0 fallos.
