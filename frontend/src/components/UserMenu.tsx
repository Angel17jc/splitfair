import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../features/auth/useAuth'
import Button from './Button'

/**
 * Menu del usuario: quien esta dentro y como salir.
 *
 * Se cierra al pulsar fuera y con Escape. Ninguna de las dos es opcional: un
 * menu que solo se cierra volviendo a pulsar su boton se queda abierto sobre
 * el contenido en cuanto el usuario se distrae, y quien navega con teclado no
 * tiene forma de descartarlo sin recorrerlo entero.
 */
export default function UserMenu() {
  const { usuario, salir } = useAuth()
  const [abierto, setAbierto] = useState(false)
  const [saliendo, setSaliendo] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return

    const alPulsarFuera = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) {
        setAbierto(false)
      }
    }
    const alPulsarTecla = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') setAbierto(false)
    }

    document.addEventListener('mousedown', alPulsarFuera)
    document.addEventListener('keydown', alPulsarTecla)
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera)
      document.removeEventListener('keydown', alPulsarTecla)
    }
  }, [abierto])

  if (!usuario) return null

  const iniciales = usuario.name
    .split(' ')
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase() ?? '')
    .join('')

  return (
    <div className="relative" ref={contenedor}>
      <button
        type="button"
        onClick={() => setAbierto((estaba) => !estaba)}
        aria-expanded={abierto}
        aria-haspopup="menu"
        className="flex items-center gap-2 rounded-full py-1 pl-1 pr-3 text-sm text-slate-700 hover:bg-slate-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
      >
        <span
          aria-hidden="true"
          className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-900 text-xs font-semibold text-white"
        >
          {iniciales}
        </span>
        <span className="hidden sm:inline">{usuario.name}</span>
      </button>

      {abierto && (
        <div
          role="menu"
          className="absolute right-0 z-10 mt-2 w-60 rounded-lg border border-slate-200 bg-white p-2 shadow-lg"
        >
          <div className="border-b border-slate-100 px-3 pb-2 pt-1">
            <p className="truncate text-sm font-medium text-slate-900">{usuario.name}</p>
            {/* El email puede ser largo: truncar evita que estire el menu
                fuera de la pantalla en un movil. */}
            <p className="truncate text-xs text-slate-500">{usuario.email}</p>
          </div>
          <Button
            variante="texto"
            ancho
            cargando={saliendo}
            role="menuitem"
            className="mt-1 justify-start"
            onClick={async () => {
              // No se cierra el menu antes de tiempo: si salir falla, el
              // usuario sigue dentro y debe poder volver a intentarlo.
              setSaliendo(true)
              try {
                await salir()
              } finally {
                setSaliendo(false)
                setAbierto(false)
              }
            }}
          >
            Cerrar sesion
          </Button>
        </div>
      )}
    </div>
  )
}
