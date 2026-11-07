# Histórias de usuário do JobDragonQuest

Padrão: **Como** [ator], **quero** [ação] **para que** [valor]. Os critérios de aceite estão em Gherkin (Dado / Quando / Então). Situação de cada história:
- ✅ implementada e testada na v0.2.0.
- ⬜ planejada.

---

### US01 Entrar com segurança ✅ · [RF01]

**Como** candidata, **quero** criar minha conta e voltar ao painel sem digitar a senha toda hora **para que** eu acesse meus dados com rapidez e segurança.

```gherkin
Cenário: Cadastro e sessão renovada
  Dado que não tenho conta
  Quando me cadastro com e-mail e uma senha de 12 caracteres
  Então vejo a visão geral
  E ao reabrir o painel a sessão continua sem pedir a senha

Cenário: Tentativas erradas
  Dado que tenho conta
  Quando erro a senha cinco vezes seguidas
  Então a conta fica bloqueada por 15 minutos
```

### US02 Manter vários perfis profissionais ✅ · [RF02] [RF03]

**Como** candidata, **quero** manter perfis separados (Java, Estágio, Internacional) **para que** cada vaga seja comparada com o perfil certo, sem misturar informações.

```gherkin
Cenário: Criar perfil com competências
  Dado que estou na tela de perfis
  Quando crio o perfil "Desenvolvedora Java" com Java nível 4 e a modalidade híbrida
  Então o perfil aparece na lista com as competências declaradas

Cenário: Edição concorrente
  Dado que editei o perfil em outra aba
  Quando salvo a versão antiga
  Então vejo o aviso de versão desatualizada e nada é sobrescrito
```

### US03 Vigiar várias fontes para vários perfis de uma vez ✅ · [RF13] [RF14]

**Como** candidata, **quero** cadastrar de uma vez vários sites e redes sociais e ligá-los a mais de um perfil **para que** eu não precise configurar fonte por fonte, perfil por perfil.

```gherkin
Cenário: Cadastro em lote
  Dado os perfis "Desenvolvedora Java" e "Estágio"
  Quando cadastro GitLab (Greenhouse), uma busca do LinkedIn e uma hashtag do Instagram marcando os dois perfis
  Então as três fontes são criadas ligadas aos dois perfis

Cenário: Edição em lote
  Dado várias fontes selecionadas
  Quando escolho "Pausar"
  Então todas ficam pausadas

Cenário: Fonte repetida
  Dado que já vigio o board "nubank"
  Quando cadastro "NUBANK" de novo
  Então ele aparece como já existente e não é recriado
```

### US04 Entender a compatibilidade com cada vaga ✅ · [RF06] [RF07] [RF08]

**Como** candidata, **quero** ver a nota e o motivo de cada requisito **para que** eu decida com clareza onde vale me candidatar.

```gherkin
Cenário: Análise explicada
  Dado uma vaga com Java obrigatório e Kafka desejável
  Quando abro a vaga
  Então vejo, para cada perfil, a nota, a recomendação e o motivo de cada requisito

Cenário: Impedimento eliminatório
  Dado um perfil que aceita só trabalho remoto
  Quando a vaga é presencial em outra cidade
  Então a recomendação é "Não recomendada" mesmo com nota alta

Cenário: Obrigatório parcial
  Dado uma vaga que pede 5 anos de Spring Boot
  Quando meu perfil declara 1 ano
  Então a recomendação é no máximo "Revisar"
```

### US05 Registrar candidaturas feitas em qualquer lugar ✅ · [RF05] [RF09]

**Como** candidata, **quero** registrar candidaturas que fiz no LinkedIn, no site da empresa ou no Instagram **para que** nenhuma fique de fora do acompanhamento.

```gherkin
Cenário: Candidatura do LinkedIn
  Dado que me candidatei pelo LinkedIn
  Quando registro empresa, cargo e link
  Então a candidatura aparece no Kanban como "Enviada"

Cenário: Duplicada
  Dado que já registrei essa vaga
  Quando registro o mesmo link com rastreio diferente
  Então o sistema abre a candidatura existente
```

### US06 Acompanhar cada processo pela evidência ✅ · [RF10] [RF12] [RF19]

**Como** candidata, **quero** registrar o que aconteceu em cada etapa **para que** eu tenha o histórico completo e saiba o que veio de onde.

```gherkin
Cenário: Mudança de etapa
  Dado uma candidatura enviada
  Quando registro "Teste técnico pendente" com a evidência do e-mail
  Então o histórico mostra a etapa, a origem, a confiança e a evidência

Cenário: Silêncio não é reprovação
  Dado uma candidatura sem novidade há mais de 21 dias
  Quando abro o painel
  Então ela aparece como "Sem atualização" e continua na mesma etapa
```

### US07 Decidir sobre mudanças incertas ✅ · [RF11]

**Como** candidata, **quero** confirmar mudanças que o sistema não tem certeza **para que** nenhuma etapa mude por engano.

```gherkin
Cenário: Sugestão de baixa confiança
  Dado uma informação automática com confiança 0,55
  Quando ela chega
  Então vira uma sugestão e a etapa não muda

Cenário: Aceitar sugestão
  Dado uma sugestão pendente
  Quando eu aceito
  Então a etapa muda e o histórico registra o aceite
```

### US08 Não duplicar ao reenviar ✅ · [RF05]

**Como** candidata, **quero** que reenviar o mesmo formulário não crie registros repetidos **para que** meu painel continue confiável.

```gherkin
Cenário: Reenvio com a mesma chave
  Dado que enviei o cadastro com uma chave de idempotência
  Quando a mesma requisição chega de novo
  Então recebo o mesmo registro, sem duplicar
```

### US09 Receber vagas de ATS automaticamente ✅ · [RF15]

**Como** candidata, **quero** que o sistema busque sozinho as vagas dos boards que escolhi **para que** eu veja as oportunidades sem visitar cada página.

```gherkin
Cenário: Coleta real
  Dado uma fonte Greenhouse do board "gitlab" com a palavra-chave "Engineer"
  Quando a coleta roda
  Então as vagas relevantes aparecem na lista, ligadas à fonte

Cenário: Sem duplicar
  Dado que a fonte já foi coletada
  Quando a coleta roda de novo
  Então nenhuma vaga é recriada

Cenário: Uma coleta por vez
  Dado uma coleta em andamento
  Quando peço "Coletar agora"
  Então vejo que já existe uma coleta em andamento
```

### US10 Ser lembrada de conferir redes sociais ✅ · [RF16]

**Como** candidata, **quero** ser lembrada de conferir LinkedIn, Instagram e Facebook **para que** eu acompanhe essas fontes sem violar as regras delas.

```gherkin
Cenário: Lembrete
  Dado uma fonte do LinkedIn com frequência diária
  Quando chega a hora
  Então ela aparece nas pendências com o link

Cenário: Conferida
  Dado o lembrete na lista
  Quando marco como conferida
  Então ele sai da lista e o próximo é agendado
```

### US11 Saber o que depende de mim ✅ · [RF17]

**Como** candidata, **quero** uma lista do que precisa da minha ação **para que** eu não perca testes, entrevistas e prazos.

```gherkin
Cenário: Central de pendências
  Dado um teste técnico pendente e uma fonte a conferir
  Quando abro a visão geral
  Então vejo as duas pendências com a ação ao lado, ordenadas por prioridade
```

### US12 Ver tudo num Kanban ✅ · [RF18]

**Como** candidata, **quero** ver minhas candidaturas por etapa e filtrar por perfil, empresa, plataforma e país **para que** eu entenda rápido o que está avançando.

```gherkin
Cenário: Kanban filtrado
  Dado candidaturas de perfis diferentes
  Quando filtro pelo perfil "Estágio"
  Então só vejo as candidaturas desse perfil, em colunas por etapa
```

### US13 Usar o painel em inglês ✅ · [RF20]

**Como** candidata que se candidata no exterior, **quero** usar o painel em inglês **para que** eu possa mostrá-lo a recrutadores internacionais.

```gherkin
Cenário: Troca de idioma
  Dado o painel em português
  Quando escolho "English"
  Então menus, rótulos e frases do sistema aparecem em inglês
```

### US14 Classificar e-mails de processos seletivos ⬜ · [RF21]

**Como** candidata, **quero** que o sistema leia os e-mails de recrutadores e atualize as candidaturas **para que** eu não precise fazer isso à mão.

```gherkin
Cenário: Convite para entrevista
  Dado uma candidatura registrada na Sensedia
  Quando chega um e-mail da Sensedia convidando para entrevista
  Então a etapa vai para "Entrevista agendada" com o e-mail como evidência
  E nenhum e-mail é apagado
```

### US15 Revisar a candidatura antes de enviar ⬜ · [RF22] [RF23]

**Como** candidata, **quero** uma revisão das perguntas eliminatórias e da candidatura inteira **para que** eu evite erros que desclassificam.

```gherkin
Cenário: Pergunta eliminatória
  Dado o formulário com "Você reside em São Paulo?"
  Quando peço a análise
  Então vejo que a pergunta parece eliminatória, o requisito ligado e as respostas compatíveis com meus dados reais
```
