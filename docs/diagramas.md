# Diagramas do JobDragonQuest

Diagramas em Mermaid, renderizados pelo GitHub. Eles refletem o código da v0.2.0 e as migrações Flyway V1 a V3.

## C4: nível 1, contexto

```mermaid
C4Context
  title JobDragonQuest: contexto do sistema
  Person(candidata, "Candidata", "Procura emprego ou estágio e acompanha as candidaturas")
  System(jobdragonquest, "JobDragonQuest", "Central de perfis, fontes vigiadas, vagas, compatibilidade e candidaturas")
  System_Ext(ats, "ATS públicos", "Greenhouse, Lever, SmartRecruiters e Ashby: API pública de vagas")
  System_Ext(assistidas, "Plataformas assistidas", "LinkedIn, Instagram, Facebook, Gupy: sem coleta automática permitida")
  System_Ext(email, "Gmail / Outlook", "Planejado (v0.3.0), por OAuth")
  Rel(candidata, jobdragonquest, "Usa o painel", "HTTP em 127.0.0.1")
  Rel(jobdragonquest, ats, "Coleta vagas", "HTTPS/JSON, só leitura")
  Rel(candidata, assistidas, "Confere vagas quando lembrada")
  Rel(jobdragonquest, email, "Lerá e classificará mensagens", "OAuth, escopos mínimos")
```

## C4: nível 2, contêineres

```mermaid
C4Container
  title JobDragonQuest: contêineres
  Person(candidata, "Candidata")
  System_Boundary(jobdragonquest, "JobDragonQuest (máquina local)") {
    Container(web, "Painel web", "React 19, TypeScript, Vite 7", "SPA em pt-BR/en. Token em memória, proxy /api")
    Container(api, "API", "Java 21, Spring Boot 4.1", "REST /api/v1, segurança, compatibilidade, candidaturas, agendador de coleta")
    ContainerDb(db, "Banco", "PostgreSQL 16", "Cluster do usuário em 127.0.0.1:5433, migrações Flyway")
  }
  System_Ext(ats, "ATS públicos", "Greenhouse, Lever, SmartRecruiters, Ashby")
  Rel(candidata, web, "Usa", "HTTP 127.0.0.1:5173")
  Rel(web, api, "Chama", "JSON via proxy /api")
  Rel(api, db, "Lê e grava", "JDBC/JPA")
  Rel(api, ats, "Coleta", "HTTPS, hosts fixos, sem redirecionamento")
```

## C4: nível 3, componentes da API

```mermaid
flowchart TB
    subgraph API["API (Spring Boot)"]
        identity["identity<br/>AuthController · AuthService · TokenService"]
        profiles["profiles<br/>ProfileService · ProfileSnapshot"]
        jobs["jobs<br/>JobService · UrlCanonicalizer"]
        matching["matching<br/>MatchingEngine"]
        applications["applications<br/>ApplicationService · ApplicationStateMachine"]
        sources["sources<br/>SourceService · SourceCollector · SourceScheduler"]
        collect["sources.collect<br/>AtsClient (4 adaptadores) · BoundedHttpFetcher · SkillCatalog"]
        dashboard["dashboard<br/>DashboardService"]
        audit["audit<br/>AuditService"]
    end
    applications --> jobs & profiles & matching
    sources --> profiles
    sources --> collect
    sources --> jobs & matching & applications
    dashboard --> applications & jobs & sources
    identity & profiles & jobs & applications & sources --> audit
```

## Diagrama de classes (domínio)

```mermaid
classDiagram
    direction LR
    class UserAccount {
      UUID id
      String email
      Role role
      String locale
      String timeZone
    }
    class Profile {
      UUID id
      String name
      Seniority seniority
      int minMatchScore
      List~String~ desiredTitles
      List~String~ locations
      Set~WorkMode~ workModes
      long version
    }
    class ProfileSkill {
      String name
      String normalized
      int level
      BigDecimal years
    }
    class JobPosting {
      UUID id
      String source
      String canonicalUrl
      String company
      String title
      WorkMode workMode
      JobStatus status
      UUID watchedSourceId
    }
    class JobRequirement {
      Kind kind
      String text
      String skill
      BigDecimal minYears
    }
    class JobApplication {
      UUID id
      Origin origin
      String company
      String title
      ApplicationState state
      Instant lastEvidenceAt
      Integer matchScore
      long version
      isStale()
    }
    class ApplicationEvent {
      EventKind kind
      ApplicationState fromState
      ApplicationState toState
      EventSource source
      String evidence
      BigDecimal confidence
      EventStatus status
    }
    class WatchedSource {
      UUID id
      SourceKind kind
      CollectionMode collectionMode
      String identifier
      String url
      int frequencyMinutes
      boolean enabled
      Instant nextRunAt
      Set~UUID~ profileIds
      recordSuccess()
      recordFailure()
    }
    class SourceRun {
      Trigger trigger
      RunStatus status
      int found
      int created
      int recommended
    }
    class MatchingEngine {
      evaluate(ProfileSnapshot, JobSnapshot) MatchResult
      rank(List~ProfileSnapshot~, JobSnapshot) List~MatchResult~
    }
    class ApplicationStateMachine {
      canTransition(from, to, source) boolean
    }
    UserAccount "1" --> "*" Profile
    Profile "1" *-- "*" ProfileSkill
    UserAccount "1" --> "*" JobPosting
    JobPosting "1" *-- "*" JobRequirement
    UserAccount "1" --> "*" JobApplication
    JobApplication "1" *-- "*" ApplicationEvent
    JobApplication "*" --> "0..1" JobPosting
    JobApplication "*" --> "0..1" Profile
    UserAccount "1" --> "*" WatchedSource
    WatchedSource "*" --> "*" Profile : vigia para
    WatchedSource "1" *-- "*" SourceRun
    JobPosting "*" --> "0..1" WatchedSource : encontrada por
    MatchingEngine ..> Profile
    MatchingEngine ..> JobPosting
    ApplicationStateMachine ..> JobApplication
```

## Modelo entidade-relacionamento (DER)

```mermaid
erDiagram
    app_user ||--o{ refresh_token : "tem"
    app_user ||--o{ profile : "tem"
    app_user ||--o{ job : "registra"
    app_user ||--o{ job_application : "registra"
    app_user ||--o{ watched_source : "vigia"
    profile ||--o{ profile_skill : ""
    profile ||--o{ profile_language : ""
    profile ||--o{ profile_entry : ""
    profile ||--o{ profile_title : ""
    profile ||--o{ profile_location : ""
    profile ||--o{ profile_work_mode : ""
    job ||--o{ job_requirement : ""
    job ||--o{ job_language : ""
    job_application ||--o{ application_event : "histórico"
    job |o--o{ job_application : "origina"
    profile |o--o{ job_application : "usado em"
    watched_source ||--o{ watched_source_profile : ""
    profile ||--o{ watched_source_profile : ""
    watched_source ||--o{ watched_source_keyword : ""
    watched_source ||--o{ source_run : "execuções"
    watched_source |o--o{ job : "encontra"

    app_user {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar role
        int failed_logins
        timestamptz locked_until
    }
    refresh_token {
        uuid id PK
        uuid user_id FK
        uuid family_id
        varchar token_hash UK
        timestamptz expires_at
        timestamptz revoked_at
    }
    profile {
        uuid id PK
        uuid user_id FK
        varchar name
        varchar seniority
        int min_match_score
        bigint version
    }
    job {
        uuid id PK
        uuid user_id FK
        uuid watched_source_id FK
        varchar url_hash
        varchar company_norm
        varchar title_norm
        varchar work_mode
        varchar status
    }
    job_application {
        uuid id PK
        uuid user_id FK
        uuid job_id FK
        uuid profile_id FK
        varchar origin
        varchar state
        timestamptz last_evidence_at
        varchar idempotency_key
        bigint version
    }
    application_event {
        uuid id PK
        uuid application_id FK
        varchar kind
        varchar to_state
        varchar source
        numeric confidence
        varchar status
    }
    watched_source {
        uuid id PK
        uuid user_id FK
        varchar kind
        varchar collection_mode
        varchar identifier
        varchar url
        varchar dedup_key
        int frequency_minutes
        boolean enabled
        timestamptz next_run_at
        timestamptz running_until
        bigint version
    }
    source_run {
        uuid id PK
        uuid source_id FK
        varchar trigger_type
        varchar status
        int found
        int created
    }
    audit_log {
        uuid id PK
        uuid user_id
        varchar action
        timestamptz created_at
    }
```

## Máquina de estados da candidatura

```mermaid
stateDiagram-v2
    direction LR
    state "Preparação" as prep {
        JOB_FOUND --> JOB_ANALYZED
        JOB_ANALYZED --> RECOMMENDED
        RECOMMENDED --> PREPARED
        PREPARED --> AWAITING_USER
    }
    prep --> SUBMITTED
    SUBMITTED --> CONFIRMED
    CONFIRMED --> Processo
    SUBMITTED --> Processo
    state "Processo (qualquer ordem)" as Processo {
        SCREENING
        RECRUITER_CONTACT
        INTERVIEW_SCHEDULED
        INTERVIEW_DONE
        TECH_TEST_PENDING
        TECH_TEST_DONE
        BEHAVIORAL_ASSESSMENT
        ADDITIONAL_STAGE
    }
    Processo --> OFFER_RECEIVED
    Processo --> APPROVED
    Processo --> REJECTED
    OFFER_RECEIVED --> APPROVED
    OFFER_RECEIVED --> REJECTED
    prep --> CLOSED
    Processo --> CLOSED
    APPROVED --> [*]
    REJECTED --> [*]
    CLOSED --> [*]
```

Fontes automáticas (e-mail, portal, agente, sistema) só seguem as setas acima e nunca saem de um estado final. A candidata (fonte MANUAL) pode corrigir qualquer etapa, inclusive reabrir.
