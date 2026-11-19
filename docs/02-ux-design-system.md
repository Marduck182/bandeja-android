# Fase 2 — UX y Design System

## Navegación e información
```
Splash (SplashScreen API, sin retraso artificial: espera a leer ajustes)
 ├─ primera vez → Onboarding (3 páginas) ─┐
 └─ ya visto ─────────────────────────────┴→ Shell con barra inferior
                                               ├─ Inicio      (categorías · recientes · favoritos · sin categoría)
                                               ├─ Buscar      (búsqueda global + filtros)
                                               ├─ Favoritos
                                               └─ Ajustes
Pantallas apiladas: Categoría · Detalle · Crear/Editar categoría
Hoja "Guardar contenido": ShareReceiverActivity (Compartir) y FAB "Guardar enlace" en Inicio
```
Barra inferior de 4 destinos (alcance con el pulgar). FAB solo en Inicio. Acciones secundarias en menús
`⋮`. Todo lo importante queda en la mitad inferior de la pantalla.

### Flujo de guardado (la pantalla más importante)
Es una **hoja inferior modal** sobre una actividad transparente: el usuario nunca "sale" de la app de origen.
```
Hoja abre ──► tarjeta de vista previa (miniatura + título, esqueleto mientras carga)
          ├─► ¿duplicado? → banner: "Ya está en Comidas" [Abrir] [Guardar otra vez]
          ├─► fila de categorías (chips deslizables, última usada preseleccionada, "+ Nueva")
          ├─► [Notas y etiquetas ▾] (colapsado: no estorba el flujo rápido)
          └─► [Guardar]  → confirmación breve ✓ → se cierra solo → regresa a la app de origen
Error: sin URL válida → estado de error con motivo y [Cerrar] (nada se guarda)
```

## Pantallas
| # | Pantalla | Contenido clave | Estados |
|---|---|---|---|
| 1 | Splash | Icono + SplashScreen API | — |
| 2 | Onboarding | Concepto, cómo Compartir, "tus categorías" | Omitir / Siguiente / Empezar |
| 3 | Inicio | Cuadrícula de categorías compactas con contador (1 columna con fuente grande), "Recientes" (carrusel), "Favoritos", tarjeta "Sin categoría" | vacío (guía a Compartir), cargando |
| 4 | Categoría | Cabecera con icono/color, lista de contenido, menú editar/eliminar | vacío, cargando |
| 5 | Detalle | Miniatura grande, título, fuente, categoría, tags, notas, acciones (abrir, copiar, compartir, favorito, eliminar) | error (no existe) |
| 6 | Guardar | Ver flujo anterior | cargando, duplicado, error, éxito |
| 7/8 | Crear/Editar categoría | Vista previa viva, nombre, selector de emoji, selector de color | validación (nombre vacío/duplicado) |
| 9 | Buscar | Campo + chips de filtro (categoría, favoritos, fecha, fuente) | sin resultados, inicial |
| 10 | Favoritos | Lista de favoritos | vacío |
| 11 | Ajustes | Tema, colores dinámicos, ver introducción, acerca de | — |

## Design System

Principio: **papel cálido + tinta índigo + color por categoría.** Las categorías aportan el color; la interfaz
se mantiene tranquila para que el contenido (miniaturas) sea protagonista.

### Color (tokens M3, definidos una sola vez en `ui/theme/Color.kt`)
| Rol | Claro | Oscuro |
|---|---|---|
| primary | `#3B4CCA` | `#BCC3FF` |
| primaryContainer | `#E0E3FF` | `#2434AE` |
| tertiary (favoritos/ámbar) | `#8A5100` | `#FFB964` |
| background / surface | `#FBF8F4` (papel cálido) | `#131316` |
| surfaceContainerLow / Container / High | `#F6F2EE` / `#F0ECE8` / `#EAE6E2` | `#1B1B1F` / `#1F1F23` / `#2A2A2D` |
| error | `#BA1A1A` | `#FFB4AB` |

Los demás roles siguen la escala tonal M3. **Colores dinámicos (Android 12+)**: opcionales en Ajustes, apagados
por defecto para conservar la identidad de marca.

**Acentos de categoría** (10): Coral, Ámbar, Lima, Esmeralda, Turquesa, Cielo, Índigo, Violeta, Rosa, Pizarra.
Cada uno define `container` y `onContainer` para claro y oscuro (contraste ≥ 4.5:1 verificado por test).
El acento se guarda por clave (`"coral"`), no como número, para poder reajustar tonos sin migrar datos.

### Tipografía
**Plus Jakarta Sans** (variable, incluida en el APK, licencia OFL). Escala M3 con pesos ajustados:
display/headline 700, title 600, label 600, body 400. Texto del usuario nunca por debajo de 14 sp
(12 sp solo para metadatos secundarios).

### Iconografía
- Interfaz: Material Icons (relleno para activo, contorno para inactivo).
- **Categorías: emoji** elegido de una cuadrícula curada (los ejemplos del brief ya son emoji; es
  expresivo, sin dependencias y sin pintar 200 vectores).

### Espaciado, forma y elevación
- Cuadrícula de **4 dp**. Tokens: `xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32`. Margen de pantalla 16 dp.
- Formas: `small 8 · medium 12 · large 16 · extraLarge 28` (hojas y diálogos). Tarjetas: 16 dp.
- Elevación **tonal** (superficies `surfaceContainer*`), sin sombras salvo FAB y hojas.

### Componentes reutilizables (`ui/components`)
`CategoryCard`, `CategoryBadge`, `ContentCard` (lista) y `ContentTile` (carrusel), `ThumbnailImage`
(Coil + marcador de posición coherente por tipo), `EmptyState`, `ErrorState`, `ShimmerBox`/esqueletos,
`SectionHeader`, `TagChip`, `ConfirmDialog`, `EmojiPicker`, `AccentPicker`, `BandejaTopBar`.
Regla: una pantalla **compone** componentes; no define estilos propios.

### Estados
Cargando → esqueleto (no spinner a pantalla completa). Vacío → emoji grande + frase + acción. Error → mensaje en
lenguaje llano + acción de cierre. Éxito → marca ✓ animada al guardar y `Snackbar` al copiar. Acciones destructivas
con confirmación que dice qué pasará (borrar categoría: "sus 12 elementos pasarán a Sin categoría") o, si es un enlace
(deslizar a la izquierda → aparece la papelera → presionarla), con *Deshacer*.

### Movimiento
150–300 ms, curvas M3 (*emphasized decelerate* al entrar, *accelerate* al salir). Solo animaciones que
comunican: aparición de hoja, favorito (rebote de la estrella), colapsables, `animateItem` en listas.

### Accesibilidad
- Áreas táctiles ≥ 48 dp; `contentDescription` en todo icono interactivo; `Role` y `semantics` en tarjetas
  clicables; el estado "favorito" se anuncia ("Quitar de favoritos").
- Contraste AA en ambos temas; el color **nunca** es el único portador de información (siempre hay emoji/texto).
- Escala de fuente del sistema respetada (layouts sin alturas fijas en texto). Verificado con captura al 160 %: la
  cuadrícula de Inicio pasa a una columna para no partir palabras.
- Una mano: acciones primarias abajo (barra, FAB, botón Guardar de la hoja).
