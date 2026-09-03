import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import AnonymousRoute from './AnonymousRoute'
import ProtectedRoute from './ProtectedRoute'
import { ContextoDeSesion, type EstadoDeSesion, type Sesion } from '../features/auth/context'

/**
 * Las guardas de ruta.
 *
 * Se prueban aqui y no solo en el navegador porque el caso que mas duele es el
 * intermedio: al recargar, la aplicacion todavia no sabe si hay sesion. Ese
 * instante dura milisegundos y es facil que un recorrido manual no lo pille,
 * pero si la guarda redirige durante el, **cada recarga expulsa al login a un
 * usuario perfectamente autenticado**.
 */
function conSesion(estado: EstadoDeSesion): Sesion {
  return {
    estado,
    usuario:
      estado === 'autenticado' ? { userId: 1, name: 'Ana', email: 'ana@test.com' } : null,
    entrar: async () => {
      throw new Error('no se usa en este test')
    },
    registrarse: async () => {
      throw new Error('no se usa en este test')
    },
    salir: async () => {},
  }
}

/**
 * Monta una sola ruta bajo la guarda y su destino de redireccion **fuera** de
 * ella.
 *
 * El solape importa: si el destino cuelga de la misma guarda que redirige
 * hacia el, la guarda se vuelve a evaluar, vuelve a redirigir, y el test se
 * cuelga en un bucle infinito. Las rutas reales no se solapan —cada guarda
 * envuelve solo lo suyo— pero al escribir el test es facil montarlo mal y
 * culpar al componente.
 */
function pintar(estado: EstadoDeSesion, guarda: 'protegida' | 'anonima') {
  const protegida = guarda === 'protegida'
  const Guarda = protegida ? ProtectedRoute : AnonymousRoute
  const rutaGuardada = protegida ? '/dashboard' : '/login'
  const contenido = protegida ? 'Contenido privado' : 'Formulario de acceso'
  const destino = protegida ? '/login' : '/dashboard'
  const contenidoDestino = protegida ? 'Formulario de acceso' : 'Contenido privado'

  return render(
    <ContextoDeSesion.Provider value={conSesion(estado)}>
      <MemoryRouter initialEntries={[rutaGuardada]}>
        <Routes>
          <Route element={<Guarda />}>
            <Route path={rutaGuardada} element={<p>{contenido}</p>} />
          </Route>
          <Route path={destino} element={<p>{contenidoDestino}</p>} />
        </Routes>
      </MemoryRouter>
    </ContextoDeSesion.Provider>,
  )
}

describe('ProtectedRoute', () => {
  it('mientras comprueba la sesion no redirige: espera', () => {
    pintar('comprobando', 'protegida')

    // Si redirigiera aqui, recargar una pagina privada mandaria al login a
    // quien si tiene sesion, un instante antes de que llegue la respuesta.
    expect(screen.getByRole('status')).toHaveTextContent(/comprobando/i)
    expect(screen.queryByText('Formulario de acceso')).not.toBeInTheDocument()
    expect(screen.queryByText('Contenido privado')).not.toBeInTheDocument()
  })

  it('sin sesion lleva al login', () => {
    pintar('anonimo', 'protegida')

    expect(screen.getByText('Formulario de acceso')).toBeInTheDocument()
    expect(screen.queryByText('Contenido privado')).not.toBeInTheDocument()
  })

  it('con sesion deja pasar', () => {
    pintar('autenticado', 'protegida')

    expect(screen.getByText('Contenido privado')).toBeInTheDocument()
  })
})

describe('AnonymousRoute', () => {
  it('mientras comprueba la sesion tampoco decide', () => {
    pintar('comprobando', 'anonima')

    // El simetrico del anterior: mostrar el formulario y quitarlo medio
    // segundo despues es peor que esperar medio segundo.
    expect(screen.getByRole('status')).toHaveTextContent(/comprobando/i)
    expect(screen.queryByText('Formulario de acceso')).not.toBeInTheDocument()
  })

  it('sin sesion muestra el formulario', () => {
    pintar('anonimo', 'anonima')

    expect(screen.getByText('Formulario de acceso')).toBeInTheDocument()
  })

  it('con sesion abierta no ensena el login', () => {
    // Ademas de resultar raro, rellenarlo abriria una segunda sesion y dejaria
    // la primera colgando en la base de datos.
    pintar('autenticado', 'anonima')

    expect(screen.getByText('Contenido privado')).toBeInTheDocument()
    expect(screen.queryByText('Formulario de acceso')).not.toBeInTheDocument()
  })
})
