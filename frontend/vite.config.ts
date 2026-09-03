import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
  },
  test: {
    // jsdom y no el entorno de node: los tests de rutas y formularios
    // necesitan un DOM real donde React monte y donde el usuario "pulse".
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    // Los tests no hablan con el backend: eso ya se verifica en un navegador
    // real contra el servidor real. Aqui se cubre la logica que un recorrido
    // por la interfaz no distingue, como que una suma cuadre al centimo.
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
