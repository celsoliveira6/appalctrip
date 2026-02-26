# ICLigo Notifications (Android)

Aplicação Android em Kotlin que faz login com username/password no site `myoffice.icligo.com` e monitoriza a página de notificações.

## Como funciona
- Guarda `username` e `password` localmente (SharedPreferences).
- Agenda um `WorkManager` periódico (mínimo Android: 15 minutos).
- Em cada execução:
  - abre a página de login,
  - envia o formulário de autenticação,
  - lê `https://myoffice.icligo.com/account/notifications`,
  - calcula uma fingerprint SHA-256 do conteúdo,
  - se mudou em relação à execução anterior, envia notificação no telemóvel.

## Limitações
- O intervalo mínimo nativo de `WorkManager` para tarefa periódica é 15 minutos.
- Se o site mudar os nomes dos campos de login de forma não-detectável automaticamente, pode ser necessário ajustar `SiteNotificationsScraper.kt`.

## Build
```bash
./gradlew assembleDebug
```
