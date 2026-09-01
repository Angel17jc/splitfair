import { useState } from 'react'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import Modal from '../components/Modal'
import { useAuth } from '../features/auth/useAuth'

export default function Dashboard() {
  const { usuario } = useAuth()
  const [explicando, setExplicando] = useState(false)

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Mis grupos</h1>
          <p className="mt-1 text-sm text-slate-500">Hola, {usuario?.name}.</p>
        </div>
      </div>

      {/*
        El listado real llega en la fase de grupos. Hasta entonces esta es la
        pantalla honesta: no hay grupos que mostrar todavia, y el usuario ve
        que hacer a continuacion en vez de un hueco en blanco.
      */}
      <EmptyState
        titulo="Todavia no tienes ningun grupo"
        descripcion="Un grupo es donde se registran los gastos compartidos y se calcula quien debe a quien."
        accion={
          <Button variante="secundario" onClick={() => setExplicando(true)}>
            Como funciona
          </Button>
        }
      />

      <Modal
        abierto={explicando}
        onCerrar={() => setExplicando(false)}
        titulo="Como funciona SplitFair"
        pie={
          <Button variante="secundario" onClick={() => setExplicando(false)}>
            Entendido
          </Button>
        }
      >
        <p>
          Cada gasto se reparte entre los miembros del grupo, a partes iguales o como
          decidas. SplitFair lleva la cuenta de quien ha adelantado dinero y quien debe, y
          propone el minimo de pagos necesarios para dejar el grupo a cero.
        </p>
      </Modal>
    </>
  )
}
