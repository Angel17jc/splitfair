import { useMutation, useQueryClient } from '@tanstack/react-query'
import { crearGrupo } from '../../api/groups'
import { aceptarInvitacion, crearInvitacion } from '../../api/invitations'
import { clavesDeGrupos } from './claves'
import type { CreateGroupInput } from '../../types/api'

/**
 * Crea un grupo e invalida **solo los listados**.
 *
 * `clavesDeGrupos.listas()` y no `todo`: un grupo nuevo cambia la lista, pero
 * no altera el detalle de ningun grupo ya cargado. Invalidar la raiz obligaria
 * a recargar cada detalle que el usuario tenga en cache, peticiones que no
 * responden a ningun cambio. Es justo lo que las claves jerarquicas permiten
 * afinar.
 */
export function useCrearGrupo() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (datos: CreateGroupInput) => crearGrupo(datos),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
    },
  })
}

/**
 * Genera un link de invitacion.
 *
 * No toca la cache: una invitacion no cambia el grupo ni sus miembros hasta
 * que alguien la acepta.
 */
export function useCrearInvitacion(groupId: number) {
  return useMutation({
    mutationFn: (email?: string) => crearInvitacion(groupId, email),
  })
}

/**
 * Acepta una invitacion.
 *
 * Invalida los listados —aparece un grupo nuevo— y tambien el detalle del
 * grupo al que se entra, porque su lista de miembros acaba de cambiar y
 * cualquier copia en cache se ha quedado sin el recien llegado.
 */
export function useAceptarInvitacion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (token: string) => aceptarInvitacion(token),
    onSuccess: (grupo) => {
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.detalle(grupo.id) })
    },
  })
}
