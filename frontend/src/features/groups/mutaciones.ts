import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cambiarRol, crearGrupo, expulsarMiembro } from '../../api/groups'
import { aceptarInvitacion, crearInvitacion } from '../../api/invitations'
import { clavesDeBalances } from '../balances/claves'
import { clavesDeGastos } from '../expenses/claves'
import { clavesDeGrupos } from './claves'
import type { CreateGroupInput, GroupRole } from '../../types/api'

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

/**
 * Cambia el rol de un miembro. Solo administradores.
 *
 * Invalida el **detalle** del grupo, que es donde viven los roles, y los
 * listados, porque cada fila del dashboard muestra el rol propio. No toca los
 * balances: ascender a alguien no mueve dinero.
 *
 * El backend impide dejar al grupo sin ningun administrador y responde 400 con
 * un mensaje que dice exactamente que hacer ("promueve antes a otro miembro").
 * Ese mensaje se muestra tal cual: es mejor que cualquier texto generico que
 * pudieramos escribir aqui.
 */
export function useCambiarRol(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: GroupRole }) =>
      cambiarRol(groupId, userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.detalle(groupId) })
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })
    },
  })
}

/**
 * Saca a alguien del grupo: a un tercero (expulsar, solo administradores) o a
 * uno mismo (salir, cualquiera).
 *
 * Es la misma peticion, pero **la cache hay que tratarla al reves segun el
 * caso**, y la diferencia se vio en el navegador: al salir del grupo quedaban
 * cinco 403 en la consola. Invalidar relanza las consultas, y las del grupo ya
 * no se pueden pedir porque quien las pide acaba de dejar de ser miembro. Una
 * accion que ha ido bien no debe dejar peticiones fallidas detras.
 *
 * - **Expulsar a otro**: se invalida el detalle y los balances, porque el
 *   informe se construye a partir de la lista de miembros y quitar a alguien
 *   lo cambia entero.
 * - **Salir uno mismo**: se **retiran** esas consultas de la cache en vez de
 *   invalidarlas. No hay nada que volver a pedir; solo hay que dejar de
 *   tenerlo.
 *
 * En ambos casos se invalidan los listados: el dashboard cambia de numero de
 * miembros o pierde una fila entera.
 *
 * Las dos negativas del backend llegan como 400 con texto util —el saldo
 * pendiente con su importe, o que eres el unico administrador— y hay que
 * mostrarlas, no traducirlas a "no se pudo completar la operacion".
 */
export function useExpulsarMiembro(groupId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId }: { userId: number; salgoYo: boolean }) =>
      expulsarMiembro(groupId, userId),

    onSuccess: (_resultado, { salgoYo }) => {
      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.listas() })

      if (salgoYo) {
        queryClient.removeQueries({ queryKey: clavesDeGrupos.detalle(groupId) })
        queryClient.removeQueries({ queryKey: clavesDeBalances.deGrupo(groupId) })
        queryClient.removeQueries({ queryKey: clavesDeGastos.deGrupo(groupId) })
        return
      }

      queryClient.invalidateQueries({ queryKey: clavesDeGrupos.detalle(groupId) })
      queryClient.invalidateQueries({ queryKey: clavesDeBalances.deGrupo(groupId) })
    },
  })
}
