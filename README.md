# Ronda de Segurança Android

Primeira versão nativa para teste no CFA Guarulhos.

## Funcionamento

- Abre a interface atual de ronda em tela cheia.
- Recebe o início e o encerramento da ronda pela interface.
- Mantém um serviço de localização ativo com notificação permanente.
- Registra posições a cada 15 segundos ou após deslocamento mínimo.
- Continua funcionando com a tela bloqueada.
- Envia as posições para o Apps Script e para a planilha `GPS_TRAJETO`.
- Informa objetivamente a permanência sem movimento a partir de cinco minutos.

## Abrir e gerar o APK

1. Abra a pasta `RondaSegurancaAndroid` no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Conecte o celular por USB e autorize a depuração.
4. Use `Build > Build APK(s)`.
5. O arquivo será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Primeiro uso

Autorize localização precisa e notificações. Inicie a primeira ronda estando na portaria. Durante o teste, confirme que a notificação `Ronda em andamento` permanece visível após bloquear a tela.
