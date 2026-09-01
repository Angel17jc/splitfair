/**
 * Invitaciones por link.
 *
 * Un solo uso y siete dias de vida. Para invitar a tres personas se generan
 * tres links: uno reutilizable reenviado al chat equivocado dejaria entrar a
 * cualquiera indefinidamente.
 */

import { apiClient } from './client'
import type { Group, Invitation, InvitationPreview } from '../types/api'

/**
 * Genera un link. Si se indica `email`, solo esa direccion podra aceptarlo.
 */
export async function crearInvitacion(
  groupId: number,
  email?: string,
): Promise<Invitation> {
  const { data } = await apiClient.post<Invitation>(`/groups/${groupId}/invitations`, {
    email,
  })
  return data
}

/**
 * Vista previa publica: quien abre el link puede no tener cuenta todavia.
 *
 * Una invitacion caducada o ya usada responde **200 con `valid: false`**, no
 * 404. Hay que mirar el campo, no el codigo: tratar el 200 como exito sin
 * comprobarlo llevaria al usuario a un formulario que va a fallar al enviarse.
 */
export async function verInvitacion(token: string): Promise<InvitationPreview> {
  const { data } = await apiClient.get<InvitationPreview>(`/invitations/${token}`)
  return data
}

/** Acepta con la sesion iniciada. Quien no tiene cuenta usa el registro con `invitationToken`. */
export async function aceptarInvitacion(token: string): Promise<Group> {
  const { data } = await apiClient.post<Group>(`/invitations/${token}/accept`)
  return data
}
