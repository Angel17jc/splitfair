import { describe, expect, it } from 'vitest'
import { zodResolver } from '@hookform/resolvers/zod'
import { validar } from '../../test/validar'
import { esquemaDeGasto, type DatosDeGasto } from './schemas'

/**
 * La validacion del formulario de gastos.
 *
 * Es la que decide si un reparto se puede enviar, y hasta ahora solo se
 * probaba su pieza central, `comprobarCuadre`, no el esquema que la usa. Se
 * escribieron **antes** de migrar a zod 4 y pasaban con zod 3: lo que fijan es
 * que la migracion no cambia lo que ve el usuario.
 *
 * Lo mas fragil es la regla que cruza campos —el cuadre depende del modo, del
 * importe y de los valores a la vez—, porque cuando y si se ejecuta depende de
 * como la libreria trate los errores previos, y eso es justo lo que cambia
 * entre versiones mayores.
 */
const resolver = zodResolver(esquemaDeGasto)

const base: DatosDeGasto = {
  description: 'Cena del sabado',
  amount: '100,00',
  expenseDate: '2026-11-08',
  category: 'COMIDA',
  splitType: 'EQUAL',
  participantes: [1, 2, 3],
  valores: {},
}

const gasto = (cambios: Partial<DatosDeGasto>) => ({ ...base, ...cambios })

describe('campos sueltos', () => {
  it('un gasto completo a partes iguales es valido', async () => {
    const { mensajes } = await validar(resolver, base)

    expect(mensajes).toEqual({})
  })

  it('con los campos vacios, cada uno pide lo suyo', async () => {
    const { mensajes } = await validar(
      resolver,
      gasto({ description: '   ', amount: '', expenseDate: '', participantes: [] }),
    )

    // El importe vacio incumple dos reglas; debe salir "indica el importe", no
    // "importe no valido", que a quien no ha escrito nada no le dice que hacer.
    expect(mensajes).toEqual({
      description: 'Describe el gasto',
      amount: 'Indica el importe',
      expenseDate: 'Indica la fecha',
      participantes: 'Elige al menos una persona entre las que repartir',
    })
  })

  it.each(['12,555', 'abc', '0', '-5'])('rechaza el importe %s', async (amount) => {
    const { mensajes } = await validar(resolver, gasto({ amount }))

    expect(mensajes).toEqual({
      amount: 'Importe no valido. Usa como maximo dos decimales, por ejemplo 12,50',
    })
  })

  it('la descripcion se envia recortada', async () => {
    const { valores } = await validar(resolver, gasto({ description: '  Cena  ' }))

    expect(valores).toMatchObject({ description: 'Cena' })
  })
})

describe('el cuadre del reparto', () => {
  it('cantidades exactas que suman el total son validas', async () => {
    const { mensajes } = await validar(
      resolver,
      gasto({ splitType: 'EXACT', valores: { 1: '33,33', 2: '33,33', 3: '33,34' } }),
    )

    expect(mensajes).toEqual({})
  })

  it('si falta dinero, el error va al reparto y dice cuanto', async () => {
    const { mensajes } = await validar(
      resolver,
      gasto({ splitType: 'EXACT', valores: { 1: '33,33', 2: '33,33', 3: '33,33' } }),
    )

    expect(mensajes).toEqual({ valores: 'Faltan 0,01 por repartir.' })
  })

  it('unos porcentajes que no suman 100 dicen cuanto sobra', async () => {
    const { mensajes } = await validar(
      resolver,
      gasto({ splitType: 'PERCENTAGE', valores: { 1: '50', 2: '30', 3: '30' } }),
    )

    expect(mensajes).toEqual({ valores: 'Sobra un 10,00 %.' })
  })

  it('solo cuentan los valores de quien participa', async () => {
    // Al desmarcar a alguien su valor se limpia en el formulario, pero el
    // esquema no debe depender de eso: un valor huerfano no descuadra nada.
    const { mensajes } = await validar(
      resolver,
      gasto({ splitType: 'EXACT', participantes: [1, 2], valores: { 1: '50', 2: '50', 3: '999' } }),
    )

    expect(mensajes).toEqual({})
  })

  it('a partes iguales no mira los valores', async () => {
    const { mensajes } = await validar(resolver, gasto({ valores: { 1: 'basura' } }))

    expect(mensajes).toEqual({})
  })

  it('con un importe invalido no se comprueba el cuadre', async () => {
    // No hay total contra el que cuadrar. Mostrar ademas "faltan X" seria un
    // segundo error derivado del primero, y la cifra no significaria nada.
    const { mensajes } = await validar(
      resolver,
      gasto({ amount: 'abc', splitType: 'EXACT', valores: { 1: '1', 2: '1', 3: '1' } }),
    )

    expect(mensajes).toEqual({
      amount: 'Importe no valido. Usa como maximo dos decimales, por ejemplo 12,50',
    })
  })

  it('el descuadre sale a la vez que los demas errores, no en una segunda vuelta', async () => {
    // Si el cuadre solo se comprobara con el resto del formulario ya limpio,
    // el usuario corregiria la descripcion, volveria a enviar y solo entonces
    // descubriria que el reparto tampoco vale.
    const { mensajes } = await validar(
      resolver,
      gasto({
        description: '',
        splitType: 'EXACT',
        valores: { 1: '33,33', 2: '33,33', 3: '33,33' },
      }),
    )

    expect(mensajes).toEqual({
      description: 'Describe el gasto',
      valores: 'Faltan 0,01 por repartir.',
    })
  })
})
