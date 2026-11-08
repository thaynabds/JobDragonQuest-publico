# ADR-001: Monólito modular em vez de microsserviços

- **Situação:** Aceita
- **Desde:** v0.1.0

## Contexto

Há um usuário principal, uma pessoa desenvolvendo e um computador sem Docker. O projeto precisa evitar tanto um monólito desorganizado quanto uma fragmentação desnecessária.

## Decisão

Um serviço Spring Boot com módulos isolados por pacote (`identity`, `profiles`, `jobs`, `matching`, `applications`, `sources`, `notifications`, `documents`, `career`, `assist`, `audit`...). Cada módulo expõe uma fachada (`*Service`) e DTOs; nenhum acessa o repositório de outro.

## Alternativas consideradas

Microsserviços desde o início (custo de operação e de transação distribuída sem ganho para um usuário); monólito sem módulos (acoplamento).

## Consequências

Deploy e transações simples. Um módulo vira serviço quando carga ou equipe justificar; os primeiros candidatos são a coleta (`sources`) e o e-mail, por terem I/O externo e ritmo próprio.
