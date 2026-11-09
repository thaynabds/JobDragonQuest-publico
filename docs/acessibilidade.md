# Checklist de acessibilidade do painel

Referência: WCAG 2.2 nível AA. ✅ coberto por teste automatizado (`a11y.test.tsx`, `A11yAccountSync.test.tsx` e testes das telas, que buscam elementos pelo papel e pelo nome acessível). 🔍 conferir à mão a cada mudança de tela.

| Item | Como | Situação |
|---|---|---|
| Link "Pular para o conteúdo" | `Layout.tsx` (`#conteudo`) | ✅ |
| Navegação só por teclado e foco visível | botões e links nativos; recurso "anel de foco" | 🔍 Tab por toda tela nova |
| Rótulo em todo campo e botão | testes usam `getByRole(..., { name })`: campo sem rótulo quebra o teste | ✅ |
| Contraste | temas claro, escuro, alto contraste e os do Congo; recurso "contraste" | 🔍 conferir cor nova com um medidor de contraste (4,5:1) |
| Não depender só de cor | a nota de compatibilidade vem com texto e recomendação | ✅ |
| Daltonismo | filtros protanopia, deuteranopia, tritanopia, escala de cinza e inversão | ✅ |
| Texto ampliável | 80% a 140% sem quebrar o layout | 🔍 conferir em 140% |
| Dislexia e leitura | fonte OpenDyslexic, entrelinha, espaçamento, guia de leitura | ✅ |
| Movimento | pausar animações e vídeos; respeita `prefers-reduced-motion` | ✅ |
| Avisos | pop-ups com papel `alert`/`status`, lidos por leitor de tela | ✅ |
| Idioma | `lang` da página segue o idioma escolhido (pt, en, es) | ✅ |
| Preferências salvas na conta | valem em qualquer navegador | ✅ |
| Leitor de tela | NVDA ou Orca nas telas principais | 🔍 a cada release |

## Widget

O painel usa o [AccessAbility Widget](https://github.com/thaynabds/accessability-widget), com versão fixada em `package.json`. O CI avisa quando há versão nova. As preferências ficam só no navegador; a fonte para dislexia é baixada de um CDN público apenas se a pessoa ligar esse recurso.

O widget injeta o próprio CSS, por isso a CSP do painel aceita `style-src 'unsafe-inline'`. Scripts continuam só do próprio site, e a única origem externa de fonte é o CDN usado pela fonte para dislexia.
