import { forwardRef, useId, type InputHTMLAttributes } from 'react'

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta: string
  /** Mensaje de error. Su presencia marca el campo como invalido. */
  error?: string
  /** Ayuda breve. Se oculta cuando hay error, para no competir con el. */
  ayuda?: string
}

/**
 * Campo de formulario con su etiqueta, su error y las conexiones de
 * accesibilidad ya hechas.
 *
 * **Reenvia la ref** porque react-hook-form registra el campo a traves de
 * ella; sin `forwardRef`, `register()` no encuentra el input, el formulario no
 * lee nada y la validacion se dispara sobre valores vacios sin que nada
 * indique la causa.
 *
 * El identificador se genera con `useId` cuando no se pasa uno. Escribir los
 * `id` a mano funciona hasta que dos campos coinciden en la misma pagina: a
 * partir de ahi las etiquetas apuntan al campo equivocado y el error se
 * anuncia sobre otro.
 */
const Input = forwardRef<HTMLInputElement, Props>(function Input(
  { etiqueta, error, ayuda, id, className = '', ...resto },
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

      <input
        id={idCampo}
        ref={ref}
        aria-invalid={error ? 'true' : undefined}
        // Se apunta al error si lo hay, y si no a la ayuda: anunciar ambos
        // haria que el lector leyera la norma justo despues de decir que se
        // ha incumplido.
        aria-describedby={error ? idError : ayuda ? idAyuda : undefined}
        className={
          'mt-1 w-full rounded-md border px-3 py-2 text-sm outline-none ' +
          'focus:ring-1 disabled:bg-slate-50 ' +
          (error
            ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
            : 'border-slate-300 focus:border-slate-900 focus:ring-slate-900') +
          ` ${className}`
        }
        {...resto}
      />

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

export default Input
