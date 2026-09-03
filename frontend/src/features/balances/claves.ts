/**
 * Claves de React Query para balances y liquidaciones.
 *
 * Se agrupan bajo el grupo al que pertenecen porque casi todo lo que las
 * invalida es un cambio en ese grupo: un gasto nuevo, una liquidacion
 * confirmada, alguien que entra o sale. Invalidar `deGrupo(id)` alcanza a los
 * tres de golpe sin tocar los de ningun otro grupo.
 */
export const clavesDeBalances = {
  todo: ['balances'] as const,

  deGrupo: (groupId: number) => [...clavesDeBalances.todo, groupId] as const,

  /** Saldos con su desglose. */
  saldos: (groupId: number) => [...clavesDeBalances.deGrupo(groupId), 'saldos'] as const,

  /** Pagos sugeridos para saldar el grupo. Se calculan al vuelo, no se guardan. */
  sugerencias: (groupId: number) =>
    [...clavesDeBalances.deGrupo(groupId), 'sugerencias'] as const,

  /** Pagos realmente registrados. */
  historial: (groupId: number) =>
    [...clavesDeBalances.deGrupo(groupId), 'historial'] as const,
}
