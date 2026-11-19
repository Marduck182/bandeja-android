# Decisiones técnicas

Formato: contexto → decisión → consecuencias.

| ID | Decisión | Por qué | Costo / revisión |
|---|---|---|---|
| D-001 | Nombre **Bandeja**, paquete `com.bandeja.app`, UI en español (todo en `strings.xml`) | Coincide con el concepto "bandeja personal"; recursos externalizados permiten traducir sin tocar código | Inglés pendiente |
| D-002 | Un solo módulo `:app`, capas por paquete | ~40 archivos, un equipo; evita costo de build multi-módulo | Extraer `domain`/`data` cuando haya 2+ clientes (web/KMP) |
| D-003 | DI manual (`AppContainer`) en lugar de Hilt | Grafo de ~8 objetos; menos procesamiento de anotaciones | Migrar a Hilt si crece |
| D-004 | Casos de uso solo con lógica real (`SaveContent`) | Evitar clases que solo reenvían | — |
| D-005 | Búsqueda con `LIKE` parametrizado + índices, no FTS | Miles de filas → ms. FTS añade tablas sombra y migraciones | Medir con 50k filas antes de pasar a FTS |
| D-006 | PK `Long` local; sin columnas de sync todavía | "Sin sobreingeniería"; migración aditiva posterior | Al llegar sync: `remoteId`, `deletedAt`, UUID |
| D-007 | Icono de categoría = **emoji** | Ejemplos del brief; expresivo; cero dependencias | Selector curado (no teclado libre) para evitar texto arbitrario |
| D-008 | Color de categoría = clave de acento (`"coral"`) | Retocar tonos sin migrar datos | — |
| D-009 | Guardar desde Share = hoja modal sobre actividad transparente | El usuario no sale de la app de origen; flujo de 2 toques | Algunos OEM muestran un breve parpadeo de la actividad |
| D-010 | Borrar categoría → su contenido pasa a "Sin categoría" | Nunca perder datos del usuario por una acción de organización | Confirmación explícita |
| D-011 | Duplicados por URL normalizada, **no único** en BD | El brief permite guardar de nuevo | — |
| D-012 | Normalización: minúsculas en host, sin `www.`/`m.`, sin fragmento, sin puerto por defecto, parámetros ordenados, sin tracking (`utm_*`, `fbclid`, `gclid`, `igshid`, `si`…), `youtu.be/ID`≡`youtube.com/watch?v=ID` | Misma página compartida desde dos apps debe detectarse como duplicado | Se añaden reglas por sitio con evidencia |
| D-013 | `allowBackup=false` en MVP | Backup automático de Android no está pensado para este esquema; se diseñará con la sincronización | Decisión de producto pendiente |
| D-014 | Metadatos: jsoup + oEmbed de YouTube; Coil 3 para imágenes | Parseo HTML robusto sin regex frágiles; YouTube bloquea scraping simple | jsoup ≈ 400 KB |
| D-015 | `material-icons-extended` | Se necesitan ~15 iconos fuera de `core`; R8 elimina el resto en release | APK debug más grande |
| D-016 | Pruebas de BD con Robolectric en JVM | No hay dispositivo/emulador fijo; corre en CI | Añadir `androidTest` cuando haya emulador |
| D-017 | Fuente incluida (Plus Jakarta Sans variable) en vez de Downloadable Fonts | Offline-first: no depende de Google Play Services | +176 KB |
| D-018 | Dependencias fijadas: `navigation-compose 2.9.8`, `coil 3.3.0`, lifecycle 2.10, Compose BOM 2026.03.01 | Las versiones posteriores exigen `compileSdk 37` y AGP 9.1; el entorno tiene SDK 36 y AGP 9.0.1 | Subir toolchain y dependencias juntas (ver ROADMAP) |
| D-019 | Favorito = estrella (ámbar `tertiary`), no corazón | Coherente con la sección "Favoritos" y el color de acento; el rojo del corazón chocaría con el color de error | — |
| D-020 | Eliminar enlace: deslizar a la izquierda **revela** una papelera y solo al presionarla se borra, con *Deshacer* (barra inferior); eliminar categoría: diálogo que explica qué pasa con su contenido | Deslizar sin querer no debe destruir nada; el deshacer cubre el toque accidental. Borrar una categoría es una decisión mayor y pide confirmación | Acción de accesibilidad "Eliminar" para TalkBack porque un gesto de arrastre no lo es |
| D-024 | Eliminar categoría accesible desde 3 sitios: mantener pulsada en Inicio, papelera en la barra del editor, menú ⋮ de la categoría (+ botón al final del editor) | La opción existía solo en el menú ⋮ y no se encontraba | — |
| D-021 | Capturas de UI generadas en JVM (Robolectric + Compose) | Permite revisar el diseño y detectar roturas de composición sin dispositivo; ya detectó el fallo con fuente grande | Dibuja la ventana directamente: `captureToImage` espera un frame que el looper pausado nunca entrega |
| D-022 | Cuadrícula de categorías de Inicio: tarjetas horizontales compactas; 1 columna con `fontScale > 1.25` | Con tarjetas altas "Recientes" quedaba bajo el pliegue; con fuente grande las palabras se partían | — |
| D-023 | `youtu.be` se muestra como `youtube.com` (solo en pantalla) | La fuente es lo que el usuario filtra; el host real sigue en la URL guardada y en la normalizada | — |
