import type { PageParams } from '../../types/api'

/**
 * Claves de React Query para grupos, construidas en jerarquia.
 *
 * La forma importa: React Query invalida por **prefijo**, asi que
 * `['grupos']` alcanza todo, `['grupos','lista']` solo los listados y
 * `['grupos','detalle',7]` solo ese grupo.
 *
 * Escribir las claves a mano en cada `useQuery` parece mas corto hasta que hay
 * que invalidar. Entonces aparece el fallo tipico: se invalida `['grupos']`
 * despues de cualquier cambio, con lo que se recargan de golpe listados y
 * detalles que no han cambiado; o peor, se escribe una clave con una letra
 * distinta y la invalidacion **no alcanza nada**, sin error ninguno — los
 * datos simplemente se quedan viejos en pantalla.
 *
 * Aqui las claves son funciones: si cambia la forma, cambia en un sitio y el
 * compilador senala a los demas.
 */
export const clavesDeGrupos = {
  /** Raiz. Invalidar esto recarga todo lo relacionado con grupos. */
  todo: ['grupos'] as const,

  listas: () => [...clavesDeGrupos.todo, 'lista'] as const,

  /**
   * Un listado concreto. Los parametros forman parte de la clave porque la
   * pagina 2 no es la misma respuesta que la 1: compartir clave haria que
   * cambiar de pagina mostrara los datos de la anterior como si fueran suyos.
   */
  lista: (params: PageParams) => [...clavesDeGrupos.listas(), params] as const,

  detalles: () => [...clavesDeGrupos.todo, 'detalle'] as const,

  detalle: (groupId: number) => [...clavesDeGrupos.detalles(), groupId] as const,
}
