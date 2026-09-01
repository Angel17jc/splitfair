import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import Button from '../../components/Button'
import Input from '../../components/Input'
import Modal from '../../components/Modal'
import Select from '../../components/Select'
import { aplicarErrorDeApi } from '../../utils/formularios'
import { useCrearGrupo } from './mutaciones'
import { MONEDAS, MONEDA_POR_DEFECTO } from './monedas'

const esquema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .max(100, 'El nombre no puede pasar de 100 caracteres'),
  description: z.string().trim().max(255, 'La descripcion es demasiado larga').optional(),
  currency: z.string().length(3),
})

type Datos = z.infer<typeof esquema>

const CAMPOS = ['name', 'description', 'currency'] as const

interface Props {
  abierto: boolean
  onCerrar: () => void
}

export default function CreateGroupModal({ abierto, onCerrar }: Props) {
  const navegar = useNavigate()
  const crear = useCrearGrupo()

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({
    resolver: zodResolver(esquema),
    defaultValues: { name: '', description: '', currency: MONEDA_POR_DEFECTO },
  })

  // Al cerrar se limpia el formulario. Si no, reabrirlo muestra lo que se
  // escribio la vez anterior, incluidos los errores de un intento fallido.
  useEffect(() => {
    if (!abierto) reset()
  }, [abierto, reset])

  const enviar = handleSubmit(async (datos) => {
    try {
      const grupo = await crear.mutateAsync({
        name: datos.name,
        description: datos.description || undefined,
        currency: datos.currency,
      })
      onCerrar()
      // Se entra directo al grupo recien creado: lo siguiente que quiere hacer
      // quien acaba de crearlo es invitar a alguien o anotar un gasto.
      navegar(`/grupos/${grupo.id}`)
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Nuevo grupo"
      pie={
        <>
          <Button variante="secundario" onClick={onCerrar} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button type="submit" form="form-grupo" cargando={isSubmitting}>
            {isSubmitting ? 'Creando...' : 'Crear grupo'}
          </Button>
        </>
      }
    >
      {/* El formulario tiene id porque su boton de envio vive en el pie del
          modal, fuera del <form>: el atributo form los conecta y mantiene el
          envio con Enter desde cualquier campo. */}
      <form id="form-grupo" onSubmit={enviar} className="space-y-4" noValidate>
        {errors.root && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {errors.root.message}
          </p>
        )}

        <Input
          etiqueta="Nombre"
          autoFocus
          placeholder="Piso compartido"
          error={errors.name?.message}
          {...register('name')}
        />

        <Input
          etiqueta="Descripcion (opcional)"
          placeholder="Alquiler y suministros"
          error={errors.description?.message}
          {...register('description')}
        />

        <Select
          etiqueta="Moneda"
          ayuda="Se fija ahora y no se puede cambiar despues."
          error={errors.currency?.message}
          {...register('currency')}
        >
          {MONEDAS.map((moneda) => (
            <option key={moneda.codigo} value={moneda.codigo}>
              {moneda.codigo} — {moneda.nombre}
            </option>
          ))}
        </Select>
      </form>
    </Modal>
  )
}
