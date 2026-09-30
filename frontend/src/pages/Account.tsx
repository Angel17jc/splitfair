import { useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../components/Button'
import Card from '../components/Card'
import Input from '../components/Input'
import Modal from '../components/Modal'
import { ApiError } from '../api/errors'
import { useAuth } from '../features/auth/useAuth'
import {
  useCambiarContrasena,
  useCambiarNombre,
  useDarDeBajaCuenta,
} from '../features/account/hooks'

/**
 * Pantalla de cuenta: nombre, contrasena y baja.
 *
 * Las tres operaciones existian en la API desde la Fase 1 y no tenian
 * interfaz. La de baja es la que mas se notaba: una funcion de proteccion de
 * datos que solo se puede ejercer por API esta a medias para un usuario real.
 */
export default function Account() {
  const { usuario } = useAuth()

  if (!usuario) return null

  return (
    <>
      <nav className="mb-4 text-sm">
        <Link to="/dashboard" className="text-slate-500 underline-offset-2 hover:underline">
          ← Mis grupos
        </Link>
      </nav>

      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Tu cuenta</h1>
        <p className="mt-1 text-sm text-slate-500">{usuario.email}</p>
      </header>

      <div className="max-w-xl space-y-6">
        <CambiarNombre nombreActual={usuario.name} />
        <CambiarContrasena />
        <DarDeBaja />
      </div>
    </>
  )
}

/** El correo no se cambia por aqui: es el identificador con el que se entra. */
function CambiarNombre({ nombreActual }: { nombreActual: string }) {
  const { renombrar } = useAuth()
  const cambiar = useCambiarNombre(renombrar)
  const [nombre, setNombre] = useState(nombreActual)
  const [error, setError] = useState<string | null>(null)
  const [guardado, setGuardado] = useState(false)

  const enviar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    setError(null)
    setGuardado(false)

    if (nombre.trim().length < 2) {
      setError('El nombre debe tener al menos 2 caracteres')
      return
    }

    try {
      await cambiar.mutateAsync(nombre.trim())
      setGuardado(true)
    } catch (fallo) {
      setError(fallo instanceof ApiError ? fallo.message : 'No se pudo guardar el nombre.')
    }
  }

  return (
    <Card como="section">
      <h2 className="text-base font-medium text-slate-900">Nombre</h2>
      <p className="mt-1 text-sm text-slate-500">
        Es el que ven los demas miembros en los gastos y los balances.
      </p>

      <form onSubmit={enviar} className="mt-4 space-y-4" noValidate>
        <Input
          etiqueta="Nombre"
          value={nombre}
          onChange={(e) => {
            setNombre(e.target.value)
            setGuardado(false)
          }}
          error={error ?? undefined}
        />

        {guardado && (
          <p role="status" className="text-sm text-emerald-700">
            Nombre actualizado.
          </p>
        )}

        <Button type="submit" cargando={cambiar.isPending} disabled={nombre === nombreActual}>
          {cambiar.isPending ? 'Guardando...' : 'Guardar'}
        </Button>
      </form>
    </Card>
  )
}

/**
 * Cambiar la contrasena **cierra todas las sesiones, incluida esta**.
 *
 * Se avisa antes y no despues: quien la cambia suele hacerlo porque sospecha
 * que alguien mas tiene acceso, y esa revocacion es justo lo que busca. Pero
 * verse expulsado sin haberlo leido parece un fallo de la aplicacion.
 */
function CambiarContrasena() {
  const { salir } = useAuth()
  const cambiar = useCambiarContrasena()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [error, setError] = useState<string | null>(null)

  const enviar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    setError(null)

    if (nueva.length < 8) {
      setError('La contrasena nueva debe tener al menos 8 caracteres')
      return
    }

    try {
      await cambiar.mutateAsync({ currentPassword: actual, newPassword: nueva })
      // El refresh token ya esta revocado, asi que la sesion esta muerta
      // aunque el access token aun aguante sus quince minutos. Se cierra aqui
      // para que el usuario vuelva a entrar ahora, y no dentro de un rato con
      // un 401 que no espera.
      await salir()
    } catch (fallo) {
      setError(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cambiar la contrasena.',
      )
    }
  }

  return (
    <Card como="section">
      <h2 className="text-base font-medium text-slate-900">Contrasena</h2>
      <p className="mt-1 text-sm text-slate-500">
        Al cambiarla se <strong>cierran todas las sesiones</strong>, tambien esta. Tendras que
        volver a entrar.
      </p>

      <form onSubmit={enviar} className="mt-4 space-y-4" noValidate>
        <Input
          etiqueta="Contrasena actual"
          type="password"
          autoComplete="current-password"
          value={actual}
          onChange={(e) => setActual(e.target.value)}
        />
        <Input
          etiqueta="Contrasena nueva"
          type="password"
          autoComplete="new-password"
          ayuda="Al menos 8 caracteres."
          value={nueva}
          onChange={(e) => setNueva(e.target.value)}
          error={error ?? undefined}
        />

        <Button type="submit" cargando={cambiar.isPending} disabled={!actual || !nueva}>
          {cambiar.isPending ? 'Cambiando...' : 'Cambiar contrasena'}
        </Button>
      </form>
    </Card>
  )
}

/**
 * Baja de cuenta.
 *
 * Se explica que **no se borra el historico** antes de pedir confirmacion. No
 * es letra pequena: alguien que se da de baja esperando que sus gastos
 * desaparezcan del grupo tiene que enterarse antes, no despues. Y el motivo es
 * comprensible dicho en una linea: si sus apuntes se fueran, las cuentas de
 * los demas dejarian de cuadrar.
 */
function DarDeBaja() {
  const { salir } = useAuth()
  const baja = useDarDeBajaCuenta()
  const [confirmando, setConfirmando] = useState(false)
  const [contrasena, setContrasena] = useState('')
  const [error, setError] = useState<string | null>(null)

  const confirmar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    setError(null)

    try {
      await baja.mutateAsync({ currentPassword: contrasena })
      await salir()
    } catch (fallo) {
      setError(fallo instanceof ApiError ? fallo.message : 'No se pudo dar de baja la cuenta.')
    }
  }

  return (
    <Card como="section">
      <h2 className="text-base font-medium text-slate-900">Dar de baja la cuenta</h2>
      <p className="mt-1 text-sm text-slate-500">
        Se eliminan tu nombre y tu correo, y la cuenta queda inutilizable. Los gastos y pagos en
        los que participaste <strong>se conservan</strong> de forma anonima: si desaparecieran,
        las cuentas de tus grupos dejarian de cuadrar.
      </p>

      <div className="mt-4">
        <Button variante="peligro" onClick={() => setConfirmando(true)}>
          Dar de baja
        </Button>
      </div>

      {confirmando && (
        <Modal
          abierto
          onCerrar={() => {
            setConfirmando(false)
            setContrasena('')
            setError(null)
          }}
          titulo="Dar de baja la cuenta"
          pie={
            <>
              <Button
                variante="secundario"
                onClick={() => setConfirmando(false)}
                disabled={baja.isPending}
              >
                Cancelar
              </Button>
              {/*
                "Confirmar baja" y no "Dar de baja": con el dialogo abierto
                habria dos botones con el mismo nombre accesible en la misma
                pantalla —el de la tarjeta y este—, y quien navega con lector
                no tendria forma de distinguir cual confirma.
              */}
              <Button
                variante="peligro"
                type="submit"
                form="form-baja"
                cargando={baja.isPending}
              >
                {baja.isPending ? 'Dando de baja...' : 'Confirmar baja'}
              </Button>
            </>
          }
        >
          <form id="form-baja" onSubmit={confirmar} className="space-y-4" noValidate>
            <p className="text-sm text-slate-700">
              Esta accion <strong>no se puede deshacer</strong>. Para continuar, escribe tu
              contrasena.
            </p>

            <Input
              etiqueta="Contrasena"
              type="password"
              autoComplete="current-password"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              error={error ?? undefined}
            />
          </form>
        </Modal>
      )}
    </Card>
  )
}
