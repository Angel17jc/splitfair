import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listarGrupos, obtenerGrupo } from '../../api/groups'
import { clavesDeGrupos } from './claves'
import type { PageParams } from '../../types/api'

/**
 * Listado de grupos del usuario.
 *
 * `keepPreviousData` mantiene la pagina anterior mientras llega la siguiente.
 * Sin eso, cambiar de pagina vacia la lista y la sustituye por el esqueleto de
 * carga: la interfaz da un salto y el usuario pierde de vista donde estaba,
 * aunque la respuesta tarde 200 ms.
 */
export function useGrupos(params: PageParams = {}) {
  return useQuery({
    queryKey: clavesDeGrupos.lista(params),
    queryFn: () => listarGrupos(params),
    placeholderData: keepPreviousData,
  })
}

export function useGrupo(groupId: number) {
  return useQuery({
    queryKey: clavesDeGrupos.detalle(groupId),
    queryFn: () => obtenerGrupo(groupId),
    // Un id que no es un numero solo puede venir de una URL escrita a mano;
    // no merece una peticion que el backend rechazaria.
    enabled: Number.isFinite(groupId),
  })
}
