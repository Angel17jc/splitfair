/**
 * Formato de importes.
 *
 * El backend es la unica autoridad sobre el dinero; aqui solo se pinta. Estas
 * funciones no suman, no restan y no reparten: en cuanto el cliente empieza a
 * calcular importes reintroduce el error de redondeo que el reparto por mayor
 * residuo existe para evitar.
 */

/**
 * Formatea un importe en la moneda del grupo.
 *
 * Siempre con dos decimales. Hace falta decirlo porque los importes llegan
 * como numero JSON: `0.00` se recibe como `0` y `33.30` como `33.3`, y
 * pintarlos tal cual da cifras que parecen rotas.
 */
export function formatearImporte(importe: number, moneda: string): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: moneda,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(importe)
}

/** Como el anterior, pero sin signo: para cuando el texto ya dice si debes o te deben. */
export function formatearImporteAbsoluto(importe: number, moneda: string): string {
  return formatearImporte(Math.abs(importe), moneda)
}

export type SignoDeSaldo = 'acreedor' | 'deudor' | 'saldado'

/**
 * Que significa un saldo neto.
 *
 * Positivo = has adelantado mas de lo que te tocaba, te deben. Negativo =
 * debes. El umbral no es cero exacto sino medio centimo: los importes llegan
 * como coma flotante, y comparar contra cero dejaria un saldo de `-0.0000001`
 * pintado en rojo como si el usuario debiera dinero.
 *
 * Es el mismo criterio que usa el algoritmo de simplificacion en el backend.
 */
export function signoDeSaldo(saldo: number): SignoDeSaldo {
  if (saldo > 0.005) return 'acreedor'
  if (saldo < -0.005) return 'deudor'
  return 'saldado'
}

/** Clases de color por signo. El color nunca va solo: siempre acompana a un texto. */
export const COLOR_DE_SALDO: Record<SignoDeSaldo, string> = {
  acreedor: 'text-emerald-700',
  deudor: 'text-red-700',
  saldado: 'text-slate-500',
}

/** Como se lee un saldo. Evita que el usuario tenga que interpretar el signo. */
export function describirSaldo(saldo: number, moneda: string): string {
  switch (signoDeSaldo(saldo)) {
    case 'acreedor':
      return `Te deben ${formatearImporteAbsoluto(saldo, moneda)}`
    case 'deudor':
      return `Debes ${formatearImporteAbsoluto(saldo, moneda)}`
    default:
      return 'Estas al dia'
  }
}
