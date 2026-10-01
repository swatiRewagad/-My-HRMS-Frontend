import {
  __async,
  environment
} from "/chunk-KDG4QRPE.js";

// src/app/services/keycloak-auth.service.ts
import { Injectable, signal } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import Keycloak from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/keycloak-js.js?v=af65e101";
import * as i0 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
var KeycloakAuthService = class _KeycloakAuthService {
  keycloak;
  initialized = false;
  initPromise = null;
  isAuthenticated = signal(false, ...ngDevMode ? [{ debugName: "isAuthenticated" }] : (
    /* istanbul ignore next */
    []
  ));
  currentUser = signal(null, ...ngDevMode ? [{ debugName: "currentUser" }] : (
    /* istanbul ignore next */
    []
  ));
  token = signal("", ...ngDevMode ? [{ debugName: "token" }] : (
    /* istanbul ignore next */
    []
  ));
  constructor() {
    this.keycloak = new Keycloak({
      url: environment.keycloakUrl,
      realm: environment.realm,
      clientId: "cms-frontend"
    });
  }
  init() {
    return __async(this, null, function* () {
      if (this.initialized) {
        return this.isAuthenticated();
      }
      if (this.initPromise) {
        return this.initPromise;
      }
      this.initPromise = this.doInit();
      return this.initPromise;
    });
  }
  doInit() {
    return __async(this, null, function* () {
      try {
        const authenticated = yield this.keycloak.init({
          onLoad: "check-sso",
          pkceMethod: "S256",
          checkLoginIframe: false,
          silentCheckSsoRedirectUri: window.location.origin + "/assets/silent-check-sso.html"
        });
        this.initialized = true;
        if (authenticated) {
          this.updateAuthState();
          this.setupTokenRefresh();
        }
        return authenticated;
      } catch (err) {
        console.error("Keycloak init failed:", err);
        this.initialized = true;
        return false;
      }
    });
  }
  login() {
    return __async(this, null, function* () {
      if (!this.initialized) {
        yield this.init();
      }
      yield this.keycloak.login({
        redirectUri: window.location.href
      });
    });
  }
  loginWithRedirect(redirectUri) {
    return __async(this, null, function* () {
      if (!this.initialized) {
        yield this.init();
      }
      yield this.keycloak.login({ redirectUri });
    });
  }
  logout() {
    return __async(this, null, function* () {
      this.isAuthenticated.set(false);
      this.currentUser.set(null);
      this.token.set("");
      this.initialized = false;
      this.initPromise = null;
      sessionStorage.removeItem("crpc_user");
      if (this.warningTimer)
        clearInterval(this.warningTimer);
      if (this.sessionTimer)
        clearTimeout(this.sessionTimer);
      try {
        yield this.keycloak.logout({
          redirectUri: window.location.origin
        });
      } catch (e) {
        window.location.href = "/";
      }
    });
  }
  refreshToken() {
    return __async(this, null, function* () {
      try {
        const refreshed = yield this.keycloak.updateToken(30);
        if (refreshed) {
          this.token.set(this.keycloak.token || "");
        }
        return true;
      } catch (e) {
        this.isAuthenticated.set(false);
        return false;
      }
    });
  }
  getToken() {
    return this.keycloak.token || "";
  }
  getRoles() {
    return this.keycloak.realmAccess?.roles || [];
  }
  hasRole(role) {
    return this.getRoles().includes(role);
  }
  hasAnyRole(roles) {
    return roles.some((r) => this.hasRole(r));
  }
  updateAuthState() {
    this.isAuthenticated.set(true);
    this.token.set(this.keycloak.token || "");
    const tokenParsed = this.keycloak.tokenParsed;
    const roles = this.getRoles();
    this.currentUser.set({
      id: tokenParsed?.sub || "",
      username: tokenParsed?.preferred_username || "",
      firstName: tokenParsed?.given_name || "",
      lastName: tokenParsed?.family_name || "",
      email: tokenParsed?.email || "",
      roles,
      department: this.detectDepartment(roles)
    });
  }
  sessionTimer;
  warningTimer;
  activityDebounce;
  sessionExpiring = signal(false, ...ngDevMode ? [{ debugName: "sessionExpiring" }] : (
    /* istanbul ignore next */
    []
  ));
  sessionRemainingSeconds = signal(0, ...ngDevMode ? [{ debugName: "sessionRemainingSeconds" }] : (
    /* istanbul ignore next */
    []
  ));
  setupTokenRefresh() {
    setInterval(() => __async(this, null, function* () {
      if (this.isAuthenticated()) {
        yield this.refreshToken();
      }
    }), 6e4);
    this.startSessionTimer();
    this.setupActivityListeners();
  }
  setupActivityListeners() {
    const onActivity = () => {
      if (this.sessionExpiring())
        return;
      if (this.activityDebounce)
        clearTimeout(this.activityDebounce);
      this.activityDebounce = setTimeout(() => this.resetSessionTimer(), 5e3);
    };
    ["click", "keydown", "mousemove", "scroll"].forEach((event) => {
      document.addEventListener(event, onActivity, { passive: true });
    });
  }
  resetSessionTimer() {
    if (this.warningTimer)
      clearInterval(this.warningTimer);
    if (this.sessionTimer)
      clearTimeout(this.sessionTimer);
    this.startSessionTimer();
  }
  startSessionTimer() {
    if (this.warningTimer)
      clearInterval(this.warningTimer);
    if (this.sessionTimer)
      clearTimeout(this.sessionTimer);
    const timeoutMs = environment.sessionTimeoutMinutes * 60 * 1e3;
    const warningMs = timeoutMs - 6e4;
    this.sessionExpiring.set(false);
    this.sessionTimer = setTimeout(() => {
      this.sessionExpiring.set(true);
      this.sessionRemainingSeconds.set(60);
      this.warningTimer = setInterval(() => {
        const remaining = this.sessionRemainingSeconds() - 1;
        this.sessionRemainingSeconds.set(remaining);
        if (remaining <= 0) {
          clearInterval(this.warningTimer);
          this.logout();
        }
      }, 1e3);
    }, warningMs);
  }
  extendSession() {
    this.sessionExpiring.set(false);
    if (this.warningTimer)
      clearInterval(this.warningTimer);
    this.refreshToken();
    this.startSessionTimer();
  }
  detectDepartment(roles) {
    if (roles.some((r) => r.startsWith("RBIO_")))
      return "RBIO";
    if (roles.some((r) => r.startsWith("CEPC_")))
      return "CEPC";
    if (roles.some((r) => r.startsWith("CRPC_") || r === "DEO" || r === "REVIEWER"))
      return "CRPC";
    if (roles.some((r) => r.startsWith("AA_")))
      return "AA";
    if (roles.some((r) => r.startsWith("RE_")))
      return "RE";
    if (roles.includes("ADMIN"))
      return "ADMIN";
    return "UNKNOWN";
  }
  static \u0275fac = function KeycloakAuthService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _KeycloakAuthService)();
  };
  static \u0275prov = /* @__PURE__ */ i0.\u0275\u0275defineInjectable({ token: _KeycloakAuthService, factory: _KeycloakAuthService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i0.\u0275setClassMetadata(KeycloakAuthService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], () => [], null);
})();

export {
  KeycloakAuthService
};


//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbInNyYy9hcHAvc2VydmljZXMva2V5Y2xvYWstYXV0aC5zZXJ2aWNlLnRzIl0sInNvdXJjZXNDb250ZW50IjpbImltcG9ydCB7IEluamVjdGFibGUsIHNpZ25hbCB9IGZyb20gJ0Bhbmd1bGFyL2NvcmUnO1xyXG5pbXBvcnQgS2V5Y2xvYWsgZnJvbSAna2V5Y2xvYWstanMnO1xyXG5pbXBvcnQgeyBlbnZpcm9ubWVudCB9IGZyb20gJy4uLy4uL2Vudmlyb25tZW50cy9lbnZpcm9ubWVudCc7XHJcblxyXG5leHBvcnQgaW50ZXJmYWNlIFN0YWZmVXNlciB7XHJcbiAgaWQ6IHN0cmluZztcclxuICB1c2VybmFtZTogc3RyaW5nO1xyXG4gIGZpcnN0TmFtZTogc3RyaW5nO1xyXG4gIGxhc3ROYW1lOiBzdHJpbmc7XHJcbiAgZW1haWw6IHN0cmluZztcclxuICByb2xlczogc3RyaW5nW107XHJcbiAgZGVwYXJ0bWVudDogJ1JCSU8nIHwgJ0NFUEMnIHwgJ0NSUEMnIHwgJ0FBJyB8ICdSRScgfCAnQURNSU4nIHwgJ1VOS05PV04nO1xyXG59XHJcblxyXG5ASW5qZWN0YWJsZSh7IHByb3ZpZGVkSW46ICdyb290JyB9KVxyXG5leHBvcnQgY2xhc3MgS2V5Y2xvYWtBdXRoU2VydmljZSB7XHJcblxyXG4gIHByaXZhdGUga2V5Y2xvYWs6IEtleWNsb2FrO1xyXG4gIHByaXZhdGUgaW5pdGlhbGl6ZWQgPSBmYWxzZTtcclxuICBwcml2YXRlIGluaXRQcm9taXNlOiBQcm9taXNlPGJvb2xlYW4+IHwgbnVsbCA9IG51bGw7XHJcblxyXG4gIGlzQXV0aGVudGljYXRlZCA9IHNpZ25hbChmYWxzZSk7XHJcbiAgY3VycmVudFVzZXIgPSBzaWduYWw8U3RhZmZVc2VyIHwgbnVsbD4obnVsbCk7XHJcbiAgdG9rZW4gPSBzaWduYWw8c3RyaW5nPignJyk7XHJcblxyXG4gIGNvbnN0cnVjdG9yKCkge1xyXG4gICAgdGhpcy5rZXljbG9hayA9IG5ldyBLZXljbG9hayh7XHJcbiAgICAgIHVybDogZW52aXJvbm1lbnQua2V5Y2xvYWtVcmwsXHJcbiAgICAgIHJlYWxtOiBlbnZpcm9ubWVudC5yZWFsbSxcclxuICAgICAgY2xpZW50SWQ6ICdjbXMtZnJvbnRlbmQnXHJcbiAgICB9KTtcclxuICB9XHJcblxyXG4gIGFzeW5jIGluaXQoKTogUHJvbWlzZTxib29sZWFuPiB7XHJcbiAgICBpZiAodGhpcy5pbml0aWFsaXplZCkge1xyXG4gICAgICByZXR1cm4gdGhpcy5pc0F1dGhlbnRpY2F0ZWQoKTtcclxuICAgIH1cclxuXHJcbiAgICBpZiAodGhpcy5pbml0UHJvbWlzZSkge1xyXG4gICAgICByZXR1cm4gdGhpcy5pbml0UHJvbWlzZTtcclxuICAgIH1cclxuXHJcbiAgICB0aGlzLmluaXRQcm9taXNlID0gdGhpcy5kb0luaXQoKTtcclxuICAgIHJldHVybiB0aGlzLmluaXRQcm9taXNlO1xyXG4gIH1cclxuXHJcbiAgcHJpdmF0ZSBhc3luYyBkb0luaXQoKTogUHJvbWlzZTxib29sZWFuPiB7XHJcbiAgICB0cnkge1xyXG4gICAgICBjb25zdCBhdXRoZW50aWNhdGVkID0gYXdhaXQgdGhpcy5rZXljbG9hay5pbml0KHtcclxuICAgICAgICBvbkxvYWQ6ICdjaGVjay1zc28nLFxyXG4gICAgICAgIHBrY2VNZXRob2Q6ICdTMjU2JyxcclxuICAgICAgICBjaGVja0xvZ2luSWZyYW1lOiBmYWxzZSxcclxuICAgICAgICBzaWxlbnRDaGVja1Nzb1JlZGlyZWN0VXJpOiB3aW5kb3cubG9jYXRpb24ub3JpZ2luICsgJy9hc3NldHMvc2lsZW50LWNoZWNrLXNzby5odG1sJ1xyXG4gICAgICB9KTtcclxuXHJcbiAgICAgIHRoaXMuaW5pdGlhbGl6ZWQgPSB0cnVlO1xyXG5cclxuICAgICAgaWYgKGF1dGhlbnRpY2F0ZWQpIHtcclxuICAgICAgICB0aGlzLnVwZGF0ZUF1dGhTdGF0ZSgpO1xyXG4gICAgICAgIHRoaXMuc2V0dXBUb2tlblJlZnJlc2goKTtcclxuICAgICAgfVxyXG4gICAgICByZXR1cm4gYXV0aGVudGljYXRlZDtcclxuICAgIH0gY2F0Y2ggKGVycikge1xyXG4gICAgICBjb25zb2xlLmVycm9yKCdLZXljbG9hayBpbml0IGZhaWxlZDonLCBlcnIpO1xyXG4gICAgICB0aGlzLmluaXRpYWxpemVkID0gdHJ1ZTtcclxuICAgICAgcmV0dXJuIGZhbHNlO1xyXG4gICAgfVxyXG4gIH1cclxuXHJcbiAgYXN5bmMgbG9naW4oKTogUHJvbWlzZTx2b2lkPiB7XHJcbiAgICBpZiAoIXRoaXMuaW5pdGlhbGl6ZWQpIHtcclxuICAgICAgYXdhaXQgdGhpcy5pbml0KCk7XHJcbiAgICB9XHJcbiAgICBhd2FpdCB0aGlzLmtleWNsb2FrLmxvZ2luKHtcclxuICAgICAgcmVkaXJlY3RVcmk6IHdpbmRvdy5sb2NhdGlvbi5ocmVmXHJcbiAgICB9KTtcclxuICB9XHJcblxyXG4gIGFzeW5jIGxvZ2luV2l0aFJlZGlyZWN0KHJlZGlyZWN0VXJpOiBzdHJpbmcpOiBQcm9taXNlPHZvaWQ+IHtcclxuICAgIGlmICghdGhpcy5pbml0aWFsaXplZCkge1xyXG4gICAgICBhd2FpdCB0aGlzLmluaXQoKTtcclxuICAgIH1cclxuICAgIGF3YWl0IHRoaXMua2V5Y2xvYWsubG9naW4oeyByZWRpcmVjdFVyaSB9KTtcclxuICB9XHJcblxyXG4gIGFzeW5jIGxvZ291dCgpOiBQcm9taXNlPHZvaWQ+IHtcclxuICAgIHRoaXMuaXNBdXRoZW50aWNhdGVkLnNldChmYWxzZSk7XHJcbiAgICB0aGlzLmN1cnJlbnRVc2VyLnNldChudWxsKTtcclxuICAgIHRoaXMudG9rZW4uc2V0KCcnKTtcclxuICAgIHRoaXMuaW5pdGlhbGl6ZWQgPSBmYWxzZTtcclxuICAgIHRoaXMuaW5pdFByb21pc2UgPSBudWxsO1xyXG4gICAgc2Vzc2lvblN0b3JhZ2UucmVtb3ZlSXRlbSgnY3JwY191c2VyJyk7XHJcbiAgICBpZiAodGhpcy53YXJuaW5nVGltZXIpIGNsZWFySW50ZXJ2YWwodGhpcy53YXJuaW5nVGltZXIpO1xyXG4gICAgaWYgKHRoaXMuc2Vzc2lvblRpbWVyKSBjbGVhclRpbWVvdXQodGhpcy5zZXNzaW9uVGltZXIpO1xyXG5cclxuICAgIHRyeSB7XHJcbiAgICAgIGF3YWl0IHRoaXMua2V5Y2xvYWsubG9nb3V0KHtcclxuICAgICAgICByZWRpcmVjdFVyaTogd2luZG93LmxvY2F0aW9uLm9yaWdpblxyXG4gICAgICB9KTtcclxuICAgIH0gY2F0Y2gge1xyXG4gICAgICB3aW5kb3cubG9jYXRpb24uaHJlZiA9ICcvJztcclxuICAgIH1cclxuICB9XHJcblxyXG4gIGFzeW5jIHJlZnJlc2hUb2tlbigpOiBQcm9taXNlPGJvb2xlYW4+IHtcclxuICAgIHRyeSB7XHJcbiAgICAgIGNvbnN0IHJlZnJlc2hlZCA9IGF3YWl0IHRoaXMua2V5Y2xvYWsudXBkYXRlVG9rZW4oMzApO1xyXG4gICAgICBpZiAocmVmcmVzaGVkKSB7XHJcbiAgICAgICAgdGhpcy50b2tlbi5zZXQodGhpcy5rZXljbG9hay50b2tlbiB8fCAnJyk7XHJcbiAgICAgIH1cclxuICAgICAgcmV0dXJuIHRydWU7XHJcbiAgICB9IGNhdGNoIHtcclxuICAgICAgdGhpcy5pc0F1dGhlbnRpY2F0ZWQuc2V0KGZhbHNlKTtcclxuICAgICAgcmV0dXJuIGZhbHNlO1xyXG4gICAgfVxyXG4gIH1cclxuXHJcbiAgZ2V0VG9rZW4oKTogc3RyaW5nIHtcclxuICAgIHJldHVybiB0aGlzLmtleWNsb2FrLnRva2VuIHx8ICcnO1xyXG4gIH1cclxuXHJcbiAgZ2V0Um9sZXMoKTogc3RyaW5nW10ge1xyXG4gICAgcmV0dXJuIHRoaXMua2V5Y2xvYWsucmVhbG1BY2Nlc3M/LnJvbGVzIHx8IFtdO1xyXG4gIH1cclxuXHJcbiAgaGFzUm9sZShyb2xlOiBzdHJpbmcpOiBib29sZWFuIHtcclxuICAgIHJldHVybiB0aGlzLmdldFJvbGVzKCkuaW5jbHVkZXMocm9sZSk7XHJcbiAgfVxyXG5cclxuICBoYXNBbnlSb2xlKHJvbGVzOiBzdHJpbmdbXSk6IGJvb2xlYW4ge1xyXG4gICAgcmV0dXJuIHJvbGVzLnNvbWUociA9PiB0aGlzLmhhc1JvbGUocikpO1xyXG4gIH1cclxuXHJcbiAgcHJpdmF0ZSB1cGRhdGVBdXRoU3RhdGUoKTogdm9pZCB7XHJcbiAgICB0aGlzLmlzQXV0aGVudGljYXRlZC5zZXQodHJ1ZSk7XHJcbiAgICB0aGlzLnRva2VuLnNldCh0aGlzLmtleWNsb2FrLnRva2VuIHx8ICcnKTtcclxuXHJcbiAgICBjb25zdCB0b2tlblBhcnNlZCA9IHRoaXMua2V5Y2xvYWsudG9rZW5QYXJzZWQgYXMgYW55O1xyXG4gICAgY29uc3Qgcm9sZXMgPSB0aGlzLmdldFJvbGVzKCk7XHJcblxyXG4gICAgdGhpcy5jdXJyZW50VXNlci5zZXQoe1xyXG4gICAgICBpZDogdG9rZW5QYXJzZWQ/LnN1YiB8fCAnJyxcclxuICAgICAgdXNlcm5hbWU6IHRva2VuUGFyc2VkPy5wcmVmZXJyZWRfdXNlcm5hbWUgfHwgJycsXHJcbiAgICAgIGZpcnN0TmFtZTogdG9rZW5QYXJzZWQ/LmdpdmVuX25hbWUgfHwgJycsXHJcbiAgICAgIGxhc3ROYW1lOiB0b2tlblBhcnNlZD8uZmFtaWx5X25hbWUgfHwgJycsXHJcbiAgICAgIGVtYWlsOiB0b2tlblBhcnNlZD8uZW1haWwgfHwgJycsXHJcbiAgICAgIHJvbGVzLFxyXG4gICAgICBkZXBhcnRtZW50OiB0aGlzLmRldGVjdERlcGFydG1lbnQocm9sZXMpXHJcbiAgICB9KTtcclxuICB9XHJcblxyXG4gIHByaXZhdGUgc2Vzc2lvblRpbWVyOiBhbnk7XHJcbiAgcHJpdmF0ZSB3YXJuaW5nVGltZXI6IGFueTtcclxuICBwcml2YXRlIGFjdGl2aXR5RGVib3VuY2U6IGFueTtcclxuICBzZXNzaW9uRXhwaXJpbmcgPSBzaWduYWwoZmFsc2UpO1xyXG4gIHNlc3Npb25SZW1haW5pbmdTZWNvbmRzID0gc2lnbmFsKDApO1xyXG5cclxuICBwcml2YXRlIHNldHVwVG9rZW5SZWZyZXNoKCk6IHZvaWQge1xyXG4gICAgc2V0SW50ZXJ2YWwoYXN5bmMgKCkgPT4ge1xyXG4gICAgICBpZiAodGhpcy5pc0F1dGhlbnRpY2F0ZWQoKSkge1xyXG4gICAgICAgIGF3YWl0IHRoaXMucmVmcmVzaFRva2VuKCk7XHJcbiAgICAgIH1cclxuICAgIH0sIDYwMDAwKTtcclxuXHJcbiAgICB0aGlzLnN0YXJ0U2Vzc2lvblRpbWVyKCk7XHJcbiAgICB0aGlzLnNldHVwQWN0aXZpdHlMaXN0ZW5lcnMoKTtcclxuICB9XHJcblxyXG4gIHByaXZhdGUgc2V0dXBBY3Rpdml0eUxpc3RlbmVycygpOiB2b2lkIHtcclxuICAgIGNvbnN0IG9uQWN0aXZpdHkgPSAoKSA9PiB7XHJcbiAgICAgIGlmICh0aGlzLnNlc3Npb25FeHBpcmluZygpKSByZXR1cm47XHJcbiAgICAgIGlmICh0aGlzLmFjdGl2aXR5RGVib3VuY2UpIGNsZWFyVGltZW91dCh0aGlzLmFjdGl2aXR5RGVib3VuY2UpO1xyXG4gICAgICB0aGlzLmFjdGl2aXR5RGVib3VuY2UgPSBzZXRUaW1lb3V0KCgpID0+IHRoaXMucmVzZXRTZXNzaW9uVGltZXIoKSwgNTAwMCk7XHJcbiAgICB9O1xyXG4gICAgWydjbGljaycsICdrZXlkb3duJywgJ21vdXNlbW92ZScsICdzY3JvbGwnXS5mb3JFYWNoKGV2ZW50ID0+IHtcclxuICAgICAgZG9jdW1lbnQuYWRkRXZlbnRMaXN0ZW5lcihldmVudCwgb25BY3Rpdml0eSwgeyBwYXNzaXZlOiB0cnVlIH0pO1xyXG4gICAgfSk7XHJcbiAgfVxyXG5cclxuICBwcml2YXRlIHJlc2V0U2Vzc2lvblRpbWVyKCk6IHZvaWQge1xyXG4gICAgaWYgKHRoaXMud2FybmluZ1RpbWVyKSBjbGVhckludGVydmFsKHRoaXMud2FybmluZ1RpbWVyKTtcclxuICAgIGlmICh0aGlzLnNlc3Npb25UaW1lcikgY2xlYXJUaW1lb3V0KHRoaXMuc2Vzc2lvblRpbWVyKTtcclxuICAgIHRoaXMuc3RhcnRTZXNzaW9uVGltZXIoKTtcclxuICB9XHJcblxyXG4gIHByaXZhdGUgc3RhcnRTZXNzaW9uVGltZXIoKTogdm9pZCB7XHJcbiAgICBpZiAodGhpcy53YXJuaW5nVGltZXIpIGNsZWFySW50ZXJ2YWwodGhpcy53YXJuaW5nVGltZXIpO1xyXG4gICAgaWYgKHRoaXMuc2Vzc2lvblRpbWVyKSBjbGVhclRpbWVvdXQodGhpcy5zZXNzaW9uVGltZXIpO1xyXG5cclxuICAgIGNvbnN0IHRpbWVvdXRNcyA9IGVudmlyb25tZW50LnNlc3Npb25UaW1lb3V0TWludXRlcyAqIDYwICogMTAwMDtcclxuICAgIGNvbnN0IHdhcm5pbmdNcyA9IHRpbWVvdXRNcyAtIDYwMDAwO1xyXG5cclxuICAgIHRoaXMuc2Vzc2lvbkV4cGlyaW5nLnNldChmYWxzZSk7XHJcblxyXG4gICAgdGhpcy5zZXNzaW9uVGltZXIgPSBzZXRUaW1lb3V0KCgpID0+IHtcclxuICAgICAgdGhpcy5zZXNzaW9uRXhwaXJpbmcuc2V0KHRydWUpO1xyXG4gICAgICB0aGlzLnNlc3Npb25SZW1haW5pbmdTZWNvbmRzLnNldCg2MCk7XHJcbiAgICAgIHRoaXMud2FybmluZ1RpbWVyID0gc2V0SW50ZXJ2YWwoKCkgPT4ge1xyXG4gICAgICAgIGNvbnN0IHJlbWFpbmluZyA9IHRoaXMuc2Vzc2lvblJlbWFpbmluZ1NlY29uZHMoKSAtIDE7XHJcbiAgICAgICAgdGhpcy5zZXNzaW9uUmVtYWluaW5nU2Vjb25kcy5zZXQocmVtYWluaW5nKTtcclxuICAgICAgICBpZiAocmVtYWluaW5nIDw9IDApIHtcclxuICAgICAgICAgIGNsZWFySW50ZXJ2YWwodGhpcy53YXJuaW5nVGltZXIpO1xyXG4gICAgICAgICAgdGhpcy5sb2dvdXQoKTtcclxuICAgICAgICB9XHJcbiAgICAgIH0sIDEwMDApO1xyXG4gICAgfSwgd2FybmluZ01zKTtcclxuICB9XHJcblxyXG4gIGV4dGVuZFNlc3Npb24oKTogdm9pZCB7XHJcbiAgICB0aGlzLnNlc3Npb25FeHBpcmluZy5zZXQoZmFsc2UpO1xyXG4gICAgaWYgKHRoaXMud2FybmluZ1RpbWVyKSBjbGVhckludGVydmFsKHRoaXMud2FybmluZ1RpbWVyKTtcclxuICAgIHRoaXMucmVmcmVzaFRva2VuKCk7XHJcbiAgICB0aGlzLnN0YXJ0U2Vzc2lvblRpbWVyKCk7XHJcbiAgfVxyXG5cclxuICBwcml2YXRlIGRldGVjdERlcGFydG1lbnQocm9sZXM6IHN0cmluZ1tdKTogU3RhZmZVc2VyWydkZXBhcnRtZW50J10ge1xyXG4gICAgaWYgKHJvbGVzLnNvbWUociA9PiByLnN0YXJ0c1dpdGgoJ1JCSU9fJykpKSByZXR1cm4gJ1JCSU8nO1xyXG4gICAgaWYgKHJvbGVzLnNvbWUociA9PiByLnN0YXJ0c1dpdGgoJ0NFUENfJykpKSByZXR1cm4gJ0NFUEMnO1xyXG4gICAgaWYgKHJvbGVzLnNvbWUociA9PiByLnN0YXJ0c1dpdGgoJ0NSUENfJykgfHwgciA9PT0gJ0RFTycgfHwgciA9PT0gJ1JFVklFV0VSJykpIHJldHVybiAnQ1JQQyc7XHJcbiAgICBpZiAocm9sZXMuc29tZShyID0+IHIuc3RhcnRzV2l0aCgnQUFfJykpKSByZXR1cm4gJ0FBJztcclxuICAgIGlmIChyb2xlcy5zb21lKHIgPT4gci5zdGFydHNXaXRoKCdSRV8nKSkpIHJldHVybiAnUkUnO1xyXG4gICAgaWYgKHJvbGVzLmluY2x1ZGVzKCdBRE1JTicpKSByZXR1cm4gJ0FETUlOJztcclxuICAgIHJldHVybiAnVU5LTk9XTic7XHJcbiAgfVxyXG59XHJcbiJdLCJtYXBwaW5ncyI6Ijs7Ozs7O0FBQUEsU0FBUyxZQUFZLGNBQWM7QUFDbkMsT0FBTyxjQUFjO0E7QUFjZixJQUFPLHNCQUFQLE1BQU8scUJBQW1CO0VBRXRCO0VBQ0EsY0FBYztFQUNkLGNBQXVDO0VBRS9DLGtCQUFrQixPQUFPLE9BQUssR0FBQSxZQUFBLENBQUEsRUFBQSxXQUFBLGtCQUFBLENBQUE7O0lBQUEsQ0FBQTtHQUFBO0VBQzlCLGNBQWMsT0FBeUIsTUFBSSxHQUFBLFlBQUEsQ0FBQSxFQUFBLFdBQUEsY0FBQSxDQUFBOztJQUFBLENBQUE7R0FBQTtFQUMzQyxRQUFRLE9BQWUsSUFBRSxHQUFBLFlBQUEsQ0FBQSxFQUFBLFdBQUEsUUFBQSxDQUFBOztJQUFBLENBQUE7R0FBQTtFQUV6QixjQUFBO0FBQ0UsU0FBSyxXQUFXLElBQUksU0FBUztNQUMzQixLQUFLLFlBQVk7TUFDakIsT0FBTyxZQUFZO01BQ25CLFVBQVU7S0FDWDtFQUNIO0VBRU0sT0FBSTs7QUFDUixVQUFJLEtBQUssYUFBYTtBQUNwQixlQUFPLEtBQUssZ0JBQWU7TUFDN0I7QUFFQSxVQUFJLEtBQUssYUFBYTtBQUNwQixlQUFPLEtBQUs7TUFDZDtBQUVBLFdBQUssY0FBYyxLQUFLLE9BQU07QUFDOUIsYUFBTyxLQUFLO0lBQ2Q7O0VBRWMsU0FBTTs7QUFDbEIsVUFBSTtBQUNGLGNBQU0sZ0JBQWdCLE1BQU0sS0FBSyxTQUFTLEtBQUs7VUFDN0MsUUFBUTtVQUNSLFlBQVk7VUFDWixrQkFBa0I7VUFDbEIsMkJBQTJCLE9BQU8sU0FBUyxTQUFTO1NBQ3JEO0FBRUQsYUFBSyxjQUFjO0FBRW5CLFlBQUksZUFBZTtBQUNqQixlQUFLLGdCQUFlO0FBQ3BCLGVBQUssa0JBQWlCO1FBQ3hCO0FBQ0EsZUFBTztNQUNULFNBQVMsS0FBSztBQUNaLGdCQUFRLE1BQU0seUJBQXlCLEdBQUc7QUFDMUMsYUFBSyxjQUFjO0FBQ25CLGVBQU87TUFDVDtJQUNGOztFQUVNLFFBQUs7O0FBQ1QsVUFBSSxDQUFDLEtBQUssYUFBYTtBQUNyQixjQUFNLEtBQUssS0FBSTtNQUNqQjtBQUNBLFlBQU0sS0FBSyxTQUFTLE1BQU07UUFDeEIsYUFBYSxPQUFPLFNBQVM7T0FDOUI7SUFDSDs7RUFFTSxrQkFBa0IsYUFBbUI7O0FBQ3pDLFVBQUksQ0FBQyxLQUFLLGFBQWE7QUFDckIsY0FBTSxLQUFLLEtBQUk7TUFDakI7QUFDQSxZQUFNLEtBQUssU0FBUyxNQUFNLEVBQUUsWUFBVyxDQUFFO0lBQzNDOztFQUVNLFNBQU07O0FBQ1YsV0FBSyxnQkFBZ0IsSUFBSSxLQUFLO0FBQzlCLFdBQUssWUFBWSxJQUFJLElBQUk7QUFDekIsV0FBSyxNQUFNLElBQUksRUFBRTtBQUNqQixXQUFLLGNBQWM7QUFDbkIsV0FBSyxjQUFjO0FBQ25CLHFCQUFlLFdBQVcsV0FBVztBQUNyQyxVQUFJLEtBQUs7QUFBYyxzQkFBYyxLQUFLLFlBQVk7QUFDdEQsVUFBSSxLQUFLO0FBQWMscUJBQWEsS0FBSyxZQUFZO0FBRXJELFVBQUk7QUFDRixjQUFNLEtBQUssU0FBUyxPQUFPO1VBQ3pCLGFBQWEsT0FBTyxTQUFTO1NBQzlCO01BQ0gsU0FBUTtBQUNOLGVBQU8sU0FBUyxPQUFPO01BQ3pCO0lBQ0Y7O0VBRU0sZUFBWTs7QUFDaEIsVUFBSTtBQUNGLGNBQU0sWUFBWSxNQUFNLEtBQUssU0FBUyxZQUFZLEVBQUU7QUFDcEQsWUFBSSxXQUFXO0FBQ2IsZUFBSyxNQUFNLElBQUksS0FBSyxTQUFTLFNBQVMsRUFBRTtRQUMxQztBQUNBLGVBQU87TUFDVCxTQUFRO0FBQ04sYUFBSyxnQkFBZ0IsSUFBSSxLQUFLO0FBQzlCLGVBQU87TUFDVDtJQUNGOztFQUVBLFdBQVE7QUFDTixXQUFPLEtBQUssU0FBUyxTQUFTO0VBQ2hDO0VBRUEsV0FBUTtBQUNOLFdBQU8sS0FBSyxTQUFTLGFBQWEsU0FBUyxDQUFBO0VBQzdDO0VBRUEsUUFBUSxNQUFZO0FBQ2xCLFdBQU8sS0FBSyxTQUFRLEVBQUcsU0FBUyxJQUFJO0VBQ3RDO0VBRUEsV0FBVyxPQUFlO0FBQ3hCLFdBQU8sTUFBTSxLQUFLLE9BQUssS0FBSyxRQUFRLENBQUMsQ0FBQztFQUN4QztFQUVRLGtCQUFlO0FBQ3JCLFNBQUssZ0JBQWdCLElBQUksSUFBSTtBQUM3QixTQUFLLE1BQU0sSUFBSSxLQUFLLFNBQVMsU0FBUyxFQUFFO0FBRXhDLFVBQU0sY0FBYyxLQUFLLFNBQVM7QUFDbEMsVUFBTSxRQUFRLEtBQUssU0FBUTtBQUUzQixTQUFLLFlBQVksSUFBSTtNQUNuQixJQUFJLGFBQWEsT0FBTztNQUN4QixVQUFVLGFBQWEsc0JBQXNCO01BQzdDLFdBQVcsYUFBYSxjQUFjO01BQ3RDLFVBQVUsYUFBYSxlQUFlO01BQ3RDLE9BQU8sYUFBYSxTQUFTO01BQzdCO01BQ0EsWUFBWSxLQUFLLGlCQUFpQixLQUFLO0tBQ3hDO0VBQ0g7RUFFUTtFQUNBO0VBQ0E7RUFDUixrQkFBa0IsT0FBTyxPQUFLLEdBQUEsWUFBQSxDQUFBLEVBQUEsV0FBQSxrQkFBQSxDQUFBOztJQUFBLENBQUE7R0FBQTtFQUM5QiwwQkFBMEIsT0FBTyxHQUFDLEdBQUEsWUFBQSxDQUFBLEVBQUEsV0FBQSwwQkFBQSxDQUFBOztJQUFBLENBQUE7R0FBQTtFQUUxQixvQkFBaUI7QUFDdkIsZ0JBQVksTUFBVztBQUNyQixVQUFJLEtBQUssZ0JBQWUsR0FBSTtBQUMxQixjQUFNLEtBQUssYUFBWTtNQUN6QjtJQUNGLElBQUcsR0FBSztBQUVSLFNBQUssa0JBQWlCO0FBQ3RCLFNBQUssdUJBQXNCO0VBQzdCO0VBRVEseUJBQXNCO0FBQzVCLFVBQU0sYUFBYSxNQUFLO0FBQ3RCLFVBQUksS0FBSyxnQkFBZTtBQUFJO0FBQzVCLFVBQUksS0FBSztBQUFrQixxQkFBYSxLQUFLLGdCQUFnQjtBQUM3RCxXQUFLLG1CQUFtQixXQUFXLE1BQU0sS0FBSyxrQkFBaUIsR0FBSSxHQUFJO0lBQ3pFO0FBQ0EsS0FBQyxTQUFTLFdBQVcsYUFBYSxRQUFRLEVBQUUsUUFBUSxXQUFRO0FBQzFELGVBQVMsaUJBQWlCLE9BQU8sWUFBWSxFQUFFLFNBQVMsS0FBSSxDQUFFO0lBQ2hFLENBQUM7RUFDSDtFQUVRLG9CQUFpQjtBQUN2QixRQUFJLEtBQUs7QUFBYyxvQkFBYyxLQUFLLFlBQVk7QUFDdEQsUUFBSSxLQUFLO0FBQWMsbUJBQWEsS0FBSyxZQUFZO0FBQ3JELFNBQUssa0JBQWlCO0VBQ3hCO0VBRVEsb0JBQWlCO0FBQ3ZCLFFBQUksS0FBSztBQUFjLG9CQUFjLEtBQUssWUFBWTtBQUN0RCxRQUFJLEtBQUs7QUFBYyxtQkFBYSxLQUFLLFlBQVk7QUFFckQsVUFBTSxZQUFZLFlBQVksd0JBQXdCLEtBQUs7QUFDM0QsVUFBTSxZQUFZLFlBQVk7QUFFOUIsU0FBSyxnQkFBZ0IsSUFBSSxLQUFLO0FBRTlCLFNBQUssZUFBZSxXQUFXLE1BQUs7QUFDbEMsV0FBSyxnQkFBZ0IsSUFBSSxJQUFJO0FBQzdCLFdBQUssd0JBQXdCLElBQUksRUFBRTtBQUNuQyxXQUFLLGVBQWUsWUFBWSxNQUFLO0FBQ25DLGNBQU0sWUFBWSxLQUFLLHdCQUF1QixJQUFLO0FBQ25ELGFBQUssd0JBQXdCLElBQUksU0FBUztBQUMxQyxZQUFJLGFBQWEsR0FBRztBQUNsQix3QkFBYyxLQUFLLFlBQVk7QUFDL0IsZUFBSyxPQUFNO1FBQ2I7TUFDRixHQUFHLEdBQUk7SUFDVCxHQUFHLFNBQVM7RUFDZDtFQUVBLGdCQUFhO0FBQ1gsU0FBSyxnQkFBZ0IsSUFBSSxLQUFLO0FBQzlCLFFBQUksS0FBSztBQUFjLG9CQUFjLEtBQUssWUFBWTtBQUN0RCxTQUFLLGFBQVk7QUFDakIsU0FBSyxrQkFBaUI7RUFDeEI7RUFFUSxpQkFBaUIsT0FBZTtBQUN0QyxRQUFJLE1BQU0sS0FBSyxPQUFLLEVBQUUsV0FBVyxPQUFPLENBQUM7QUFBRyxhQUFPO0FBQ25ELFFBQUksTUFBTSxLQUFLLE9BQUssRUFBRSxXQUFXLE9BQU8sQ0FBQztBQUFHLGFBQU87QUFDbkQsUUFBSSxNQUFNLEtBQUssT0FBSyxFQUFFLFdBQVcsT0FBTyxLQUFLLE1BQU0sU0FBUyxNQUFNLFVBQVU7QUFBRyxhQUFPO0FBQ3RGLFFBQUksTUFBTSxLQUFLLE9BQUssRUFBRSxXQUFXLEtBQUssQ0FBQztBQUFHLGFBQU87QUFDakQsUUFBSSxNQUFNLEtBQUssT0FBSyxFQUFFLFdBQVcsS0FBSyxDQUFDO0FBQUcsYUFBTztBQUNqRCxRQUFJLE1BQU0sU0FBUyxPQUFPO0FBQUcsYUFBTztBQUNwQyxXQUFPO0VBQ1Q7O3FDQWhOVyxzQkFBbUI7RUFBQTsrRUFBbkIsc0JBQW1CLFNBQW5CLHFCQUFtQixXQUFBLFlBRE4sT0FBTSxDQUFBOzs7K0VBQ25CLHFCQUFtQixDQUFBO1VBRC9CO1dBQVcsRUFBRSxZQUFZLE9BQU0sQ0FBRTs7OyIsIm5hbWVzIjpbXX0=