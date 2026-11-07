# Política de segurança

## Versões com correção

Só a versão mais recente da `main` (a tag SemVer mais nova) recebe correções de segurança.

## Como relatar uma falha

Não abra issue pública para falha de segurança. Use o relato privado do GitHub:
**Security → Report a vulnerability** neste repositório. Inclua:

- o que acontece e qual o impacto (ex.: um usuário lê dado de outro, injeção, vazamento de token);
- os passos para reproduzir, de preferência contra uma instalação local (`scripts/instalar.sh`);
- a versão ou o commit testado.

Nunca teste contra a instalação de outra pessoa nem contra plataformas de terceiros (ATS, Discord, provedores de e-mail).

## O que esperar

- Confirmação do recebimento em até 7 dias.
- Correção em branch `hotfix/` com teste que reproduz a falha, nova versão de correção e crédito ao relato, se desejado.

## Defesas já existentes

O modelo de ameaças e as defesas estão em [docs/seguranca.md](docs/seguranca.md).
