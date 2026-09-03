import type { SplitType } from '../../types/api'

/**
 * Comprobacion del cuadre de un reparto personalizado.
 *
 * ## Por que en enteros y no en coma flotante
 *
 * La tentacion es sumar los importes con `+` y comparar con el total. Pero en
 * coma flotante hay repartos validos cuya suma no da el total: `8,20 + 0,10 +
 * 1,70` deberia dar exactamente `10,00` y da `9.999999999999998`. El usuario
 * veria un error que no existe y sin forma de corregirlo, porque los numeros
 * que ha escrito **son** correctos.
 *
 * No es raro ni rebuscado: basta con que alguno de los sumandos no sea
 * representable en binario, que le pasa a la mayoria de los importes con
 * decimales.
 *
 * Aqui se convierte cada valor a su unidad minima entera —centimos para los
 * importes, centesimas de punto para los porcentajes— y se compara con `===`.
 * Es la misma decision que toma `MoneySplitter` en el backend, y por eso las
 * dos comprobaciones coinciden siempre.
 *
 * ## Lo que este modulo NO hace
 *
 * No reparte. Comprueba que lo que ha escrito el usuario suma lo que debe, y
 * nada mas. Quien decide cuanto le toca a cada uno es el backend, con reparto
 * por mayor residuo: calcularlo aqui para "adelantar" el resultado
 * reintroduciria el error de redondeo que ese algoritmo existe para evitar, y
 * ademas podria discrepar del importe que acabe guardado.
 */

/** Convierte texto a centimos enteros. `null` si no es un numero valido. */
export function aCentimos(texto: string): number | null {
  const limpio = texto.trim().replace(',', '.')
  if (limpio === '') return null
  if (!/^\d+(\.\d{1,2})?$/.test(limpio)) return null

  const [entera, decimal = ''] = limpio.split('.')
  return Number(entera) * 100 + Number(decimal.padEnd(2, '0'))
}

/** Igual, pero para porcentajes: centesimas de punto (`33.33` -> `3333`). */
export const aCentesimas = aCentimos

export interface Cuadre {
  /** Si el reparto puede enviarse. */
  valido: boolean
  /** Mensaje para el usuario. Vacio cuando no hay nada que decir. */
  mensaje: string
  /** Diferencia en unidades minimas. Negativa = falta, positiva = sobra. */
  desviacion: number
}

const OK: Cuadre = { valido: true, mensaje: '', desviacion: 0 }

/**
 * Comprueba el cuadre segun el modo de reparto.
 *
 * @param valores lo que el usuario ha escrito, uno por participante
 * @param totalEnCentimos importe del gasto, ya validado
 */
export function comprobarCuadre(
  tipo: SplitType,
  valores: string[],
  totalEnCentimos: number,
): Cuadre {
  if (tipo === 'EQUAL') {
    return OK
  }

  const enteros = valores.map(aCentimos)

  if (enteros.some((v) => v === null)) {
    return {
      valido: false,
      mensaje: 'Hay valores vacios o no validos.',
      desviacion: 0,
    }
  }

  const suma = (enteros as number[]).reduce((a, b) => a + b, 0)

  if (tipo === 'SHARES') {
    // Las partes son proporciones: no tienen que sumar nada concreto, solo
    // no ser todas cero, porque entonces no habria nada que repartir.
    return suma > 0
      ? OK
      : { valido: false, mensaje: 'Alguien tiene que llevarse alguna parte.', desviacion: 0 }
  }

  const objetivo = tipo === 'EXACT' ? totalEnCentimos : 100_00
  const desviacion = suma - objetivo

  if (desviacion === 0) {
    return OK
  }

  return {
    valido: false,
    desviacion,
    mensaje:
      tipo === 'EXACT'
        ? desviacion < 0
          ? `Faltan ${formatearUnidades(-desviacion)} por repartir.`
          : `Sobran ${formatearUnidades(desviacion)}.`
        : desviacion < 0
          ? `Falta un ${formatearUnidades(-desviacion)} %.`
          : `Sobra un ${formatearUnidades(desviacion)} %.`,
  }
}

/** `1234` -> `12,34`. Se formatea aqui y no con Intl porque no lleva moneda. */
function formatearUnidades(unidades: number): string {
  return (unidades / 100).toFixed(2).replace('.', ',')
}

/** Etiqueta y ayuda de cada modo, en un solo sitio. */
export const MODOS_DE_REPARTO: Record<
  SplitType,
  { etiqueta: string; ayuda: string; sufijo?: string }
> = {
  EQUAL: {
    etiqueta: 'A partes iguales',
    ayuda: 'El importe se divide entre quienes participan.',
  },
  EXACT: {
    etiqueta: 'Importes exactos',
    ayuda: 'Indica cuanto le corresponde a cada uno. Deben sumar el total.',
  },
  PERCENTAGE: {
    etiqueta: 'Porcentajes',
    ayuda: 'Indica que porcentaje le toca a cada uno. Deben sumar 100.',
    sufijo: '%',
  },
  SHARES: {
    etiqueta: 'Partes',
    ayuda: 'Reparto proporcional: dos partes pagan el doble que una.',
  },
}
