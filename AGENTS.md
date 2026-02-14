# AGENTS.md

## Setup
- Windows: usar PowerShell
- Comandos:
  - Instalar deps: <SEU COMANDO>
  - Rodar testes: <SEU COMANDO>
  - Build: <SEU COMANDO>

## Regras de mudança (críticas)
- Mudanças pequenas e verificáveis; evitar refactors grandes sem pedir.
- Não alterar namespace/IDs/strings de localization sem listar impacto.
- Não adicionar telemetria/logs de PII.
- Sempre atualizar changelog/notes quando mexer em features.

## Review guidelines
- Checar null-safety, permissões, crashes em startup.
- Checar regressões de UI e de compatibilidade Wear OS (quando aplicável).
