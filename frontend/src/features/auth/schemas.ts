import { z } from 'zod'

/**
 * Validacion de los formularios de acceso.
 *
 * Espejo de las restricciones del backend (`RegisterRequest`, `LoginRequest`).
 * Validar aqui no sustituye a validar alli —cualquiera puede saltarse el
 * navegador— pero evita un viaje al servidor para decir lo que se sabe antes
 * de salir: el usuario ve el error mientras escribe, no despues de esperar.
 *
 * Cuando ambas divergen manda el backend, y su mensaje llega en `fieldErrors`
 * y se pinta bajo el campo correspondiente.
 */

const email = z
  .string()
  .min(1, 'El email es obligatorio')
  .email('El email no tiene un formato valido')

export const esquemaDeAcceso = z.object({
  email,
  /**
   * Aqui no se exige longitud minima, y es deliberado aunque el registro si
   * la exija.
   *
   * Imponer la politica de contrasenas en el formulario de acceso bloquearia
   * a quien tenga una credencial anterior a la regla, que no podria ni
   * intentarlo; y de paso anuncia a cualquiera cual es la politica. Quien
   * decide si la contrasena vale es el servidor.
   */
  password: z.string().min(1, 'La contrasena es obligatoria'),
})

export const esquemaDeRegistro = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .max(100, 'El nombre no puede pasar de 100 caracteres'),
  email,
  password: z.string().min(8, 'La contrasena debe tener al menos 8 caracteres'),
})

export type DatosDeAcceso = z.infer<typeof esquemaDeAcceso>
export type DatosDeRegistro = z.infer<typeof esquemaDeRegistro>
