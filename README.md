# iCLigo Notifications - Website

Este projeto é um website para monitorizar notificações do portal iCLigo.

## O que faz
- Recebe `username` e `password` no browser.
- Faz login em `https://myoffice.icligo.com/account/login`.
- Lê `https://myoffice.icligo.com/account/notifications`.
- Calcula fingerprint SHA-256 da página para detetar alterações.
- Quando muda, envia evento em tempo real (SSE) e dispara notificação do browser (se permitida).

## Executar localmente
```bash
npm start
```

Abrir: `http://localhost:3000`

## Modo publicável (deploy)

### Opção A: Render (recomendado)
1. Criar novo serviço **Web Service** no Render ligado a este repositório.
2. O ficheiro `render.yaml` já define:
   - `startCommand: node src/server.js`
   - `PORT=3000`
3. Após deploy, usar o URL público HTTPS gerado pelo Render.

### Opção B: Docker (VPS, Coolify, Fly, etc.)
Build da imagem:
```bash
docker build -t icligo-notifications .
```
Run:
```bash
docker run -p 3000:3000 --name icligo-notifications icligo-notifications
```

## Endpoints úteis
- `GET /` -> interface web
- `GET /healthz` -> health check
- `POST /api/start`
- `POST /api/stop/:id`
- `GET /api/status/:id`
- `GET /api/events/:id`

## Notas de produção
- Em produção, usar sempre HTTPS (Render já fornece).
- As sessões de monitorização estão em memória; reinício do servidor limpa monitores ativos.
- Se o formulário de login do iCLigo mudar, pode ser necessário ajustar `src/icligoClient.js`.
