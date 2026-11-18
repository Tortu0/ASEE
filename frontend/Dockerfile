# Usamos Node.js 22 (ligero) como base
FROM node:22-alpine

WORKDIR /app

# Copiamos primero los archivos de dependencias
COPY package.json package-lock.json ./

# Instalamos solo las dependencias de producción
RUN npm ci --omit=dev

# Copiamos el resto del código (las carpetas gateway, public, mock, etc.)
COPY . .

# Exponemos el puerto del gateway
EXPOSE 8080

# El comando para arrancar el servidor real (el que conecta con el backend Java)
CMD ["npm", "start"]