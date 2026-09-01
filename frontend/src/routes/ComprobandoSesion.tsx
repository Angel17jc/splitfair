/**
 * Lo que se ve mientras se resuelve si hay sesion.
 *
 * Deliberadamente sobrio: en el caso normal dura una sola peticion y un
 * indicador llamativo produciria un parpadeo mas molesto que la espera. La
 * pantalla definitiva llega con el layout, en el commit siguiente.
 */
export default function ComprobandoSesion() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <p className="text-sm text-slate-500" role="status">
        Comprobando tu sesion...
      </p>
    </div>
  )
}
