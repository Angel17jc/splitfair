import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * Cada test arranca con el DOM vacio.
 *
 * Es el equivalente al TRUNCATE del backend: sin esta limpieza los
 * componentes de un test siguen montados en el siguiente, las consultas por
 * texto encuentran dos coincidencias y aparecen fallos que dependen del orden
 * de ejecucion, que son los peores de diagnosticar.
 */
afterEach(() => {
  cleanup()
})
