/** Reparto del gasto de un grupo por categoria y por mes. */

import { apiClient } from './client'
import type { GroupAnalytics } from '../types/api'

export async function obtenerAnalitica(groupId: number): Promise<GroupAnalytics> {
  const { data } = await apiClient.get<GroupAnalytics>(`/groups/${groupId}/analytics`)
  return data
}
