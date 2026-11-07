import { request } from "./client";
import type {
  Application,
  ApplicationDetail,
  ApplicationEvent,
  ApplicationState,
  CreatedToken,
  BoardColumn,
  BulkCreateResult,
  BulkUpdateInput,
  CatalogEntry,
  CreateApplicationInput,
  EventSource,
  Job,
  JobInput,
  JobSummary,
  MatchResult,
  Me,
  NotificationItem,
  NotificationList,
  NotificationPreference,
  NotificationSettings,
  Page,
  PendingItem,
  PersonalToken,
  PreferencesView,
  Profile,
  ProfileInput,
  ProfileSummary,
  Source,
  SourceInput,
  SourceRun,
  Summary,
  TokenResponse,
  TransitionResult,
} from "./types";

const id = (value: string) => encodeURIComponent(value);

/** Monta a query string só com os filtros preenchidos. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const api = {
  register: (body: { email: string; password: string; displayName: string; locale: string; timeZone: string }) =>
    request<TokenResponse>("/api/v1/auth/register", { method: "POST", body, anonymous: true }),
  login: (body: { email: string; password: string }) =>
    request<TokenResponse>("/api/v1/auth/login", { method: "POST", body, anonymous: true }),
  logout: () => request<void>("/api/v1/auth/logout", { method: "POST", anonymous: true }),
  me: () => request<Me>("/api/v1/me"),

  summary: () => request<Summary>("/api/v1/dashboard/summary"),
  pending: () => request<PendingItem[]>("/api/v1/dashboard/pending"),

  profiles: () => request<ProfileSummary[]>("/api/v1/profiles"),
  profile: (profileId: string) => request<Profile>(`/api/v1/profiles/${id(profileId)}`),
  createProfile: (body: ProfileInput) => request<Profile>("/api/v1/profiles", { method: "POST", body }),
  updateProfile: (profileId: string, body: ProfileInput) =>
    request<Profile>(`/api/v1/profiles/${id(profileId)}`, { method: "PUT", body }),
  deleteProfile: (profileId: string) => request<void>(`/api/v1/profiles/${id(profileId)}`, { method: "DELETE" }),

  sourceCatalog: () => request<CatalogEntry[]>("/api/v1/sources/catalog"),
  sources: (profileId?: string) => request<Source[]>(`/api/v1/sources${query({ profileId })}`),
  createSources: (body: { sources: SourceInput[]; profileIds: string[]; frequencyMinutes: number; keywords: string[] }) =>
    request<BulkCreateResult>("/api/v1/sources/bulk", { method: "POST", body }),
  updateSources: (body: BulkUpdateInput) => request<Source[]>("/api/v1/sources/bulk-update", { method: "POST", body }),
  deleteSource: (sourceId: string) => request<void>(`/api/v1/sources/${id(sourceId)}`, { method: "DELETE" }),
  runSource: (sourceId: string) => request<SourceRun>(`/api/v1/sources/${id(sourceId)}/run`, { method: "POST" }),
  checkSource: (sourceId: string, body: { jobsRegistered: number; note: string | null }) =>
    request<SourceRun>(`/api/v1/sources/${id(sourceId)}/check`, { method: "POST", body }),
  sourceRuns: (sourceId: string) => request<SourceRun[]>(`/api/v1/sources/${id(sourceId)}/runs`),

  jobs: (params: { page?: number; size?: number; sourceId?: string }) =>
    request<Page<JobSummary>>(`/api/v1/jobs${query(params)}`),
  job: (jobId: string) => request<Job>(`/api/v1/jobs/${id(jobId)}`),
  createJob: (body: JobInput) => request<Job>("/api/v1/jobs", { method: "POST", body }),
  matches: (jobId: string) => request<MatchResult[]>(`/api/v1/jobs/${id(jobId)}/matches`),

  applications: (params: Record<string, string | number | boolean | undefined>) =>
    request<Page<Application>>(`/api/v1/applications${query(params)}`),
  board: (params: Record<string, string | undefined>) => request<BoardColumn[]>(`/api/v1/applications/board${query(params)}`),
  application: (applicationId: string) => request<ApplicationDetail>(`/api/v1/applications/${id(applicationId)}`),
  createApplication: (body: CreateApplicationInput, idempotencyKey: string) =>
    request<Application>("/api/v1/applications", { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } }),
  transition: (
    applicationId: string,
    body: { toState: ApplicationState; source: EventSource; evidence: string; nextAction?: string | null },
  ) => request<TransitionResult>(`/api/v1/applications/${id(applicationId)}/transitions`, { method: "POST", body }),
  addNote: (applicationId: string, text: string) =>
    request<ApplicationEvent>(`/api/v1/applications/${id(applicationId)}/notes`, { method: "POST", body: { text } }),
  acceptSuggestion: (applicationId: string, eventId: string) =>
    request<TransitionResult>(`/api/v1/applications/${id(applicationId)}/suggestions/${id(eventId)}/accept`, { method: "POST" }),
  dismissSuggestion: (applicationId: string, eventId: string) =>
    request<void>(`/api/v1/applications/${id(applicationId)}/suggestions/${id(eventId)}/dismiss`, { method: "POST" }),
  deleteApplication: (applicationId: string) =>
    request<void>(`/api/v1/applications/${id(applicationId)}`, { method: "DELETE" }),

  tokens: () => request<PersonalToken[]>("/api/v1/tokens"),
  createToken: (body: { name: string; days: number }) => request<CreatedToken>("/api/v1/tokens", { method: "POST", body }),
  revokeToken: (tokenId: string) => request<void>(`/api/v1/tokens/${id(tokenId)}`, { method: "DELETE" }),

  notifications: (params: { unreadOnly?: boolean; page?: number; size?: number }) =>
    request<NotificationList>(`/api/v1/notifications${query(params)}`),
  notificationsDue: () => request<NotificationItem[]>("/api/v1/notifications/due"),
  markDelivered: (ids: string[]) =>
    request<{ count: number }>("/api/v1/notifications/delivered", { method: "POST", body: { ids } }),
  markRead: (notificationId: string) =>
    request<NotificationItem>(`/api/v1/notifications/${id(notificationId)}/read`, { method: "POST" }),
  markAllRead: () => request<{ count: number }>("/api/v1/notifications/read-all", { method: "POST" }),
  snooze: (notificationId: string, minutes: number) =>
    request<NotificationItem>(`/api/v1/notifications/${id(notificationId)}/snooze`, { method: "POST", body: { minutes } }),
  testNotification: () => request<NotificationItem>("/api/v1/notifications/test", { method: "POST" }),
  notificationPreferences: () => request<PreferencesView>("/api/v1/notifications/preferences"),
  updateNotificationPreferences: (body: { settings?: NotificationSettings; preferences: NotificationPreference[] }) =>
    request<PreferencesView>("/api/v1/notifications/preferences", { method: "PUT", body }),
};
