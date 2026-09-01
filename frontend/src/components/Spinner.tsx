interface Props {
  /** Texto para lectores de pantalla. Describe **que** se espera, no "cargando". */
  etiqueta?: string
  className?: string
}

/**
 * Indicador de espera.
 *
 * Lleva `role="status"` y un texto oculto porque una animacion no existe para
 * quien usa un lector de pantalla: sin el, la pagina simplemente se queda
 * muda hasta que llegan los datos.
 */
export default function Spinner({ etiqueta = 'Cargando', className = 'h-4 w-4' }: Props) {
  return (
    <span role="status" className="inline-flex items-center">
      <svg
        className={`animate-spin ${className}`}
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle
          className="opacity-25"
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="4"
        />
        <path
          className="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"
        />
      </svg>
      <span className="sr-only">{etiqueta}</span>
    </span>
  )
}
