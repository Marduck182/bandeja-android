# Pruebas

Todo corre en la JVM, sin dispositivo: `.\gradlew.bat :app:testDebugUnitTest` (≈40 s). Room se prueba con la base de
datos real en memoria (Robolectric); no hay mocks de repositorios en las pruebas de datos.

## Cobertura frente al brief (§17)

| Brief | Qué se verifica | Clases |
|---|---|---|
| **Share: recibir URL** | URL sola (YouTube/Facebook/Instagram), con título (Reddit/Chrome), con comentario (X/WhatsApp), `www.` sin esquema, puntuación final, paréntesis de Wikipedia, URL con acentos, primera URL de varias | `SharedContentParserTest`, `ShareIntentTest` |
| **Share: recibir texto** | texto con URL incrustada; texto sin URL → error `NO_URL` | ídem |
| **Share: inválido / vacío** | `null`, vacío, espacios; `javascript:`, `file:`, `content:`, `intent:`, `data:`, `ftp:`, `mailto:`; credenciales en la URL; host sin punto; URL de > 2048; título de > 200 | ídem, `UrlValidatorTest` |
| **Share: distintas apps** | Intents con distintas combinaciones de `EXTRA_TEXT/SUBJECT/TITLE`, acción distinta de `SEND`, extra de tipo erróneo, texto de 50 000 caracteres | `ShareIntentTest` |
| **Categorías: crear/editar/eliminar** | campos guardados, orden, nombre recortado, vacío/largo/duplicado (sin distinguir mayúsculas ni acentos), conservar el propio nombre al editar, `updatedAt` | `CategoryRepositoryTest` |
| **Categorías: asignar/mover** | asignar, mover, mover a "sin categoría", categoría inexistente → sin categoría (no falla la clave foránea) | ídem |
| **Categorías: borrar** | el contenido **sobrevive** y pasa a "Sin categoría"; contadores por categoría | ídem |
| **Contenido: guardar** | todos los campos, normalización, título (usuario > página > dominio), sin metadatos (offline), miniatura/tipo de YouTube sin red, textos acotados | `ContentRepositoryTest`, `SaveContentTest` |
| **Contenido: editar/eliminar/favoritos** | editar título/notas/categoría/etiquetas, limpiar etiquetas huérfanas, alternar favorito, borrar en cascada | `ContentRepositoryTest` |
| **Contenido: duplicados** | misma página desde otra app (www, `utm_*`, `fbclid`, fragmento, `youtu.be`, `m.youtube`, Shorts, `twitter.com`→`x.com`); aviso, bloqueo, "guardar otra vez" | `UrlNormalizerTest`, `SaveContentTest`, `SaveViewModelTest` |
| **Búsqueda** | título, URL, dominio, notas, categoría, etiquetas; sin distinguir mayúsculas; todas las palabras deben coincidir; `%` y `_` literales; texto hostil; filtros de favoritos/categoría/sin categoría/fecha/fuente y combinados | `SearchRepositoryTest`, `SearchQueryBuilderTest` |
| **Búsqueda a escala** | 5 000 filas en < 2 s (en la práctica, milisegundos) | `SearchRepositoryTest` |
| **Base de datos: persistencia** | escribir, cerrar, reabrir el archivo y leer; categorías iniciales al crearse la BD y editables después | `PersistenceTest` |

Además:
- **Flujo completo Share → Guardar** sin UI (`SaveViewModelTest`, 15 pruebas): parseo, vista previa, categoría recordada,
  guardado sin esperar a la red (la vista previa se completa después), título del usuario que no se pisa, duplicados,
  guardado manual con validación, cambio de enlace descarta la vista previa, crear categoría desde la hoja, categoría
  borrada mientras estaba elegida.
- **Red y seguridad**: `OpenGraphParserTest` (fixtures HTML, imágenes `data:`/`javascript:` descartadas),
  `HostGuardTest` (loopback, RFC1918, link-local, CGNAT, IPv6 ULA bloqueados).
- **Accesibilidad**: `ThemeContrastTest` exige contraste WCAG AA (4.5:1 texto, 3:1 gráficos) en ambos temas y en los
  10 acentos de categoría.
- **Humo de UI**: `UiScreenshotsTest` navega por la app real y guarda 26 capturas en `app/build/screenshots/`
  (onboarding, inicio vacío/lleno/oscuro/fuente 160 %, categoría, detalle, búsqueda, favoritos, ajustes, editor y los
  estados de la hoja de guardado). Si una pantalla no compone o la navegación falla, la prueba falla.

- **Eliminar** (`CategoryDeletionUiTest`, `SwipeDeleteUiTest`, `ContentRepositoryTest`): categoría desde Inicio (mantener pulsado), editor (papelera y botón), menú ⋮; cancelar no borra; el contenido de una categoría borrada sobrevive; deslizar solo revela la papelera, presionarla borra, *Deshacer* devuelve el enlace con notas, etiquetas, favorito y categoría.
- **Miniaturas** (`OEmbedTest`): endpoint oficial por sitio (con la URL codificada, enlaces cortos de TikTok, sin falsos positivos como `nottiktok.com`), lectura de `title`/`thumbnail_url`/`type`, descarte de miniaturas inseguras, respuesta de TikTok sin datos, oEmbed anunciado por la página, y la tarjeta de color por sitio.
- **Videos cortos** (`ShortVideoLinksTest`): lo que comparte cada plataforma para un video corto: YouTube Shorts con `si`, TikTok con comentario y enlace corto `vm.tiktok.com`, Instagram `reel`/`reels` con `igsh`, Facebook Reel con `mibextid`/`s`/`fs`, `fb.watch` y enlaces *share*; el mismo video desde web, app y móvil es un solo elemento.
- **Arquitectura**: `ArchitectureTest` falla si `domain` importa Android/`data`/`ui`, si `data` importa `ui`, o si `ui` toca Room/red.

## Lo que estas pruebas NO demuestran
Hay que hacerlo una vez en un teléfono real (no había dispositivo conectado al desarrollar):

- [ ] Que **Bandeja aparece** en el menú Compartir de YouTube, Chrome, Instagram, Facebook, TikTok, X y Reddit, y que cada
      una entrega lo que el parser espera (el parser cubre los formatos conocidos; las apps pueden cambiar).
- [ ] La hoja sobre la app de origen: animación, teclado (`adjustResize` + `imePadding`), y que al guardar **vuelve** a la app de origen.
- [ ] Miniaturas de Vimeo, Dailymotion, Spotify y SoundCloud con enlaces reales (no se encontraron enlaces de prueba vigentes). **TikTok, Instagram y Facebook ya se probaron: no entregan miniatura** (ver D-026).
- [ ] Vista previa real: títulos/miniaturas de YouTube (oEmbed), Chrome y sitios de noticias; comportamiento con
      Instagram/Facebook/TikTok (suelen no dar metadatos: el título cae al dominio).
- [ ] "Abrir" desde el aviso de duplicado (lanza `MainActivity` en su propia tarea).
- [ ] Rendimiento de arranque en frío con la hoja de Compartir.
- [ ] TalkBack: orden de lectura y etiquetas de las tarjetas, chips y la hoja.
- [ ] Android 12+ con colores dinámicos activados.

Probar Compartir sin otra app: ver el comando `adb shell am start` del [README](../README.md).

## Limitaciones conocidas de la suite
- Las capturas usan los gráficos de Robolectric: sirven para revisar composición, jerarquía y espaciado, no el aspecto
  exacto en cada fabricante (sombras, emoji, animaciones).
- No hay `androidTest` (instrumentadas): se añadirán cuando haya emulador o dispositivo fijo (D-016).
- La red real (`OpenGraphMetadataFetcher`) no se prueba en la suite para que sea determinista; su lógica de decisión
  (parseo, guardia de hosts) sí.
