import { contextNote, renderAuthPage } from "./authPage.js";
import "./styles.css";

if (window.location.pathname.startsWith("/school/")) {
  await import("./school.js");
} else {
const urlParams = new URLSearchParams(window.location.search);
const initialMode = urlParams.get("token") ? "school" : "login";

renderAuthPage({
  brandEyebrow: "SchoolDays",
  brandTitle: "Access the platform",
  brandDescription: "Sign in as a platform user or accept a school administrator invitation.",
  contextMarkup: contextNote("Platform root"),
  initialMode,
  modes: [
    { value: "login", label: "Sign in" },
    { value: "school", label: "School admin invite" },
  ],
  tenantId: "",
});
}
