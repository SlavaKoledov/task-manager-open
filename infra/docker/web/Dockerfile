FROM node:18-alpine

WORKDIR /app/apps/web

COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY apps/web ./

RUN npm run build

CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "4173"]