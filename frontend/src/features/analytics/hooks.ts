import { useQuery } from '@tanstack/react-query'
import { obtenerAnalitica } from '../../api/analytics'
import { clavesDeBalances } from '../balances/claves'

/**
 * Analitica del grupo.
 *
 * Cuelga del mismo prefijo que los balances a proposito: lo que la cambia es
 * exactamente lo mismo que cambia un saldo —un gasto nuevo, uno borrado— asi
 * que ya se invalida sola con las mutaciones que existen. Una clave propia
 * obligaria a acordarse de anadirla en cada sitio.
 */
export function useAnalitica(groupId: number) {
  return useQuery({
    queryKey: [...clavesDeBalances.deGrupo(groupId), 'analitica'],
    queryFn: () => obtenerAnalitica(groupId),
    enabled: Number.isFinite(groupId),
  })
}
