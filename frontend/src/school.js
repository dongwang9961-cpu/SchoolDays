import { contextError, contextNote, escapeHtml, renderAuthPage } from "./authPage.js";
import { updateProfile } from "./api/account.js";
import { getCurrentAuthUser } from "./api/auth.js";
import { ApiError } from "./api/client.js";
import { getPublicSchool } from "./api/schools.js";
import { renderSchoolDashboard } from "./schoolAdminDashboard.js";
import QRCode from "qrcode";
import "./styles.css";

const urlParams = new URLSearchParams(window.location.search);
consumeAccessTokenHash();
const schoolRoute = getSchoolRouteFromPath(window.location.pathname);
const schoolSlug = schoolRoute.slug;
const schoolLookup = schoolSlug ? await loadSchool(schoolSlug) : { school: null, error: null };
const school = schoolLookup.school;
const schoolLoadError = schoolSlug && !school;
const initialMode = getInitialMode();
const portalQrMarkup = school ? await portalQrCodeMarkup() : "";
const currentAuth = school && !urlParams.get("token") ? await loadExistingSession() : null;
let authenticatedBackGuardEnabled = false;

if (currentAuth) {
  handleAuthenticated(currentAuth);
} else {
  renderAuthPage({
    brandEyebrow: school ? school.name : "SchoolDays",
    brandTitle: school ? "Access your school account" : "School website",
    brandDescription: brandDescription(),
    contextMarkup: schoolContextMarkup(portalQrMarkup),
    allowGoogleLogin: Boolean(school && schoolRoute.portal === "parent" && !urlParams.get("token")),
    initialMode,
    modes: modeOptions(),
    onAuthenticated: handleAuthenticated,
    tenantId: school?.tenantId || "",
  });
}

function consumeAccessTokenHash() {
  const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : "";
  const hashParams = new URLSearchParams(hash);
  const accessToken = hashParams.get("accessToken");
  if (!accessToken) {
    return;
  }
  localStorage.setItem("schooldays.accessToken", accessToken);
  hashParams.delete("accessToken");
  const nextHash = hashParams.toString();
  const nextUrl = window.location.pathname + window.location.search + (nextHash ? `#${nextHash}` : "");
  window.history.replaceState(null, "", nextUrl);
}

function enableAuthenticatedBackGuard() {
  if (authenticatedBackGuardEnabled || !window.history?.pushState) {
    return;
  }
  authenticatedBackGuardEnabled = true;
  const portalUrl = authenticatedPortalUrl();
  window.history.replaceState({ schooldaysAuthGuard: true }, "", portalUrl);
  window.history.pushState({ schooldaysAuthGuard: true }, "", portalUrl);
  window.addEventListener("popstate", handleAuthenticatedBackNavigation);
}

function disableAuthenticatedBackGuard() {
  if (!authenticatedBackGuardEnabled) {
    return;
  }
  authenticatedBackGuardEnabled = false;
  window.removeEventListener("popstate", handleAuthenticatedBackNavigation);
}

function handleAuthenticatedBackNavigation() {
  if (!authenticatedBackGuardEnabled || !localStorage.getItem("schooldays.accessToken")) {
    return;
  }
  window.history.pushState({ schooldaysAuthGuard: true }, "", authenticatedPortalUrl());
}

function authenticatedPortalUrl() {
  const url = new URL(window.location.href);
  url.searchParams.delete("token");
  url.searchParams.delete("reset");
  return url.pathname + url.search + url.hash;
}

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

async function loadExistingSession() {
  if (!localStorage.getItem("schooldays.accessToken")) {
    return null;
  }
  try {
    return { user: await getCurrentAuthUser() };
  } catch (error) {
    localStorage.removeItem("schooldays.accessToken");
    return null;
  }
}

function getInitialMode() {
  if (schoolLoadError) {
    return "login";
  }
  if (urlParams.get("token")) {
    if (isPasswordResetLink()) {
      return "reset";
    }
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
  if (isPasswordResetLink()) {
    return "Reset password";
  }
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
        ? isPasswordResetLink()
          ? "Reset your teacher account password from the secure email link."
          : "Complete the remaining steps from your teacher invitation email."
        : "Teachers can sign in to this school.";
    }
    if (schoolRoute.portal === "admin") {
      return urlParams.get("token")
        ? isPasswordResetLink()
          ? "Reset your administrator account password from the secure email link."
          : "Complete the remaining steps from your school administrator invitation email."
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

function isPasswordResetLink() {
  return urlParams.get("reset") === "1";
}

function schoolContextMarkup(portalQrMarkup = "") {
  if (school) {
    return `
      ${contextNote(`${portalLabel()} portal: <strong>${escapeHtml(school.slug)}</strong>`)}
      ${portalQrMarkup}
    `;
  }
  if (schoolLoadError) {
    return contextError(schoolLookupErrorText());
  }
  return contextError("School is missing from the URL.");
}

async function portalQrCodeMarkup() {
  const portalUrl = `${window.location.origin}${window.location.pathname}${window.location.search}`;
  const qrDataUrl = await QRCode.toDataURL(portalUrl, {
    errorCorrectionLevel: "M",
    margin: 1,
    width: 220,
  });
  return `
    <section class="portal-qr-panel" aria-label="Portal QR code">
      <div class="portal-qr-copy">
        <strong>Scan to open this portal</strong>
        <span>${escapeHtml(portalLabel())} access on a phone</span>
      </div>
      <div class="portal-qr-code">
        <img alt="${escapeHtml(portalLabel())} portal QR code" src="${qrDataUrl}" />
      </div>
    </section>
  `;
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
    enableAuthenticatedBackGuard();
    if (role === "PARENT" && parentProfileIncomplete(response.user)) {
      renderParentProfileCompletion(response);
      return;
    }
    renderSchoolDashboard({
      role,
      school,
      user: response.user,
      onLogout: () => {
        endAuthenticatedSession();
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
    endAuthenticatedSession();
  });
}

function parentProfileIncomplete(user) {
  return !user?.phone || user.phone === "__google_profile_pending__";
}

function renderParentProfileCompletion(response) {
  document.querySelector("#root").innerHTML = `
    <main class="login-page">
      <section class="auth-panel standalone-panel">
        ${contextNote(`Signed in with Google as <strong>${escapeHtml(response.user.email)}</strong>`)}
        <form class="auth-form" data-parent-profile-completion>
          <div class="form-heading">
            <h2>Complete parent profile</h2>
            <p>Phone and home address are required before opening the parent portal.</p>
          </div>

          <label>
            <span>Phone <span class="required-marker" aria-label="required">*</span></span>
            <input autocomplete="tel" maxlength="50" name="phone" placeholder="555-0100" required type="tel" />
          </label>

          <div class="form-heading compact-heading">
            <h3>Home address</h3>
          </div>

          <label>
            <span>Street address <span class="required-marker" aria-label="required">*</span></span>
            <input autocomplete="address-line1" maxlength="200" name="streetAddress" required type="text" />
          </label>

          <label>
            <span>Suite or apartment</span>
            <input autocomplete="address-line2" maxlength="100" name="suite" type="text" />
          </label>

          <div class="field-grid">
            <label>
              <span>City <span class="required-marker" aria-label="required">*</span></span>
              <input autocomplete="address-level2" maxlength="100" name="city" required type="text" />
            </label>

            <label>
              <span>State <span class="required-marker" aria-label="required">*</span></span>
              <input autocomplete="address-level1" maxlength="50" name="state" required type="text" value="MI" />
            </label>
          </div>

          <label>
            <span>ZIP code <span class="required-marker" aria-label="required">*</span></span>
            <input autocomplete="postal-code" maxlength="20" name="zipCode" required type="text" />
          </label>

          <p class="message error" data-error hidden role="alert"></p>
          <button data-submit type="submit">Open parent portal</button>
        </form>
      </section>
    </main>
  `;

  const form = document.querySelector("[data-parent-profile-completion]");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    const submitButton = form.querySelector("[data-submit]");
    const errorMessage = form.querySelector("[data-error]");
    errorMessage.hidden = true;
    submitButton.disabled = true;
    submitButton.textContent = "Saving";
    try {
      const profile = await updateProfile({
        phone: String(formData.get("phone") || "").trim(),
        address: {
          streetAddress: String(formData.get("streetAddress") || "").trim(),
          suite: String(formData.get("suite") || "").trim(),
          city: String(formData.get("city") || "").trim(),
          state: String(formData.get("state") || "").trim(),
          zipCode: String(formData.get("zipCode") || "").trim(),
        },
      });
      renderSchoolDashboard({
        role: "PARENT",
        school,
        user: { ...response.user, phone: profile.phone },
        onLogout: () => {
          endAuthenticatedSession();
        },
      });
    } catch (error) {
      errorMessage.textContent = error instanceof Error ? error.message : "Unable to save parent profile";
      errorMessage.hidden = false;
      submitButton.disabled = false;
      submitButton.textContent = "Open parent portal";
    }
  });
}

function endAuthenticatedSession() {
  disableAuthenticatedBackGuard();
  localStorage.removeItem("schooldays.accessToken");
  returnToLoginPage();
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
