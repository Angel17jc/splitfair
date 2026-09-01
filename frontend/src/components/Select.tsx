import { forwardRef, useId, type SelectHTMLAttributes } from 'react'

interface Props extends SelectHTMLAttributes<HTMLSelectElement> {
  etiqueta: string
  error?: string
  ayuda?: string
}

/**
 * Desplegable con etiqueta, error y accesibilidad, igual que `Input`.
 *
 * Es un `<select>` nativo y no una lista construida a mano: en movil abre el
 * selector del sistema, funciona con teclado sin escribir nada, y no hay que
 * reimplementar el foco ni el anuncio del valor elegido. Un desplegable
 * casero solo compensa cuando hace falta buscar o pintar cada opcion, y aqui
 * no es el caso.
 */
const Select = forwardRef<HTMLSelectElement, Props>(function Select(
  { etiqueta, error, ayuda, id, className = '', children, ...resto },
  ref,
) {
  const generado = useId()
  const idCampo = id ?? generado
  const idError = `${idCampo}-error`
  const idAyuda = `${idCampo}-ayuda`

  return (
    <div>
      <label htmlFor={idCampo} className="block text-sm font-medium text-slate-700">
        {etiqueta}
      </label>

      <select
        id={idCampo}
        ref={ref}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? idError : ayuda ? idAyuda : undefined}
        className={
          'mt-1 w-full rounded-md border bg-white px-3 py-2 text-sm outline-none focus:ring-1 ' +
          (error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
            : 'border-slate-300 focus:border-slate-900 focus:ring-slate-900') +
          ` ${className}`
        }
        {...resto}
      >
        {children}
      </select>

      {error ? (
        <p id={idError} role="alert" className="mt-1 text-sm text-red-600">
          {error}
        </p>
      ) : ayuda ? (
        <p id={idAyuda} className="mt-1 text-xs text-slate-500">
          {ayuda}
        </p>
      ) : null}
    </div>
  )
})

export default Select
