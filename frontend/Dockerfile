# Imagen del dashboard: compila el front con Node y lo sirve con nginx.
# Se usa desde docker-compose.yml. Cada cambio en el front requiere: docker compose up -d --build

# Compilación: misma versión mayor de Node que en Versiones.md
FROM node:26-alpine AS compilacion
WORKDIR /fuente
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Ejecución: solo nginx y los archivos compilados
FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /fuente/dist /usr/share/nginx/html
