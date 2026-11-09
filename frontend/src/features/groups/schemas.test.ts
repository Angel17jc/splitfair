import { describe, expect, it } from 'vitest'
import { zodResolver } from '@hookform/resolvers/zod'
import { validar } from '../../test/validar'
import { esquemaDeGrupo } from './schemas'

/**
 * La validacion del alta de grupos. Escrita antes de migrar a zod 4, como las
 * de los demas formularios: fija lo que ve el usuario, no la libreria.
 */
const resolver = zodResolver(esquemaDeGrupo)

describe('formulario de grupo', () => {
  it('un nombre de solo espacios cuenta como vacio', async () => {
    const { mensajes } = await validar(resolver, { name: '   ', description: '', currency: 'EUR' })

    expect(mensajes).toEqual({ name: 'El nombre es obligatorio' })
  })

  it('nombre y descripcion se envian recortados', async () => {
    const { mensajes, valores } = await validar(resolver, {
      name: '  Viaje a Lisboa ',
      description: '  Junio ',
      currency: 'EUR',
    })

    expect(mensajes).toEqual({})
    expect(valores).toEqual({ name: 'Viaje a Lisboa', description: 'Junio', currency: 'EUR' })
  })

  it('la descripcion es opcional', async () => {
    const { mensajes } = await validar(resolver, { name: 'Piso', currency: 'EUR' })

    expect(mensajes).toEqual({})
  })

  it('aplica los limites del backend', async () => {
    const { mensajes } = await validar(resolver, {
      name: 'a'.repeat(101),
      description: 'a'.repeat(256),
      currency: 'EUR',
    })

    expect(mensajes).toEqual({
      name: 'El nombre no puede pasar de 100 caracteres',
      description: 'La descripcion es demasiado larga',
    })
  })

  it('una moneda que no tiene tres letras no pasa', async () => {
    // Se afirma que hay error, no su texto: la moneda sale de un desplegable y
    // el usuario no puede escribir otra, asi que el mensaje es el de la
    // libreria y nadie lo ve. Fijarlo romperia el test en cada version de zod
    // por un texto que no llega a la pantalla.
    const { mensajes } = await validar(resolver, { name: 'Piso', currency: 'EU' })

    expect(Object.keys(mensajes)).toEqual(['currency'])
  })
})
