# Changelog — HyprLink Android

Todas as alterações relevantes da aplicação Android do HyprLink são registadas neste ficheiro.
O formato baseia-se em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e respeita [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [0.1.3] — 2026-10-29 (versionCode 65)

### Adicionado
- **Organização de ficheiros recebidos por tipo no PC:**
  - As capturas de ecrã sincronizadas automaticamente são enviadas com `kind="screenshot"` e gravadas pelo daemon em `~/Transferências/HyprLink/Screenshot/`.
  - Os ficheiros partilhados manualmente continuam a ser gravados em `~/Transferências/HyprLink/Transferidos/`.
  - Requer o daemon do PC com o commit `bf29670` ou mais recente (com versões anteriores do daemon, todos os ficheiros são guardados na pasta base de transferências sem erros).

### Melhorias e Limpeza Técnica
- Limpeza de código morto e compatibilidade retroativa para SDK <= 33 (Prompt 83).
- Migração de APIs obsoletas do Android (`ResolutionSelector`, `DisplayManager`, `VibrationAttributes`).
- Encerramento limpo de leituras assíncronas do Kwik sem interrupções de thread.
- Remoção do sinalizador desnecessário `largeHeap`.
- Redução de avisos do compilador Kotlin para apenas 2 avisos upstream.

## [0.1.2] — 2026-10-29 (versionCode 64)

### Corrigido
- Ajuste de resolução e rotação da câmara em serviços de segundo plano sem Activity associada.
- Otimizações de compilação release com R8/ProGuard.

## [0.1.1] — 2026-10-28 (versionCode 63)

### Adicionado
- Novo applicationId oficial `io.maggio.hyprlink`.
- Suporte a múltiplos modos de áudio e streaming de webcam via QUIC.
- Atalhos rápidos e sincronização bidirecional de clipboard.
