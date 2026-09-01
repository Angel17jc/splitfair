import { NavLink, Outlet } from 'react-router-dom'
import UserMenu from './UserMenu'

const ENLACES = [{ a: '/dashboard', texto: 'Mis grupos' }]

/**
 * Estructura comun de las paginas con sesion.
 *
 * Se monta como ruta contenedora, no envolviendo cada pagina a mano: asi
 * anadir una pantalla privada consiste en colgarla de esta rama, sin
 * acordarse de nada. Un layout que se aplica por repeticion acaba faltando en
 * la pantalla que se anadio con prisa.
 */
export default function Layout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <span className="text-base font-semibold text-slate-900">SplitFair</span>
            <nav aria-label="Principal" className="flex gap-1">
              {ENLACES.map((enlace) => (
                <NavLink
                  key={enlace.a}
                  to={enlace.a}
                  /* aria-current lo pone NavLink solo, y es lo que anuncia a un
                     lector de pantalla en que seccion esta: el color por si
                     solo no informa a nadie que no lo vea. */
                  className={({ isActive }) =>
                    `rounded-md px-3 py-1.5 text-sm ${
                      isActive
                        ? 'bg-slate-100 font-medium text-slate-900'
                        : 'text-slate-600 hover:bg-slate-50'
                    }`
                  }
                >
                  {enlace.texto}
                </NavLink>
              ))}
            </nav>
          </div>

          <UserMenu />
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
