FROM node:20.19.1-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY . .
ARG VITE_API_URL=""
ARG VITE_PREVIEW_ENABLED="false"
ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_PREVIEW_ENABLED=${VITE_PREVIEW_ENABLED}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27.4-alpine
ENV API_GATEWAY_UPSTREAM=http://api-gateway.codegen-core.svc.cluster.local
ENV NGINX_ENVSUBST_FILTER=API_GATEWAY_UPSTREAM
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
