import { apiGet, apiPatch, apiPost } from "./client.js";

export function getProfile() {
  return apiGet("/api/me/profile");
}

export function updateProfile(request) {
  return apiPatch("/api/me/profile", request);
}

export function changePassword(request) {
  return apiPost("/api/me/password", request);
}
