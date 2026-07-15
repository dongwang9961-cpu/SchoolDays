import { contextNote, renderAuthPage } from "./authPage.js";
import "./styles.css";

if (isSchoolEntryPoint()) {
  await import("./school.js");
} else {
  const urlParams = new URLSearchParams(window.location.search);
  const hasToken = Boolean(urlParams.get("token"));
  const initialMode = hasToken ? "school" : "login";

  renderAuthPage({
    brandEyebrow: "SchoolDays",
    brandTitle: "Access the platform",
    brandDescription: hasToken
      ? "Complete the remaining steps from your school administrator invitation email."
      : "Sign in as a platform user.",
    contextMarkup: contextNote("Platform root"),
    initialMode,
    modes: hasToken
      ? [{ value: "school", label: "Complete school invitation" }]
      : [{ value: "login", label: "Sign in" }],
    tenantId: "",
  });
}

function isSchoolEntryPoint() {
  return window.location.pathname.startsWith("/school/") || Boolean(schoolSlugFromSubdomain());
}

function schoolSlugFromSubdomain() {
  const hostname = window.location.hostname.toLowerCase();
  const suffix = ".schooldays.cc";
  if (!hostname.endsWith(suffix)) {
    return "";
  }
  const subdomain = hostname.slice(0, -suffix.length);
  if (!subdomain || ["www", "api"].includes(subdomain) || subdomain.includes(".")) {
    return "";
  }
  return subdomain;
}
