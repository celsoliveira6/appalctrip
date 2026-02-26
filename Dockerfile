FROM node:22-alpine
WORKDIR /app

COPY package.json ./
COPY public ./public
COPY src ./src
COPY README.md ./README.md

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

CMD ["node", "src/server.js"]
