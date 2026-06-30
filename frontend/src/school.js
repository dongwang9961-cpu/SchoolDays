import { contextError, contextNote, escapeHtml, renderAuthPage } from "./authPage.js";
import { ApiError } from "./api/client.js";
import { getPublicSchool } from "./api/schools.js";
import { bestSchoolRole, renderSchoolDashboard } from "./schoolAdminDashboard.js";
import "./styles.css";

const urlParams = new URLSearchParams(window.location.search);
const schoolSlug = getSchoolSlugFromPath(window.location.pathname);
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

function getSchoolSlugFromPath(pathname) {
  const segments = pathname.split("/").filter(Boolean);
  return segments[0] === "school" && segments[1] ? segments[1].toLowerCase() : "";
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
    return "complete";
  }
  return "login";
}

function modeOptions() {
  if (!school) {
    return [{ value: "login", label: "Sign in" }];
  }
  return [
    { value: "login", label: "Sign in" },
    { value: "register", label: "Parent register" },
    { value: "complete", label: "Complete parent registration" },
    { value: "teacher", label: "Teacher invite" },
  ];
}

function brandDescription() {
  if (school) {
    return "Parents can register for this school, and teachers can accept invitations for this school.";
  }
  if (schoolLoadError) {
    return schoolLookupErrorText();
  }
  return "Open a school URL like /school/longlong-art-studio to access that school website.";
}

function schoolContextMarkup() {
  if (school) {
    return contextNote(`School website: <strong>${escapeHtml(school.slug)}</strong>`);
  }
  if (schoolLoadError) {
    return contextError(schoolLookupErrorText());
  }
  return contextError("School is missing from the URL.");
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
  const role = school ? bestSchoolRole(response.user, school.tenantId) : "";
  if (role) {
    renderSchoolDashboard({
      role,
      school,
      user: response.user,
      onLogout: () => {
        localStorage.removeItem("schooldays.accessToken");
        window.location.reload();
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
    window.location.reload();
  });
}
