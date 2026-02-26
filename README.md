# iCLigo Notifications - Website

Este projeto foi convertido para **website** (em vez de app Android).

## O que faz
- Permite inserir `username` e `password` do portal `https://myoffice.icligo.com`.
- Faz login no portal e lê `https://myoffice.icligo.com/account/notifications`.
- Compara o conteúdo periodicamente por fingerprint (SHA-256).
- Quando há alteração, envia evento em tempo real para o browser e mostra notificação do navegador (se autorizada).

## Como executar
```bash
npm install
npm start
```

Depois abrir: `http://localhost:3000`

## Observações
- O monitor fica ativo enquanto o servidor Node estiver a correr.
- Cada sessão de monitorização fica em memória do servidor.
- Se o portal mudar o formulário de login, pode ser necessário ajustar `src/icligoClient.js`.
