import { describe, expect, it } from 'vitest'
import { zodResolver } from '@hookform/resolvers/zod'
import { validar } from '../../test/validar'
import { esquemaDeAcceso, esquemaDeRegistro } from './schemas'

/**
 * La validacion de los formularios de acceso, tal como la ve el usuario.
 *
 * Estos tests se escribieron **antes** de migrar de zod 3 a zod 4, y pasaban
 * con la 3: son la definicion de "la migracion no cambia nada". Lo que mas
 * riesgo tenia es lo que nadie mira, como que mensaje sale primero cuando un
 * campo falla dos reglas a la vez o que direcciones de correo se aceptan.
 */
const acceso = zodResolver(esquemaDeAcceso)
const registro = zodResolver(esquemaDeRegistro)

describe('formulario de acceso', () => {
  it('con todo vacio, cada campo dice que es obligatorio', async () => {
    const { mensajes } = await validar(acceso, { email: '', password: '' })

    // Un email vacio incumple dos reglas, "obligatorio" y "formato". Debe salir
    // la primera: decirle a alguien que no ha escrito nada que su email tiene
    // un formato invalido es confuso.
    expect(mensajes).toEqual({
      email: 'El email es obligatorio',
      password: 'La contrasena es obligatoria',
    })
  })

  it('un email mal formado se marca como tal', async () => {
    const { mensajes } = await validar(acceso, { email: 'ana', password: 'x' })

    expect(mensajes).toEqual({ email: 'El email no tiene un formato valido' })
  })

  it('no exige longitud minima de contrasena', async () => {
    // Deliberado: imponer la politica aqui bloquearia a quien tenga una
    // contrasena anterior a la regla. Ver el comentario en schemas.ts.
    const { mensajes } = await validar(acceso, { email: 'ana@example.com', password: '1' })

    expect(mensajes).toEqual({})
  })
})

describe('formulario de registro', () => {
  it('un nombre de solo espacios cuenta como vacio', async () => {
    const { mensajes } = await validar(registro, {
      name: '   ',
      email: 'ana@example.com',
      password: '12345678',
    })

    expect(mensajes).toEqual({ name: 'El nombre es obligatorio' })
  })

  it('el nombre se envia recortado', async () => {
    const { mensajes, valores } = await validar(registro, {
      name: '  Ana Torres  ',
      email: 'ana@example.com',
      password: '12345678',
    })

    expect(mensajes).toEqual({})
    expect(valores).toEqual({ name: 'Ana Torres', email: 'ana@example.com', password: '12345678' })
  })

  it('aplica los limites del backend al nombre y a la contrasena', async () => {
    const { mensajes } = await validar(registro, {
      name: 'a'.repeat(101),
      email: 'ana@example.com',
      password: '1234567',
    })

    expect(mensajes).toEqual({
      name: 'El nombre no puede pasar de 100 caracteres',
      password: 'La contrasena debe tener al menos 8 caracteres',
    })
  })
})

describe('que direcciones de correo se aceptan', () => {
  // La regla de email cambio de implementacion entre zod 3 y zod 4. Esta tabla
  // fija el resultado, no la expresion regular: si una version futura empieza
  // a rechazar una direccion real, alguien se queda sin poder registrarse y
  // nada mas lo avisaria.
  it.each([
    'ana@example.com',
    'ana.torres@example.com',
    'ana+gastos@example.com',
    'ANA@EXAMPLE.COM',
    'beto_ruiz@mail.example.co',
    "o'brien@example.com",
    // Los usuarios de la base de desarrollo usan este dominio.
    'auditoria@test.local',
  ])('acepta %s', async (email) => {
    const { mensajes } = await validar(acceso, { email, password: 'x' })

    expect(mensajes).toEqual({})
  })

  it.each([
    'ana',
    'ana@',
    '@example.com',
    'ana@example',
    'ana torres@example.com',
    '.ana@example.com',
    'ana.@example.com',
    'ana..torres@example.com',
    'ana@-example.com',
  ])('rechaza %s', async (email) => {
    const { mensajes } = await validar(acceso, { email, password: 'x' })

    expect(mensajes).toEqual({ email: 'El email no tiene un formato valido' })
  })
})
