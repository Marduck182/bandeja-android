# Roadmap

Orden sugerido. Cada punto indica **dónde engancha** en la arquitectura actual, para comprobar que no obliga a reescribir.

## 0. Antes de usarla a diario
1. Pasar el checklist del teléfono real de [TESTING](TESTING.md).
2. Decidir si se inicializa un repositorio git (el proyecto aún no está bajo control de versiones).
3. Firma de release y `applicationId` definitivo si se va a distribuir.

## 1. Pulido del MVP (sin cambiar la arquitectura)
| Mejora | Dónde |
|---|---|
| **Búsqueda sin distinguir mayúsculas/acentos en letras no ASCII** (hoy `Árboles` no se encuentra buscando `árboles`; ASCII sí funciona). **Pendiente más importante.** | Columna `searchKey` normalizada (minúsculas, sin diacríticos) en `contents`, `categories` y `tags`, calculada en Kotlin al escribir; `SearchQueryBuilder` busca el término normalizado. Respeta D-005 (sin FTS) |
| Miniaturas: pasar Coil por el mismo guardia de hosts que la vista previa (hoy una página maliciosa podría hacer que se pida una imagen a una IP privada; solo GET, solo https) | `OkHttpClient` propio con `Dns` que aplique `HostGuard`, en `BandejaApp.newImageLoader` |
| `AsyncImage` en vez de `SubcomposeAsyncImage` en listas largas (scroll más barato) | `ThumbnailImage` |
| Búsqueda sin distinguir acentos (`musica` ↔ `música`) | `SearchQueryBuilder`; probablemente FTS5 con `unicode61 remove_diacritics` (D-005: medir con datos reales antes) |
| Textos en inglés | `values-en/strings.xml`; todo ya está externalizado |
| Reordenar categorías | `Category.sortOrder` ya existe; solo falta la UI |
| Compartir varios enlaces a la vez / `ACTION_SEND_MULTIPLE` | `ShareInput` + parser devuelve lista |
| *Direct Share* (categorías favoritas en el menú Compartir) | `ShortcutManager` + `ShareReceiverActivity` |
| Notas/ideas **sin URL** | `ContentType.NOTE` + `url` opcional + migración Room |
| Miniaturas en caché explícita y reintento de vista previa fallida | `fillMissingMetadata` ya es idempotente |
| Ordenar por: reciente, título, fuente | `SearchFilters.sort` |

### Miniaturas de Facebook, Instagram y TikTok
Hoy no hay forma legítima y sin clave (TikTok incluido: su oEmbed responde sin título ni imagen, probado): Meta exige un token de aplicación para oEmbed, y un token dentro del APK se
extraería en minutos (el brief prohíbe claves en el código). Camino correcto: un **backend pequeño propio** que guarde
el token, reciba la URL y devuelva `{título, miniatura}` (con caché); la app lo llama como un `MetadataFetcher` más
(la interfaz ya existe). Mientras tanto, esos enlaces muestran su tarjeta de color con la marca del sitio.

## 2. Datos del usuario
| Función | Enfoque |
|---|---|
| **Exportar / importar** (JSON o CSV) | nuevo `BackupRepository` en `domain`; formato versionado; nada que cambiar en UI existente |
| **Backup** | decidir junto con D-013 (`allowBackup=false` hoy) |
| **Estadísticas** | consultas agregadas en los DAOs; pantalla nueva |

## 3. Cuenta y sincronización
Prerrequisitos de datos (migración aditiva, D-006): `remoteId`, `deletedAt` (borrado lógico), `updatedAt` ya existe,
IDs tipo UUID.
- Login y cuenta → módulo `auth` nuevo; la UI solo ve un `SessionRepository`.
- Sincronización → `SyncingContentRepository` que **decora** `RoomContentRepository` (misma interfaz); `WorkManager` para
  sincronización automática; resolución de conflictos por `updatedAt`.
- Múltiples dispositivos, restauración y almacenamiento en la nube: consecuencia de lo anterior.
- Compartir colecciones: categoría con `ownerId` y enlace de invitación.

## 4. Plataformas e IA
- **Web / escritorio**: extraer `domain` (Kotlin puro) a un módulo KMP (D-002 ya lo permite: no importa nada de Android).
- **IA**: sugerir categoría y etiquetas al guardar (entrada: título + dominio + notas; salida: `categoryId?`, `tags`);
  se conecta en `SaveContent`, que ya es el único punto de guardado. Sin claves en el cliente: pasaría por backend.

## Deuda técnica conocida
- Dependencias fijadas por `compileSdk 36`/AGP 9.0.1 (D-018): subir AGP y SDK 37 para volver a las últimas versiones.
- `OpenGraphMetadataFetcher`: resuelve DNS dos veces (comprobación y conexión); riesgo residual de *DNS rebinding* contra
  hosts privados. Mitigación posible: conectar a la IP ya validada (requiere manejar SNI).
