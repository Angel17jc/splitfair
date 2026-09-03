import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query'
import { crearGasto, listarGastos } from '../../api/expenses'
import { sesion } from '../../api/session'
import { clavesDeBalances } from '../balances/claves'
import { clavesDeGrupos } from '../groups/claves'
import { clavesDeGastos } from './claves'
import type { Expense, ExpenseFilters, ExpenseInput, Paged } from '../../types/api'

const POR_PAGINA = 20

/** Un gasto que todavia no ha confirmado el servidor. */
export type GastoEnLista = Expense & { optimista?: true }

type PaginasDeGastos = InfiniteData<Paged<Expense>, number>

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
 * Registra un gasto, lo muestra al instante y lo retira si el servidor lo
 * rechaza.
 *
 * ## Que se muestra por adelantado, y que no
 *
 * Solo lo que el cliente **ya sabe**: descripcion, importe total, fecha,
 * categoria y cuantas personas participan. Nunca cuanto le toca a cada uno.
 * Ese calculo es del backend, que reparte por mayor residuo; adelantarlo aqui
 * significaria dividir en coma flotante y ensenar cifras que pueden diferir en
 * un centimo de las que se guarden. Un numero provisional que cambia solo,
 * en una aplicacion de dinero, es peor que esperar medio segundo.
 *
 * Por lo mismo **el saldo del dashboard no se adelanta**: se actualiza cuando
 * responde el servidor. Prefiero un saldo que tarda a un saldo inventado.
 *
 * ## Que pasa si falla
 *
 * `onMutate` guarda una copia de todas las consultas de gastos del grupo y
 * `onError` la restituye. Se guardan todas y no solo la visible porque el
 * usuario puede tener varias combinaciones de filtros en cache, y dejar el
 * gasto fantasma en una de ellas lo haria reaparecer al volver a filtrar.
 */
export function useCrearGasto(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (datos: ExpenseInput) => crearGasto(groupId, datos),

    onMutate: async (datos) => {
      const clave = { queryKey: clavesDeGastos.deGrupo(groupId) }

      // Sin esto, una peticion en vuelo puede responder despues de la
      // insercion optimista y sobrescribirla, haciendo parpadear el gasto.
      await queryClient.cancelQueries(clave)

      const copia = queryClient.getQueriesData<PaginasDeGastos>(clave)

      queryClient.setQueriesData<PaginasDeGastos>(clave, (viejo) =>
        viejo ? conGastoProvisional(viejo, datos) : viejo,
      )

      return { copia }
    },

    onError: (_error, _datos, contexto) => {
      contexto?.copia.forEach(([clave, datos]) => queryClient.setQueryData(clave, datos))
    },

    onSettled: () => {
      // Se invalida pase lo que pase: tras el exito para cambiar el
      // provisional por el real con sus importes, y tras el fallo para
      // asegurar que lo que queda en pantalla viene del servidor.
      queryClient.invalidateQueries({ queryKey: clavesDeGastos.deGrupo(groupId) })
      // Cada fila del dashboard trae myBalance, y el gasto acaba de moverlo.
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
      // Y los saldos del grupo, que es lo que el gasto acaba de cambiar de
      // verdad: sin esto el panel de balances se queda con las cifras de
      // antes, que es peor que verlas cargando porque parecen correctas.
      queryClient.invalidateQueries({ queryKey: clavesDeBalances.deGrupo(groupId) })
    },
  })
}

/**
 * Inserta el gasto provisional al principio de la primera pagina.
 *
 * Va arriba porque el listado se ordena por fecha descendente y lo habitual es
 * anotar un gasto de hoy. Si la fecha fuera anterior acabara en otro sitio al
 * llegar la respuesta del servidor, y el salto es preferible a no dar senal
 * ninguna de que se ha guardado.
 */
function conGastoProvisional(paginas: PaginasDeGastos, datos: ExpenseInput): PaginasDeGastos {
  const usuario = sesion.usuario()
  const participantes = datos.splits?.map((s) => s.userId) ?? datos.splitBetweenUserIds ?? []

  const provisional: GastoEnLista = {
    // Id negativo: no puede chocar con ninguno real y hace evidente en
    // cualquier depuracion que esta fila no viene del servidor.
    id: -Date.now(),
    description: datos.description,
    amount: datos.amount,
    category: datos.category ?? 'OTROS',
    splitType: datos.splitType ?? 'EQUAL',
    expenseDate: datos.expenseDate,
    paidByName: usuario?.name ?? '',
    // Solo se conserva a cuantos afecta; los importes por persona los decide
    // el backend y aqui quedan a cero, que nunca se pinta.
    splits: participantes.map((userId) => ({ userId, userName: '', amountOwed: 0 })),
    optimista: true,
  }

  const [primera, ...resto] = paginas.pages

  return {
    ...paginas,
    pages: [
      {
        ...primera,
        content: [provisional, ...primera.content],
        totalElements: primera.totalElements + 1,
      },
      ...resto,
    ],
  }
}
