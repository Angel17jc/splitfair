# Node 22 LTS: Vite 8 exige ^20.19.0 || >=22.12.0
FROM node:22-alpine
WORKDIR /app

# Se copia el lockfile aparte para aprovechar la cache de capas de Docker:
# las dependencias solo se reinstalan si package*.json cambia.
COPY package.json package-lock.json ./

# npm ci instala exactamente lo del lockfile (reproducible), a diferencia de npm install
RUN npm ci

COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]
