import { apiGet } from "./client.js";

export function getPublicSchool(slug) {
  return apiGet(`/api/public/schools/${encodeURIComponent(slug)}`, { auth: false });
}
