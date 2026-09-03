import { describe, expect, it } from 'vitest'
import { aCentimos, comprobarCuadre } from './reparto'

/**
 * El cuadre de un reparto personalizado.
 *
 * Es la funcion mas delicada del frontend: decide si el usuario puede enviar
 * el formulario. Un falso negativo aqui bloquea un reparto correcto sin que la
 * persona tenga forma de arreglarlo, porque lo que ha escrito **es** correcto.
 */
describe('aCentimos', () => {
  it('convierte a enteros sin pasar por coma flotante', () => {
    expect(aCentimos('10')).toBe(1000)
    expect(aCentimos('10,5')).toBe(1050)
    expect(aCentimos('10,05')).toBe(1005)
    expect(aCentimos('0,01')).toBe(1)
  })

  it('acepta coma y punto por igual', () => {
    expect(aCentimos('33,33')).toBe(aCentimos('33.33'))
  })

  it('devuelve null para lo que no es un importe de dos decimales', () => {
    expect(aCentimos('')).toBeNull()
    expect(aCentimos('   ')).toBeNull()
    expect(aCentimos('10,555')).toBeNull()
    expect(aCentimos('abc')).toBeNull()
    expect(aCentimos('-1')).toBeNull()
  })

  it('no pierde precision con importes grandes', () => {
    // 99999,99 en coma flotante sigue siendo exacto, pero el resultado
    // entero es lo que se compara despues, y ahi no cabe la duda.
    expect(aCentimos('99999,99')).toBe(9999999)
  })
})

describe('comprobarCuadre', () => {
  describe('EQUAL', () => {
    it('no exige nada: el reparto lo hace el backend', () => {
      expect(comprobarCuadre('EQUAL', [], 10000).valido).toBe(true)
    })
  })

  describe('EXACT', () => {
    it('cuadra cuando los importes suman el total', () => {
      expect(comprobarCuadre('EXACT', ['30', '30', '40'], 10000).valido).toBe(true)
    })

    it('CUADRA un reparto que en coma flotante no sumaria su total', () => {
      // Es la razon de ser de este modulo. Una cuenta de 10,00 repartida en
      // 8,20 + 0,10 + 1,70 es exacta, pero sumada con `+` da
      // 9.999999999999998, asi que saldria marcada como descuadrada y el
      // usuario no podria hacer nada al respecto: sus numeros son correctos.
      expect(8.2 + 0.1 + 1.7).not.toBe(10) // el problema, demostrado
      expect(comprobarCuadre('EXACT', ['8,20', '0,10', '1,70'], 1000).valido).toBe(true)
    })

    it('y tambien cuadra 33,33 + 33,33 + 33,34, que si suma bien', () => {
      // Se deja explicito porque este era el ejemplo que se citaba como
      // problematico y no lo es: esa suma da exactamente 100. El modulo tiene
      // que aceptarlo igual, pero la razon de existir es el caso de arriba.
      expect(33.33 + 33.33 + 33.34).toBe(100)
      expect(comprobarCuadre('EXACT', ['33,33', '33,33', '33,34'], 10000).valido).toBe(true)
    })

    it('avisa de cuanto falta', () => {
      const cuadre = comprobarCuadre('EXACT', ['30', '30', '30'], 10000)
      expect(cuadre.valido).toBe(false)
      expect(cuadre.desviacion).toBe(-1000)
      expect(cuadre.mensaje).toContain('Faltan')
      expect(cuadre.mensaje).toContain('10,00')
    })

    it('avisa de cuanto sobra', () => {
      const cuadre = comprobarCuadre('EXACT', ['30', '30', '45'], 10000)
      expect(cuadre.valido).toBe(false)
      expect(cuadre.desviacion).toBe(500)
      expect(cuadre.mensaje).toContain('Sobran')
      expect(cuadre.mensaje).toContain('5,00')
    })

    it('un solo centimo de diferencia ya descuadra', () => {
      // El cuadre no admite tolerancia: las partes tienen que sumar el
      // importe exacto, que es la invariante que garantiza el backend.
      expect(comprobarCuadre('EXACT', ['33,33', '33,33', '33,33'], 10000).valido).toBe(false)
    })

    it('un campo vacio no cuenta como cero', () => {
      // Tratar el vacio como cero enviaria un reparto donde alguien asume 0,00
      // sin haberlo decidido. Se pide que lo escriba.
      const cuadre = comprobarCuadre('EXACT', ['50', ''], 10000)
      expect(cuadre.valido).toBe(false)
      expect(cuadre.mensaje).toContain('vacios')
    })
  })

  describe('PERCENTAGE', () => {
    it('cuadra cuando suman 100', () => {
      expect(comprobarCuadre('PERCENTAGE', ['50', '30', '20'], 9000).valido).toBe(true)
    })

    it('admite porcentajes con decimales que suman 100', () => {
      expect(comprobarCuadre('PERCENTAGE', ['33,33', '33,33', '33,34'], 9000).valido).toBe(true)
    })

    it('no depende del importe del gasto', () => {
      // El porcentaje cuadra contra 100, no contra el total: si dependiera del
      // importe, cambiar el gasto invalidaria un reparto ya correcto.
      expect(comprobarCuadre('PERCENTAGE', ['50', '50'], 1).valido).toBe(true)
      expect(comprobarCuadre('PERCENTAGE', ['50', '50'], 999999).valido).toBe(true)
    })

    it('avisa de cuanto porcentaje falta', () => {
      const cuadre = comprobarCuadre('PERCENTAGE', ['50', '30', '10'], 9000)
      expect(cuadre.valido).toBe(false)
      expect(cuadre.mensaje).toContain('10,00')
      expect(cuadre.mensaje).toContain('%')
    })
  })

  describe('SHARES', () => {
    it('vale cualquier reparto con alguna parte', () => {
      // Las partes son proporciones: no tienen que sumar nada concreto.
      expect(comprobarCuadre('SHARES', ['2', '1', '1'], 6000).valido).toBe(true)
      expect(comprobarCuadre('SHARES', ['7'], 6000).valido).toBe(true)
    })

    it('todo a cero no vale: no habria nada que repartir', () => {
      const cuadre = comprobarCuadre('SHARES', ['0', '0'], 6000)
      expect(cuadre.valido).toBe(false)
      expect(cuadre.mensaje).toContain('parte')
    })
  })
})
