import type { ExpenseFilters } from '../../types/api'

/**
 * Claves de React Query para gastos, con la misma jerarquia que las de grupos.
 *
 * Los filtros forman parte de la clave: cada combinacion es una respuesta
 * distinta del servidor. Dejarlos fuera haria que al filtrar por COMIDA se
 * mostrara la lista sin filtrar que ya estaba en cache, y el usuario veria que
 * su filtro "no hace nada".
 */
export const clavesDeGastos = {
  todo: ['gastos'] as const,

  /** Todo lo de un grupo. Es el prefijo que se invalida al crear un gasto. */
  deGrupo: (groupId: number) => [...clavesDeGastos.todo, groupId] as const,

  lista: (groupId: number, filtros: ExpenseFilters) =>
    [...clavesDeGastos.deGrupo(groupId), 'lista', filtros] as const,
}
