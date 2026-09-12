FROM node:20.19.1-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --ignore-scripts --no-audit --no-fund
COPY --chown=node:node index.js ./
EXPOSE 8080
USER node
CMD ["node", "index.js"]
