# ADR-002: Java 21 e Spring Boot 4.1

- **Situação:** Aceita
- **Desde:** v0.1.0

## Contexto

Preferência declarada pela autora e necessidade de um ecossistema maduro de segurança. A linha 3.5 do Spring Boot saiu do suporte aberto.

## Decisão

Java 21 com Spring Boot 4.1 (Spring Security 7, OAuth2 Resource Server, Hibernate 7) e Maven Wrapper.

## Alternativas consideradas

Spring Boot 3.5 (sem correções de segurança abertas: dívida desde o primeiro dia); Node ou Python no backend (menos ferramentas de segurança prontas para o caso).

## Consequências

Versão estável atual com correções; build reproduzível pelo `./mvnw` sem Maven instalado.
