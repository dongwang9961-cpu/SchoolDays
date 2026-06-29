import { apiGet, apiPost } from "./client.js";

export function login({ email, password }) {
  return apiPost("/api/auth/login", { email, password }, { auth: false });
}

export function completeRegistration(request) {
  return apiPost("/api/auth/complete-registration", request, { auth: false });
}

export function requestParentRegistrationLink({ tenantId, email }) {
  return apiPost("/api/auth/request-parent-registration-link", { tenantId, email }, { auth: false });
}

export function startGoogleRegistration({ tenantId }) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/auth/google/start?${params.toString()}`, { auth: false });
}

export function acceptTenantInvitation(request) {
  return apiPost("/api/auth/accept-tenant-invitation", request, { auth: false });
}

export function acceptTeacherInvitation(request) {
  return apiPost("/api/auth/accept-teacher-invitation", request, { auth: false });
}
