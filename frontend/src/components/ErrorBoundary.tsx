import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * Ultima red de seguridad ante un error de renderizado.
 *
 * Desde React 16, un error lanzado durante el render que nadie captura
 * **desmonta el arbol entero**: el usuario no ve un componente roto, ve una
 * pagina en blanco, que es indistinguible de una aplicacion que no ha
 * cargado. Esto lo convierte en algo que al menos se puede leer y del que se
 * puede salir.
 *
 * Tiene que ser una clase: no existe equivalente con hooks.
 *
 * ## Lo que NO captura
 *
 * Solo errores durante el render, en los metodos de ciclo de vida y en los
 * constructores de los componentes que envuelve. Se le escapan los de
 * manejadores de eventos, los de codigo asincrono y los del propio
 * ErrorBoundary. Por eso no sustituye al manejo de errores de la capa de API:
 * un fallo de red se trata alli, con su mensaje y su reintento, no aqui.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Se registra en consola porque, si no, el error desaparece: el usuario
    // ve el mensaje generico y no queda ni rastro de la causa. En produccion
    // este es el punto por donde se enviaria a un servicio de errores.
    console.error('Error no capturado en el arbol de React:', error, info.componentStack)
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }

    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-center">
        <h1 className="text-xl font-medium text-slate-900">Algo ha ido mal</h1>
        <p className="max-w-sm text-sm text-slate-500">
          La pagina no se ha podido mostrar. Puedes recargar para intentarlo de nuevo; tus
          datos no se han visto afectados.
        </p>
        {/* Una recarga completa y no un reintento en caliente: si el estado de
            la aplicacion es lo que provoco el error, reintentar sobre el mismo
            estado vuelve a romper en el acto. */}
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Recargar la pagina
        </button>
      </div>
    )
  }
}
