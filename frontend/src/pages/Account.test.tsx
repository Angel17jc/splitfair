import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Account from './Account'
import { ContextoDeSesion, type Sesion } from '../features/auth/context'

/**
 * La pantalla de cuenta.
 *
 * Lo que se fija aqui no es que los formularios envien —eso se ve en un
 * navegador— sino **que la sesion se cierre despues de las dos operaciones que
 * la invalidan**. Si no se cierra, la pantalla se queda aparentemente dentro y
 * cada peticion responde 401 sin explicacion:
 *
 * - Cambiar la contrasena revoca **todos** los refresh tokens en el backend,
 *   incluido el de esta pestana.
 * - Darse de baja sustituye el correo, que es el sujeto del access token, asi
 *   que este deja de resolver a ningun usuario en el acto.
 *
 * En ambos casos la sesion ya esta muerta en el servidor; lo que falta es que
 * el cliente se entere.
 */
vi.mock('../api/users', () => ({
  obtenerPerfil: vi.fn(),
  actualizarPerfil: vi.fn(),
  cambiarContrasena: vi.fn(),
  darDeBajaCuenta: vi.fn(),
}))

const { actualizarPerfil, cambiarContrasena, darDeBajaCuenta } = await import('../api/users')
const actualizarPerfilSimulado = vi.mocked(actualizarPerfil)
const cambiarContrasenaSimulada = vi.mocked(cambiarContrasena)
const darDeBajaSimulada = vi.mocked(darDeBajaCuenta)

const salir = vi.fn()
const renombrar = vi.fn()

function montar() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  const sesion: Sesion = {
    estado: 'autenticado',
    usuario: { userId: 1, name: 'Ana', email: 'ana@test.com' },
    entrar: async () => {
      throw new Error('no se usa')
    },
    registrarse: async () => {
      throw new Error('no se usa')
    },
    salir,
    renombrar,
  }

  return render(
    <QueryClientProvider client={queryClient}>
      <ContextoDeSesion.Provider value={sesion}>
        <MemoryRouter>
          <Account />
        </MemoryRouter>
      </ContextoDeSesion.Provider>
    </QueryClientProvider>,
  )
}

describe('Pantalla de cuenta', () => {
  beforeEach(() => {
    salir.mockReset()
    renombrar.mockReset()
    actualizarPerfilSimulado.mockReset()
    cambiarContrasenaSimulada.mockReset()
    darDeBajaSimulada.mockReset()

    actualizarPerfilSimulado.mockResolvedValue({
      id: 1,
      name: 'Ana Torres',
      email: 'ana@test.com',
      createdAt: '2026-01-01T00:00:00',
    })
    cambiarContrasenaSimulada.mockResolvedValue(undefined)
    darDeBajaSimulada.mockResolvedValue(undefined)
  })

  it('cambiar la contrasena cierra la sesion', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.type(screen.getByLabelText('Contrasena actual'), 'password123')
    await usuario.type(screen.getByLabelText('Contrasena nueva'), 'otracontrasena')
    await usuario.click(screen.getByRole('button', { name: 'Cambiar contrasena' }))

    expect(cambiarContrasenaSimulada).toHaveBeenCalledWith({
      currentPassword: 'password123',
      newPassword: 'otracontrasena',
    })
    expect(salir).toHaveBeenCalled()
  })

  it('avisa de que se cierran todas las sesiones antes de cambiarla', () => {
    montar()

    // El aviso va antes y no despues: quien la cambia suele buscar justo esa
    // revocacion, pero verse expulsado sin haberlo leido parece un fallo.
    expect(screen.getByText(/cierran todas las sesiones/i)).toBeInTheDocument()
  })

  it('darse de baja exige la contrasena y cierra la sesion', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.click(screen.getByRole('button', { name: 'Dar de baja' }))
    await usuario.type(screen.getByLabelText('Contrasena'), 'password123')
    await usuario.click(screen.getByRole('button', { name: 'Confirmar baja' }))

    expect(darDeBajaSimulada).toHaveBeenCalledWith({ currentPassword: 'password123' })
    expect(salir).toHaveBeenCalled()
  })

  it('dice que el historico se conserva antes de pedir confirmacion', () => {
    montar()

    // Alguien que se da de baja esperando que sus gastos desaparezcan del
    // grupo tiene que enterarse **antes**, no despues. Y el motivo cabe en una
    // linea: si se fueran, las cuentas de los demas dejarian de cuadrar.
    expect(screen.getByText(/se conservan/i)).toBeInTheDocument()
    expect(screen.getByText(/dejarian de cuadrar/i)).toBeInTheDocument()
  })

  it('el nombre se refleja en la cabecera sin recargar', async () => {
    const usuario = userEvent.setup()
    montar()

    const campo = screen.getByLabelText('Nombre')
    await usuario.clear(campo)
    await usuario.type(campo, 'Ana Torres')
    await usuario.click(screen.getByRole('button', { name: 'Guardar' }))

    // Sin esto, el menu de la cabecera seguiria mostrando el nombre viejo
    // hasta la siguiente recarga: el nombre que se pinta vive en el estado de
    // React, no en la respuesta de la API.
    expect(actualizarPerfilSimulado).toHaveBeenCalledWith('Ana Torres')
    expect(renombrar).toHaveBeenCalledWith('Ana Torres')
  })
})
