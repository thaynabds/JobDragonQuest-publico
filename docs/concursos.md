# Acompanhamento de concursos públicos

Este documento descreve os dados de um concurso, onde as notícias oficiais são publicadas e como o JobDragonQuest vai
acompanhar cada etapa. É o requisito da funcionalidade: a implementação segue o que está aqui.

As referências legais são para orientar o desenho do produto, não são aconselhamento jurídico. O edital de cada concurso
prevalece sobre qualquer regra geral descrita aqui.

## 1. Objetivo

A pessoa cadastra os concursos em que tem interesse ou está inscrita e o sistema:

1. mostra todas as etapas, com datas e prazos;
2. vigia as páginas oficiais informadas e avisa quando algo muda;
3. lê editais de convocação e listas de resultado em HTML ou PDF, procurando o nome e o número de inscrição da pessoa;
4. avisa sobre prazos: inscrição, isenção, recurso, envio de documentos, prova, posse.

Regra central: **o sistema nunca afirma que a pessoa foi chamada**. Ele informa "possível menção encontrada", com o trecho
e o link da fonte, para ela conferir no documento oficial. Nomes iguais existem, e um aviso falso ou perdido tem custo alto.

## 2. Dados de um concurso

| Grupo | Dados |
|---|---|
| Identificação | nome, órgão, esfera (federal, estadual, municipal, distrital), UF, cidade, banca, número do edital, ano |
| Cargo | cargo, área ou especialidade, nível (fundamental, médio, técnico, superior), jornada, remuneração, vagas imediatas, cadastro de reserva, cotas (ampla, PcD, pessoas negras, indígenas, quilombolas) |
| Inscrição | período, taxa, vencimento do boleto, período de isenção, número de inscrição, situação (pretendida, feita, paga, deferida, indeferida) |
| Prova | data, horário, cidade, local, sala, cartão de confirmação |
| Resultado | notas, classificação geral, classificação na cota, situação (aprovado, classificado, eliminado) |
| Validade | data da homologação, prazo de validade, prorrogação |
| Links | site do concurso, edital, página de comunicados, diário oficial, área do candidato |
| Arquivos | edital e retificações, comprovantes, cartão de confirmação, gabaritos |

### Dados pessoais

- **Guardados:** nome como consta na inscrição, número de inscrição, modalidade de concorrência.
- **Nunca guardados para busca:** CPF, RG, senha da área do candidato. Listas públicas podem trazer CPF mascarado, e o
  sistema não precisa dele para encontrar a pessoa.
- A condição de PcD e a cota são dados sensíveis: só a pessoa vê, e nunca vão para serviço de terceiros.
- Trechos encontrados em listas podem citar outras pessoas. O aviso guarda só a linha que contém a correspondência.

## 3. Etapas

Nem todo concurso tem todas as etapas: a pessoa liga as que o edital prevê.

| Fase | Etapas |
|---|---|
| Antes do edital | autorização, comissão organizadora, contratação da banca |
| Edital | edital de abertura, retificações, impugnação do edital |
| Inscrição | pedido de isenção, resultado da isenção, recurso da isenção, inscrição, pagamento, homologação das inscrições, atendimento especial |
| Provas | cartão de confirmação, prova objetiva, prova discursiva, gabarito preliminar, recurso, gabarito definitivo, resultado preliminar, resultado definitivo |
| Etapas seguintes | prova prática, títulos, teste físico, avaliação psicológica, heteroidentificação, perícia multiprofissional, exames médicos, investigação social, curso de formação |
| Fim | resultado final, homologação, validade, prorrogação |
| Chamada | convocação, nomeação, entrega de documentos, posse, exercício, tornar sem efeito, remanejamento para o fim da fila |

Cada etapa guarda: tipo, título, data ou período, prazo, situação (prevista, aberta, concluída, perdida, não se aplica),
link da fonte e observação.

### Regras que afetam os avisos

- A validade do concurso é de até dois anos, prorrogável uma vez por igual período (Constituição, art. 37, III). A
  chamada pode vir muito tempo depois da prova, por isso a vigilância segue até o fim da validade.
- Na esfera federal, a posse ocorre em até 30 dias da publicação da nomeação, e o exercício em até 15 dias da posse (Lei
  8.112/1990, arts. 13 e 15). Estados e municípios têm estatutos próprios: o prazo é um campo editável.
- A Lei 14.965/2024 traz normas gerais de concursos. Cotas e reserva de vagas variam por lei e por edital.
- Perder a publicação pode significar perder a vaga. Por isso o prazo do aviso é calculado a partir da data da publicação,
  e não da data em que o sistema a encontrou.

## 4. Como as notícias oficiais são publicadas

| Canal | Onde | Formato | Como acompanhar |
|---|---|---|---|
| Diário Oficial da União | in.gov.br | HTML e PDF | busca pública por termo |
| Diários estaduais | ex.: Diário Oficial de Pernambuco (CEPE) | PDF, às vezes HTML | link informado pela pessoa |
| Diários municipais | site de cada prefeitura, associações de municípios | PDF | link informado; Querido Diário quando a cidade for coberta |
| Bancas | Cebraspe, FGV, FCC, Vunesp, IBFC, Idecan, Quadrix, Consulplan, Iades, AOCP, UPENET/IAUPE e outras | página do concurso com lista de PDFs | vigiar a página específica do concurso |
| Site do órgão | página de concursos, "seleções", "gestão de pessoas" | HTML e PDF | vigiar o link |
| Área do candidato | site da banca, com login | privado | **não acessado**: a pessoa confere |
| E-mail e SMS | banca ou órgão | texto | não substitui a publicação oficial; o sistema não apaga mensagens |
| Anais e publicações | casas legislativas, anais de eventos usados como títulos | PDF ou página | vigiar o link específico, quando o edital apontar |
| Agregadores | sites de notícias de concursos | HTML | só como pista: nunca é prova de convocação |

O ponto difícil é que a chamada costuma sair como um PDF novo, com título genérico ("Edital nº 12"), numa página que muda
pouco, e às vezes em um site diferente do da inscrição. Por isso cada concurso aceita **vários links vigiados**,
inclusive endereços específicos dados pela pessoa.

## 5. Como o sistema vai vigiar

1. A pessoa cadastra páginas por concurso.
2. Em cada rodada, o sistema baixa a página com o buscador protegido (`SafeWebFetcher`) e compara com a versão anterior.
3. Links novos para PDF ou HTML na mesma página são baixados, com limite de tamanho e de quantidade por rodada.
4. O texto é extraído e comparado, sem acento e sem diferença de maiúsculas, com o nome da inscrição e com o número de
   inscrição.
5. Cada documento é processado uma vez, identificado pelo hash do conteúdo.
6. O resultado vira aviso:
   - **Página mudou:** há novidade, sem indício sobre a pessoa.
   - **Possível menção:** número de inscrição ou nome completo encontrado. Número de inscrição tem confiança alta; nome
     completo tem confiança média, pelo risco de homônimo.
   - **Prazo próximo:** aviso 7 dias, 3 dias e 1 dia antes.
   - **Falha na leitura:** PDF digitalizado sem texto ou página fora do ar. A pessoa é avisada de que aquele documento
     **não foi lido**, para que a ausência de aviso nunca seja entendida como "não foi chamada".

## 6. Limites e segurança

| Risco | Medida |
|---|---|
| SSRF por link informado | apenas http/https, bloqueio de redes internas e metadados, verificação a cada redirecionamento |
| PDF malicioso ou gigante | limite de bytes, páginas e tempo; processamento sem executar conteúdo |
| Texto de documento dando ordens | conteúdo externo é só dado: nunca vira instrução |
| Sobrecarga dos sites oficiais | intervalo mínimo entre rodadas, poucas requisições, respeito a `robots.txt` e aos termos de uso |
| CAPTCHA, login, bloqueio | a página é marcada como "não vigiável" e a pessoa acompanha manualmente; nunca se contorna |
| Acesso entre contas | toda consulta filtra pelo usuário; recurso de outro usuário responde 404 |
| Exposição de terceiros | só a linha com a correspondência é guardada |
| Memória do servidor gratuito | uma rodada por vez e documentos em sequência, com limite baixo por execução |

Fora do escopo: entrar na área do candidato, fazer inscrição, pagar boleto, enviar recurso e reconhecer texto em imagem
(OCR) na primeira versão.

## 7. Modelo de dados planejado

- `contest`: identificação, cargo, inscrição, validade, notas, nome buscado, número de inscrição, dono.
- `contest_stage`: etapa, período, prazo, situação, fonte.
- `contest_watch`: URL, último hash, última leitura, situação da leitura.
- `contest_document`: hash, URL, data, situação de leitura (lido, sem texto, falhou).
- `contest_match`: documento, tipo (inscrição, nome), confiança, linha encontrada, confirmado pela pessoa.

## 8. Critérios de aceite

- Criar, editar e excluir concurso e etapas, com painel em português, inglês e espanhol.
- Vários links vigiados por concurso.
- Um documento novo com o número de inscrição gera aviso de confiança alta, com a linha e o link.
- Nome igual gera aviso de confiança média, com alerta de homônimo.
- PDF sem texto gera aviso de "não lido".
- O mesmo documento não gera aviso duas vezes.
- Endereços internos são recusados.
- Outra conta recebe 404.
