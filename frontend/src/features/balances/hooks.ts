import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelarPago,
  confirmarPago,
  historialDePagos,
  obtenerBalances,
  obtenerSugerencias,
  registrarPago,
} from '../../api/settlements'
import { clavesDeGrupos } from '../groups/claves'
import { clavesDeBalances } from './claves'
import type { CreateSettlementInput, Paged, Settlement } from '../../types/api'

/**
 * Saldos del grupo, con el desglose que los explica.
 *
 * Los `netBalance` de un grupo suman siempre cero: es la invariante que el
 * backend garantiza y la razon por la que aqui no se recalcula nada. Esta capa
 * pide el dato y lo pinta.
 */
export function useBalances(groupId: number) {
  return useQuery({
    queryKey: clavesDeBalances.saldos(groupId),
    queryFn: () => obtenerBalances(groupId),
    enabled: Number.isFinite(groupId),
  })
}

/**
 * Pagos sugeridos para dejar el grupo a cero.
 *
 * Se calculan al vuelo en cada peticion y no se guardan en ninguna parte: son
 * una propuesta, no un compromiso. Por eso comparten prefijo con los saldos y
 * se invalidan a la vez, porque cualquier cosa que mueva un saldo cambia la
 * propuesta.
 */
export function useSugerencias(groupId: number) {
  return useQuery({
    queryKey: clavesDeBalances.sugerencias(groupId),
    queryFn: () => obtenerSugerencias(groupId),
    enabled: Number.isFinite(groupId),
  })
}

/** Pagos realmente registrados, del mas reciente al mas antiguo. */
export function useHistorial(groupId: number) {
  return useQuery({
    queryKey: clavesDeBalances.historial(groupId),
    queryFn: () => historialDePagos(groupId, { size: 20 }),
    enabled: Number.isFinite(groupId),
  })
}

/**
 * Lo que hay que refrescar cuando una liquidacion cambia de estado.
 *
 * Son cuatro consultas alcanzadas con dos prefijos, y ninguna sobra: los
 * saldos y las sugerencias porque el pago los mueve, el historial porque es
 * donde aparece, y los listados de grupos porque cada fila del dashboard trae
 * `myBalance`. Que basten dos prefijos es justo lo que dan las claves
 * jerarquicas.
 */
function refrescarTrasLiquidar(queryClient: ReturnType<typeof useQueryClient>, groupId: number) {
  queryClient.invalidateQueries({ queryKey: clavesDeBalances.deGrupo(groupId) })
  queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
}

/**
 * Registra un pago. Nace **pendiente**: no mueve ningun saldo hasta que quien
 * cobra lo confirma.
 *
 * Es deliberado y se dice en la interfaz. Si una liquidacion pendiente contara,
 * bastaria declarar un pago inexistente para borrar una deuda, que es
 * exactamente la discusion que esta aplicacion existe para evitar.
 */
export function useRegistrarPago(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (datos: CreateSettlementInput) => registrarPago(groupId, datos),
    onSuccess: () => refrescarTrasLiquidar(queryClient, groupId),
  })
}

/**
 * Confirma el cobro. **Solo quien recibe el dinero.**
 *
 * La actualizacion optimista cambia el estado a CONFIRMED al instante, que es
 * un dato que el cliente conoce con certeza. Los saldos **no** se adelantan:
 * eso exigiria restar importes aqui, y se refrescan cuando responde el
 * servidor. Un saldo provisional que luego se corrige solo es peor que uno que
 * tarda medio segundo.
 */
export function useConfirmarPago(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (settlementId: number) => confirmarPago(settlementId),

    onMutate: async (settlementId) => {
      const clave = { queryKey: clavesDeBalances.historial(groupId) }
      await queryClient.cancelQueries(clave)
      const copia = queryClient.getQueryData<Paged<Settlement>>(clave.queryKey)

      queryClient.setQueryData<Paged<Settlement>>(clave.queryKey, (viejo) =>
        viejo
          ? {
              ...viejo,
              content: viejo.content.map((pago) =>
                pago.id === settlementId ? { ...pago, status: 'CONFIRMED' as const } : pago,
              ),
            }
          : viejo,
      )

      return { copia }
    },

    onError: (_error, _id, contexto) => {
      if (contexto?.copia) {
        queryClient.setQueryData(clavesDeBalances.historial(groupId), contexto.copia)
      }
    },

    onSettled: () => refrescarTrasLiquidar(queryClient, groupId),
  })
}

/**
 * Cancela un pago aun pendiente.
 *
 * Una liquidacion confirmada no se puede borrar: es un hecho contable, dinero
 * que cambio de manos. El backend responde 400 si se intenta, y la interfaz ni
 * siquiera ofrece el boton.
 */
export function useCancelarPago(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (settlementId: number) => cancelarPago(settlementId),
    onSuccess: () => refrescarTrasLiquidar(queryClient, groupId),
  })
}
