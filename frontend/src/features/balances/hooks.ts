import { useQuery } from '@tanstack/react-query'
import { obtenerBalances } from '../../api/settlements'
import { clavesDeBalances } from './claves'

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
