/** Grupos y sus miembros. */

import { apiClient } from './client'
import type {
  CreateGroupInput,
  Group,
  GroupRole,
  GroupSummary,
  PageParams,
  Paged,
  UpdateGroupInput,
} from '../types/api'

export async function listarGrupos(params: PageParams = {}): Promise<Paged<GroupSummary>> {
  const { data } = await apiClient.get<Paged<GroupSummary>>('/groups', { params })
  return data
}

export async function obtenerGrupo(groupId: number): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/groups/${groupId}`)
  return data
}

export async function crearGrupo(datos: CreateGroupInput): Promise<Group> {
  const { data } = await apiClient.post<Group>('/groups', datos)
  return data
}

/** Solo ADMIN. La moneda no esta: se fija al crear y no cambia. */
export async function actualizarGrupo(
  groupId: number,
  datos: UpdateGroupInput,
): Promise<Group> {
  const { data } = await apiClient.patch<Group>(`/groups/${groupId}`, datos)
  return data
}

export async function anadirMiembro(groupId: number, userId: number): Promise<Group> {
  const { data } = await apiClient.post<Group>(`/groups/${groupId}/members/${userId}`)
  return data
}

/**
 * Saca a alguien del grupo, o te saca a ti.
 *
 * Falla con 400 si su saldo no es cero. No es una molestia burocratica: los
 * balances se construyen a partir de la lista de miembros, asi que si alguien
 * con deuda deja de serlo, sus gastos siguen en la base pero desaparecen del
 * informe y los balances de los que quedan dejan de sumar cero.
 */
export async function expulsarMiembro(groupId: number, userId: number): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/members/${userId}`)
}

/** Falla con 400 si dejaria al grupo sin ningun administrador. */
export async function cambiarRol(
  groupId: number,
  userId: number,
  role: GroupRole,
): Promise<Group> {
  const { data } = await apiClient.patch<Group>(
    `/groups/${groupId}/members/${userId}/role`,
    { role },
  )
  return data
}
