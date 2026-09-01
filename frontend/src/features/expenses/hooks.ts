import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { crearGasto, listarGastos } from '../../api/expenses'
import { clavesDeGrupos } from '../groups/claves'
import { clavesDeGastos } from './claves'
import type { ExpenseFilters, ExpenseInput } from '../../types/api'

const POR_PAGINA = 20

/**
 * Gastos del grupo, en paginas encadenadas.
 *
 * `getNextPageParam` se apoya en el campo `last` que ya devuelve el backend en
 * vez de comparar `page + 1 < totalPages`. Es la misma decision, pero
 * calcularla aqui duplicaria una regla que el servidor ya resolvio, y las dos
 * copias pueden divergir el dia que cambie la paginacion.
 */
export function useGastos(groupId: number, filtros: ExpenseFilters) {
  return useInfiniteQuery({
    queryKey: clavesDeGastos.lista(groupId, filtros),
    queryFn: ({ pageParam }) =>
      listarGastos(groupId, { ...filtros, page: pageParam, size: POR_PAGINA }),
    initialPageParam: 0,
    getNextPageParam: (ultima) => (ultima.last ? undefined : ultima.page + 1),
    enabled: Number.isFinite(groupId),
  })
}

/**
 * Registra un gasto e invalida las dos cosas que cambia.
 *
 * Los gastos del grupo, evidentemente. Pero tambien **los listados de
 * grupos**: cada fila del dashboard trae `myBalance`, y anotar un gasto lo
 * mueve. Sin esta segunda invalidacion el usuario vuelve atras y ve su saldo
 * anterior, que es peor que verlo cargando: parece un dato correcto.
 *
 * Se invalida `deGrupo(groupId)` y no cada combinacion de filtros, porque un
 * gasto nuevo puede entrar o no en cualquiera de ellas segun su categoria y su
 * fecha. Es el prefijo lo que hace esto posible sin enumerar nada.
 */
export function useCrearGasto(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (datos: ExpenseInput) => crearGasto(groupId, datos),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: clavesDeGastos.deGrupo(groupId) })
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
    },
  })
}
