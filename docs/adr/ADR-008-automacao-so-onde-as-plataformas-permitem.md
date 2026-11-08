# ADR-008: Automação só onde as plataformas permitem

- **Situação:** Aceita
- **Desde:** v0.2.0

## Contexto

LinkedIn, Indeed e Glassdoor proíbem scraping e automação nos termos de uso (RN08).

## Decisão

Coleta automática só em APIs públicas de ATS (Greenhouse, Lever, SmartRecruiters, Ashby). Nas demais, as entradas são link, extensão do navegador (ação da pessoa), importação e lembretes de conferência. CAPTCHA, MFA e bloqueios nunca são contornados. Detalhes em [integracoes.md](../integracoes.md).

## Alternativas consideradas

Scraping com navegador automatizado (viola os termos e quebra com frequência).

## Consequências

Menos fontes automáticas, mas nenhuma conta da pessoa em risco.
