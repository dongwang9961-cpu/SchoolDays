import { apiGet, apiPost } from "./client.js";

export function listNotificationProviders(tenantId) {
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/notification-providers`);
}

export function startGmailConnection(tenantId, returnUrl = window.location.href) {
  return apiPost(`/api/tenants/${encodeURIComponent(tenantId)}/notification-providers/gmail/start`, { returnUrl });
}

export function sendNotification(tenantId, payload) {
  return apiPost(`/api/tenants/${encodeURIComponent(tenantId)}/notifications`, payload);
}

export function listNotificationHistory(tenantId) {
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/notification-history`);
}
