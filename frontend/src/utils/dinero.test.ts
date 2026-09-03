import { describe, expect, it } from 'vitest'
import {
  describirSaldo,
  formatearFecha,
  formatearImporte,
  hoyISO,
  parsearImporte,
  signoDeSaldo,
} from './dinero'

/**
 * Estas son las funciones donde un fallo no se ve.
 *
 * Un recorrido por la interfaz no distingue "33,30 €" de "33,3 €" a simple
 * vista, ni nota que un saldo de -0,000001 se pinte en rojo, ni que un gasto
 * quede fechado manana. Por eso se cubren aqui y no con el navegador.
 */
describe('parsearImporte', () => {
  it('acepta la coma decimal, que es como se escribe en espanol', () => {
    // Un input numerico rechaza la coma segun el idioma del navegador, asi
    // que el importe se lee como texto y se normaliza aqui.
    expect(parsearImporte('12,50')).toBe(12.5)
    expect(parsearImporte('12.50')).toBe(12.5)
  })

  it('acepta enteros y un solo decimal', () => {
    expect(parsearImporte('10')).toBe(10)
    expect(parsearImporte('10,5')).toBe(10.5)
  })

  it('rechaza mas de dos decimales en vez de redondearlos', () => {
    // Redondear 10,555 a 10,56 en silencio cambia lo que el usuario escribio.
    // En una aplicacion de dinero eso no es una comodidad: es una sorpresa.
    expect(parsearImporte('10,555')).toBeNull()
  })

  it('rechaza cero y negativos: un gasto de cero no es un gasto', () => {
    expect(parsearImporte('0')).toBeNull()
    expect(parsearImporte('-5')).toBeNull()
  })

  it('rechaza lo que no es un numero', () => {
    expect(parsearImporte('')).toBeNull()
    expect(parsearImporte('   ')).toBeNull()
    expect(parsearImporte('doce')).toBeNull()
    expect(parsearImporte('1e3')).toBeNull()
    // "12,50 €" con la moneda pegada tampoco: el campo pide solo el numero.
    expect(parsearImporte('12,50 EUR')).toBeNull()
  })

  it('ignora los espacios de alrededor, que se cuelan al pegar', () => {
    expect(parsearImporte('  12,50  ')).toBe(12.5)
  })
})

describe('formatearImporte', () => {
  it('siempre pone dos decimales', () => {
    // Los importes llegan como numero JSON: 0.00 se recibe como 0 y 33.30
    // como 33.3. Pintarlos tal cual da cifras que parecen rotas.
    expect(formatearImporte(0, 'EUR')).toContain('0,00')
    expect(formatearImporte(33.3, 'EUR')).toContain('33,30')
  })

  it('usa la moneda del grupo', () => {
    expect(formatearImporte(10, 'USD')).toMatch(/10,00/)
    expect(formatearImporte(10, 'EUR')).toMatch(/10,00/)
    expect(formatearImporte(10, 'USD')).not.toEqual(formatearImporte(10, 'EUR'))
  })
})

describe('signoDeSaldo', () => {
  it('distingue acreedor, deudor y saldado', () => {
    expect(signoDeSaldo(50)).toBe('acreedor')
    expect(signoDeSaldo(-50)).toBe('deudor')
    expect(signoDeSaldo(0)).toBe('saldado')
  })

  it('trata como saldado lo que esta por debajo de medio centimo', () => {
    // Los importes son coma flotante. Comparar contra cero exacto dejaria un
    // -0.0000001 pintado en rojo, diciendole al usuario que debe dinero que
    // no debe. Es el mismo umbral que usa el algoritmo del backend.
    expect(signoDeSaldo(-0.0000001)).toBe('saldado')
    expect(signoDeSaldo(0.004)).toBe('saldado')
  })

  it('un centimo si cuenta como deuda', () => {
    // El umbral separa el ruido de la coma flotante, no deudas reales: un
    // centimo es dinero y debe salir como tal.
    expect(signoDeSaldo(0.01)).toBe('acreedor')
    expect(signoDeSaldo(-0.01)).toBe('deudor')
  })
})

describe('describirSaldo', () => {
  it('dice el sentido con palabras, no solo con el signo', () => {
    // El color no puede ser el unico portador del significado.
    expect(describirSaldo(50, 'EUR')).toContain('Te deben')
    expect(describirSaldo(-50, 'EUR')).toContain('Debes')
    expect(describirSaldo(0, 'EUR')).toBe('Estas al dia')
  })

  it('muestra el importe sin signo: el texto ya dice la direccion', () => {
    expect(describirSaldo(-50, 'EUR')).not.toContain('-')
  })
})

describe('hoyISO', () => {
  it('devuelve el dia local, no el UTC', () => {
    const ahora = new Date()
    const esperado = `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(
      ahora.getDate(),
    ).padStart(2, '0')}`

    // toISOString() convierte a UTC: en husos al oeste de Greenwich, por la
    // tarde ya devuelve el dia siguiente y el gasto quedaria fechado manana.
    expect(hoyISO()).toBe(esperado)
  })

  it('rellena mes y dia con cero a la izquierda', () => {
    expect(hoyISO()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})

describe('formatearFecha', () => {
  it('no desplaza el dia por el huso horario', () => {
    // expenseDate es un dia, no un instante. Pasarlo por new Date('2026-01-01')
    // lo interpreta como medianoche UTC y en America lo retrasa al 31 de
    // diciembre. Se construye componente a componente justo por eso.
    expect(formatearFecha('2026-01-01')).toContain('1')
    expect(formatearFecha('2026-01-01')).toContain('2026')
    expect(formatearFecha('2026-01-01')).not.toContain('2025')
  })
})
