import { contextError, contextNote, escapeHtml, renderAuthPage } from "./authPage.js";
import { ApiError } from "./api/client.js";
import { getPublicSchool } from "./api/schools.js";
import { renderSchoolDashboard } from "./schoolAdminDashboard.js";
import "./styles.css";

const urlParams = new URLSearchParams(window.location.search);
const schoolRoute = getSchoolRouteFromPath(window.location.pathname);
const schoolSlug = schoolRoute.slug;
const schoolLookup = schoolSlug ? await loadSchool(schoolSlug) : { school: null, error: null };
const school = schoolLookup.school;
const schoolLoadError = schoolSlug && !school;
const initialMode = getInitialMode();

renderAuthPage({
  brandEyebrow: school ? school.name : "SchoolDays",
  brandTitle: school ? "Access your school account" : "School website",
  brandDescription: brandDescription(),
  contextMarkup: schoolContextMarkup(),
  initialMode,
  modes: modeOptions(),
  onAuthenticated: handleAuthenticated,
  tenantId: school?.tenantId || "",
});

function getSchoolRouteFromPath(pathname) {
  const segments = pathname.split("/").filter(Boolean);
  if (segments[0] !== "school" || !segments[1]) {
    return { slug: "", portal: "parent" };
  }
  const portal = segments[2] === "t" ? "teacher" : segments[2] === "admin" ? "admin" : "parent";
  return { slug: segments[1].toLowerCase(), portal };
}

async function loadSchool(slug) {
  try {
    return { school: await getPublicSchool(slug), error: null };
  } catch (error) {
    return { school: null, error };
  }
}

function getInitialMode() {
  if (schoolLoadError) {
    return "login";
  }
  if (urlParams.get("token")) {
    if (schoolRoute.portal === "teacher") {
      return "teacher";
    }
    if (schoolRoute.portal === "admin") {
      return "school";
    }
    return "complete";
  }
  return "login";
}

function modeOptions() {
  if (!school) {
    return [{ value: "login", label: "Sign in" }];
  }
  if (urlParams.get("token")) {
    return [{ value: getInitialMode(), label: completionModeLabel() }];
  }
  if (schoolRoute.portal === "teacher") {
    return [{ value: "login", label: "Teacher sign in" }];
  }
  if (schoolRoute.portal === "admin") {
    return [{ value: "login", label: "Admin sign in" }];
  }
  return [
    { value: "login", label: "Parent sign in" },
    { value: "register", label: "Parent register" },
  ];
}

function completionModeLabel() {
  if (schoolRoute.portal === "teacher") {
    return "Complete teacher invitation";
  }
  if (schoolRoute.portal === "admin") {
    return "Complete school invitation";
  }
  return "Complete parent registration";
}

function brandDescription() {
  if (school) {
    if (schoolRoute.portal === "teacher") {
      return urlParams.get("token")
        ? "Complete the remaining steps from your teacher invitation email."
        : "Teachers can sign in to this school.";
    }
    if (schoolRoute.portal === "admin") {
      return urlParams.get("token")
        ? "Complete the remaining steps from your school administrator invitation email."
        : "School administrators can sign in to this school.";
    }
    return urlParams.get("token")
      ? "Complete the remaining steps from your parent registration email."
      : "Parents can sign in or register for this school.";
  }
  if (schoolLoadError) {
    return schoolLookupErrorText();
  }
  return "Open a school URL like /school/longlong-art-studio to access that school website.";
}

function schoolContextMarkup() {
  if (school) {
    return contextNote(`${portalLabel()} portal: <strong>${escapeHtml(school.slug)}</strong>`);
  }
  if (schoolLoadError) {
    return contextError(schoolLookupErrorText());
  }
  return contextError("School is missing from the URL.");
}

function portalLabel() {
  if (schoolRoute.portal === "teacher") {
    return "Teacher";
  }
  if (schoolRoute.portal === "admin") {
    return "Admin";
  }
  return "Parent";
}

function schoolLookupErrorText() {
  if (schoolLookup.error instanceof ApiError && schoolLookup.error.status === 401) {
    localStorage.removeItem("schooldays.accessToken");
    return "The school lookup was rejected because the browser had stale sign-in state. Refresh this page and sign in again.";
  }
  if (schoolLookup.error instanceof ApiError && schoolLookup.error.status === 404) {
    return `School "${schoolSlug}" was not found. Run the backend with seed test data enabled or create a tenant with slug "longlong-art-studio".`;
  }
  if (schoolLookup.error) {
    return `School "${schoolSlug}" could not be loaded. Check the API server connection and try again.`;
  }
  return `School "${schoolSlug}" was not found.`;
}

function handleAuthenticated(response) {
  const role = school ? schoolPortalRole(response.user, school.tenantId) : "";
  if (role) {
    renderSchoolDashboard({
      role,
      school,
      user: response.user,
      onLogout: () => {
        localStorage.removeItem("schooldays.accessToken");
        returnToLoginPage();
      },
    });
    return;
  }

  document.querySelector("#root").innerHTML = `
    <main class="login-page">
      <section class="auth-panel standalone-panel">
        ${contextNote(`Signed in as <strong>${escapeHtml(response.user.email)}</strong>`)}
        <div class="form-heading">
          <h2>Account signed in</h2>
          <p>This account does not have a school role for ${escapeHtml(school?.name || "this school")}.</p>
        </div>
        <button data-return-login type="button">Return to sign in</button>
      </section>
    </main>
  `;

  document.querySelector("[data-return-login]").addEventListener("click", () => {
    localStorage.removeItem("schooldays.accessToken");
    returnToLoginPage();
  });
}

function returnToLoginPage() {
  window.location.replace(window.location.pathname);
}

function schoolPortalRole(user, tenantId) {
  if (schoolRoute.portal === "admin") {
    return hasSchoolRole(user, tenantId, "SCHOOL_ADMIN") ? "SCHOOL_ADMIN" : "";
  }
  if (schoolRoute.portal === "teacher") {
    return hasSchoolRole(user, tenantId, "TEACHER") ? "TEACHER" : "";
  }
  if (hasSchoolRole(user, tenantId, "PARENT")) {
    return "PARENT";
  }
  return "";
}

function hasSchoolRole(user, tenantId, role) {
  return (user.tenantRoles || []).some(
    (tenantRole) => tenantRole.tenantId === tenantId && tenantRole.role === role
  );
}
