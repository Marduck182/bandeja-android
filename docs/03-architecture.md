# Fase 3 — Arquitectura

## Capas
```
ui/            Composables sin lógica de negocio. Reciben UiState, emiten eventos.
  <feature>/   XScreen.kt (UI) + XViewModel.kt + XUiState (presentación, StateFlow)
domain/        Kotlin puro (sin Android): modelos, interfaces de repositorio, parser de Share,
               normalizador/validador de URL, caso de uso SaveContent.
data/          Room (entidades, DAOs, consultas de búsqueda), DataStore, red (Open Graph), mapeos.
di/            AppContainer: composición manual.
```
Dependencias: `ui → domain ← data`. `domain` no importa nada de Android, `data` ni `ui`; `data` no importa `ui`; y
`ui` no toca Room ni la red directamente. Lo hace cumplir `ArchitectureTest` (lee los imports y falla el build si
alguien cruza una capa). Los tests de parser/normalizador corren en JVM puro.

**Casos de uso**: solo donde hay lógica real (`SaveContent`: validar → duplicado → persistir → enriquecer).
Operaciones CRUD triviales llaman directo al repositorio desde el ViewModel; un `UseCase` que solo reenvía
es ruido (ver D-004).

## Módulos
Un solo módulo Gradle `:app` con paquetes por capa. Separar en módulos con un único equipo y ~40 archivos
añade costo de build sin beneficio; los límites de paquete ya permiten extraerlos después (D-002).

## Stack
Kotlin · Compose + Material 3 · Navigation Compose · ViewModel + StateFlow · Coroutines · Room (+KSP) ·
DataStore Preferences · Coil 3 (miniaturas) · jsoup (parseo HTML de metadatos) · SplashScreen API.
Pruebas: JUnit4, kotlinx-coroutines-test, Robolectric (Room en JVM), Turbine.

## Inyección de dependencias
`AppContainer` manual creado en `BandejaApp`. El grafo tiene ~8 objetos; Hilt añadiría procesador de anotaciones
y ~1 min de build por poco valor (D-003). Los ViewModels se construyen con una `viewModelFactory` de Compose.
Migrar a Hilt es mecánico si el grafo crece.

## Modelo de datos (Room, `exportSchema = true`)
```
categories                         contents                               tags
 id PK                              id PK                                  id PK
 name  (UNIQUE NOCASE)              url                                    name (UNIQUE, minúsculas)
 icon  (emoji)                      normalizedUrl  (INDEX)
 color (clave de acento)            title
 sortOrder                          description  (notas)               content_tags
 createdAt, updatedAt               thumbnailUrl?                       contentId FK→contents CASCADE
        ▲                           source (dominio, INDEX)             tagId     FK→tags     CASCADE
        │ SET NULL                  contentType
        └────────── categoryId? ────isFavorite (INDEX)                  PK (contentId, tagId)
                                    publishedAt?, createdAt (INDEX), updatedAt
```
- `categoryId` nulo = **Sin categoría**. Al borrar una categoría su contenido **no se pierde**: pasa a Sin categoría
  (la UI lo avisa antes de confirmar).
- `normalizedUrl` **no** es único: el brief permite "guardar de nuevo si el usuario quiere".
- Etiquetas normalizadas (`trim`, minúsculas); se eliminan las huérfanas al editar.
- Preparado para sync futuro sin reescribir: PK `Long` autogenerado hoy; migrar a UUID/`remoteId` + `deletedAt` es
  una migración Room aditiva (D-006). No se añaden columnas "por si acaso" ahora.

### Búsqueda
`SearchQueryBuilder` genera SQL parametrizado: cada palabra de la consulta debe coincidir (AND) con alguno de
`title | url | source | description | categoría | tag` (LIKE con escape de `% _ \`). Filtros opcionales:
categoría (incl. "sin categoría"), favoritos, rango de fecha, fuente. Con índices en `createdAt/source/isFavorite`
y miles de filas el escaneo LIKE tarda milisegundos; FTS4/5 queda como mejora medida, no supuesta (D-005).
`Flow` + `debounce(200ms)` + `collectLatest`.

## Share Intent
```
ShareReceiverActivity (ACTION_SEND, text/plain, tema transparente, dentro de la tarea de la app de origen)
  → ShareInput(text, subject, title)          // datos crudos del Intent, sin interpretar
  → SharedContentParser.parse()               // domain, puro, probado
       ├ Parsed(url, titleHint)
       └ Invalid(Empty | NoUrl | UnsupportedScheme)
  → SaveViewModel → hoja de guardado
```
Robustez: toma la **primera** URL `http(s)` del texto (o `www.`→https); recorta puntuación final; usa
`EXTRA_SUBJECT`/`EXTRA_TITLE` o el texto restante como título sugerido; ignora `Intent` sin `EXTRA_TEXT`;
no hay `exported` más amplio que el filtro necesario; los datos del Intent **nunca** se ejecutan ni se
reenvían como `Intent` (solo se abren con `ACTION_VIEW` + esquema validado cuando el usuario lo pide).

## Offline-first
Room es la fuente de verdad; la UI solo lee `Flow`s de Room. La red es opcional y solo *enriquece*
(título/miniatura). Guardar nunca espera a la red: si el fetch no terminó, `SaveContent` persiste ya y un
`CoroutineScope` de aplicación completa los campos vacíos después. Para añadir backend: nuevas
implementaciones de las interfaces de `domain/repository` (p.ej. `SyncingContentRepository` que decora la Room)
sin tocar UI ni ViewModels.

## Seguridad
- Solo `http`/`https`; rechazo de `javascript:`, `file:`, `content:`, `intent:`, `data:`; límite de longitud
  (2048) y sin credenciales embebidas (`user:pass@`).
- Fetch de metadatos: solo https/http públicos; **bloqueo de hosts locales/privados** (loopback, link-local,
  RFC1918) para no usar el teléfono como proxy hacia la red local; timeouts 5 s; máx. 512 KB leídos; sin cookies;
  redirecciones revalidadas (máx. 3).
- `usesCleartextTraffic=false` (el fetch hace upgrade a https y cae a sin metadatos si no hay https).
- `allowBackup=false` en el MVP (se decidirá junto con la estrategia de backup real).
- Sin API keys, sin secretos, sin permisos peligrosos (solo `INTERNET`).
- Texto de usuario recortado y acotado (título 200, notas 5000, tag 30, 20 tags).
- Release con R8 activado.

## Estado de UI
Cada pantalla: `StateFlow<UiState>` (data class / sealed) con `stateIn(WhileSubscribed(5000))`; eventos hacia
el ViewModel como funciones; efectos de una sola vez (snackbar, navegar) como `Channel`/estado consumible.
