# Fase 1 — Discovery

## Objetivo
**Bandeja** es una bandeja personal de contenido: el usuario guarda desde cualquier app (YouTube, Instagram,
TikTok, X, Reddit, navegador…) con el menú nativo *Compartir* de Android y lo organiza en **sus propias categorías**
para encontrarlo después.

## Usuarios
| Perfil | Necesidad | Fricción actual |
|---|---|---|
| Consumidor de redes/videos | Guardar la receta/rutina/tutorial "para después" | Favoritos dispersos en cada app, imposibles de buscar juntos |
| Estudiante / autodidacta | Reunir cursos, artículos y repos por tema | Capturas de pantalla, chats consigo mismo, notas sueltas |
| Viajero / planeador | Juntar lugares y recomendaciones | Mismo problema, repartido entre apps |

## Flujo principal (el que el MVP debe resolver perfectamente)
```
App externa → Compartir → Bandeja (hoja inferior sobre la app de origen)
   → [título/miniatura se completan solos] → elegir categoría → Guardar → vuelve a la app de origen
Después: Home → categoría (o Búsqueda) → contenido → abrir original
```
Meta de fricción: **2 toques** (categoría + Guardar) tras abrir el menú Compartir. La última categoría usada
queda preseleccionada.

## Casos de uso
1. Guardar una URL compartida (con o sin título/miniatura disponibles).
2. Guardar texto compartido que *contiene* una URL ("Mira esto https://…").
3. Rechazar con mensaje claro texto sin URL válida / esquemas no soportados.
4. Detectar duplicado (URL normalizada) → abrir existente / guardar otra vez / cancelar.
5. Guardar sin conexión (se usa lo que trae el Share + dominio; la miniatura se completa si hay red).
6. Crear / editar / eliminar categorías (nombre, icono, color).
7. Mover contenido entre categorías, editar título/notas/tags, favorito.
8. Buscar globalmente (título, URL, dominio, notas, categoría, tags) + filtros.
9. Abrir original, copiar URL, compartir de nuevo, eliminar.
10. Guardar manualmente un enlace pegado desde dentro de la app (mismo flujo que Share).

## MVP (alcance cerrado)
Dentro: las 11 pantallas del brief, Share `ACTION_SEND text/plain`, Room local, categorías personalizables,
duplicados, búsqueda con filtros, favoritos, ajustes (tema, colores dinámicos, ver introducción), tests y docs.

Fuera (a propósito, ver [ROADMAP](ROADMAP.md)): login, sincronización, backup, notas sin URL, compartir imágenes/
archivos, widgets, IA, versión web.

## Supuestos y límites honestos
- **Metadatos**: se obtienen de Open Graph / `<title>` y, para YouTube, de oEmbed + miniatura derivada del ID.
  Instagram/Facebook/TikTok suelen exigir login o bloquear bots: ahí el título puede quedar vacío y se usa
  el dominio. La app lo trata como caso normal, no como error.
- Una "nota/idea" sin URL no entra en el MVP: el modelo exige URL (el brief la define como clave de duplicados).
  Queda en el roadmap como `ContentType.NOTE`.
