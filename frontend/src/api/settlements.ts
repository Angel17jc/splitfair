/**
 * Balances y liquidaciones.
 *
 * Las sugerencias se calculan al vuelo y no se guardan: son una propuesta de
 * como saldar el grupo en el minimo de transacciones. Registrar un pago
 * (`registrarPago`) es otra cosa, y hasta que quien cobra lo confirma no
 * altera ningun balance.
 */

import { apiClient } from './client'
import type {
  CreateSettlementInput,
  GroupBalances,
  PageParams,
  Paged,
  Settlement,
  SettlementSuggestion,
} from '../types/api'

export async function obtenerBalances(groupId: number): Promise<GroupBalances> {
  const { data } = await apiClient.get<GroupBalances>(`/groups/${groupId}/balances`)
  return data
}

/** Array pelado, no paginado: son pocas por definicion. */
export async function obtenerSugerencias(groupId: number): Promise<SettlementSuggestion[]> {
  const { data } = await apiClient.get<SettlementSuggestion[]>(
    `/groups/${groupId}/settlements`,
  )
  return data
}

/** Nace PENDING: la palabra de quien paga no basta para mover un balance. */
export async function registrarPago(
  groupId: number,
  datos: CreateSettlementInput,
): Promise<Settlement> {
  const { data } = await apiClient.post<Settlement>(`/groups/${groupId}/settlements`, datos)
  return data
}

export async function historialDePagos(
  groupId: number,
  params: PageParams = {},
): Promise<Paged<Settlement>> {
  const { data } = await apiClient.get<Paged<Settlement>>(
    `/groups/${groupId}/settlements/history`,
    { params },
  )
  return data
}

/**
 * Confirma el cobro. **Solo quien recibe el dinero.**
 *
 * Si pudiera confirmarla quien paga, la confirmacion no anadiria nada sobre
 * el registro inicial. Para el resto de miembros responde 403.
 */
export async function confirmarPago(settlementId: number): Promise<Settlement> {
  const { data } = await apiClient.post<Settlement>(`/settlements/${settlementId}/confirm`)
  return data
}

/**
 * Cancela un pago aun pendiente.
 *
 * Una liquidacion confirmada no se borra: es un hecho contable, dinero que
 * cambio de manos. Para corregir un error se registra el pago inverso. El
 * backend responde 400 si se intenta.
 */
export async function cancelarPago(settlementId: number): Promise<void> {
  await apiClient.delete(`/settlements/${settlementId}`)
}
