// Tipos espelhando os DTOs da API REST v1 (docs/api.md). Datas chegam como texto ISO-8601 em UTC.

export type Seniority = "INTERN" | "JUNIOR" | "MID" | "SENIOR" | "LEAD";
export type WorkMode = "REMOTE" | "HYBRID" | "ONSITE";
export type LanguageLevel = "A1" | "A2" | "B1" | "B2" | "C1" | "C2" | "NATIVE";
export type EntryKind = "EXPERIENCE" | "ACADEMIC_EXPERIENCE" | "EDUCATION" | "PROJECT" | "COURSE" | "CERTIFICATION";

export const SENIORITIES: Seniority[] = ["INTERN", "JUNIOR", "MID", "SENIOR", "LEAD"];
export const WORK_MODES: WorkMode[] = ["REMOTE", "HYBRID", "ONSITE"];
export const LANGUAGE_LEVELS: LanguageLevel[] = ["A1", "A2", "B1", "B2", "C1", "C2", "NATIVE"];
export const ENTRY_KINDS: EntryKind[] = ["EXPERIENCE", "ACADEMIC_EXPERIENCE", "EDUCATION", "PROJECT", "COURSE", "CERTIFICATION"];

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Me {
  id: string;
  email: string;
  displayName: string;
  role: string;
  locale: string;
  timeZone: string;
}

export interface Skill {
  name: string;
  level: number;
  years: number | null;
}

export interface Language {
  code: string;
  level: LanguageLevel;
}

export interface Entry {
  kind: EntryKind;
  title: string;
  organization: string | null;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
  url: string | null;
}

export interface ProfileInput {
  name: string;
  objective: string | null;
  seniority: Seniority;
  minMatchScore: number;
  salaryExpectation: number | null;
  salaryCurrency: string | null;
  availability: string | null;
  restrictions: string | null;
  preferredLanguage: "pt-BR" | "en";
  desiredTitles: string[];
  locations: string[];
  workModes: WorkMode[];
  skills: Skill[];
  languages: Language[];
  entries: Entry[];
  version?: number;
}

export interface Profile extends ProfileInput {
  id: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ProfileSummary {
  id: string;
  name: string;
  seniority: Seniority;
  desiredTitles: string[];
  skillCount: number;
}

export type JobStatus = "OPEN" | "CLOSED" | "UNKNOWN";
export type RequirementKind = "MANDATORY" | "DESIRED";

export interface Requirement {
  kind: RequirementKind;
  text: string;
  skill: string | null;
  minYears: number | null;
}

export interface JobLanguage {
  code: string;
  minLevel: LanguageLevel;
  required: boolean;
}

export interface JobInput {
  source: string;
  url: string | null;
  company: string;
  title: string;
  location: string | null;
  country: string | null;
  workMode: WorkMode | null;
  seniority: Seniority | null;
  description: string | null;
  requirements: Requirement[];
  languages: JobLanguage[];
}

export interface Job extends JobInput {
  id: string;
  status: JobStatus;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: string | null;
  foundAt: string;
  updatedAt: string;
  watchedSourceId: string | null;
}

export interface JobSummary {
  id: string;
  company: string;
  title: string;
  location: string | null;
  workMode: WorkMode | null;
  status: JobStatus;
  source: string;
  url: string | null;
  foundAt: string;
  watchedSourceId: string | null;
}

export type Outcome = "MET" | "PARTIAL" | "UNMET" | "NOT_EVALUATED";
export type Recommendation = "APPLY" | "REVIEW" | "SKIP" | "INSUFFICIENT_DATA";

export interface MatchResult {
  profileId: string;
  profileName: string;
  score: number | null;
  recommendation: Recommendation;
  requirements: { kind: RequirementKind; text: string; skill: string | null; outcome: Outcome; reason: string }[];
  blockers: { code: string; message: string }[];
  strengths: string[];
  warnings: string[];
  titleAffinity: boolean;
  summary: string;
}

export const APPLICATION_STATES = [
  "JOB_FOUND",
  "JOB_ANALYZED",
  "RECOMMENDED",
  "PREPARED",
  "AWAITING_USER",
  "SUBMITTED",
  "CONFIRMED",
  "SCREENING",
  "RECRUITER_CONTACT",
  "INTERVIEW_SCHEDULED",
  "INTERVIEW_DONE",
  "TECH_TEST_PENDING",
  "TECH_TEST_DONE",
  "BEHAVIORAL_ASSESSMENT",
  "ADDITIONAL_STAGE",
  "OFFER_RECEIVED",
  "APPROVED",
  "REJECTED",
  "CLOSED",
] as const;
export type ApplicationState = (typeof APPLICATION_STATES)[number];

export const ORIGINS = ["MANUAL", "LINK", "EMAIL", "BROWSER_EXTENSION", "IMPORT", "SOCIAL", "REFERRAL", "AUTOMATION"] as const;
export type Origin = (typeof ORIGINS)[number];
export type EventSource = "MANUAL" | "EMAIL" | "PORTAL" | "AGENT" | "SYSTEM" | "EXTENSION";

export interface Application {
  id: string;
  jobId: string | null;
  profileId: string | null;
  origin: Origin;
  platform: string | null;
  company: string;
  title: string;
  url: string | null;
  country: string | null;
  appliedAt: string | null;
  state: ApplicationState;
  stateChangedAt: string;
  lastEvidenceAt: string;
  stale: boolean;
  nextAction: string | null;
  nextDueAt: string | null;
  matchScore: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ApplicationEvent {
  id: string;
  kind: "STATE_CHANGE" | "NOTE" | "SUGGESTION";
  fromState: ApplicationState | null;
  toState: ApplicationState | null;
  source: EventSource;
  evidence: string;
  confidence: number;
  status: "APPLIED" | "PENDING" | "ACCEPTED" | "DISMISSED";
  occurredAt: string;
  createdAt: string;
}

export interface ApplicationDetail {
  application: Application;
  events: ApplicationEvent[];
}

export interface BoardColumn {
  state: ApplicationState;
  count: number;
  cards: Application[];
}

export interface CreateApplicationInput {
  origin: Origin;
  platform?: string | null;
  company?: string | null;
  title?: string | null;
  url?: string | null;
  country?: string | null;
  appliedAt?: string | null;
  state?: ApplicationState | null;
  notes?: string | null;
  jobId?: string | null;
  profileId?: string | null;
}

export interface TransitionResult {
  outcome: "APPLIED" | "SUGGESTED" | "NOTED";
  application: Application;
  event: ApplicationEvent;
}

export type PendingType =
  | "ACTION_REQUIRED"
  | "TECH_TEST"
  | "INTERVIEW_SOON"
  | "DEADLINE_SOON"
  | "SUGGESTION_TO_REVIEW"
  | "SOURCE_TO_CHECK"
  | "SOURCE_FAILING"
  | "NO_UPDATE";

export interface PendingItem {
  type: PendingType;
  priority: "HIGH" | "MEDIUM" | "LOW";
  applicationId: string | null;
  eventId: string | null;
  sourceId: string | null;
  company: string;
  title: string;
  message: string;
  dueAt: string | null;
  url: string | null;
  suggestedState: ApplicationState | null;
  confidence: number | null;
  detail: string | null;
}

export interface Summary {
  jobsFound: number;
  applicationsTotal: number;
  submitted: number;
  inProgress: number;
  interviewsScheduled: number;
  testsPending: number;
  offers: number;
  approved: number;
  rejected: number;
  stale: number;
  pendingItems: number;
  sourcesWatched: number;
  sourcesToCheck: number;
  byState: Record<ApplicationState, number>;
}

export type SourceKind =
  | "GREENHOUSE"
  | "LEVER"
  | "SMARTRECRUITERS"
  | "ASHBY"
  | "LINKEDIN"
  | "INSTAGRAM"
  | "FACEBOOK"
  | "INDEED"
  | "GLASSDOOR"
  | "GUPY"
  | "WORKDAY"
  | "CAREER_PAGE"
  | "OTHER";
export type CollectionMode = "AUTOMATIC" | "ASSISTED";

export interface CatalogEntry {
  kind: SourceKind;
  label: string;
  mode: CollectionMode;
  needsIdentifier: boolean;
}

export interface Source {
  id: string;
  name: string;
  kind: SourceKind;
  kindLabel: string;
  collectionMode: CollectionMode;
  identifier: string | null;
  url: string | null;
  openUrl: string | null;
  frequencyMinutes: number;
  enabled: boolean;
  profileIds: string[];
  keywords: string[];
  nextRunAt: string;
  lastRunAt: string | null;
  lastStatus: "OK" | "FAILED" | "CHECKED" | null;
  lastMessage: string | null;
  lastFound: number | null;
  consecutiveFailures: number;
  due: boolean;
  running: boolean;
  createdAt: string;
  version: number;
}

export interface SourceInput {
  name: string;
  kind: SourceKind;
  identifier?: string | null;
  url?: string | null;
}

export interface BulkCreateResult {
  created: Source[];
  duplicates: { index: number; existingId: string | null; name: string }[];
}

export interface BulkUpdateInput {
  sourceIds: string[];
  enabled?: boolean;
  frequencyMinutes?: number;
  setProfileIds?: string[];
  addProfileIds?: string[];
  removeProfileIds?: string[];
}

export interface SourceRun {
  id: string;
  trigger: "SCHEDULED" | "MANUAL";
  status: "OK" | "FAILED" | "CHECKED";
  found: number;
  created: number;
  recommended: number;
  message: string | null;
  startedAt: string;
  finishedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
