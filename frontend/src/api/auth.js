import { apiGet, apiPost, apiPostForm } from "./client.js";

export function login({ email, password }) {
  return apiPost("/api/auth/login", { email, password }, { auth: false });
}

export function completeRegistration(request) {
  return apiPost("/api/auth/complete-registration", request, { auth: false });
}

export function requestParentRegistrationLink({ tenantId, email }) {
  return apiPost("/api/auth/request-parent-registration-link", { tenantId, email }, { auth: false });
}

export function inviteUsers({ tenantId, role, emails, classId }) {
  return apiPost(`/api/tenants/${encodeURIComponent(tenantId)}/user-invitations`, {
    role,
    emails,
    classId,
  });
}

export function importExternalStudents({ tenantId, file }) {
  const formData = new FormData();
  formData.append("file", file);
  return apiPostForm(`/api/tenants/${encodeURIComponent(tenantId)}/external-students/import`, formData);
}

export function listExternalStudents({ tenantId, page = 1, pageSize = 25 }) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  });
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/external-students?${params.toString()}`);
}

export function getAuthConfig() {
  return apiGet("/api/auth/config", { auth: false });
}

export function startGoogleAuth({ tenantId }) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/auth/google/start?${params.toString()}`, { auth: false });
}

export function startGoogleRegistration({ tenantId }) {
  return startGoogleAuth({ tenantId });
}

export function getCurrentAuthUser() {
  return apiGet("/api/auth/me");
}

export function acceptTenantInvitation(request) {
  return apiPost("/api/auth/accept-tenant-invitation", request, { auth: false });
}

export function acceptTeacherInvitation(request) {
  return apiPost("/api/auth/accept-teacher-invitation", request, { auth: false });
}
