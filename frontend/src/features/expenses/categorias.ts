import type { Category } from '../../types/api'

/**
 * Etiquetas de las categorias.
 *
 * El backend guarda el enum en mayusculas; aqui se traduce a algo legible.
 * El mapa es `Record<Category, string>`, asi que **anadir un valor al enum sin
 * etiquetarlo rompe la compilacion** en vez de pintar `undefined` en un
 * desplegable.
 */
export const ETIQUETA_DE_CATEGORIA: Record<Category, string> = {
  COMIDA: 'Comida',
  TRANSPORTE: 'Transporte',
  ALOJAMIENTO: 'Alojamiento',
  OCIO: 'Ocio',
  SERVICIOS: 'Servicios',
  COMPRAS: 'Compras',
  SALUD: 'Salud',
  OTROS: 'Otros',
}

/** Color de fondo de la etiqueta. Decorativo: el texto ya dice la categoria. */
export const COLOR_DE_CATEGORIA: Record<Category, string> = {
  COMIDA: 'bg-amber-100 text-amber-800',
  TRANSPORTE: 'bg-sky-100 text-sky-800',
  ALOJAMIENTO: 'bg-violet-100 text-violet-800',
  OCIO: 'bg-pink-100 text-pink-800',
  SERVICIOS: 'bg-teal-100 text-teal-800',
  COMPRAS: 'bg-indigo-100 text-indigo-800',
  SALUD: 'bg-rose-100 text-rose-800',
  OTROS: 'bg-slate-100 text-slate-700',
}
