# Build do app Angular
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx serve o app e encaminha /api ao backend: mesma origem, sem CORS
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/mydly/browser /usr/share/nginx/html
EXPOSE 80
