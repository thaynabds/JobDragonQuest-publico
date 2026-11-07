// Tipos espelhando os DTOs da API REST v1 (docs/api.md). Datas chegam como texto ISO-8601 em UTC.

export type Seniority =
  | "APPRENTICE"
  | "INTERN"
  | "TRAINEE"
  | "ENTRY"
  | "JUNIOR"
  | "MID"
  | "SENIOR"
  | "SPECIALIST"
  | "LEAD"
  | "COORDINATOR"
  | "MANAGER"
  | "DIRECTOR"
  | "EXECUTIVE";
export type WorkMode = "REMOTE" | "HYBRID" | "ONSITE";
export type LanguageLevel = "A1" | "A2" | "B1" | "B2" | "C1" | "C2" | "NATIVE";
export type EntryKind =
  | "EXPERIENCE"
  | "ACADEMIC_EXPERIENCE"
  | "EDUCATION"
  | "PROJECT"
  | "COURSE"
  | "CERTIFICATION"
  | "VOLUNTEER"
  | "AWARD"
  | "PUBLICATION";
export type EmploymentType = "CLT" | "PJ" | "FULL_TIME" | "PART_TIME" | "INTERNSHIP" | "APPRENTICE" | "TRAINEE" | "TEMPORARY" | "FREELANCE" | "VOLUNTEER";
export type TravelAvailability = "NONE" | "OCCASIONAL" | "FREQUENT";
export type SalaryPeriod = "HOURLY" | "MONTHLY" | "YEARLY";
export type Degree = "HIGH_SCHOOL" | "TECHNICAL" | "TECHNOLOGIST" | "BACHELOR" | "LICENTIATE" | "POSTGRADUATE" | "MBA" | "MASTER" | "DOCTORATE" | "POSTDOC" | "OTHER";
export type EntryStatus = "IN_PROGRESS" | "COMPLETED" | "INCOMPLETE" | "PAUSED";

export const SENIORITIES: Seniority[] = ["APPRENTICE", "INTERN", "TRAINEE", "ENTRY", "JUNIOR", "MID", "SENIOR", "SPECIALIST", "LEAD", "COORDINATOR", "MANAGER", "DIRECTOR", "EXECUTIVE"];
export const WORK_MODES: WorkMode[] = ["REMOTE", "HYBRID", "ONSITE"];
export const LANGUAGE_LEVELS: LanguageLevel[] = ["A1", "A2", "B1", "B2", "C1", "C2", "NATIVE"];
export const ENTRY_KINDS: EntryKind[] = ["EXPERIENCE", "ACADEMIC_EXPERIENCE", "EDUCATION", "PROJECT", "COURSE", "CERTIFICATION", "VOLUNTEER", "AWARD", "PUBLICATION"];
export const EMPLOYMENT_TYPES: EmploymentType[] = ["CLT", "PJ", "FULL_TIME", "PART_TIME", "INTERNSHIP", "APPRENTICE", "TRAINEE", "TEMPORARY", "FREELANCE", "VOLUNTEER"];
export const TRAVEL_AVAILABILITIES: TravelAvailability[] = ["NONE", "OCCASIONAL", "FREQUENT"];
export const SALARY_PERIODS: SalaryPeriod[] = ["HOURLY", "MONTHLY", "YEARLY"];
export const DEGREES: Degree[] = ["HIGH_SCHOOL", "TECHNICAL", "TECHNOLOGIST", "BACHELOR", "LICENTIATE", "POSTGRADUATE", "MBA", "MASTER", "DOCTORATE", "POSTDOC", "OTHER"];
export const ENTRY_STATUSES: EntryStatus[] = ["IN_PROGRESS", "COMPLETED", "INCOMPLETE", "PAUSED"];

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
  months: number | null;
  days: number | null;
}

export interface SkillView extends Skill {
  totalYears: number | null;
}

/** Na edição, o idioma vai pelo nome por extenso; a API devolve o código ISO. */
export interface LanguageInput {
  name: string;
  level: LanguageLevel;
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
  location: string | null;
  employmentType: EmploymentType | null;
  current: boolean | null;
  degree: Degree | null;
  fieldOfStudy: string | null;
  status: EntryStatus | null;
  credentialId: string | null;
  expirationDate: string | null;
  hours: number | null;
  skills: string | null;
}

export interface Answer {
  question: string;
  answer: string;
}

/** Campos do perfil que não dependem do formato de idioma (iguais na edição e na resposta). */
export interface ProfileFields {
  name: string;
  objective: string | null;
  seniority: Seniority;
  minMatchScore: number;
  salaryExpectation: number | null;
  salaryCurrency: string | null;
  availability: string | null;
  restrictions: string | null;
  preferredLanguage: string;
  desiredTitles: string[];
  locations: string[];
  workModes: WorkMode[];
  entries: Entry[];
  headline: string | null;
  summary: string | null;
  currentTitle: string | null;
  city: string | null;
  stateRegion: string | null;
  country: string | null;
  requiresSponsorship: boolean | null;
  willingToRelocate: boolean | null;
  travelAvailability: TravelAvailability | null;
  noticePeriodDays: number | null;
  availableFrom: string | null;
  currentSalary: number | null;
  salaryPeriod: SalaryPeriod | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  websiteUrl: string | null;
  coverLetter: string | null;
  disability: boolean | null;
  disabilityDetails: string | null;
  workAuthorization: string[];
  employmentTypes: EmploymentType[];
  excludedCompanies: string[];
  includeKeywords: string[];
  excludeKeywords: string[];
  industries: string[];
  answers: Answer[];
}

export interface ProfileInput extends ProfileFields {
  skills: Skill[];
  languages: LanguageInput[];
  version?: number;
}

export interface Profile extends ProfileFields {
  id: string;
  skills: SkillView[];
  languages: Language[];
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
  headline: string | null;
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

export interface PersonalToken {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  active: boolean;
}

export interface CreatedToken {
  token: PersonalToken;
  value: string;
}

export type NotificationType =
  | "STAGE_CHANGED"
  | "ACTION_REQUIRED"
  | "TECH_TEST"
  | "INTERVIEW_REMINDER"
  | "DEADLINE_REMINDER"
  | "SUGGESTION_TO_REVIEW"
  | "SOURCE_FAILING"
  | "SOURCE_TO_CHECK"
  | "NO_UPDATE"
  | "NEW_JOBS"
  | "TEST";

export const NOTIFICATION_TYPES: NotificationType[] = [
  "STAGE_CHANGED",
  "ACTION_REQUIRED",
  "TECH_TEST",
  "INTERVIEW_REMINDER",
  "DEADLINE_REMINDER",
  "SUGGESTION_TO_REVIEW",
  "SOURCE_FAILING",
  "SOURCE_TO_CHECK",
  "NO_UPDATE",
  "NEW_JOBS",
  "TEST",
];

export type NotificationPriority = "HIGH" | "MEDIUM" | "LOW";

export interface NotificationItem {
  id: string;
  topic: string;
  type: NotificationType;
  priority: NotificationPriority;
  applicationId: string | null;
  sourceId: string | null;
  state: ApplicationState | null;
  company: string | null;
  title: string | null;
  detail: string | null;
  message: string;
  dueAt: string | null;
  itemCount: number | null;
  leadMinutes: number | null;
  popup: boolean;
  system: boolean;
  sound: boolean;
  quiet: boolean;
  createdAt: string;
  deliveredAt: string | null;
  readAt: string | null;
  snoozedUntil: string | null;
}

export interface NotificationList {
  items: NotificationItem[];
  unreadCount: number;
  page: number;
  size: number;
  totalItems: number;
}

export interface NotificationPreference {
  topic: string;
  enabled: boolean;
  popup: boolean;
  system: boolean;
  sound: boolean;
  leadMinutes: number[] | null;
  minScore: number | null;
  staleDays: number | null;
}

export interface NotificationSettings {
  quietEnabled: boolean;
  quietStart: string;
  quietEnd: string;
  quietAllowHigh: boolean;
  hideDetails: boolean;
}

export interface PreferenceView {
  topic: string;
  group: "STAGE" | "PENDING" | "SOURCE";
  key: string;
  label: string | null;
  priority: NotificationPriority;
  supportsLead: boolean;
  supportsStaleDays: boolean;
  supportsMinScore: boolean;
  preference: NotificationPreference;
}

export interface PreferencesView {
  settings: NotificationSettings;
  timeZone: string;
  preferences: PreferenceView[];
}
