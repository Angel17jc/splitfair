/**
 * Monedas que se ofrecen al crear un grupo.
 *
 * **Solo monedas de dos decimales.** No es una preferencia de la lista: el
 * backend las rechaza con 400. Todo el reparto trabaja en centimos con
 * aritmetica entera y el esquema guarda `NUMERIC(12,2)`, asi que un gasto de
 * 1000 JPY entre tres daria cuotas de 333,33 yenes, un importe que no existe.
 * Admitirlas exigiria guardar en la unidad minima de cada moneda.
 *
 * Por eso faltan CLP, JPY, KRW y PYG, que son las que mas se echan en falta
 * en esta zona. Comprobado contra la API: las catorce de aqui devuelven 200 y
 * CLP y JPY devuelven 400.
 *
 * La lista es un atajo comodo, no el limite real: el backend acepta cualquier
 * codigo ISO 4217 de dos decimales.
 */
export const MONEDAS = [
  { codigo: 'USD', nombre: 'Dolar estadounidense' },
  { codigo: 'EUR', nombre: 'Euro' },
  { codigo: 'MXN', nombre: 'Peso mexicano' },
  { codigo: 'COP', nombre: 'Peso colombiano' },
  { codigo: 'ARS', nombre: 'Peso argentino' },
  { codigo: 'PEN', nombre: 'Sol peruano' },
  { codigo: 'BOB', nombre: 'Boliviano' },
  { codigo: 'UYU', nombre: 'Peso uruguayo' },
  { codigo: 'DOP', nombre: 'Peso dominicano' },
  { codigo: 'GTQ', nombre: 'Quetzal guatemalteco' },
  { codigo: 'BRL', nombre: 'Real brasileno' },
  { codigo: 'GBP', nombre: 'Libra esterlina' },
  { codigo: 'CHF', nombre: 'Franco suizo' },
  { codigo: 'CAD', nombre: 'Dolar canadiense' },
] as const

/** Coincide con `app.default-currency` del backend. */
export const MONEDA_POR_DEFECTO = 'USD'
