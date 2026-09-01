import { Link } from 'react-router-dom'

/**
 * Una ruta inexistente no debe dejar la pantalla en blanco: sin este caso, un
 * enlace mal escrito parece una aplicacion rota.
 */
export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-center">
      <p className="text-5xl font-semibold text-slate-300">404</p>
      <h1 className="text-xl font-medium text-slate-800">Esta pagina no existe</h1>
      <p className="max-w-sm text-sm text-slate-500">
        Puede que el enlace este mal escrito o que el contenido se haya movido.
      </p>
      <Link
        to="/"
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
      >
        Volver al inicio
      </Link>
    </div>
  )
}
