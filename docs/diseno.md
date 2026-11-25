# Sistema de diseño del dashboard

Rediseño del 25-11-2026, a partir del mockup del PRLAC (paleta del Manual de Marca). El mockup era HTML de referencia; el dashboard sigue en React + Tailwind 4. Esta nota dice dónde está cada pieza para no repetir estilos sueltos.

## Tokens (`frontend/src/index.css`)

Tailwind 4 los lee del bloque `@theme`; no hay `tailwind.config`. Cada token da sus clases (`bg-navy`, `text-teal-oscuro`, `border-linea`…).

| Token | Valor | Uso |
|---|---|---|
| `navy` (+ `navy-hover`) | `#003349` | Títulos, botón normal, cabecera de acceso, pestaña activa del segmentado |
| `teal` (+ `-hover`, `-oscuro`, `-tinte`, `-borde`) | `#00A3AD` | Acción principal, subrayado de pestañas, foco, curvas |
| `magenta` (+ `-tinte`, `-texto`) | `#960051` | Gravedad grave, "Por asignar", lo que requiere acción |
| `naranja` (+ `-tinte`, `-texto`, `-borde`) | `#E87722` | Gravedad media, advertencias |
| `verde` (+ `-tinte`, `-texto`) | `#00843D` | Gravedad leve, éxito, banda normal |
| `purpura` | `#483698` | Reservado |
| `gob-azul`, `gob-rojo` | `#0051a8`, `#d62828` | Solo la franja del Gobierno (32 % / 68 %) |
| `fondo`, `superficie`, `superficie-sub` | `#f6f8f9`, blanco, `#eff3f5` | Página, paneles, cabeceras de tabla |
| `texto`, `apagado` | `#2c3e4c`, `#5c7080` | Texto normal y secundario |
| `linea`, `linea-oscura` | `#dce4e8`, `#c2cfd6` | Bordes |

Los nombres antiguos (`marino`, `tinta`, `prlac`) apuntan a `navy` para que nada quede sin color; en código nuevo se usan los de la tabla. Radio de paneles `rounded-panel` (6 px). Letras Roboto y Roboto Mono (Google Fonts en `index.html`); la utilidad `cifra` pone mono con números tabulares para horas, RUN, folios y cifras.

## Componentes (`frontend/src/comun/ui/`)

| Componente | Para qué |
|---|---|
| `Boton`, `claseBoton(variante, chico)` | Variantes `principal` (teal), `normal` (navy), `contorno`, `peligro` (magenta), `enlace`. `claseBoton` sirve para un `Link` con aspecto de botón |
| `Panel`, `EncabezadoVista`, `Migas`, `ListaDatos` | Bloque blanco con título y acciones; cabecera de cada pantalla con migas; lista de datos etiqueta–valor |
| `Aviso`, `BannerAccion`, `Cargando`, `Vacio` | Mensajes `info`, `advertencia`, `error`, `exito`; banner de acción; estados de carga y lista vacía |
| `Insignia` | Etiqueta con punto: `grave`, `media`, `leve`, `nueva`, `neutra`, `cerrada`, `teal` |
| `Tabla`, `TABLA`, `usePaginacion`, `Paginacion` | Tabla desplazable en celular y paginación "Mostrando X–Y de Z" |
| `Pestanas`, `Segmentado` | Pestañas accesibles (flechas del teclado) y control segmentado (también como pasos del flujo) |
| `Dialogo` | Modal sobre `<dialog>` nativo |
| `ToastProvider`, `useToast` | Aviso breve al guardar |
| `TiraMetricas`, `Anillo`, `BarraLeyenda`, `ResumenAnillo` | Indicadores del mockup |

`navegacion/Marcas.tsx` tiene la franja y las marcas (PRLAC, Chile Cuida, MDSF; MDSF se oculta bajo 760 px). `navegacion/Plantilla.tsx` es la cabecera con sesión y `navegacion/PlantillaAcceso.tsx` la caja de las páginas sin sesión (ingreso, recuperar, activar, restablecer, sesión finalizada y respuesta del contacto).

## Reglas

- Nada del mockup sin respaldo en el backend: si un dato no existe, no se muestra (por ejemplo, el "tiempo medio de respuesta" o Carabineros y CESFAM, que llegan con el nuevo escalamiento).
- Ancho de trabajo: escritorio, 760 px y 390 px. Las tablas se desplazan de lado en vez de romper la página.
- Foco visible en teal y contraste AA en textos sobre los tintes.
- Las constantes `CLASE_*` de `comun/estilos.ts` siguen para los formularios; ya usan los tokens.
