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

/**
 * Lee un importe escrito por el usuario.
 *
 * Acepta coma **y** punto como separador decimal. En es-ES lo natural es
 * escribir `10,50`, y un `<input type="number">` en un navegador configurado
 * en espanol lo acepta pero `valueAsNumber` puede devolver NaN segun el
 * locale; leer el texto y normalizarlo aqui evita depender de eso.
 *
 * Devuelve `null` si no es un importe valido de como mucho dos decimales. Se
 * rechazan mas decimales en vez de redondearlos: redondear en silencio un
 * `10,555` a `10,56` cambia lo que el usuario escribio sin decirselo, y en una
 * aplicacion de dinero eso no es una comodidad.
 */
export function parsearImporte(texto: string): number | null {
  const limpio = texto.trim().replace(',', '.')

  if (!/^\d+(\.\d{1,2})?$/.test(limpio)) {
    return null
  }

  const valor = Number(limpio)
  return Number.isFinite(valor) && valor > 0 ? valor : null
}

/** Fecha de hoy en el formato `YYYY-MM-DD` que espera la API. */
export function hoyISO(): string {
  const ahora = new Date()
  // No se usa toISOString(): convierte a UTC y, en husos al oeste de
  // Greenwich, por la tarde ya devuelve el dia siguiente. El gasto quedaria
  // fechado manana.
  const mes = String(ahora.getMonth() + 1).padStart(2, '0')
  const dia = String(ahora.getDate()).padStart(2, '0')
  return `${ahora.getFullYear()}-${mes}-${dia}`
}

/** `2026-08-31` -> `31 ago 2026`. Sin husos: es solo un dia, no un instante. */
export function formatearFecha(iso: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat('es-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(ano, mes - 1, dia))
}

/**
 * Formatea un instante UTC (`...Z`) como fecha y hora locales.
 *
 * Solo para los campos que **si** llevan zona: `createdAt` y `settledAt` de
 * las liquidaciones, y `expiresAt` de las invitaciones. Usarlo con el
 * `createdAt` de un grupo, que viene sin zona, desplazaria la hora tantas
 * horas como diga el huso del navegador.
 */
export function formatearInstante(iso: string): string {
  return new Intl.DateTimeFormat('es-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}
