import { injectQuery as __vite__injectQuery } from "/@vite/client";import { createHotContext as __vite__createHotContext } from "/@vite/client";import.meta.hot = __vite__createHotContext("/chunk-YJTPY6S3.js");import {
  AppShellComponent
} from "/chunk-UD5FEUAC.js";
import {
  WorkflowActionBarComponent
} from "/chunk-GQCIK4U4.js";
import {
  ContextRailComponent
} from "/chunk-3Q7ZDNXF.js";
import {
  ComplaintSummaryComponent
} from "/chunk-UVDGDX7F.js";
import {
  CommentThreadComponent
} from "/chunk-WXZFWHYF.js";
import "/chunk-6W3MBYZE.js";
import "/chunk-XCMRDIHB.js";
import {
  KeycloakAuthService
} from "/chunk-63C2FE33.js";
import {
  StatusBadgeComponent
} from "/chunk-J4QVPMDM.js";
import "/chunk-3UXWU6UZ.js";
import {
  TranslatePipe
} from "/chunk-YOBDLYE4.js";
import {
  __async,
  __spreadProps,
  __spreadValues,
  environment
} from "/chunk-KDG4QRPE.js";

// src/app/components/aa/aa-appeal-detail/aa-appeal-detail.component.ts
import { Component as Component3, inject as inject3, signal as signal3, computed } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import { CommonModule as CommonModule3 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import { FormsModule as FormsModule3 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
import { Router, ActivatedRoute } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_router.js?v=af65e101";
import { HttpClient as HttpClient3 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common_http.js?v=af65e101";

// src/app/components/aa/aa-hearing/aa-hearing.component.ts
import { Component, Input, Output, EventEmitter, inject, signal } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import { CommonModule } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import { FormsModule } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
import { HttpClient } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common_http.js?v=af65e101";
import * as i0 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import * as i1 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import * as i2 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
var _forTrack0 = ($index, $item) => $item.value;
var _forTrack1 = ($index, $item) => $item.id;
function AaHearingComponent_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275text(0);
    i0.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(1, 1, "aa.hearing.reschedule_title"), " ");
  }
}
function AaHearingComponent_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275text(0);
    i0.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(1, 1, "aa.hearing.title"), " ");
  }
}
function AaHearingComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "div", 1);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext();
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(2, 1, ctx_r0.successKey()), " ");
  }
}
function AaHearingComponent_Conditional_5_For_38_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "li");
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "titlecase");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const party_r3 = ctx.$implicit;
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(2, 1, party_r3));
  }
}
function AaHearingComponent_Conditional_5_Conditional_39_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "p")(1, "strong");
    i0.\u0275\u0275text(2);
    i0.\u0275\u0275pipe(3, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(4);
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext(2);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(3, 2, "aa.hearing.reason"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", ctx_r0.reason);
  }
}
function AaHearingComponent_Conditional_5_Conditional_45_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275text(0);
    i0.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(1, 1, "aa.hearing.scheduling"), " ");
  }
}
function AaHearingComponent_Conditional_5_Conditional_46_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275text(0);
    i0.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(1, 1, "aa.hearing.confirm"), " ");
  }
}
function AaHearingComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = i0.\u0275\u0275getCurrentView();
    i0.\u0275\u0275elementStart(0, "div", 2)(1, "div", 7)(2, "strong");
    i0.\u0275\u0275text(3);
    i0.\u0275\u0275pipe(4, "translate");
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(5, "div", 8)(6, "p")(7, "strong");
    i0.\u0275\u0275text(8);
    i0.\u0275\u0275pipe(9, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(10);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(11, "p")(12, "strong");
    i0.\u0275\u0275text(13);
    i0.\u0275\u0275pipe(14, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(15);
    i0.\u0275\u0275pipe(16, "date");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(17, "p")(18, "strong");
    i0.\u0275\u0275text(19);
    i0.\u0275\u0275pipe(20, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(21);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(22, "p")(23, "strong");
    i0.\u0275\u0275text(24);
    i0.\u0275\u0275pipe(25, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(26);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(27, "p")(28, "strong");
    i0.\u0275\u0275text(29);
    i0.\u0275\u0275pipe(30, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(31);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(32, "p")(33, "strong");
    i0.\u0275\u0275text(34);
    i0.\u0275\u0275pipe(35, "translate");
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(36, "ul", 9);
    i0.\u0275\u0275repeaterCreate(37, AaHearingComponent_Conditional_5_For_38_Template, 3, 3, "li", null, i0.\u0275\u0275repeaterTrackByIdentity);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275conditionalCreate(39, AaHearingComponent_Conditional_5_Conditional_39_Template, 5, 4, "p");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(40, "p", 10);
    i0.\u0275\u0275text(41);
    i0.\u0275\u0275pipe(42, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(43, "div", 11)(44, "button", 12);
    i0.\u0275\u0275listener("click", function AaHearingComponent_Conditional_5_Template_button_click_44_listener() {
      i0.\u0275\u0275restoreView(_r2);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.scheduleHearing());
    });
    i0.\u0275\u0275conditionalCreate(45, AaHearingComponent_Conditional_5_Conditional_45_Template, 2, 3)(46, AaHearingComponent_Conditional_5_Conditional_46_Template, 2, 3);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(47, "button", 13);
    i0.\u0275\u0275listener("click", function AaHearingComponent_Conditional_5_Template_button_click_47_listener() {
      i0.\u0275\u0275restoreView(_r2);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.showPreview.set(false));
    });
    i0.\u0275\u0275text(48);
    i0.\u0275\u0275pipe(49, "translate");
    i0.\u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext();
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(4, 18, "aa.hearing.notice_preview"));
    i0.\u0275\u0275advance(5);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(9, 20, "aa.hearing.appeal_no"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", ctx_r0.appeal == null ? null : ctx_r0.appeal.appealNumber);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(14, 22, "aa.hearing.date"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind2(16, 24, ctx_r0.hearingDate, "dd MMMM yyyy"));
    i0.\u0275\u0275advance(4);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(20, 27, "aa.hearing.time"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", ctx_r0.hearingTime);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(25, 29, "aa.hearing.venue"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", ctx_r0.hearingVenue);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(30, 31, "aa.hearing.mode"), ":");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", ctx_r0.hearingMode);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate1("", i0.\u0275\u0275pipeBind1(35, 33, "aa.hearing.parties_to_notify"), ":");
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275repeater(ctx_r0.partiesToNotify);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275conditional(ctx_r0.isReschedule && ctx_r0.reason ? 39 : -1);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(42, 35, "aa.hearing.notice_recorded_caveat"), " ");
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275property("disabled", ctx_r0.scheduling());
    i0.\u0275\u0275attribute("aria-busy", ctx_r0.scheduling());
    i0.\u0275\u0275advance();
    i0.\u0275\u0275conditional(ctx_r0.scheduling() ? 45 : 46);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(49, 37, "aa.hearing.edit"));
  }
}
function AaHearingComponent_Conditional_6_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "div", 14);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(2, 1, "aa.hearing.reschedule_warning"), " ");
  }
}
function AaHearingComponent_Conditional_6_For_20_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "option", 23);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const v_r5 = ctx.$implicit;
    i0.\u0275\u0275property("value", v_r5.value);
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(2, 2, v_r5.labelKey));
  }
}
function AaHearingComponent_Conditional_6_For_27_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "option", 23);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const m_r6 = ctx.$implicit;
    i0.\u0275\u0275property("value", m_r6.value);
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(2, 2, m_r6.labelKey));
  }
}
function AaHearingComponent_Conditional_6_Conditional_45_Template(rf, ctx) {
  if (rf & 1) {
    const _r7 = i0.\u0275\u0275getCurrentView();
    i0.\u0275\u0275elementStart(0, "div", 15)(1, "label", 37);
    i0.\u0275\u0275text(2);
    i0.\u0275\u0275pipe(3, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(4, "textarea", 38);
    i0.\u0275\u0275pipe(5, "translate");
    i0.\u0275\u0275twoWayListener("ngModelChange", function AaHearingComponent_Conditional_6_Conditional_45_Template_textarea_ngModelChange_4_listener($event) {
      i0.\u0275\u0275restoreView(_r7);
      const ctx_r0 = i0.\u0275\u0275nextContext(2);
      i0.\u0275\u0275twoWayBindingSet(ctx_r0.reason, $event) || (ctx_r0.reason = $event);
      return i0.\u0275\u0275resetView($event);
    });
    i0.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext(2);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(3, 3, "aa.hearing.reason"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275twoWayProperty("ngModel", ctx_r0.reason);
    i0.\u0275\u0275attribute("placeholder", i0.\u0275\u0275pipeBind1(5, 5, "aa.hearing.reason_placeholder"));
  }
}
function AaHearingComponent_Conditional_6_Conditional_46_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "div", 33);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext(2);
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(2, 1, ctx_r0.errorKey()), " ");
  }
}
function AaHearingComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = i0.\u0275\u0275getCurrentView();
    i0.\u0275\u0275conditionalCreate(0, AaHearingComponent_Conditional_6_Conditional_0_Template, 3, 3, "div", 14);
    i0.\u0275\u0275elementStart(1, "div", 15)(2, "label", 16);
    i0.\u0275\u0275text(3);
    i0.\u0275\u0275pipe(4, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(5, "input", 17);
    i0.\u0275\u0275twoWayListener("ngModelChange", function AaHearingComponent_Conditional_6_Template_input_ngModelChange_5_listener($event) {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      i0.\u0275\u0275twoWayBindingSet(ctx_r0.hearingDate, $event) || (ctx_r0.hearingDate = $event);
      return i0.\u0275\u0275resetView($event);
    });
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(6, "div", 15)(7, "label", 18);
    i0.\u0275\u0275text(8);
    i0.\u0275\u0275pipe(9, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(10, "input", 19);
    i0.\u0275\u0275twoWayListener("ngModelChange", function AaHearingComponent_Conditional_6_Template_input_ngModelChange_10_listener($event) {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      i0.\u0275\u0275twoWayBindingSet(ctx_r0.hearingTime, $event) || (ctx_r0.hearingTime = $event);
      return i0.\u0275\u0275resetView($event);
    });
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(11, "div", 15)(12, "label", 20);
    i0.\u0275\u0275text(13);
    i0.\u0275\u0275pipe(14, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(15, "select", 21);
    i0.\u0275\u0275twoWayListener("ngModelChange", function AaHearingComponent_Conditional_6_Template_select_ngModelChange_15_listener($event) {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      i0.\u0275\u0275twoWayBindingSet(ctx_r0.hearingVenue, $event) || (ctx_r0.hearingVenue = $event);
      return i0.\u0275\u0275resetView($event);
    });
    i0.\u0275\u0275elementStart(16, "option", 22);
    i0.\u0275\u0275text(17);
    i0.\u0275\u0275pipe(18, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275repeaterCreate(19, AaHearingComponent_Conditional_6_For_20_Template, 3, 4, "option", 23, _forTrack0);
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(21, "div", 15)(22, "label", 24);
    i0.\u0275\u0275text(23);
    i0.\u0275\u0275pipe(24, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(25, "select", 25);
    i0.\u0275\u0275twoWayListener("ngModelChange", function AaHearingComponent_Conditional_6_Template_select_ngModelChange_25_listener($event) {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      i0.\u0275\u0275twoWayBindingSet(ctx_r0.hearingMode, $event) || (ctx_r0.hearingMode = $event);
      return i0.\u0275\u0275resetView($event);
    });
    i0.\u0275\u0275repeaterCreate(26, AaHearingComponent_Conditional_6_For_27_Template, 3, 4, "option", 23, _forTrack0);
    i0.\u0275\u0275elementEnd()();
    i0.\u0275\u0275elementStart(28, "fieldset", 15)(29, "legend");
    i0.\u0275\u0275text(30);
    i0.\u0275\u0275pipe(31, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(32, "div", 26)(33, "label", 27)(34, "input", 28);
    i0.\u0275\u0275listener("change", function AaHearingComponent_Conditional_6_Template_input_change_34_listener() {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.toggleParty("appellant"));
    });
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(35);
    i0.\u0275\u0275pipe(36, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(37, "label", 29)(38, "input", 30);
    i0.\u0275\u0275listener("change", function AaHearingComponent_Conditional_6_Template_input_change_38_listener() {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.toggleParty("respondent"));
    });
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(39);
    i0.\u0275\u0275pipe(40, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(41, "label", 31)(42, "input", 32);
    i0.\u0275\u0275listener("change", function AaHearingComponent_Conditional_6_Template_input_change_42_listener() {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.toggleParty("ombudsman"));
    });
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275text(43);
    i0.\u0275\u0275pipe(44, "translate");
    i0.\u0275\u0275elementEnd()()();
    i0.\u0275\u0275conditionalCreate(45, AaHearingComponent_Conditional_6_Conditional_45_Template, 6, 7, "div", 15);
    i0.\u0275\u0275conditionalCreate(46, AaHearingComponent_Conditional_6_Conditional_46_Template, 3, 3, "div", 33);
    i0.\u0275\u0275elementStart(47, "div", 34)(48, "button", 35);
    i0.\u0275\u0275listener("click", function AaHearingComponent_Conditional_6_Template_button_click_48_listener() {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.previewNotice());
    });
    i0.\u0275\u0275text(49);
    i0.\u0275\u0275pipe(50, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(51, "button", 36);
    i0.\u0275\u0275listener("click", function AaHearingComponent_Conditional_6_Template_button_click_51_listener() {
      i0.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i0.\u0275\u0275nextContext();
      return i0.\u0275\u0275resetView(ctx_r0.cancel());
    });
    i0.\u0275\u0275text(52);
    i0.\u0275\u0275pipe(53, "translate");
    i0.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext();
    i0.\u0275\u0275conditional(ctx_r0.isReschedule ? 0 : -1);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(4, 27, "aa.hearing.date"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275twoWayProperty("ngModel", ctx_r0.hearingDate);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(9, 29, "aa.hearing.time"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275twoWayProperty("ngModel", ctx_r0.hearingTime);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(14, 31, "aa.hearing.venue"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275twoWayProperty("ngModel", ctx_r0.hearingVenue);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(18, 33, "aa.hearing.select_venue"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275repeater(ctx_r0.venueOptions);
    i0.\u0275\u0275advance(4);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(24, 35, "aa.hearing.mode"));
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275twoWayProperty("ngModel", ctx_r0.hearingMode);
    i0.\u0275\u0275advance();
    i0.\u0275\u0275repeater(ctx_r0.modeOptions);
    i0.\u0275\u0275advance(4);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(31, 37, "aa.hearing.parties_to_notify"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275classProp("checked", ctx_r0.isPartySelected("appellant"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275property("checked", ctx_r0.isPartySelected("appellant"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(36, 39, "aa.hearing.party_appellant"), " ");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275classProp("checked", ctx_r0.isPartySelected("respondent"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275property("checked", ctx_r0.isPartySelected("respondent"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(40, 41, "aa.hearing.party_respondent"), " ");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275classProp("checked", ctx_r0.isPartySelected("ombudsman"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275property("checked", ctx_r0.isPartySelected("ombudsman"));
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate1(" ", i0.\u0275\u0275pipeBind1(44, 43, "aa.hearing.party_ombudsman"), " ");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275conditional(ctx_r0.isReschedule ? 45 : -1);
    i0.\u0275\u0275advance();
    i0.\u0275\u0275conditional(ctx_r0.errorKey() ? 46 : -1);
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(50, 45, "aa.hearing.preview_action"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(53, 47, "aa.hearing.cancel"));
  }
}
function AaHearingComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "p", 4);
    i0.\u0275\u0275element(1, "i", 39);
    i0.\u0275\u0275elementEnd();
  }
}
function AaHearingComponent_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "p", 5);
    i0.\u0275\u0275text(1);
    i0.\u0275\u0275pipe(2, "translate");
    i0.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i0.\u0275\u0275advance();
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(2, 1, "aa.hearing.history_empty"));
  }
}
function AaHearingComponent_Conditional_13_For_17_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "tr")(1, "td");
    i0.\u0275\u0275text(2);
    i0.\u0275\u0275pipe(3, "date");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(4, "td");
    i0.\u0275\u0275text(5);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(6, "td");
    i0.\u0275\u0275text(7);
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(8, "td");
    i0.\u0275\u0275text(9);
    i0.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const h_r8 = ctx.$implicit;
    i0.\u0275\u0275classProp("superseded", h_r8.superseded);
    i0.\u0275\u0275attribute("data-testid", "hearing-row-" + h_r8.id);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind2(3, 7, h_r8.date, "dd MMM yyyy, HH:mm"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(h_r8.venue || "\u2014");
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate(h_r8.eventType);
    i0.\u0275\u0275advance(2);
    i0.\u0275\u0275textInterpolate(h_r8.outcome || "\u2014");
  }
}
function AaHearingComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    i0.\u0275\u0275elementStart(0, "table", 6)(1, "thead")(2, "tr")(3, "th");
    i0.\u0275\u0275text(4);
    i0.\u0275\u0275pipe(5, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(6, "th");
    i0.\u0275\u0275text(7);
    i0.\u0275\u0275pipe(8, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(9, "th");
    i0.\u0275\u0275text(10);
    i0.\u0275\u0275pipe(11, "translate");
    i0.\u0275\u0275elementEnd();
    i0.\u0275\u0275elementStart(12, "th");
    i0.\u0275\u0275text(13);
    i0.\u0275\u0275pipe(14, "translate");
    i0.\u0275\u0275elementEnd()()();
    i0.\u0275\u0275elementStart(15, "tbody");
    i0.\u0275\u0275repeaterCreate(16, AaHearingComponent_Conditional_13_For_17_Template, 10, 10, "tr", 40, _forTrack1);
    i0.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i0.\u0275\u0275nextContext();
    i0.\u0275\u0275advance(4);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(5, 4, "aa.hearing.date"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(8, 6, "aa.hearing.venue"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(11, 8, "aa.hearing.event"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(14, 10, "aa.hearing.outcome"));
    i0.\u0275\u0275advance(3);
    i0.\u0275\u0275repeater(ctx_r0.history());
  }
}
var AaHearingComponent = class _AaHearingComponent {
  appeal;
  hearingScheduled = new EventEmitter();
  cancelled = new EventEmitter();
  http = inject(HttpClient);
  scheduling = signal(false, ...ngDevMode ? [{ debugName: "scheduling" }] : (
    /* istanbul ignore next */
    []
  ));
  errorKey = signal("", ...ngDevMode ? [{ debugName: "errorKey" }] : (
    /* istanbul ignore next */
    []
  ));
  successKey = signal("", ...ngDevMode ? [{ debugName: "successKey" }] : (
    /* istanbul ignore next */
    []
  ));
  showPreview = signal(false, ...ngDevMode ? [{ debugName: "showPreview" }] : (
    /* istanbul ignore next */
    []
  ));
  history = signal([], ...ngDevMode ? [{ debugName: "history" }] : (
    /* istanbul ignore next */
    []
  ));
  historyLoading = signal(true, ...ngDevMode ? [{ debugName: "historyLoading" }] : (
    /* istanbul ignore next */
    []
  ));
  /** The sitting currently in force, or null when none is fixed. */
  operative = signal(null, ...ngDevMode ? [{ debugName: "operative" }] : (
    /* istanbul ignore next */
    []
  ));
  hearingDate = "";
  hearingTime = "";
  hearingVenue = "";
  /** Sent separately from the venue: the server normalises mode, and a venue string cannot express it. */
  hearingMode = "IN_PERSON";
  /** A reason is mandatory when a sitting already exists — vacating one without a reason is not auditable. */
  reason = "";
  partiesToNotify = ["appellant", "respondent"];
  /** Venue labels are keys; 'Virtual' is deliberately NOT here — that is the MODE, not a venue. */
  venueOptions = [
    { value: "RBI Head Office, Mumbai - Conference Room A", labelKey: "aa.hearing.venue_ho_mumbai_a" },
    { value: "RBI Regional Office - Hearing Room 1", labelKey: "aa.hearing.venue_ro_1" },
    { value: "RBI Regional Office - Hearing Room 2", labelKey: "aa.hearing.venue_ro_2" },
    { value: "Other", labelKey: "aa.hearing.venue_other" }
  ];
  modeOptions = [
    { value: "IN_PERSON", labelKey: "aa.hearing.mode_in_person" },
    { value: "VIDEO", labelKey: "aa.hearing.mode_video" },
    { value: "HYBRID", labelKey: "aa.hearing.mode_hybrid" }
  ];
  ngOnInit() {
    this.loadHistory();
  }
  /** True when a sitting is already fixed, so this scheduling is a RESCHEDULE and needs a reason. */
  get isReschedule() {
    return this.operative() !== null;
  }
  loadHistory() {
    const appealNumber = this.appeal?.appealNumber;
    if (!appealNumber) {
      this.historyLoading.set(false);
      return;
    }
    this.historyLoading.set(true);
    this.http.get(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/hearings`).subscribe({
      next: (res) => {
        this.history.set(res?.hearingHistory ?? []);
        this.operative.set(res?.operative ?? null);
        this.historyLoading.set(false);
      },
      error: () => {
        this.history.set([]);
        this.errorKey.set("aa.hearing.error_history_unavailable");
        this.historyLoading.set(false);
      }
    });
  }
  toggleParty(party) {
    const index = this.partiesToNotify.indexOf(party);
    if (index > -1) {
      this.partiesToNotify.splice(index, 1);
    } else {
      this.partiesToNotify.push(party);
    }
  }
  isPartySelected(party) {
    return this.partiesToNotify.includes(party);
  }
  previewNotice() {
    if (!this.validate()) {
      return;
    }
    this.errorKey.set("");
    this.showPreview.set(true);
  }
  validate() {
    if (!this.hearingDate) {
      this.errorKey.set("aa.hearing.error_date_required");
      return false;
    }
    if (!this.hearingTime) {
      this.errorKey.set("aa.hearing.error_time_required");
      return false;
    }
    if (!this.hearingVenue) {
      this.errorKey.set("aa.hearing.error_venue_required");
      return false;
    }
    if (this.isReschedule && !this.reason.trim()) {
      this.errorKey.set("aa.hearing.error_reason_required");
      return false;
    }
    this.errorKey.set("");
    return true;
  }
  scheduleHearing() {
    if (!this.validate()) {
      return;
    }
    this.scheduling.set(true);
    const appealNumber = this.appeal?.appealNumber;
    const body = {
      // A full ISO datetime. The endpoint tolerates date-only and defaults the time, but an unstated
      // default on a hearing time is exactly the sort of thing a party turns up wrong for.
      hearingDate: `${this.hearingDate}T${this.hearingTime}:00`,
      hearingVenue: this.hearingVenue,
      hearingMode: this.hearingMode,
      partiesToNotify: this.partiesToNotify
    };
    if (this.isReschedule) {
      body["reason"] = this.reason.trim();
    }
    this.http.post(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/hearings`, body).subscribe({
      next: (res) => {
        this.scheduling.set(false);
        if (res?.success === false) {
          this.errorKey.set(res.messageKey || "aa.hearing.error_failed");
          this.showPreview.set(false);
          return;
        }
        this.successKey.set(res?.messageKey || "aa.hearing.scheduled_notices_recorded");
        this.loadHistory();
        setTimeout(() => this.hearingScheduled.emit(), 1200);
      },
      error: (err) => {
        this.scheduling.set(false);
        this.showPreview.set(false);
        if (err?.status === 409) {
          this.errorKey.set(err.error?.messageKey || "aa.hearing.error_conflict");
          return;
        }
        this.errorKey.set(err.error?.messageKey || err.error?.message || "aa.hearing.error_failed");
      }
    });
  }
  cancel() {
    this.cancelled.emit();
  }
  static \u0275fac = function AaHearingComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AaHearingComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ i0.\u0275\u0275defineComponent({ type: _AaHearingComponent, selectors: [["app-aa-hearing"]], inputs: { appeal: "appeal" }, outputs: { hearingScheduled: "hearingScheduled", cancelled: "cancelled" }, decls: 14, vars: 6, consts: [[1, "hearing-panel"], ["role", "status", "aria-live", "polite", "data-testid", "hearing-success", 1, "success-msg"], ["data-testid", "notice-preview", 1, "notice-preview"], [1, "past-hearings"], ["data-testid", "history-loading", 1, "loading"], ["data-testid", "history-empty", 1, "empty"], ["data-testid", "hearing-history", 1, "history-table"], [1, "notice-header"], [1, "notice-body"], ["data-testid", "preview-parties"], ["data-testid", "notice-caveat", 1, "notice-caveat"], [1, "notice-actions"], ["type", "button", "data-testid", "confirm-hearing", 1, "submit-btn", 3, "click", "disabled"], ["type", "button", "data-testid", "edit-hearing", 1, "cancel-btn", 3, "click"], ["data-testid", "reschedule-warning", 1, "warning-banner"], [1, "form-field"], ["for", "hearing-date"], ["id", "hearing-date", "type", "date", "data-testid", "hearing-date", 3, "ngModelChange", "ngModel"], ["for", "hearing-time"], ["id", "hearing-time", "type", "time", "data-testid", "hearing-time", 3, "ngModelChange", "ngModel"], ["for", "hearing-venue"], ["id", "hearing-venue", "data-testid", "hearing-venue", 3, "ngModelChange", "ngModel"], ["value", ""], [3, "value"], ["for", "hearing-mode"], ["id", "hearing-mode", "data-testid", "hearing-mode", 3, "ngModelChange", "ngModel"], [1, "checkbox-group"], ["for", "party-appellant", 1, "checkbox-item"], ["id", "party-appellant", "type", "checkbox", "data-testid", "party-appellant", 3, "change", "checked"], ["for", "party-respondent", 1, "checkbox-item"], ["id", "party-respondent", "type", "checkbox", "data-testid", "party-respondent", 3, "change", "checked"], ["for", "party-ombudsman", 1, "checkbox-item"], ["id", "party-ombudsman", "type", "checkbox", "data-testid", "party-ombudsman", 3, "change", "checked"], ["role", "alert", "aria-live", "assertive", "data-testid", "hearing-error", 1, "error-msg"], [1, "form-actions"], ["type", "button", "data-testid", "preview-notice", 1, "preview-btn", 3, "click"], ["type", "button", "data-testid", "cancel-hearing", 1, "cancel-btn", 3, "click"], ["for", "hearing-reason"], ["id", "hearing-reason", "rows", "3", "data-testid", "hearing-reason", 3, "ngModelChange", "ngModel"], ["aria-hidden", "true", 1, "pi", "pi-spin", "pi-spinner"], [3, "superseded"]], template: function AaHearingComponent_Template(rf, ctx) {
    if (rf & 1) {
      i0.\u0275\u0275elementStart(0, "div", 0)(1, "h5");
      i0.\u0275\u0275conditionalCreate(2, AaHearingComponent_Conditional_2_Template, 2, 3)(3, AaHearingComponent_Conditional_3_Template, 2, 3);
      i0.\u0275\u0275elementEnd();
      i0.\u0275\u0275conditionalCreate(4, AaHearingComponent_Conditional_4_Template, 3, 3, "div", 1)(5, AaHearingComponent_Conditional_5_Template, 50, 39, "div", 2)(6, AaHearingComponent_Conditional_6_Template, 54, 49);
      i0.\u0275\u0275elementStart(7, "div", 3)(8, "h6");
      i0.\u0275\u0275text(9);
      i0.\u0275\u0275pipe(10, "translate");
      i0.\u0275\u0275elementEnd();
      i0.\u0275\u0275conditionalCreate(11, AaHearingComponent_Conditional_11_Template, 2, 0, "p", 4)(12, AaHearingComponent_Conditional_12_Template, 3, 3, "p", 5)(13, AaHearingComponent_Conditional_13_Template, 18, 12, "table", 6);
      i0.\u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      i0.\u0275\u0275advance(2);
      i0.\u0275\u0275conditional(ctx.isReschedule ? 2 : 3);
      i0.\u0275\u0275advance(2);
      i0.\u0275\u0275conditional(ctx.successKey() ? 4 : ctx.showPreview() ? 5 : 6);
      i0.\u0275\u0275advance(5);
      i0.\u0275\u0275textInterpolate(i0.\u0275\u0275pipeBind1(10, 4, "aa.hearing.history"));
      i0.\u0275\u0275advance(2);
      i0.\u0275\u0275conditional(ctx.historyLoading() ? 11 : ctx.history().length === 0 ? 12 : 13);
    }
  }, dependencies: [CommonModule, i1.NgClass, i1.NgComponentOutlet, i1.NgForOf, i1.NgIf, i1.NgTemplateOutlet, i1.NgStyle, i1.NgSwitch, i1.NgSwitchCase, i1.NgSwitchDefault, i1.NgPlural, i1.NgPluralCase, FormsModule, i2.\u0275NgNoValidate, i2.NgSelectOption, i2.\u0275NgSelectMultipleOption, i2.DefaultValueAccessor, i2.NumberValueAccessor, i2.RangeValueAccessor, i2.CheckboxControlValueAccessor, i2.SelectControlValueAccessor, i2.SelectMultipleControlValueAccessor, i2.RadioControlValueAccessor, i2.NgControlStatus, i2.NgControlStatusGroup, i2.RequiredValidator, i2.MinLengthValidator, i2.MaxLengthValidator, i2.PatternValidator, i2.CheckboxRequiredValidator, i2.EmailValidator, i2.MinValidator, i2.MaxValidator, i2.NgModel, i2.NgModelGroup, i2.NgForm, i1.AsyncPipe, i1.UpperCasePipe, i1.LowerCasePipe, i1.JsonPipe, i1.SlicePipe, i1.DecimalPipe, i1.PercentPipe, i1.TitleCasePipe, i1.CurrencyPipe, i1.DatePipe, i1.I18nPluralPipe, i1.I18nSelectPipe, i1.KeyValuePipe, TranslatePipe], styles: ["\n.hearing-panel[_ngcontent-%COMP%] {\n  border-top: 1px solid var(--border-subtle);\n  padding-top: 16px;\n  margin-top: 8px;\n}\n.hearing-panel[_ngcontent-%COMP%]   h5[_ngcontent-%COMP%] {\n  margin: 0 0 12px;\n  font-size: 14px;\n  color: var(--brand-primary-strong);\n}\n.form-field[_ngcontent-%COMP%] {\n  margin-bottom: 12px;\n}\n.form-field[_ngcontent-%COMP%]   label[_ngcontent-%COMP%] {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus, \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%]:focus, \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%]:focus {\n  outline: none;\n  border-color: var(--brand-primary-strong);\n}\n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%] {\n  resize: vertical;\n}\n.checkbox-group[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.checkbox-item[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  padding: 6px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 4px;\n  font-size: 13px;\n  cursor: pointer;\n}\n.checkbox-item.checked[_ngcontent-%COMP%] {\n  background: var(--brand-primary-bg);\n  border-color: var(--brand-primary-strong);\n}\n.checkbox-item[_ngcontent-%COMP%]   input[_ngcontent-%COMP%] {\n  accent-color: var(--brand-primary-strong);\n}\n.form-actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 10px;\n  margin-top: 12px;\n}\n.preview-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.preview-btn[_ngcontent-%COMP%]:hover {\n  background: #0d1b6b;\n}\n.submit-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.submit-btn[_ngcontent-%COMP%]:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.cancel-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: white;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n}\n.notice-preview[_ngcontent-%COMP%] {\n  border: 1px solid var(--border-subtle);\n  border-radius: 8px;\n  overflow: hidden;\n  margin-bottom: 12px;\n}\n.notice-header[_ngcontent-%COMP%] {\n  padding: 10px 14px;\n  background: var(--brand-primary-strong);\n  color: white;\n  font-size: 12px;\n  text-align: center;\n}\n.notice-body[_ngcontent-%COMP%] {\n  padding: 14px;\n  font-size: 13px;\n  line-height: 1.6;\n}\n.notice-body[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  margin: 4px 0;\n}\n.notice-body[_ngcontent-%COMP%]   ul[_ngcontent-%COMP%] {\n  margin: 4px 0 4px 16px;\n  padding: 0;\n}\n.notice-body[_ngcontent-%COMP%]   li[_ngcontent-%COMP%] {\n  margin: 2px 0;\n}\n.notice-actions[_ngcontent-%COMP%] {\n  padding: 12px 14px;\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  gap: 10px;\n}\n.success-msg[_ngcontent-%COMP%] {\n  padding: 12px;\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n  border-radius: 6px;\n  font-size: 13px;\n}\n.error-msg[_ngcontent-%COMP%] {\n  padding: 8px 12px;\n  background: #fbe9e7;\n  color: #d32f2f;\n  border-radius: 6px;\n  font-size: 13px;\n  margin-top: 8px;\n}\n.past-hearings[_ngcontent-%COMP%] {\n  margin-top: 16px;\n  padding-top: 12px;\n  border-top: 1px solid var(--border-subtle);\n}\n.past-hearings[_ngcontent-%COMP%]   h6[_ngcontent-%COMP%] {\n  font-size: 12px;\n  color: var(--text-secondary);\n  margin: 0 0 8px;\n  text-transform: uppercase;\n}\n.history-table[_ngcontent-%COMP%] {\n  width: 100%;\n  border-collapse: collapse;\n  font-size: 12px;\n}\n.history-table[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  padding: 6px 8px;\n  background: var(--surface-sunken);\n  text-align: left;\n  font-weight: 600;\n  color: var(--text-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.history-table[_ngcontent-%COMP%]   td[_ngcontent-%COMP%] {\n  padding: 6px 8px;\n  border-bottom: 1px solid var(--surface-subtle);\n  color: var(--text-body);\n}\n.warning-banner[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  padding: 8px 12px;\n  margin-bottom: 12px;\n  border-radius: 6px;\n  font-size: 12px;\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n  border: 1px solid #ffcc80;\n}\n.notice-caveat[_ngcontent-%COMP%] {\n  margin: 10px 0 0;\n  padding: 8px 10px;\n  border-radius: 4px;\n  font-size: 11px;\n  background: var(--brand-primary-bg);\n  color: #283593;\n  border-left: 3px solid var(--brand-primary);\n}\n.past-hearings[_ngcontent-%COMP%]   .loading[_ngcontent-%COMP%], \n.past-hearings[_ngcontent-%COMP%]   .empty[_ngcontent-%COMP%] {\n  margin: 6px 0;\n  font-size: 12px;\n  color: var(--text-muted);\n}\n.history-table[_ngcontent-%COMP%]   tr.superseded[_ngcontent-%COMP%]   td[_ngcontent-%COMP%] {\n  opacity: 0.55;\n  text-decoration: line-through;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n}\n/*# sourceMappingURL=aa-hearing.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i0.\u0275setClassMetadata(AaHearingComponent, [{
    type: Component,
    args: [{ selector: "app-aa-hearing", standalone: true, imports: [CommonModule, FormsModule, TranslatePipe], template: `<div class="hearing-panel">
  <h5>
    @if (isReschedule) {
      {{ 'aa.hearing.reschedule_title' | translate }}
    } @else {
      {{ 'aa.hearing.title' | translate }}
    }
  </h5>

  @if (successKey()) {
    <div class="success-msg" role="status" aria-live="polite" data-testid="hearing-success">
      {{ successKey() | translate }}
    </div>
  } @else if (showPreview()) {
    <div class="notice-preview" data-testid="notice-preview">
      <div class="notice-header">
        <strong>{{ 'aa.hearing.notice_preview' | translate }}</strong>
      </div>
      <div class="notice-body">
        <p><strong>{{ 'aa.hearing.appeal_no' | translate }}:</strong> {{ appeal?.appealNumber }}</p>
        <p><strong>{{ 'aa.hearing.date' | translate }}:</strong> {{ hearingDate | date:'dd MMMM yyyy' }}</p>
        <p><strong>{{ 'aa.hearing.time' | translate }}:</strong> {{ hearingTime }}</p>
        <p><strong>{{ 'aa.hearing.venue' | translate }}:</strong> {{ hearingVenue }}</p>
        <p><strong>{{ 'aa.hearing.mode' | translate }}:</strong> {{ hearingMode }}</p>
        <p><strong>{{ 'aa.hearing.parties_to_notify' | translate }}:</strong></p>
        <ul data-testid="preview-parties">
          @for (party of partiesToNotify; track party) {
            <li>{{ party | titlecase }}</li>
          }
        </ul>
        @if (isReschedule && reason) {
          <p><strong>{{ 'aa.hearing.reason' | translate }}:</strong> {{ reason }}</p>
        }
      </div>

      <!-- Says RECORDED, not sent. There is no email or SMS gateway in this deployment, and the server
           returns noticeStatus=PENDING to say exactly that. -->
      <p class="notice-caveat" data-testid="notice-caveat">
        {{ 'aa.hearing.notice_recorded_caveat' | translate }}
      </p>

      <div class="notice-actions">
        <button type="button" class="submit-btn" data-testid="confirm-hearing"
                [disabled]="scheduling()" [attr.aria-busy]="scheduling()"
                (click)="scheduleHearing()">
          @if (scheduling()) {
            {{ 'aa.hearing.scheduling' | translate }}
          } @else {
            {{ 'aa.hearing.confirm' | translate }}
          }
        </button>
        <button type="button" class="cancel-btn" data-testid="edit-hearing"
                (click)="showPreview.set(false)">{{ 'aa.hearing.edit' | translate }}</button>
      </div>
    </div>
  } @else {
    @if (isReschedule) {
      <div class="warning-banner" data-testid="reschedule-warning">
        {{ 'aa.hearing.reschedule_warning' | translate }}
      </div>
    }

    <div class="form-field">
      <label for="hearing-date">{{ 'aa.hearing.date' | translate }}</label>
      <input id="hearing-date" type="date" data-testid="hearing-date" [(ngModel)]="hearingDate" />
    </div>

    <div class="form-field">
      <label for="hearing-time">{{ 'aa.hearing.time' | translate }}</label>
      <input id="hearing-time" type="time" data-testid="hearing-time" [(ngModel)]="hearingTime" />
    </div>

    <div class="form-field">
      <label for="hearing-venue">{{ 'aa.hearing.venue' | translate }}</label>
      <select id="hearing-venue" data-testid="hearing-venue" [(ngModel)]="hearingVenue">
        <option value="">{{ 'aa.hearing.select_venue' | translate }}</option>
        @for (v of venueOptions; track v.value) {
          <option [value]="v.value">{{ v.labelKey | translate }}</option>
        }
      </select>
    </div>

    <!-- Mode is separate from venue: the server normalises it to IN_PERSON/VIDEO/HYBRID, so a venue
         string of "Virtual (Video Conference)" could never have set it. -->
    <div class="form-field">
      <label for="hearing-mode">{{ 'aa.hearing.mode' | translate }}</label>
      <select id="hearing-mode" data-testid="hearing-mode" [(ngModel)]="hearingMode">
        @for (m of modeOptions; track m.value) {
          <option [value]="m.value">{{ m.labelKey | translate }}</option>
        }
      </select>
    </div>

    <fieldset class="form-field">
      <legend>{{ 'aa.hearing.parties_to_notify' | translate }}</legend>
      <div class="checkbox-group">
        <label class="checkbox-item" for="party-appellant"
               [class.checked]="isPartySelected('appellant')">
          <input id="party-appellant" type="checkbox" data-testid="party-appellant"
                 [checked]="isPartySelected('appellant')" (change)="toggleParty('appellant')">
          {{ 'aa.hearing.party_appellant' | translate }}
        </label>
        <label class="checkbox-item" for="party-respondent"
               [class.checked]="isPartySelected('respondent')">
          <input id="party-respondent" type="checkbox" data-testid="party-respondent"
                 [checked]="isPartySelected('respondent')" (change)="toggleParty('respondent')">
          {{ 'aa.hearing.party_respondent' | translate }}
        </label>
        <label class="checkbox-item" for="party-ombudsman"
               [class.checked]="isPartySelected('ombudsman')">
          <input id="party-ombudsman" type="checkbox" data-testid="party-ombudsman"
                 [checked]="isPartySelected('ombudsman')" (change)="toggleParty('ombudsman')">
          {{ 'aa.hearing.party_ombudsman' | translate }}
        </label>
      </div>
    </fieldset>

    @if (isReschedule) {
      <div class="form-field">
        <label for="hearing-reason">{{ 'aa.hearing.reason' | translate }}</label>
        <textarea id="hearing-reason" rows="3" data-testid="hearing-reason"
                  [(ngModel)]="reason"
                  [attr.placeholder]="'aa.hearing.reason_placeholder' | translate"></textarea>
      </div>
    }

    @if (errorKey()) {
      <div class="error-msg" role="alert" aria-live="assertive" data-testid="hearing-error">
        {{ errorKey() | translate }}
      </div>
    }

    <div class="form-actions">
      <button type="button" class="preview-btn" data-testid="preview-notice"
              (click)="previewNotice()">{{ 'aa.hearing.preview_action' | translate }}</button>
      <button type="button" class="cancel-btn" data-testid="cancel-hearing"
              (click)="cancel()">{{ 'aa.hearing.cancel' | translate }}</button>
    </div>
  }

  <!-- Real hearing history from the hearings endpoint. A reschedule appends rather than overwrites, so
       a vacated sitting stays visible \u2014 the previous binding was to a field no endpoint returned, so
       this table was permanently empty. -->
  <div class="past-hearings">
    <h6>{{ 'aa.hearing.history' | translate }}</h6>
    @if (historyLoading()) {
      <p class="loading" data-testid="history-loading">
        <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
      </p>
    } @else if (history().length === 0) {
      <p class="empty" data-testid="history-empty">{{ 'aa.hearing.history_empty' | translate }}</p>
    } @else {
      <table class="history-table" data-testid="hearing-history">
        <thead>
          <tr>
            <th>{{ 'aa.hearing.date' | translate }}</th>
            <th>{{ 'aa.hearing.venue' | translate }}</th>
            <th>{{ 'aa.hearing.event' | translate }}</th>
            <th>{{ 'aa.hearing.outcome' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <!-- track h.id, not h.date: a reschedule to the same date would collide on a date key. -->
          @for (h of history(); track h.id) {
            <tr [class.superseded]="h.superseded" [attr.data-testid]="'hearing-row-' + h.id">
              <td>{{ h.date | date:'dd MMM yyyy, HH:mm' }}</td>
              <td>{{ h.venue || '\u2014' }}</td>
              <td>{{ h.eventType }}</td>
              <td>{{ h.outcome || '\u2014' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  </div>
</div>
`, styles: ["/* src/app/components/aa/aa-hearing/aa-hearing.component.scss */\n.hearing-panel {\n  border-top: 1px solid var(--border-subtle);\n  padding-top: 16px;\n  margin-top: 8px;\n}\n.hearing-panel h5 {\n  margin: 0 0 12px;\n  font-size: 14px;\n  color: var(--brand-primary-strong);\n}\n.form-field {\n  margin-bottom: 12px;\n}\n.form-field label {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field input,\n.form-field select,\n.form-field textarea {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field input:focus,\n.form-field select:focus,\n.form-field textarea:focus {\n  outline: none;\n  border-color: var(--brand-primary-strong);\n}\n.form-field textarea {\n  resize: vertical;\n}\n.checkbox-group {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.checkbox-item {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  padding: 6px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 4px;\n  font-size: 13px;\n  cursor: pointer;\n}\n.checkbox-item.checked {\n  background: var(--brand-primary-bg);\n  border-color: var(--brand-primary-strong);\n}\n.checkbox-item input {\n  accent-color: var(--brand-primary-strong);\n}\n.form-actions {\n  display: flex;\n  gap: 10px;\n  margin-top: 12px;\n}\n.preview-btn {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.preview-btn:hover {\n  background: #0d1b6b;\n}\n.submit-btn {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.submit-btn:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.cancel-btn {\n  padding: 10px 20px;\n  background: white;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n}\n.notice-preview {\n  border: 1px solid var(--border-subtle);\n  border-radius: 8px;\n  overflow: hidden;\n  margin-bottom: 12px;\n}\n.notice-header {\n  padding: 10px 14px;\n  background: var(--brand-primary-strong);\n  color: white;\n  font-size: 12px;\n  text-align: center;\n}\n.notice-body {\n  padding: 14px;\n  font-size: 13px;\n  line-height: 1.6;\n}\n.notice-body p {\n  margin: 4px 0;\n}\n.notice-body ul {\n  margin: 4px 0 4px 16px;\n  padding: 0;\n}\n.notice-body li {\n  margin: 2px 0;\n}\n.notice-actions {\n  padding: 12px 14px;\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  gap: 10px;\n}\n.success-msg {\n  padding: 12px;\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n  border-radius: 6px;\n  font-size: 13px;\n}\n.error-msg {\n  padding: 8px 12px;\n  background: #fbe9e7;\n  color: #d32f2f;\n  border-radius: 6px;\n  font-size: 13px;\n  margin-top: 8px;\n}\n.past-hearings {\n  margin-top: 16px;\n  padding-top: 12px;\n  border-top: 1px solid var(--border-subtle);\n}\n.past-hearings h6 {\n  font-size: 12px;\n  color: var(--text-secondary);\n  margin: 0 0 8px;\n  text-transform: uppercase;\n}\n.history-table {\n  width: 100%;\n  border-collapse: collapse;\n  font-size: 12px;\n}\n.history-table th {\n  padding: 6px 8px;\n  background: var(--surface-sunken);\n  text-align: left;\n  font-weight: 600;\n  color: var(--text-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.history-table td {\n  padding: 6px 8px;\n  border-bottom: 1px solid var(--surface-subtle);\n  color: var(--text-body);\n}\n.warning-banner {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  padding: 8px 12px;\n  margin-bottom: 12px;\n  border-radius: 6px;\n  font-size: 12px;\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n  border: 1px solid #ffcc80;\n}\n.notice-caveat {\n  margin: 10px 0 0;\n  padding: 8px 10px;\n  border-radius: 4px;\n  font-size: 11px;\n  background: var(--brand-primary-bg);\n  color: #283593;\n  border-left: 3px solid var(--brand-primary);\n}\n.past-hearings .loading,\n.past-hearings .empty {\n  margin: 6px 0;\n  font-size: 12px;\n  color: var(--text-muted);\n}\n.history-table tr.superseded td {\n  opacity: 0.55;\n  text-decoration: line-through;\n}\n.form-field input:focus-visible,\n.form-field select:focus-visible,\n.form-field textarea:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n}\n/*# sourceMappingURL=aa-hearing.component.css.map */\n"] }]
  }], null, { appeal: [{
    type: Input
  }], hearingScheduled: [{
    type: Output
  }], cancelled: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i0.\u0275setClassDebugInfo(AaHearingComponent, { className: "AaHearingComponent", filePath: "src/app/components/aa/aa-hearing/aa-hearing.component.ts", lineNumber: 45 });
})();
(() => {
  const id = "src%2Fapp%2Fcomponents%2Faa%2Faa-hearing%2Faa-hearing.component.ts%40AaHearingComponent";
  function AaHearingComponent_HmrLoad(t) {
    import(
      /* @vite-ignore */
      __vite__injectQuery(i0.\u0275\u0275getReplaceMetadataURL(id, t, import.meta.url), 'import')
    ).then((m) => m.default && i0.\u0275\u0275replaceMetadata(AaHearingComponent, m.default, [i0, i1, i2], [CommonModule, FormsModule, TranslatePipe, Component, Input, Output], import.meta, id));
  }
  (typeof ngDevMode === "undefined" || ngDevMode) && AaHearingComponent_HmrLoad(Date.now());
  (typeof ngDevMode === "undefined" || ngDevMode) && (import.meta.hot && import.meta.hot.on("angular:component-update", (d) => d.id === id && AaHearingComponent_HmrLoad(d.timestamp)));
})();

// src/app/components/aa/aa-order/aa-order.component.ts
import { Component as Component2, Input as Input2, Output as Output2, EventEmitter as EventEmitter2, inject as inject2, signal as signal2 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import { CommonModule as CommonModule2 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import { FormsModule as FormsModule2 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
import { HttpClient as HttpClient2 } from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common_http.js?v=af65e101";
import * as i02 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import * as i12 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import * as i22 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
var _forTrack02 = ($index, $item) => $item.value;
function AaOrderComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    i02.\u0275\u0275elementStart(0, "div", 1);
    i02.\u0275\u0275text(1);
    i02.\u0275\u0275pipe(2, "translate");
    i02.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext();
    i02.\u0275\u0275advance();
    i02.\u0275\u0275textInterpolate1(" ", i02.\u0275\u0275pipeBind1(2, 1, ctx_r0.successKey()), " ");
  }
}
function AaOrderComponent_Conditional_5_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    i02.\u0275\u0275elementStart(0, "p")(1, "strong");
    i02.\u0275\u0275text(2);
    i02.\u0275\u0275pipe(3, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(4, "span", 10);
    i02.\u0275\u0275text(5);
    i02.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext(2);
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275textInterpolate1("", i02.\u0275\u0275pipeBind1(3, 2, "aa.order.award_amount"), ":");
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate1("\u20B9", ctx_r0.awardAmount);
  }
}
function AaOrderComponent_Conditional_5_Conditional_31_Template(rf, ctx) {
  if (rf & 1) {
    i02.\u0275\u0275text(0);
    i02.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i02.\u0275\u0275textInterpolate1(" ", i02.\u0275\u0275pipeBind1(1, 1, "aa.order.submitting"), " ");
  }
}
function AaOrderComponent_Conditional_5_Conditional_32_Template(rf, ctx) {
  if (rf & 1) {
    i02.\u0275\u0275text(0);
    i02.\u0275\u0275pipe(1, "translate");
  }
  if (rf & 2) {
    i02.\u0275\u0275textInterpolate1(" ", i02.\u0275\u0275pipeBind1(1, 1, "aa.order.confirm"), " ");
  }
}
function AaOrderComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = i02.\u0275\u0275getCurrentView();
    i02.\u0275\u0275elementStart(0, "div", 2)(1, "div", 3)(2, "strong");
    i02.\u0275\u0275text(3);
    i02.\u0275\u0275pipe(4, "translate");
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275elementStart(5, "div", 4)(6, "p")(7, "strong");
    i02.\u0275\u0275text(8);
    i02.\u0275\u0275pipe(9, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275text(10);
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(11, "p")(12, "strong");
    i02.\u0275\u0275text(13);
    i02.\u0275\u0275pipe(14, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275text(15);
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(16, "p")(17, "strong");
    i02.\u0275\u0275text(18);
    i02.\u0275\u0275pipe(19, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(20, "span", 5);
    i02.\u0275\u0275text(21);
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275conditionalCreate(22, AaOrderComponent_Conditional_5_Conditional_22_Template, 6, 4, "p");
    i02.\u0275\u0275elementStart(23, "p")(24, "strong");
    i02.\u0275\u0275text(25);
    i02.\u0275\u0275pipe(26, "translate");
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275elementStart(27, "div", 6);
    i02.\u0275\u0275text(28);
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275elementStart(29, "div", 7)(30, "button", 8);
    i02.\u0275\u0275listener("click", function AaOrderComponent_Conditional_5_Template_button_click_30_listener() {
      i02.\u0275\u0275restoreView(_r2);
      const ctx_r0 = i02.\u0275\u0275nextContext();
      return i02.\u0275\u0275resetView(ctx_r0.submitOrder());
    });
    i02.\u0275\u0275conditionalCreate(31, AaOrderComponent_Conditional_5_Conditional_31_Template, 2, 3)(32, AaOrderComponent_Conditional_5_Conditional_32_Template, 2, 3);
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(33, "button", 9);
    i02.\u0275\u0275listener("click", function AaOrderComponent_Conditional_5_Template_button_click_33_listener() {
      i02.\u0275\u0275restoreView(_r2);
      const ctx_r0 = i02.\u0275\u0275nextContext();
      return i02.\u0275\u0275resetView(ctx_r0.showPreview.set(false));
    });
    i02.\u0275\u0275text(34);
    i02.\u0275\u0275pipe(35, "translate");
    i02.\u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext();
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(4, 15, "aa.order.preview"));
    i02.\u0275\u0275advance(5);
    i02.\u0275\u0275textInterpolate1("", i02.\u0275\u0275pipeBind1(9, 17, "aa.order.appeal_no"), ":");
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275textInterpolate1(" ", ctx_r0.appeal == null ? null : ctx_r0.appeal.appealNumber);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate1("", i02.\u0275\u0275pipeBind1(14, 19, "aa.order.classification"), ":");
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275textInterpolate1(" ", ctx_r0.appeal == null ? null : ctx_r0.appeal.classification);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate1("", i02.\u0275\u0275pipeBind1(19, 21, "aa.order.outcome"), ":");
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275attribute("data-outcome", ctx_r0.outcome);
    i02.\u0275\u0275advance();
    i02.\u0275\u0275textInterpolate1(" ", ctx_r0.outcome, " ");
    i02.\u0275\u0275advance();
    i02.\u0275\u0275conditional(ctx_r0.awardBearing && ctx_r0.awardAmount !== null ? 22 : -1);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate1("", i02.\u0275\u0275pipeBind1(26, 23, "aa.order.summary"), ":");
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(ctx_r0.orderSummary);
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275property("disabled", ctx_r0.submitting());
    i02.\u0275\u0275attribute("aria-busy", ctx_r0.submitting());
    i02.\u0275\u0275advance();
    i02.\u0275\u0275conditional(ctx_r0.submitting() ? 31 : 32);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(35, 25, "aa.order.edit"));
  }
}
function AaOrderComponent_Conditional_6_For_7_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = i02.\u0275\u0275getCurrentView();
    i02.\u0275\u0275elementStart(0, "label", 20)(1, "input", 21);
    i02.\u0275\u0275twoWayListener("ngModelChange", function AaOrderComponent_Conditional_6_For_7_Template_input_ngModelChange_1_listener($event) {
      i02.\u0275\u0275restoreView(_r4);
      const ctx_r0 = i02.\u0275\u0275nextContext(2);
      i02.\u0275\u0275twoWayBindingSet(ctx_r0.outcome, $event) || (ctx_r0.outcome = $event);
      return i02.\u0275\u0275resetView($event);
    });
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(2, "div", 22)(3, "span", 23);
    i02.\u0275\u0275text(4);
    i02.\u0275\u0275pipe(5, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(6, "span", 24);
    i02.\u0275\u0275text(7);
    i02.\u0275\u0275pipe(8, "translate");
    i02.\u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const o_r5 = ctx.$implicit;
    const ctx_r0 = i02.\u0275\u0275nextContext(2);
    i02.\u0275\u0275classProp("selected", ctx_r0.outcome === o_r5.value);
    i02.\u0275\u0275attribute("for", "outcome-" + o_r5.value);
    i02.\u0275\u0275advance();
    i02.\u0275\u0275property("id", "outcome-" + o_r5.value)("value", o_r5.value);
    i02.\u0275\u0275twoWayProperty("ngModel", ctx_r0.outcome);
    i02.\u0275\u0275attribute("data-testid", "outcome-" + o_r5.value);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(5, 9, o_r5.labelKey));
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(8, 11, o_r5.descriptionKey));
  }
}
function AaOrderComponent_Conditional_6_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = i02.\u0275\u0275getCurrentView();
    i02.\u0275\u0275elementStart(0, "div", 11)(1, "label", 25);
    i02.\u0275\u0275text(2);
    i02.\u0275\u0275pipe(3, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(4, "input", 26);
    i02.\u0275\u0275pipe(5, "translate");
    i02.\u0275\u0275twoWayListener("ngModelChange", function AaOrderComponent_Conditional_6_Conditional_8_Template_input_ngModelChange_4_listener($event) {
      i02.\u0275\u0275restoreView(_r6);
      const ctx_r0 = i02.\u0275\u0275nextContext(2);
      i02.\u0275\u0275twoWayBindingSet(ctx_r0.awardAmount, $event) || (ctx_r0.awardAmount = $event);
      return i02.\u0275\u0275resetView($event);
    });
    i02.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext(2);
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(3, 3, "aa.order.award_amount"));
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275twoWayProperty("ngModel", ctx_r0.awardAmount);
    i02.\u0275\u0275attribute("placeholder", i02.\u0275\u0275pipeBind1(5, 5, "aa.order.award_amount_placeholder"));
  }
}
function AaOrderComponent_Conditional_6_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    i02.\u0275\u0275elementStart(0, "div", 16);
    i02.\u0275\u0275text(1);
    i02.\u0275\u0275pipe(2, "translate");
    i02.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext(2);
    i02.\u0275\u0275advance();
    i02.\u0275\u0275textInterpolate1(" ", i02.\u0275\u0275pipeBind1(2, 1, ctx_r0.errorKey()), " ");
  }
}
function AaOrderComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = i02.\u0275\u0275getCurrentView();
    i02.\u0275\u0275elementStart(0, "fieldset", 11)(1, "legend");
    i02.\u0275\u0275text(2);
    i02.\u0275\u0275pipe(3, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(4, "div", 12);
    i02.\u0275\u0275pipe(5, "translate");
    i02.\u0275\u0275repeaterCreate(6, AaOrderComponent_Conditional_6_For_7_Template, 9, 13, "label", 13, _forTrack02);
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275conditionalCreate(8, AaOrderComponent_Conditional_6_Conditional_8_Template, 6, 7, "div", 11);
    i02.\u0275\u0275elementStart(9, "div", 11)(10, "label", 14);
    i02.\u0275\u0275text(11);
    i02.\u0275\u0275pipe(12, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(13, "textarea", 15);
    i02.\u0275\u0275pipe(14, "translate");
    i02.\u0275\u0275twoWayListener("ngModelChange", function AaOrderComponent_Conditional_6_Template_textarea_ngModelChange_13_listener($event) {
      i02.\u0275\u0275restoreView(_r3);
      const ctx_r0 = i02.\u0275\u0275nextContext();
      i02.\u0275\u0275twoWayBindingSet(ctx_r0.orderSummary, $event) || (ctx_r0.orderSummary = $event);
      return i02.\u0275\u0275resetView($event);
    });
    i02.\u0275\u0275elementEnd()();
    i02.\u0275\u0275conditionalCreate(15, AaOrderComponent_Conditional_6_Conditional_15_Template, 3, 3, "div", 16);
    i02.\u0275\u0275elementStart(16, "div", 17)(17, "button", 18);
    i02.\u0275\u0275listener("click", function AaOrderComponent_Conditional_6_Template_button_click_17_listener() {
      i02.\u0275\u0275restoreView(_r3);
      const ctx_r0 = i02.\u0275\u0275nextContext();
      return i02.\u0275\u0275resetView(ctx_r0.previewOrder());
    });
    i02.\u0275\u0275text(18);
    i02.\u0275\u0275pipe(19, "translate");
    i02.\u0275\u0275elementEnd();
    i02.\u0275\u0275elementStart(20, "button", 19);
    i02.\u0275\u0275listener("click", function AaOrderComponent_Conditional_6_Template_button_click_20_listener() {
      i02.\u0275\u0275restoreView(_r3);
      const ctx_r0 = i02.\u0275\u0275nextContext();
      return i02.\u0275\u0275resetView(ctx_r0.cancel());
    });
    i02.\u0275\u0275text(21);
    i02.\u0275\u0275pipe(22, "translate");
    i02.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = i02.\u0275\u0275nextContext();
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(3, 9, "aa.order.outcome"));
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275attribute("aria-label", i02.\u0275\u0275pipeBind1(5, 11, "aa.order.outcome"));
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275repeater(ctx_r0.outcomes);
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275conditional(ctx_r0.awardBearing ? 8 : -1);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(12, 13, "aa.order.summary"));
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275twoWayProperty("ngModel", ctx_r0.orderSummary);
    i02.\u0275\u0275attribute("placeholder", i02.\u0275\u0275pipeBind1(14, 15, "aa.order.summary_placeholder"));
    i02.\u0275\u0275advance(2);
    i02.\u0275\u0275conditional(ctx_r0.errorKey() ? 15 : -1);
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(19, 17, "aa.order.preview_action"));
    i02.\u0275\u0275advance(3);
    i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(22, 19, "aa.order.cancel"));
  }
}
var AaOrderComponent = class _AaOrderComponent {
  appeal;
  orderPassed = new EventEmitter2();
  cancelled = new EventEmitter2();
  http = inject2(HttpClient2);
  submitting = signal2(false, ...ngDevMode ? [{ debugName: "submitting" }] : (
    /* istanbul ignore next */
    []
  ));
  errorKey = signal2("", ...ngDevMode ? [{ debugName: "errorKey" }] : (
    /* istanbul ignore next */
    []
  ));
  successKey = signal2("", ...ngDevMode ? [{ debugName: "successKey" }] : (
    /* istanbul ignore next */
    []
  ));
  showPreview = signal2(false, ...ngDevMode ? [{ debugName: "showPreview" }] : (
    /* istanbul ignore next */
    []
  ));
  outcome = "";
  awardAmount = null;
  orderSummary = "";
  /** Labels are translation keys; the values are the server's vocabulary and must not be localised. */
  outcomes = [
    { value: "UPHELD", labelKey: "aa.order.outcome_upheld", descriptionKey: "aa.order.outcome_upheld_desc" },
    { value: "MODIFIED", labelKey: "aa.order.outcome_modified", descriptionKey: "aa.order.outcome_modified_desc" },
    { value: "SET_ASIDE", labelKey: "aa.order.outcome_set_aside", descriptionKey: "aa.order.outcome_set_aside_desc" },
    { value: "REMANDED", labelKey: "aa.order.outcome_remanded", descriptionKey: "aa.order.outcome_remanded_desc" },
    { value: "DISMISSED", labelKey: "aa.order.outcome_dismissed", descriptionKey: "aa.order.outcome_dismissed_desc" }
  ];
  /**
   * Outcomes that can carry a monetary award.
   *
   * Mirrors the server's own rule: an award submitted with any other outcome is silently dropped, so
   * offering the field would invite an officer to enter a figure that never gets stored.
   */
  get awardBearing() {
    return this.outcome === "MODIFIED" || this.outcome === "UPHELD";
  }
  previewOrder() {
    this.errorKey.set("");
    if (!this.outcome) {
      this.errorKey.set("aa.order.error_outcome_required");
      return;
    }
    if (!this.orderSummary.trim()) {
      this.errorKey.set("aa.order.error_summary_required");
      return;
    }
    if (this.awardBearing && this.awardAmount !== null && this.awardAmount < 0) {
      this.errorKey.set("aa.order.error_amount_invalid");
      return;
    }
    this.showPreview.set(true);
  }
  submitOrder() {
    this.errorKey.set("");
    this.submitting.set(true);
    const appealNumber = this.appeal?.appealNumber;
    const body = {
      outcome: this.outcome,
      orderSummary: this.orderSummary
    };
    if (this.awardBearing && this.awardAmount !== null) {
      body["awardAmount"] = this.awardAmount;
    }
    this.http.post(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/order`, body).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (res?.success === false) {
          this.errorKey.set(res.messageKey || "aa.order.error_failed");
          this.showPreview.set(false);
          return;
        }
        this.successKey.set(res?.messageKey || "aa.order.passed");
        setTimeout(() => this.orderPassed.emit(), 1200);
      },
      error: (err) => {
        this.submitting.set(false);
        this.showPreview.set(false);
        this.errorKey.set(err.error?.messageKey || err.error?.message || "aa.order.error_failed");
      }
    });
  }
  cancel() {
    this.cancelled.emit();
  }
  static \u0275fac = function AaOrderComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AaOrderComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ i02.\u0275\u0275defineComponent({ type: _AaOrderComponent, selectors: [["app-aa-order"]], inputs: { appeal: "appeal" }, outputs: { orderPassed: "orderPassed", cancelled: "cancelled" }, decls: 7, vars: 4, consts: [[1, "order-panel"], ["role", "status", "aria-live", "polite", "data-testid", "order-success", 1, "success-msg"], ["data-testid", "order-preview", 1, "order-preview"], [1, "preview-header"], [1, "preview-body"], ["data-testid", "preview-outcome", 1, "outcome-badge"], ["data-testid", "preview-summary", 1, "preview-summary"], [1, "preview-actions"], ["type", "button", "data-testid", "confirm-order", 1, "submit-btn", 3, "click", "disabled"], ["type", "button", "data-testid", "edit-order", 1, "cancel-btn", 3, "click"], ["data-testid", "preview-award"], [1, "form-field"], ["role", "radiogroup", 1, "outcome-options"], [1, "outcome-option", 3, "selected"], ["for", "order-summary"], ["id", "order-summary", "rows", "5", "data-testid", "order-summary", 3, "ngModelChange", "ngModel"], ["role", "alert", "aria-live", "assertive", "data-testid", "order-error", 1, "error-msg"], [1, "form-actions"], ["type", "button", "data-testid", "preview-order", 1, "preview-btn", 3, "click"], ["type", "button", "data-testid", "cancel-order", 1, "cancel-btn", 3, "click"], [1, "outcome-option"], ["type", "radio", "name", "outcome", 3, "ngModelChange", "id", "value", "ngModel"], [1, "outcome-info"], [1, "outcome-label"], [1, "outcome-desc"], ["for", "award-amount"], ["id", "award-amount", "type", "number", "min", "0", "data-testid", "award-amount", 3, "ngModelChange", "ngModel"]], template: function AaOrderComponent_Template(rf, ctx) {
    if (rf & 1) {
      i02.\u0275\u0275elementStart(0, "div", 0)(1, "h5");
      i02.\u0275\u0275text(2);
      i02.\u0275\u0275pipe(3, "translate");
      i02.\u0275\u0275elementEnd();
      i02.\u0275\u0275conditionalCreate(4, AaOrderComponent_Conditional_4_Template, 3, 3, "div", 1)(5, AaOrderComponent_Conditional_5_Template, 36, 27, "div", 2)(6, AaOrderComponent_Conditional_6_Template, 23, 21);
      i02.\u0275\u0275elementEnd();
    }
    if (rf & 2) {
      i02.\u0275\u0275advance(2);
      i02.\u0275\u0275textInterpolate(i02.\u0275\u0275pipeBind1(3, 2, "aa.order.title"));
      i02.\u0275\u0275advance(2);
      i02.\u0275\u0275conditional(ctx.successKey() ? 4 : ctx.showPreview() ? 5 : 6);
    }
  }, dependencies: [CommonModule2, i12.NgClass, i12.NgComponentOutlet, i12.NgForOf, i12.NgIf, i12.NgTemplateOutlet, i12.NgStyle, i12.NgSwitch, i12.NgSwitchCase, i12.NgSwitchDefault, i12.NgPlural, i12.NgPluralCase, FormsModule2, i22.\u0275NgNoValidate, i22.NgSelectOption, i22.\u0275NgSelectMultipleOption, i22.DefaultValueAccessor, i22.NumberValueAccessor, i22.RangeValueAccessor, i22.CheckboxControlValueAccessor, i22.SelectControlValueAccessor, i22.SelectMultipleControlValueAccessor, i22.RadioControlValueAccessor, i22.NgControlStatus, i22.NgControlStatusGroup, i22.RequiredValidator, i22.MinLengthValidator, i22.MaxLengthValidator, i22.PatternValidator, i22.CheckboxRequiredValidator, i22.EmailValidator, i22.MinValidator, i22.MaxValidator, i22.NgModel, i22.NgModelGroup, i22.NgForm, i12.AsyncPipe, i12.UpperCasePipe, i12.LowerCasePipe, i12.JsonPipe, i12.SlicePipe, i12.DecimalPipe, i12.PercentPipe, i12.TitleCasePipe, i12.CurrencyPipe, i12.DatePipe, i12.I18nPluralPipe, i12.I18nSelectPipe, i12.KeyValuePipe, TranslatePipe], styles: ["\n.order-panel[_ngcontent-%COMP%] {\n  border-top: 1px solid var(--border-subtle);\n  padding-top: 16px;\n  margin-top: 8px;\n}\n.order-panel[_ngcontent-%COMP%]   h5[_ngcontent-%COMP%] {\n  margin: 0 0 12px;\n  font-size: 14px;\n  color: var(--brand-primary-strong);\n}\n.reference-info[_ngcontent-%COMP%] {\n  padding: 10px 14px;\n  background: var(--surface-sunken);\n  border-radius: 6px;\n  margin-bottom: 14px;\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.reference-info[_ngcontent-%COMP%]   .ref-label[_ngcontent-%COMP%] {\n  font-size: 12px;\n  color: var(--text-muted);\n}\n.reference-info[_ngcontent-%COMP%]   .ref-value[_ngcontent-%COMP%] {\n  font-size: 14px;\n  font-weight: 600;\n  color: var(--brand-primary-strong);\n}\n.form-field[_ngcontent-%COMP%] {\n  margin-bottom: 12px;\n}\n.form-field[_ngcontent-%COMP%]   label[_ngcontent-%COMP%] {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus, \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%]:focus, \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%]:focus {\n  outline: none;\n  border-color: var(--brand-primary-strong);\n}\n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%] {\n  resize: vertical;\n}\n.outcome-options[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.outcome-option[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 10px;\n  padding: 10px 12px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  transition: all 0.2s;\n}\n.outcome-option[_ngcontent-%COMP%]:hover {\n  border-color: var(--brand-primary-strong);\n  background: #f8f9ff;\n}\n.outcome-option.selected[_ngcontent-%COMP%] {\n  border-color: var(--brand-primary-strong);\n  background: var(--brand-primary-bg);\n}\n.outcome-option[_ngcontent-%COMP%]   input[_ngcontent-%COMP%] {\n  accent-color: var(--brand-primary-strong);\n}\n.outcome-option[_ngcontent-%COMP%]   .outcome-info[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n}\n.outcome-option[_ngcontent-%COMP%]   .outcome-label[_ngcontent-%COMP%] {\n  font-size: 13px;\n  font-weight: 600;\n  color: var(--text-body);\n}\n.outcome-option[_ngcontent-%COMP%]   .outcome-desc[_ngcontent-%COMP%] {\n  font-size: 11px;\n  color: var(--text-muted);\n}\n.form-actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 10px;\n  margin-top: 12px;\n}\n.preview-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.preview-btn[_ngcontent-%COMP%]:hover {\n  background: #0d1b6b;\n}\n.submit-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.submit-btn[_ngcontent-%COMP%]:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.cancel-btn[_ngcontent-%COMP%] {\n  padding: 10px 20px;\n  background: white;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n}\n.order-preview[_ngcontent-%COMP%] {\n  border: 1px solid var(--border-subtle);\n  border-radius: 8px;\n  overflow: hidden;\n  margin-bottom: 12px;\n}\n.preview-header[_ngcontent-%COMP%] {\n  padding: 10px 14px;\n  background: var(--brand-primary-strong);\n  color: white;\n  font-size: 12px;\n  text-align: center;\n}\n.preview-body[_ngcontent-%COMP%] {\n  padding: 14px;\n  font-size: 13px;\n  line-height: 1.6;\n}\n.preview-body[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  margin: 4px 0;\n}\n.preview-summary[_ngcontent-%COMP%] {\n  padding: 10px;\n  background: var(--surface-subtle);\n  border: 1px solid var(--border-subtle);\n  border-radius: 4px;\n  font-size: 13px;\n  white-space: pre-wrap;\n  line-height: 1.5;\n  margin-top: 6px;\n}\n.preview-actions[_ngcontent-%COMP%] {\n  padding: 12px 14px;\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  gap: 10px;\n}\n.outcome-badge[_ngcontent-%COMP%] {\n  display: inline-block;\n  padding: 2px 8px;\n  border-radius: 4px;\n  font-size: 12px;\n  font-weight: 600;\n}\n.outcome-badge[data-outcome=UPHELD][_ngcontent-%COMP%] {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.outcome-badge[data-outcome=MODIFIED][_ngcontent-%COMP%] {\n  background: var(--brand-primary-bg-strong);\n  color: var(--brand-primary-strong);\n}\n.outcome-badge[data-outcome=SET_ASIDE][_ngcontent-%COMP%] {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n}\n.outcome-badge[data-outcome=REMANDED][_ngcontent-%COMP%] {\n  background: var(--accent-pink-bg);\n  color: var(--accent-pink-fg);\n}\n.outcome-badge[data-outcome=DISMISSED][_ngcontent-%COMP%] {\n  background: var(--state-danger-bg);\n  color: var(--state-danger-fg);\n}\n.success-msg[_ngcontent-%COMP%] {\n  padding: 12px;\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n  border-radius: 6px;\n  font-size: 13px;\n}\n.error-msg[_ngcontent-%COMP%] {\n  padding: 8px 12px;\n  background: #fbe9e7;\n  color: #d32f2f;\n  border-radius: 6px;\n  font-size: 13px;\n  margin-top: 8px;\n}\n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n}\nfieldset.form-field[_ngcontent-%COMP%] {\n  border: none;\n  padding: 0;\n  margin: 0 0 12px;\n}\nfieldset.form-field[_ngcontent-%COMP%]   legend[_ngcontent-%COMP%] {\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n  padding: 0;\n}\n/*# sourceMappingURL=aa-order.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i02.\u0275setClassMetadata(AaOrderComponent, [{
    type: Component2,
    args: [{ selector: "app-aa-order", standalone: true, imports: [CommonModule2, FormsModule2, TranslatePipe], template: `<div class="order-panel">
  <h5>{{ 'aa.order.title' | translate }}</h5>

  @if (successKey()) {
    <div class="success-msg" role="status" aria-live="polite" data-testid="order-success">
      {{ successKey() | translate }}
    </div>
  } @else if (showPreview()) {
    <!-- Preview before an irreversible act: an issued order cannot be edited, only superseded by a
         linked correction, so the officer confirms the exact text first. -->
    <div class="order-preview" data-testid="order-preview">
      <div class="preview-header">
        <strong>{{ 'aa.order.preview' | translate }}</strong>
      </div>
      <div class="preview-body">
        <p><strong>{{ 'aa.order.appeal_no' | translate }}:</strong> {{ appeal?.appealNumber }}</p>
        <p><strong>{{ 'aa.order.classification' | translate }}:</strong> {{ appeal?.classification }}</p>
        <p><strong>{{ 'aa.order.outcome' | translate }}:</strong>
          <span class="outcome-badge" [attr.data-outcome]="outcome" data-testid="preview-outcome">
            {{ outcome }}
          </span>
        </p>
        @if (awardBearing && awardAmount !== null) {
          <p><strong>{{ 'aa.order.award_amount' | translate }}:</strong>
            <span data-testid="preview-award">&#8377;{{ awardAmount }}</span>
          </p>
        }
        <p><strong>{{ 'aa.order.summary' | translate }}:</strong></p>
        <div class="preview-summary" data-testid="preview-summary">{{ orderSummary }}</div>
      </div>
      <div class="preview-actions">
        <button type="button" class="submit-btn" data-testid="confirm-order"
                [disabled]="submitting()" [attr.aria-busy]="submitting()"
                (click)="submitOrder()">
          @if (submitting()) {
            {{ 'aa.order.submitting' | translate }}
          } @else {
            {{ 'aa.order.confirm' | translate }}
          }
        </button>
        <button type="button" class="cancel-btn" data-testid="edit-order"
                (click)="showPreview.set(false)">{{ 'aa.order.edit' | translate }}</button>
      </div>
    </div>
  } @else {
    <fieldset class="form-field">
      <legend>{{ 'aa.order.outcome' | translate }}</legend>
      <div class="outcome-options" role="radiogroup"
           [attr.aria-label]="'aa.order.outcome' | translate">
        @for (o of outcomes; track o.value) {
          <label class="outcome-option" [class.selected]="outcome === o.value"
                 [attr.for]="'outcome-' + o.value">
            <input type="radio" name="outcome" [id]="'outcome-' + o.value"
                   [attr.data-testid]="'outcome-' + o.value"
                   [value]="o.value" [(ngModel)]="outcome">
            <div class="outcome-info">
              <span class="outcome-label">{{ o.labelKey | translate }}</span>
              <span class="outcome-desc">{{ o.descriptionKey | translate }}</span>
            </div>
          </label>
        }
      </div>
    </fieldset>

    <!-- Shown for UPHELD as well as MODIFIED: the server accepts an award on both, and hiding it for
         UPHELD would make a legitimate award impossible to enter. -->
    @if (awardBearing) {
      <div class="form-field">
        <label for="award-amount">{{ 'aa.order.award_amount' | translate }}</label>
        <input id="award-amount" type="number" min="0" data-testid="award-amount"
               [(ngModel)]="awardAmount"
               [attr.placeholder]="'aa.order.award_amount_placeholder' | translate" />
      </div>
    }

    <div class="form-field">
      <label for="order-summary">{{ 'aa.order.summary' | translate }}</label>
      <textarea id="order-summary" rows="5" data-testid="order-summary"
                [(ngModel)]="orderSummary"
                [attr.placeholder]="'aa.order.summary_placeholder' | translate"></textarea>
    </div>

    @if (errorKey()) {
      <div class="error-msg" role="alert" aria-live="assertive" data-testid="order-error">
        {{ errorKey() | translate }}
      </div>
    }

    <div class="form-actions">
      <button type="button" class="preview-btn" data-testid="preview-order"
              (click)="previewOrder()">{{ 'aa.order.preview_action' | translate }}</button>
      <button type="button" class="cancel-btn" data-testid="cancel-order"
              (click)="cancel()">{{ 'aa.order.cancel' | translate }}</button>
    </div>
  }
</div>
`, styles: ["/* src/app/components/aa/aa-order/aa-order.component.scss */\n.order-panel {\n  border-top: 1px solid var(--border-subtle);\n  padding-top: 16px;\n  margin-top: 8px;\n}\n.order-panel h5 {\n  margin: 0 0 12px;\n  font-size: 14px;\n  color: var(--brand-primary-strong);\n}\n.reference-info {\n  padding: 10px 14px;\n  background: var(--surface-sunken);\n  border-radius: 6px;\n  margin-bottom: 14px;\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.reference-info .ref-label {\n  font-size: 12px;\n  color: var(--text-muted);\n}\n.reference-info .ref-value {\n  font-size: 14px;\n  font-weight: 600;\n  color: var(--brand-primary-strong);\n}\n.form-field {\n  margin-bottom: 12px;\n}\n.form-field label {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field input,\n.form-field select,\n.form-field textarea {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field input:focus,\n.form-field select:focus,\n.form-field textarea:focus {\n  outline: none;\n  border-color: var(--brand-primary-strong);\n}\n.form-field textarea {\n  resize: vertical;\n}\n.outcome-options {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.outcome-option {\n  display: flex;\n  align-items: center;\n  gap: 10px;\n  padding: 10px 12px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  transition: all 0.2s;\n}\n.outcome-option:hover {\n  border-color: var(--brand-primary-strong);\n  background: #f8f9ff;\n}\n.outcome-option.selected {\n  border-color: var(--brand-primary-strong);\n  background: var(--brand-primary-bg);\n}\n.outcome-option input {\n  accent-color: var(--brand-primary-strong);\n}\n.outcome-option .outcome-info {\n  display: flex;\n  flex-direction: column;\n}\n.outcome-option .outcome-label {\n  font-size: 13px;\n  font-weight: 600;\n  color: var(--text-body);\n}\n.outcome-option .outcome-desc {\n  font-size: 11px;\n  color: var(--text-muted);\n}\n.form-actions {\n  display: flex;\n  gap: 10px;\n  margin-top: 12px;\n}\n.preview-btn {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.preview-btn:hover {\n  background: #0d1b6b;\n}\n.submit-btn {\n  padding: 10px 20px;\n  background: var(--brand-primary-strong);\n  color: white;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n  font-weight: 600;\n}\n.submit-btn:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n}\n.cancel-btn {\n  padding: 10px 20px;\n  background: white;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  cursor: pointer;\n  font-size: 13px;\n}\n.order-preview {\n  border: 1px solid var(--border-subtle);\n  border-radius: 8px;\n  overflow: hidden;\n  margin-bottom: 12px;\n}\n.preview-header {\n  padding: 10px 14px;\n  background: var(--brand-primary-strong);\n  color: white;\n  font-size: 12px;\n  text-align: center;\n}\n.preview-body {\n  padding: 14px;\n  font-size: 13px;\n  line-height: 1.6;\n}\n.preview-body p {\n  margin: 4px 0;\n}\n.preview-summary {\n  padding: 10px;\n  background: var(--surface-subtle);\n  border: 1px solid var(--border-subtle);\n  border-radius: 4px;\n  font-size: 13px;\n  white-space: pre-wrap;\n  line-height: 1.5;\n  margin-top: 6px;\n}\n.preview-actions {\n  padding: 12px 14px;\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  gap: 10px;\n}\n.outcome-badge {\n  display: inline-block;\n  padding: 2px 8px;\n  border-radius: 4px;\n  font-size: 12px;\n  font-weight: 600;\n}\n.outcome-badge[data-outcome=UPHELD] {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.outcome-badge[data-outcome=MODIFIED] {\n  background: var(--brand-primary-bg-strong);\n  color: var(--brand-primary-strong);\n}\n.outcome-badge[data-outcome=SET_ASIDE] {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n}\n.outcome-badge[data-outcome=REMANDED] {\n  background: var(--accent-pink-bg);\n  color: var(--accent-pink-fg);\n}\n.outcome-badge[data-outcome=DISMISSED] {\n  background: var(--state-danger-bg);\n  color: var(--state-danger-fg);\n}\n.success-msg {\n  padding: 12px;\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n  border-radius: 6px;\n  font-size: 13px;\n}\n.error-msg {\n  padding: 8px 12px;\n  background: #fbe9e7;\n  color: #d32f2f;\n  border-radius: 6px;\n  font-size: 13px;\n  margin-top: 8px;\n}\n.form-field input:focus-visible,\n.form-field select:focus-visible,\n.form-field textarea:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n}\nfieldset.form-field {\n  border: none;\n  padding: 0;\n  margin: 0 0 12px;\n}\nfieldset.form-field legend {\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n  padding: 0;\n}\n/*# sourceMappingURL=aa-order.component.css.map */\n"] }]
  }], null, { appeal: [{
    type: Input2
  }], orderPassed: [{
    type: Output2
  }], cancelled: [{
    type: Output2
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i02.\u0275setClassDebugInfo(AaOrderComponent, { className: "AaOrderComponent", filePath: "src/app/components/aa/aa-order/aa-order.component.ts", lineNumber: 30 });
})();
(() => {
  const id = "src%2Fapp%2Fcomponents%2Faa%2Faa-order%2Faa-order.component.ts%40AaOrderComponent";
  function AaOrderComponent_HmrLoad(t) {
    import(
      /* @vite-ignore */
      __vite__injectQuery(i02.\u0275\u0275getReplaceMetadataURL(id, t, import.meta.url), 'import')
    ).then((m) => m.default && i02.\u0275\u0275replaceMetadata(AaOrderComponent, m.default, [i02, i12, i22], [CommonModule2, FormsModule2, TranslatePipe, Component2, Input2, Output2], import.meta, id));
  }
  (typeof ngDevMode === "undefined" || ngDevMode) && AaOrderComponent_HmrLoad(Date.now());
  (typeof ngDevMode === "undefined" || ngDevMode) && (import.meta.hot && import.meta.hot.on("angular:component-update", (d) => d.id === id && AaOrderComponent_HmrLoad(d.timestamp)));
})();

// src/app/components/aa/aa-appeal-detail/aa-appeal-detail.component.ts
import * as i03 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_core.js?v=af65e101";
import * as i13 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_common.js?v=af65e101";
import * as i23 from "/@fs/C:/Projects/My-HRMS-Frontend/CMS2.0_Redzone/cms-portal-frontend/.angular/cache/21.2.8/cms-portal-frontend/vite/deps/@angular_forms.js?v=af65e101";
var _forTrack03 = ($index, $item) => $item.id;
var _forTrack12 = ($index, $item) => $item.timestamp;
function AaAppealDetailComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 6);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate2(" ", i03.\u0275\u0275pipeBind1(2, 2, "aa.detail.reviewer_tier"), ": ", ctx, " ");
  }
}
function AaAppealDetailComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 8);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 1, "aa.detail.loading"));
  }
}
function AaAppealDetailComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 9);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 1, "aa.detail.not_found"));
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 16);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275property("title", ctx_r1.appeal().classificationOverrideReason || "");
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate1(" ", i03.\u0275\u0275pipeBind1(2, 2, "aa.overridden"), " ");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 49);
    i03.\u0275\u0275text(1, "\u26A0");
    i03.\u0275\u0275elementEnd();
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 52);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(4);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate2(" ", i03.\u0275\u0275pipeBind1(2, 2, "aa.detail.sla_overdue_by"), ": ", 0 - ctx_r1.sla().daysRemaining, " ");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 52);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(4);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate2(" ", i03.\u0275\u0275pipeBind1(2, 2, "aa.detail.sla_days_remaining"), ": ", ctx_r1.sla().daysRemaining, " ");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275conditionalCreate(0, AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Conditional_0_Template, 3, 4, "span", 52)(1, AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Conditional_1_Template, 3, 4, "span", 52);
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(3);
    i03.\u0275\u0275conditional(ctx_r1.sla().breached ? 0 : 1);
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 48);
    i03.\u0275\u0275conditionalCreate(1, AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_1_Template, 2, 0, "span", 49);
    i03.\u0275\u0275elementStart(2, "span", 50);
    i03.\u0275\u0275text(3);
    i03.\u0275\u0275pipe(4, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(5, "span", 51);
    i03.\u0275\u0275text(6);
    i03.\u0275\u0275pipe(7, "translate");
    i03.\u0275\u0275pipe(8, "date");
    i03.\u0275\u0275pipe(9, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275conditionalCreate(10, AaAppealDetailComponent_Conditional_9_Conditional_15_Conditional_10_Template, 2, 1);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275classProp("error-banner", ctx_r1.sla().breached)("warning-banner", !ctx_r1.sla().breached);
    i03.\u0275\u0275attribute("data-testid", ctx_r1.sla().breached ? "sla-overdue" : "sla-status");
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.sla().breached ? 1 : -1);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(4, 10, ctx_r1.sla().statusKey));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate2(" ", i03.\u0275\u0275pipeBind1(7, 12, "aa.detail.sla_deadline"), ": ", ctx_r1.sla().deadline ? i03.\u0275\u0275pipeBind2(8, 14, ctx_r1.sla().deadline, "dd MMM yyyy") : i03.\u0275\u0275pipeBind1(9, 17, "aa.detail.not_available"), " ");
    i03.\u0275\u0275advance(4);
    i03.\u0275\u0275conditional(ctx_r1.sla().daysRemaining !== null ? 10 : -1);
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_58_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 30)(1, "span", 23);
    i03.\u0275\u0275text(2);
    i03.\u0275\u0275pipe(3, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(4, "p", 53);
    i03.\u0275\u0275text(5);
    i03.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(3, 2, "aa.detail.reason_for_delay"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().reasonForDelay);
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_142_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "section", 42)(1, "h4");
    i03.\u0275\u0275text(2);
    i03.\u0275\u0275pipe(3, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(4, "div", 21)(5, "div", 22)(6, "span", 23);
    i03.\u0275\u0275text(7);
    i03.\u0275\u0275pipe(8, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(9, "span", 54);
    i03.\u0275\u0275text(10);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(11, "div", 22)(12, "span", 23);
    i03.\u0275\u0275text(13);
    i03.\u0275\u0275pipe(14, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(15, "span", 55);
    i03.\u0275\u0275text(16);
    i03.\u0275\u0275pipe(17, "date");
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(18, "div", 22)(19, "span", 23);
    i03.\u0275\u0275text(20);
    i03.\u0275\u0275pipe(21, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(22, "span", 56);
    i03.\u0275\u0275text(23);
    i03.\u0275\u0275elementEnd()()();
    i03.\u0275\u0275elementStart(24, "div", 30)(25, "span", 23);
    i03.\u0275\u0275text(26);
    i03.\u0275\u0275pipe(27, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(28, "p", 57);
    i03.\u0275\u0275text(29);
    i03.\u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(3, 10, "aa.detail.order"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(8, 12, "aa.detail.order_outcome"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275attribute("data-outcome", ctx_r1.appeal().orderOutcome);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate1(" ", ctx_r1.appeal().orderOutcome, " ");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(14, 14, "aa.detail.order_date"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate1(" ", ctx_r1.appeal().orderDate ? i03.\u0275\u0275pipeBind2(17, 16, ctx_r1.appeal().orderDate, "dd MMM yyyy") : "\u2014", " ");
    i03.\u0275\u0275advance(4);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(21, 19, "aa.detail.award_amount"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate1(" ", ctx_r1.appeal().awardModifiedAmount ? "\u20B9" + ctx_r1.appeal().awardModifiedAmount : "\u2014", " ");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(27, 21, "aa.detail.order_summary"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().orderSummary || "\u2014");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_143_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "section", 43)(1, "h4");
    i03.\u0275\u0275text(2);
    i03.\u0275\u0275pipe(3, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(4, "div", 21)(5, "div", 22)(6, "span", 23);
    i03.\u0275\u0275text(7);
    i03.\u0275\u0275pipe(8, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(9, "span", 58);
    i03.\u0275\u0275text(10);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(11, "div", 22)(12, "span", 23);
    i03.\u0275\u0275text(13);
    i03.\u0275\u0275pipe(14, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(15, "span", 59);
    i03.\u0275\u0275text(16);
    i03.\u0275\u0275pipe(17, "date");
    i03.\u0275\u0275elementEnd()()()();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(3, 5, "aa.detail.closure"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(8, 7, "aa.detail.closure_cause"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().closureCause || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(14, 9, "aa.detail.closed_at"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate1(" ", ctx_r1.appeal().closedAt ? i03.\u0275\u0275pipeBind2(17, 11, ctx_r1.appeal().closedAt, "dd MMM yyyy, HH:mm") : "\u2014", " ");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_145_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 60);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275classProp("success", ctx_r1.actionSuccess())("error", !ctx_r1.actionSuccess());
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate1(" ", i03.\u0275\u0275pipeBind1(2, 5, ctx_r1.actionResult()), " ");
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_146_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 46)(1, "span", 61);
    i03.\u0275\u0275text(2, "\u2713");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(3, "h4");
    i03.\u0275\u0275text(4);
    i03.\u0275\u0275pipe(5, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275element(6, "app-status-badge", 62);
    i03.\u0275\u0275elementStart(7, "p");
    i03.\u0275\u0275text(8);
    i03.\u0275\u0275pipe(9, "translate");
    i03.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275advance(4);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(5, 3, "aa.detail.no_actions_available"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275property("status", ctx_r1.appeal().status);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(9, 5, "aa.detail.no_actions_hint"));
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Conditional_0_For_9_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "option", 70);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const officer_r5 = ctx.$implicit;
    i03.\u0275\u0275property("value", officer_r5.id);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(officer_r5.name);
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = i03.\u0275\u0275getCurrentView();
    i03.\u0275\u0275elementStart(0, "div", 66)(1, "label", 67);
    i03.\u0275\u0275text(2);
    i03.\u0275\u0275pipe(3, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(4, "select", 68);
    i03.\u0275\u0275twoWayListener("ngModelChange", function AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Conditional_0_Template_select_ngModelChange_4_listener($event) {
      i03.\u0275\u0275restoreView(_r4);
      const ctx_r1 = i03.\u0275\u0275nextContext(4);
      i03.\u0275\u0275twoWayBindingSet(ctx_r1.targetUser, $event) || (ctx_r1.targetUser = $event);
      return i03.\u0275\u0275resetView($event);
    });
    i03.\u0275\u0275elementStart(5, "option", 69);
    i03.\u0275\u0275text(6);
    i03.\u0275\u0275pipe(7, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275repeaterCreate(8, AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Conditional_0_For_9_Template, 2, 2, "option", 70, _forTrack03);
    i03.\u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(4);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(3, 3, "aa.detail.assign_to"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275twoWayProperty("ngModel", ctx_r1.targetUser);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(7, 5, "aa.detail.select_officer"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275repeater(ctx_r1.aaOfficers());
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275conditionalCreate(0, AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Conditional_0_Template, 10, 7, "div", 66);
  }
  if (rf & 2) {
    const action_r6 = ctx.$implicit;
    i03.\u0275\u0275conditional(action_r6.requiresTarget && action_r6.targetType === "user" ? 0 : -1);
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    const _r7 = i03.\u0275\u0275getCurrentView();
    i03.\u0275\u0275elementStart(0, "app-aa-hearing", 71);
    i03.\u0275\u0275listener("hearingScheduled", function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_9_Template_app_aa_hearing_hearingScheduled_0_listener() {
      i03.\u0275\u0275restoreView(_r7);
      const ctx_r1 = i03.\u0275\u0275nextContext(3);
      return i03.\u0275\u0275resetView(ctx_r1.onHearingScheduled());
    })("cancelled", function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_9_Template_app_aa_hearing_cancelled_0_listener() {
      i03.\u0275\u0275restoreView(_r7);
      const ctx_r1 = i03.\u0275\u0275nextContext(3);
      return i03.\u0275\u0275resetView(ctx_r1.cancelAction());
    });
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(3);
    i03.\u0275\u0275property("appeal", ctx_r1.appeal());
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    const _r8 = i03.\u0275\u0275getCurrentView();
    i03.\u0275\u0275elementStart(0, "app-aa-order", 72);
    i03.\u0275\u0275listener("orderPassed", function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_10_Template_app_aa_order_orderPassed_0_listener() {
      i03.\u0275\u0275restoreView(_r8);
      const ctx_r1 = i03.\u0275\u0275nextContext(3);
      return i03.\u0275\u0275resetView(ctx_r1.onOrderPassed());
    })("cancelled", function AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_10_Template_app_aa_order_cancelled_0_listener() {
      i03.\u0275\u0275restoreView(_r8);
      const ctx_r1 = i03.\u0275\u0275nextContext(3);
      return i03.\u0275\u0275resetView(ctx_r1.cancelAction());
    });
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(3);
    i03.\u0275\u0275property("appeal", ctx_r1.appeal());
  }
}
function AaAppealDetailComponent_Conditional_9_Conditional_147_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = i03.\u0275\u0275getCurrentView();
    i03.\u0275\u0275elementStart(0, "h4");
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(3, "p", 63);
    i03.\u0275\u0275text(4);
    i03.\u0275\u0275pipe(5, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(6, "app-workflow-action-bar", 64);
    i03.\u0275\u0275twoWayListener("remarksChange", function AaAppealDetailComponent_Conditional_9_Conditional_147_Template_app_workflow_action_bar_remarksChange_6_listener($event) {
      i03.\u0275\u0275restoreView(_r3);
      const ctx_r1 = i03.\u0275\u0275nextContext(2);
      i03.\u0275\u0275twoWayBindingSet(ctx_r1.remarks, $event) || (ctx_r1.remarks = $event);
      return i03.\u0275\u0275resetView($event);
    });
    i03.\u0275\u0275listener("select", function AaAppealDetailComponent_Conditional_9_Conditional_147_Template_app_workflow_action_bar_select_6_listener($event) {
      i03.\u0275\u0275restoreView(_r3);
      const ctx_r1 = i03.\u0275\u0275nextContext(2);
      return i03.\u0275\u0275resetView(ctx_r1.selectAction($event));
    })("commit", function AaAppealDetailComponent_Conditional_9_Conditional_147_Template_app_workflow_action_bar_commit_6_listener() {
      i03.\u0275\u0275restoreView(_r3);
      const ctx_r1 = i03.\u0275\u0275nextContext(2);
      return i03.\u0275\u0275resetView(ctx_r1.submitAction());
    })("cancel", function AaAppealDetailComponent_Conditional_9_Conditional_147_Template_app_workflow_action_bar_cancel_6_listener() {
      i03.\u0275\u0275restoreView(_r3);
      const ctx_r1 = i03.\u0275\u0275nextContext(2);
      return i03.\u0275\u0275resetView(ctx_r1.cancelAction());
    });
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275template(7, AaAppealDetailComponent_Conditional_9_Conditional_147_ng_template_7_Template, 1, 1, "ng-template", null, 1, i03.\u0275\u0275templateRefExtractor);
    i03.\u0275\u0275conditionalCreate(9, AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_9_Template, 1, 1, "app-aa-hearing", 65);
    i03.\u0275\u0275conditionalCreate(10, AaAppealDetailComponent_Conditional_9_Conditional_147_Conditional_10_Template, 1, 1, "app-aa-order", 65);
  }
  if (rf & 2) {
    let tmp_7_0;
    const targetPicker_r9 = i03.\u0275\u0275reference(8);
    const ctx_r1 = i03.\u0275\u0275nextContext(2);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 10, "aa.detail.available_actions"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(5, 12, "aa.detail.available_actions_hint"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275property("actions", ctx_r1.availableActions())("selectedId", ((tmp_7_0 = ctx_r1.selectedAction()) == null ? null : tmp_7_0.id) ?? null)("processing", ctx_r1.processing());
    i03.\u0275\u0275twoWayProperty("remarks", ctx_r1.remarks);
    i03.\u0275\u0275property("showFormTitle", true)("fields", targetPicker_r9);
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275conditional(ctx_r1.showHearingPanel() ? 9 : -1);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.showOrderPanel() ? 10 : -1);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 73);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 1, "aa.detail.timeline_loading"));
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "p", 74);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 1, "aa.detail.timeline_empty"));
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "span", 83);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const entry_r10 = i03.\u0275\u0275nextContext().$implicit;
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(entry_r10.performedBy);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "p", 84);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const entry_r10 = i03.\u0275\u0275nextContext().$implicit;
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(entry_r10.remarks);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 76)(1, "div", 77)(2, "span", 78);
    i03.\u0275\u0275text(3);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(4, "div", 79)(5, "div", 80)(6, "span", 81);
    i03.\u0275\u0275text(7);
    i03.\u0275\u0275pipe(8, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(9, "span", 82);
    i03.\u0275\u0275text(10);
    i03.\u0275\u0275pipe(11, "date");
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275conditionalCreate(12, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Conditional_12_Template, 2, 1, "span", 83);
    i03.\u0275\u0275conditionalCreate(13, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Conditional_13_Template, 2, 1, "p", 84);
    i03.\u0275\u0275elementStart(14, "span", 85);
    i03.\u0275\u0275text(15);
    i03.\u0275\u0275elementStart(16, "span", 5);
    i03.\u0275\u0275text(17, "\u2192");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275text(18);
    i03.\u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const entry_r10 = ctx.$implicit;
    const ctx_r1 = i03.\u0275\u0275nextContext(5);
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.getTimelineIcon(entry_r10.action));
    i03.\u0275\u0275advance(4);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(8, 7, ctx_r1.getTimelineLabelKey(entry_r10.action)));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind2(11, 9, entry_r10.timestamp, "dd MMM yyyy HH:mm"));
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275conditional(entry_r10.performedBy ? 12 : -1);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(entry_r10.remarks ? 13 : -1);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275textInterpolate1(" ", entry_r10.fromStatus, " ");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate1(" ", entry_r10.toStatus, " ");
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "div", 75);
    i03.\u0275\u0275repeaterCreate(1, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_For_2_Template, 19, 12, "div", 76, _forTrack12);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(4);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275repeater(ctx_r1.timeline());
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275conditionalCreate(0, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_0_Template, 3, 3, "div", 73)(1, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_1_Template, 3, 3, "p", 74)(2, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Conditional_2_Template, 3, 0, "div", 75);
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(3);
    i03.\u0275\u0275conditional(ctx_r1.timelineLoading() ? 0 : ctx_r1.timeline().length === 0 ? 1 : 2);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275element(0, "app-comment-thread", 86);
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(4);
    i03.\u0275\u0275property("complaintNumber", ctx_r1.appeal().originalComplaintNumber)("audienceRoles", ctx_r1.commentAudienceRoles);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275elementStart(0, "p", 87);
    i03.\u0275\u0275text(1);
    i03.\u0275\u0275pipe(2, "translate");
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    i03.\u0275\u0275advance();
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(2, 1, "aa.detail.not_available"));
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275conditionalCreate(0, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Conditional_0_Template, 1, 2, "app-comment-thread", 86)(1, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Conditional_1_Template, 3, 3, "p", 87);
  }
  if (rf & 2) {
    const ctx_r1 = i03.\u0275\u0275nextContext(3);
    i03.\u0275\u0275conditional(ctx_r1.appeal().originalComplaintNumber ? 0 : 1);
  }
}
function AaAppealDetailComponent_Conditional_9_ng_template_149_Template(rf, ctx) {
  if (rf & 1) {
    i03.\u0275\u0275conditionalCreate(0, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_0_Template, 3, 1)(1, AaAppealDetailComponent_Conditional_9_ng_template_149_Case_1_Template, 2, 1);
  }
  if (rf & 2) {
    let tmp_4_0;
    const open_r11 = ctx.$implicit;
    i03.\u0275\u0275conditional((tmp_4_0 = open_r11) === "timeline" ? 0 : tmp_4_0 === "comments" ? 1 : -1);
  }
}
function AaAppealDetailComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = i03.\u0275\u0275getCurrentView();
    i03.\u0275\u0275elementStart(0, "app-complaint-summary", 10);
    i03.\u0275\u0275listener("back", function AaAppealDetailComponent_Conditional_9_Template_app_complaint_summary_back_0_listener() {
      i03.\u0275\u0275restoreView(_r1);
      const ctx_r1 = i03.\u0275\u0275nextContext();
      return i03.\u0275\u0275resetView(ctx_r1.goBack());
    });
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(1, "div", 11)(2, "div", 12)(3, "div", 13)(4, "div", 14)(5, "h3");
    i03.\u0275\u0275text(6);
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275element(7, "app-status-badge", 15);
    i03.\u0275\u0275conditionalCreate(8, AaAppealDetailComponent_Conditional_9_Conditional_8_Template, 3, 4, "span", 16);
    i03.\u0275\u0275element(9, "app-status-badge", 17);
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(10, "div", 18)(11, "span");
    i03.\u0275\u0275text(12);
    i03.\u0275\u0275pipe(13, "translate");
    i03.\u0275\u0275pipe(14, "date");
    i03.\u0275\u0275elementEnd()()();
    i03.\u0275\u0275conditionalCreate(15, AaAppealDetailComponent_Conditional_9_Conditional_15_Template, 11, 19, "div", 19);
    i03.\u0275\u0275elementStart(16, "section", 20)(17, "h4");
    i03.\u0275\u0275text(18);
    i03.\u0275\u0275pipe(19, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(20, "div", 21)(21, "div", 22)(22, "span", 23);
    i03.\u0275\u0275text(23);
    i03.\u0275\u0275pipe(24, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(25, "span", 24);
    i03.\u0275\u0275text(26);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(27, "div", 22)(28, "span", 23);
    i03.\u0275\u0275text(29);
    i03.\u0275\u0275pipe(30, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(31, "span", 25);
    i03.\u0275\u0275text(32);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(33, "div", 22)(34, "span", 23);
    i03.\u0275\u0275text(35);
    i03.\u0275\u0275pipe(36, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(37, "span", 26);
    i03.\u0275\u0275text(38);
    i03.\u0275\u0275pipe(39, "date");
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(40, "div", 22)(41, "span", 23);
    i03.\u0275\u0275text(42);
    i03.\u0275\u0275pipe(43, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(44, "span", 27);
    i03.\u0275\u0275text(45);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(46, "div", 22)(47, "span", 23);
    i03.\u0275\u0275text(48);
    i03.\u0275\u0275pipe(49, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(50, "span", 28);
    i03.\u0275\u0275text(51);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(52, "div", 22)(53, "span", 23);
    i03.\u0275\u0275text(54);
    i03.\u0275\u0275pipe(55, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(56, "span", 29);
    i03.\u0275\u0275text(57);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275conditionalCreate(58, AaAppealDetailComponent_Conditional_9_Conditional_58_Template, 6, 4, "div", 30);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(59, "section", 20)(60, "h4");
    i03.\u0275\u0275text(61);
    i03.\u0275\u0275pipe(62, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(63, "div", 21)(64, "div", 22)(65, "span", 23);
    i03.\u0275\u0275text(66);
    i03.\u0275\u0275pipe(67, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(68, "span", 31);
    i03.\u0275\u0275text(69);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(70, "div", 22)(71, "span", 23);
    i03.\u0275\u0275text(72);
    i03.\u0275\u0275pipe(73, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(74, "span", 32);
    i03.\u0275\u0275text(75);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(76, "div", 22)(77, "span", 23);
    i03.\u0275\u0275text(78);
    i03.\u0275\u0275pipe(79, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(80, "span", 33);
    i03.\u0275\u0275text(81);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(82, "div", 22)(83, "span", 23);
    i03.\u0275\u0275text(84);
    i03.\u0275\u0275pipe(85, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(86, "span", 34);
    i03.\u0275\u0275text(87);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(88, "div", 22)(89, "span", 23);
    i03.\u0275\u0275text(90);
    i03.\u0275\u0275pipe(91, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(92, "span", 35);
    i03.\u0275\u0275text(93);
    i03.\u0275\u0275elementEnd()()()();
    i03.\u0275\u0275elementStart(94, "section", 20)(95, "h4");
    i03.\u0275\u0275text(96);
    i03.\u0275\u0275pipe(97, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(98, "div", 21)(99, "div", 22)(100, "span", 23);
    i03.\u0275\u0275text(101);
    i03.\u0275\u0275pipe(102, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(103, "span", 36);
    i03.\u0275\u0275text(104);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(105, "div", 22)(106, "span", 23);
    i03.\u0275\u0275text(107);
    i03.\u0275\u0275pipe(108, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(109, "span", 37);
    i03.\u0275\u0275text(110);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(111, "div", 22)(112, "span", 23);
    i03.\u0275\u0275text(113);
    i03.\u0275\u0275pipe(114, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(115, "span", 38);
    i03.\u0275\u0275text(116);
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(117, "div", 22)(118, "span", 23);
    i03.\u0275\u0275text(119);
    i03.\u0275\u0275pipe(120, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(121, "span", 39);
    i03.\u0275\u0275text(122);
    i03.\u0275\u0275elementEnd()()()();
    i03.\u0275\u0275elementStart(123, "section", 20)(124, "h4");
    i03.\u0275\u0275text(125);
    i03.\u0275\u0275pipe(126, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(127, "div", 21)(128, "div", 22)(129, "span", 23);
    i03.\u0275\u0275text(130);
    i03.\u0275\u0275pipe(131, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(132, "span", 40);
    i03.\u0275\u0275text(133);
    i03.\u0275\u0275pipe(134, "date");
    i03.\u0275\u0275pipe(135, "translate");
    i03.\u0275\u0275elementEnd()();
    i03.\u0275\u0275elementStart(136, "div", 22)(137, "span", 23);
    i03.\u0275\u0275text(138);
    i03.\u0275\u0275pipe(139, "translate");
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(140, "span", 41);
    i03.\u0275\u0275text(141);
    i03.\u0275\u0275elementEnd()()()();
    i03.\u0275\u0275conditionalCreate(142, AaAppealDetailComponent_Conditional_9_Conditional_142_Template, 30, 23, "section", 42);
    i03.\u0275\u0275conditionalCreate(143, AaAppealDetailComponent_Conditional_9_Conditional_143_Template, 18, 14, "section", 43);
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(144, "div", 44);
    i03.\u0275\u0275conditionalCreate(145, AaAppealDetailComponent_Conditional_9_Conditional_145_Template, 3, 7, "div", 45);
    i03.\u0275\u0275conditionalCreate(146, AaAppealDetailComponent_Conditional_9_Conditional_146_Template, 10, 7, "div", 46)(147, AaAppealDetailComponent_Conditional_9_Conditional_147_Template, 11, 14);
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275elementStart(148, "aside", 47);
    i03.\u0275\u0275twoWayListener("openChange", function AaAppealDetailComponent_Conditional_9_Template_aside_openChange_148_listener($event) {
      i03.\u0275\u0275restoreView(_r1);
      const ctx_r1 = i03.\u0275\u0275nextContext();
      i03.\u0275\u0275twoWayBindingSet(ctx_r1.railOpen, $event) || (ctx_r1.railOpen = $event);
      return i03.\u0275\u0275resetView($event);
    });
    i03.\u0275\u0275elementEnd();
    i03.\u0275\u0275template(149, AaAppealDetailComponent_Conditional_9_ng_template_149_Template, 2, 1, "ng-template", null, 0, i03.\u0275\u0275templateRefExtractor);
    i03.\u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_8_0;
    const railBody_r12 = i03.\u0275\u0275reference(150);
    const ctx_r1 = i03.\u0275\u0275nextContext();
    i03.\u0275\u0275property("items", ctx_r1.summaryItems());
    i03.\u0275\u0275advance(6);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appealNumber);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275property("status", ctx_r1.appeal().classification);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.appeal().classificationOverridden ? 8 : -1);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275property("status", ctx_r1.appeal().status);
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate2("", i03.\u0275\u0275pipeBind1(13, 54, "aa.detail.filed"), ": ", i03.\u0275\u0275pipeBind2(14, 56, ctx_r1.appeal().filedAt, "dd MMM yyyy"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275conditional(((tmp_8_0 = ctx_r1.sla()) == null ? null : tmp_8_0.tracked) ? 15 : -1);
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(19, 59, "aa.detail.appeal_information"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(24, 61, "aa.detail.ground_for_appeal"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appealGround || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(30, 63, "aa.detail.relief_sought"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().reliefSought || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(36, 65, "aa.detail.filed_date"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind2(39, 67, ctx_r1.appeal().filedAt, "dd MMM yyyy"));
    i03.\u0275\u0275advance(4);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(43, 70, "aa.detail.original_complaint"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().originalComplaintNumber || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(49, 72, "aa.detail.closure_clause"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().closureClause || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(55, 74, "aa.detail.mode_of_receipt"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().modeOfReceipt || "\u2014");
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.appeal().reasonForDelay ? 58 : -1);
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(62, 76, "aa.detail.appellant_details"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(67, 78, "aa.detail.name"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appellantName || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(73, 80, "aa.detail.email"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appellantEmail || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(79, 82, "aa.detail.phone"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appellantPhone || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(85, 84, "aa.detail.entity"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().entityCode || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(91, 86, "aa.detail.filed_by"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().appealFiledBy || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(97, 88, "aa.detail.assignment"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(102, 90, "aa.detail.assigned_role"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().assignedRole || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(108, 92, "aa.detail.assigned_officer"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().assignedOfficer || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(114, 94, "aa.detail.workflow_stage"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().workflowStage || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(120, 96, "aa.detail.priority"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().priority || "\u2014");
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(126, 98, "aa.detail.hearing"));
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(131, 100, "aa.detail.hearing_date"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate1(" ", ctx_r1.appeal().hearingDate ? i03.\u0275\u0275pipeBind2(134, 102, ctx_r1.appeal().hearingDate, "dd MMM yyyy, HH:mm") : i03.\u0275\u0275pipeBind1(135, 105, "aa.detail.not_scheduled"), " ");
    i03.\u0275\u0275advance(5);
    i03.\u0275\u0275textInterpolate(i03.\u0275\u0275pipeBind1(139, 107, "aa.detail.hearing_venue"));
    i03.\u0275\u0275advance(3);
    i03.\u0275\u0275textInterpolate(ctx_r1.appeal().hearingVenue || "\u2014");
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.appeal().orderOutcome ? 142 : -1);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.appeal().closureCause || ctx_r1.appeal().closedAt ? 143 : -1);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275conditional(ctx_r1.actionResult() ? 145 : -1);
    i03.\u0275\u0275advance();
    i03.\u0275\u0275conditional(ctx_r1.isTerminalState() ? 146 : 147);
    i03.\u0275\u0275advance(2);
    i03.\u0275\u0275property("panels", ctx_r1.railPanels);
    i03.\u0275\u0275twoWayProperty("open", ctx_r1.railOpen);
    i03.\u0275\u0275property("body", railBody_r12);
  }
}
var AaAppealDetailComponent = class _AaAppealDetailComponent {
  router = inject3(Router);
  route = inject3(ActivatedRoute);
  http = inject3(HttpClient3);
  auth = inject3(KeycloakAuthService);
  appeal = signal3(null, ...ngDevMode ? [{ debugName: "appeal" }] : (
    /* istanbul ignore next */
    []
  ));
  loading = signal3(true, ...ngDevMode ? [{ debugName: "loading" }] : (
    /* istanbul ignore next */
    []
  ));
  processing = signal3(false, ...ngDevMode ? [{ debugName: "processing" }] : (
    /* istanbul ignore next */
    []
  ));
  userRole = signal3("AA_DO", ...ngDevMode ? [{ debugName: "userRole" }] : (
    /* istanbul ignore next */
    []
  ));
  timeline = signal3([], ...ngDevMode ? [{ debugName: "timeline" }] : (
    /* istanbul ignore next */
    []
  ));
  timelineLoading = signal3(true, ...ngDevMode ? [{ debugName: "timelineLoading" }] : (
    /* istanbul ignore next */
    []
  ));
  selectedAction = signal3(null, ...ngDevMode ? [{ debugName: "selectedAction" }] : (
    /* istanbul ignore next */
    []
  ));
  remarks = "";
  /**
   * RESTRICTED targets the composer offers on an appeal.
   *
   * The AA ladder only, even though the thread is keyed on the original complaint and RBIO officers can
   * therefore read it: an AA officer addressing RBIO_OFFICER would be routing round the appeal, which
   * is a workflow action, not a comment.
   */
  commentAudienceRoles = [
    { value: "AA_DO", label: "AA Dealing Official" },
    { value: "AA_REVIEWER", label: "AA Reviewer" },
    { value: "AA_SECRETARIAT", label: "AA Secretariat" }
  ];
  targetUser = "";
  hearingDate = "";
  hearingVenue = "";
  actionResult = signal3("", ...ngDevMode ? [{ debugName: "actionResult" }] : (
    /* istanbul ignore next */
    []
  ));
  actionSuccess = signal3(false, ...ngDevMode ? [{ debugName: "actionSuccess" }] : (
    /* istanbul ignore next */
    []
  ));
  // Sub-component panels
  showHearingPanel = signal3(false, ...ngDevMode ? [{ debugName: "showHearingPanel" }] : (
    /* istanbul ignore next */
    []
  ));
  showOrderPanel = signal3(false, ...ngDevMode ? [{ debugName: "showOrderPanel" }] : (
    /* istanbul ignore next */
    []
  ));
  // Officers for reassignment
  aaOfficers = signal3([], ...ngDevMode ? [{ debugName: "aaOfficers" }] : (
    /* istanbul ignore next */
    []
  ));
  /**
   * Translation keys for the acting role, reusing the aa.role_* keys S1 already seeded in all ten
   * locales rather than the English literals that were here.
   */
  static ROLE_LABEL_KEYS = {
    "AA_DO": "aa.role_do",
    "AA_REVIEWER": "aa.role_reviewer",
    "AA_SECRETARIAT": "aa.role_secretariat",
    "AA_ADMIN": "aa.role_admin"
  };
  roleLabelKey = computed(() => _AaAppealDetailComponent.ROLE_LABEL_KEYS[this.userRole()], ...ngDevMode ? [{ debugName: "roleLabelKey" }] : (
    /* istanbul ignore next */
    []
  ));
  /**
   * Presentation metadata ONLY. Whether an action is offered is decided by the SERVER; this map merely
   * says how to render one the server already offered.
   *
   * This replaced a client-side derivation that gated on six statuses the backend cannot produce
   * (accepted, assigned, hearing_completed, order_reserved, documents_requested, dismissed). The real
   * status after ACCEPT is under_review, so an AA_REVIEWER was shown ZERO actions and the workflow
   * dead-ended at step two. Any client-side action list can drift from what the server will accept;
   * this one had, silently, in production.
   */
  static ACTION_PRESENTATION = {
    ACCEPT: { labelKey: "aa.action.accept", descriptionKey: "aa.action.accept_desc", style: "primary", requiresRemarks: false },
    REJECT: { labelKey: "aa.action.reject", descriptionKey: "aa.action.reject_desc", style: "close", requiresRemarks: true },
    ASSIGN_TO_BENCH: { labelKey: "aa.action.assign_to_bench", descriptionKey: "aa.action.assign_to_bench_desc", style: "forward", requiresRemarks: true },
    REQUEST_DOCUMENTS: { labelKey: "aa.action.request_documents", descriptionKey: "aa.action.request_documents_desc", style: "info", requiresRemarks: true },
    PREPARE_BRIEF: { labelKey: "aa.action.prepare_brief", descriptionKey: "aa.action.prepare_brief_desc", style: "info", requiresRemarks: true },
    ESCALATE_TO_TIER2: { labelKey: "aa.action.escalate_to_tier2", descriptionKey: "aa.action.escalate_to_tier2_desc", style: "escalate", requiresRemarks: true },
    SCHEDULE_HEARING: { labelKey: "aa.action.schedule_hearing", descriptionKey: "aa.action.schedule_hearing_desc", style: "primary", requiresRemarks: false },
    FORWARD_TO_AUTHORITY: { labelKey: "aa.action.forward_to_authority", descriptionKey: "aa.action.forward_to_authority_desc", style: "forward", requiresRemarks: true },
    SEND_BACK_REGISTRAR: { labelKey: "aa.action.send_back_registrar", descriptionKey: "aa.action.send_back_registrar_desc", style: "return", requiresRemarks: true },
    PASS_ORDER: { labelKey: "aa.action.pass_order", descriptionKey: "aa.action.pass_order_desc", style: "primary", requiresRemarks: false },
    REMAND_TO_OMBUDSMAN: { labelKey: "aa.action.remand_to_ombudsman", descriptionKey: "aa.action.remand_to_ombudsman_desc", style: "escalate", requiresRemarks: true },
    DISMISS: { labelKey: "aa.action.dismiss", descriptionKey: "aa.action.dismiss_desc", style: "close", requiresRemarks: true },
    REASSIGN: { labelKey: "aa.action.reassign", descriptionKey: "aa.action.reassign_desc", style: "info", requiresRemarks: true, requiresTarget: true, targetType: "user" },
    CLOSE: { labelKey: "aa.action.close", descriptionKey: "aa.action.close_desc", style: "close", requiresRemarks: true },
    REOPEN: { labelKey: "aa.action.reopen", descriptionKey: "aa.action.reopen_desc", style: "escalate", requiresRemarks: true }
  };
  /**
   * Exactly what the server says this caller may do, in the server's order.
   *
   * An action the server offers but this map does not know how to render is still shown, labelled with
   * its raw id — failing visible beats hiding a legitimate action because the frontend is out of date.
   */
  availableActions = computed(() => (this.appeal()?.availableActions ?? []).map((id) => __spreadValues({
    id
  }, _AaAppealDetailComponent.ACTION_PRESENTATION[id] ?? {
    labelKey: id,
    descriptionKey: "",
    style: "info",
    requiresRemarks: true
  })), ...ngDevMode ? [{ debugName: "availableActions" }] : (
    /* istanbul ignore next */
    []
  ));
  /** Server-computed SLA. Never recalculated here: working-day maths belongs with the holiday master. */
  sla = computed(() => this.appeal()?.sla ?? null, ...ngDevMode ? [{ debugName: "sla" }] : (
    /* istanbul ignore next */
    []
  ));
  slaOverdue = computed(() => this.sla()?.breached === true, ...ngDevMode ? [{ debugName: "slaOverdue" }] : (
    /* istanbul ignore next */
    []
  ));
  /**
   * The shared summary strip's facts.
   *
   * Reuses the `aa.detail.*` keys this template already seeds rather than the `ui.col.*` family the
   * complaint screens use: an appeal's identifying facts are a different vocabulary (appeal number,
   * classification, original complaint) and only the AA keys exist in the bundles for them.
   *
   * No SLA item even though the appeal carries one: `sla.statusKey` is a translation key and the strip's
   * 'sla' kind takes a rendered string plus its own severity, so the banner in the left region — which
   * also shows the deadline and the overdue day count — stays the single place the SLA is stated.
   */
  summaryItems = computed(() => {
    const a = this.appeal();
    if (!a)
      return [];
    return [
      { labelKey: "aa.detail.title", value: a.appealNumber, icon: "pi-file" },
      { labelKey: "aa.detail.name", value: a.appellantName, icon: "pi-user", tone: "owner" },
      { labelKey: "aa.detail.entity", value: a.entityCode, icon: "pi-building" },
      { labelKey: "aa.detail.workflow_stage", value: a.status, kind: "status", icon: "pi-flag" },
      { labelKey: "aa.detail.original_complaint", value: a.originalComplaintNumber, icon: "pi-link" },
      { labelKey: "aa.detail.assigned_officer", value: a.assignedOfficer, icon: "pi-users" }
    ];
  }, ...ngDevMode ? [{ debugName: "summaryItems" }] : (
    /* istanbul ignore next */
    []
  ));
  // ═══ Right context rail ════════════════════════════════════════════════════════════════════════
  // The timeline and the comment thread are material a bench officer CONSULTS; the action cards and the
  // hearing/order forms are what they work in. Both used to sit full-width at the bottom of the left
  // column, so reading the history scrolled the actions off screen.
  railOpen = signal3(null, ...ngDevMode ? [{ debugName: "railOpen" }] : (
    /* istanbul ignore next */
    []
  ));
  railPanels = [
    { key: "timeline", label: "Timeline", icon: "pi-history" },
    { key: "comments", label: "Comments", icon: "pi-comments" }
  ];
  ngOnInit() {
    return __async(this, null, function* () {
      const authenticated = yield this.auth.init();
      if (!authenticated) {
        this.router.navigate(["/staff/login"]);
        return;
      }
      const roles = this.auth.getRoles();
      if (roles.includes("AA_ADMIN"))
        this.userRole.set("AA_ADMIN");
      else if (roles.includes("AA_SECRETARIAT"))
        this.userRole.set("AA_SECRETARIAT");
      else if (roles.includes("AA_REVIEWER"))
        this.userRole.set("AA_REVIEWER");
      else
        this.userRole.set("AA_DO");
      const appealNumber = this.route.snapshot.params["appealNumber"];
      this.loadAppeal(appealNumber);
      this.loadTimeline(appealNumber);
      this.loadOfficers();
    });
  }
  loadAppeal(appealNumber) {
    this.loading.set(true);
    this.http.get(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}`).subscribe({
      next: (res) => {
        this.appeal.set(res?.data || null);
        this.loading.set(false);
      },
      error: () => {
        this.appeal.set(null);
        this.loading.set(false);
      }
    });
  }
  loadTimeline(appealNumber) {
    this.timelineLoading.set(true);
    this.http.get(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/timeline`).subscribe({
      next: (res) => {
        const entries = Array.isArray(res?.data?.timeline) ? res.data.timeline : [];
        this.timeline.set(entries.map((e) => __spreadProps(__spreadValues({}, e), { timestamp: e.performedAt ?? e.timestamp })));
        this.timelineLoading.set(false);
      },
      error: () => {
        this.timeline.set([]);
        this.timelineLoading.set(false);
      }
    });
  }
  loadOfficers() {
    this.http.get(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=AA_REVIEWER`).subscribe({
      next: (res) => {
        const users = (res || []).map((u) => ({ id: u.username || u.userId, name: u.displayName || `${u.firstName} ${u.lastName}` }));
        this.aaOfficers.set(users);
      },
      error: () => this.aaOfficers.set([])
    });
  }
  selectAction(action) {
    if (action.id === "PASS_ORDER") {
      this.showOrderPanel.set(true);
      this.showHearingPanel.set(false);
      this.selectedAction.set(null);
      return;
    }
    if (action.id === "SCHEDULE_HEARING") {
      this.showHearingPanel.set(true);
      this.showOrderPanel.set(false);
      this.selectedAction.set(null);
      return;
    }
    this.showHearingPanel.set(false);
    this.showOrderPanel.set(false);
    this.selectedAction.set(action);
    this.remarks = "";
    this.targetUser = "";
    this.actionResult.set("");
  }
  cancelAction() {
    this.selectedAction.set(null);
    this.showHearingPanel.set(false);
    this.showOrderPanel.set(false);
    this.remarks = "";
  }
  submitAction() {
    const action = this.selectedAction();
    if (!action)
      return;
    const appealNumber = this.appeal()?.appealNumber;
    if (!appealNumber)
      return;
    this.processing.set(true);
    const body = {
      action: action.id,
      remarks: this.remarks,
      actor: this.auth.currentUser()?.username || "",
      targetUser: this.targetUser,
      hearingDate: this.hearingDate,
      hearingVenue: this.hearingVenue
    };
    this.http.post(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/action`, body).subscribe({
      next: (res) => {
        this.processing.set(false);
        if (res?.success === false) {
          this.actionSuccess.set(false);
          this.actionResult.set(res.messageKey || res.message || "aa.action.failed");
          return;
        }
        this.actionSuccess.set(true);
        this.actionResult.set("aa.action.completed");
        this.selectedAction.set(null);
        this.loadAppeal(appealNumber);
        this.loadTimeline(appealNumber);
        this.restoreFocusToActions();
      },
      error: (err) => {
        this.actionSuccess.set(false);
        if (err?.status === 409) {
          this.actionResult.set(err.error?.messageKey || "aa.workflow.error_illegal_transition");
          this.loadAppeal(appealNumber);
        } else {
          this.actionResult.set(err.error?.messageKey || err.error?.message || "aa.action.failed");
        }
        this.processing.set(false);
      }
    });
  }
  /**
   * Returns focus to the action list after a panel closes.
   *
   * The panels are @if-gated inline blocks, so when one is removed the focused element vanishes and focus
   * falls to the document body — a keyboard user loses their place entirely.
   */
  restoreFocusToActions() {
    setTimeout(() => {
      const target = document.querySelector('[data-testid="action-list"] button');
      target?.focus();
    });
  }
  onHearingScheduled() {
    this.showHearingPanel.set(false);
    const appealNumber = this.appeal()?.appealNumber;
    if (appealNumber) {
      this.loadAppeal(appealNumber);
      this.loadTimeline(appealNumber);
    }
  }
  onOrderPassed() {
    this.showOrderPanel.set(false);
    const appealNumber = this.appeal()?.appealNumber;
    if (appealNumber) {
      this.loadAppeal(appealNumber);
      this.loadTimeline(appealNumber);
    }
  }
  /**
   * True when the server offers this caller nothing.
   *
   * Derived from the server's own answer rather than a hardcoded terminal-status list. The old list
   * included `dismissed`, which the backend never sets \u2014 DISMISS produces `closed` \u2014 so it was both
   * wrong and a second place the vocabulary could drift.
   */
  isTerminalState() {
    return this.availableActions().length === 0;
  }
  getTimelineIcon(action) {
    const icons = {
      "FILED": "\u{1F4E5}",
      "ACCEPT": "\u2705",
      "REJECT": "\u274C",
      // Keyed on the real action ids from AaWorkflowTransition. These were ASSIGN_BENCH /
      // FORWARD_AUTHORITY / REMAND_OMBUDSMAN \u2014 names the server never emits \u2014 so those rows silently
      // fell through to the default icon and a raw action string.
      "ASSIGN_TO_BENCH": "\u{1F4E4}",
      "REQUEST_DOCUMENTS": "\u2753",
      "SCHEDULE_HEARING": "\u{1F4C5}",
      "PREPARE_BRIEF": "\u{1F4DD}",
      "ESCALATE_TO_TIER2": "\u2B06\uFE0F",
      "FORWARD_TO_AUTHORITY": "\u27A1\uFE0F",
      "SEND_BACK_REGISTRAR": "\u21A9\uFE0F",
      "PASS_ORDER": "\u{1F4DC}",
      "REMAND_TO_OMBUDSMAN": "\u{1F501}",
      "DISMISS": "\u{1F6AB}",
      "REASSIGN": "\u{1F501}",
      "CLOSE": "\u{1F512}",
      "REOPEN": "\u{1F504}"
    };
    return icons[action] || "\u{1F4CB}";
  }
  /** Translation key for a timeline action; the raw id is the fallback so a new action still reads. */
  getTimelineLabelKey(action) {
    const known = [
      "FILED",
      "ACCEPT",
      "REJECT",
      "ASSIGN_TO_BENCH",
      "REQUEST_DOCUMENTS",
      "SCHEDULE_HEARING",
      "PREPARE_BRIEF",
      "ESCALATE_TO_TIER2",
      "FORWARD_TO_AUTHORITY",
      "SEND_BACK_REGISTRAR",
      "PASS_ORDER",
      "REMAND_TO_OMBUDSMAN",
      "DISMISS",
      "REASSIGN",
      "CLOSE",
      "REOPEN"
    ];
    return known.includes(action) ? `aa.timeline.${action.toLowerCase()}` : action;
  }
  /**
   * The acting reviewer's tier, from the token claim.
   *
   * reviewer_tier is a real claim (aa_reviewer_001 = 1, aa_reviewer_002 = 2) that nothing in the UI has
   * ever surfaced, so a tier-1 reviewer had no way to know escalation was open to them.
   */
  reviewerTier = computed(() => {
    if (this.userRole() !== "AA_REVIEWER") {
      return null;
    }
    const claims = this.auth.currentUser();
    const tier = claims?.["reviewer_tier"];
    return tier == null ? null : String(tier);
  }, ...ngDevMode ? [{ debugName: "reviewerTier" }] : (
    /* istanbul ignore next */
    []
  ));
  goBack() {
    this.router.navigate(["/aa/dashboard"]);
  }
  static \u0275fac = function AaAppealDetailComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AaAppealDetailComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ i03.\u0275\u0275defineComponent({ type: _AaAppealDetailComponent, selectors: [["app-aa-appeal-detail"]], decls: 10, vars: 4, consts: [["railBody", ""], ["targetPicker", ""], [3, "titleKey", "roleKey"], ["shell-actions", ""], ["type", "button", "data-testid", "back-to-dashboard", 1, "back-btn", 3, "click"], ["aria-hidden", "true"], ["data-testid", "reviewer-tier", 1, "tier-badge"], [1, "aa-detail"], ["data-testid", "detail-loading", 1, "loading"], ["data-testid", "detail-not-found", 1, "error-state"], [3, "back", "items"], [1, "detail-layout", "detail-content"], [1, "detail-panel", "left-panel"], [1, "appeal-header"], [1, "header-left"], ["keyPrefix", "classification", 3, "status"], [1, "override-tag", 3, "title"], ["data-testid", "appeal-status", 3, "status"], [1, "header-meta"], ["role", "status", "aria-live", "polite", 1, "sla-banner", 3, "error-banner", "warning-banner"], [1, "detail-section"], [1, "field-grid"], [1, "field"], [1, "field-label"], ["data-testid", "appeal-ground"], ["data-testid", "relief-sought"], ["data-testid", "filed-at"], ["data-testid", "original-complaint", 1, "complaint-link"], ["data-testid", "closure-clause"], ["data-testid", "mode-of-receipt"], [1, "field", "full-width"], ["data-testid", "appellant-name"], ["data-testid", "appellant-email"], ["data-testid", "appellant-phone"], ["data-testid", "entity-code"], ["data-testid", "appeal-filed-by"], ["data-testid", "assigned-role"], ["data-testid", "assigned-officer"], ["data-testid", "workflow-stage"], ["data-testid", "priority"], ["data-testid", "hearing-date-summary"], ["data-testid", "hearing-venue-summary"], ["data-testid", "order-section", 1, "detail-section", "order-section"], ["data-testid", "closure-section", 1, "detail-section"], [1, "action-panel", "right-panel"], ["role", "status", "aria-live", "polite", "data-testid", "action-result", 1, "result-msg", 3, "success", "error"], ["data-testid", "terminal-banner", 1, "closed-banner"], ["app-context-rail", "", 3, "openChange", "panels", "open", "body"], ["role", "status", "aria-live", "polite", 1, "sla-banner"], ["aria-hidden", "true", 1, "sla-icon"], [1, "sla-status-label"], [1, "sla-deadline"], [1, "sla-days"], ["data-testid", "reason-for-delay", 1, "description-text"], ["data-testid", "order-outcome", 1, "outcome-badge"], ["data-testid", "order-date"], ["data-testid", "award-modified-amount"], ["data-testid", "order-summary", 1, "description-text"], ["data-testid", "closure-cause"], ["data-testid", "closed-at"], ["role", "status", "aria-live", "polite", "data-testid", "action-result", 1, "result-msg"], ["aria-hidden", "true", 1, "closed-icon"], [3, "status"], [1, "action-hint"], ["remarksLabelKey", "aa.detail.remarks", "remarksPlaceholderKey", "aa.detail.remarks_placeholder", "commitLabelKey", "aa.detail.confirm", "processingLabelKey", "aa.detail.processing", "cancelLabelKey", "aa.detail.cancel", 3, "remarksChange", "select", "commit", "cancel", "actions", "selectedId", "processing", "remarks", "showFormTitle", "fields"], [3, "appeal"], [1, "form-field"], ["for", "action-target-user"], ["id", "action-target-user", "data-testid", "action-target-user", 3, "ngModelChange", "ngModel"], ["value", ""], [3, "value"], [3, "hearingScheduled", "cancelled", "appeal"], [3, "orderPassed", "cancelled", "appeal"], ["data-testid", "timeline-loading", 1, "loading-inline"], ["data-testid", "timeline-empty", 1, "empty-timeline"], ["data-testid", "timeline", 1, "timeline"], [1, "timeline-item"], [1, "timeline-dot"], ["aria-hidden", "true", 1, "action-icon"], [1, "timeline-content"], [1, "timeline-main"], [1, "timeline-action"], [1, "timeline-time"], [1, "timeline-actor"], [1, "timeline-remarks"], [1, "timeline-flow"], [3, "complaintNumber", "audienceRoles"], [1, "empty-timeline"]], template: function AaAppealDetailComponent_Template(rf, ctx) {
    if (rf & 1) {
      i03.\u0275\u0275elementStart(0, "app-shell", 2)(1, "div", 3)(2, "button", 4);
      i03.\u0275\u0275listener("click", function AaAppealDetailComponent_Template_button_click_2_listener() {
        return ctx.goBack();
      });
      i03.\u0275\u0275elementStart(3, "span", 5);
      i03.\u0275\u0275text(4, "\u2190");
      i03.\u0275\u0275elementEnd()();
      i03.\u0275\u0275conditionalCreate(5, AaAppealDetailComponent_Conditional_5_Template, 3, 4, "span", 6);
      i03.\u0275\u0275elementEnd();
      i03.\u0275\u0275elementStart(6, "div", 7);
      i03.\u0275\u0275conditionalCreate(7, AaAppealDetailComponent_Conditional_7_Template, 3, 3, "div", 8)(8, AaAppealDetailComponent_Conditional_8_Template, 3, 3, "div", 9)(9, AaAppealDetailComponent_Conditional_9_Template, 151, 109);
      i03.\u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_2_0;
      i03.\u0275\u0275property("titleKey", "aa.detail.title")("roleKey", ctx.roleLabelKey());
      i03.\u0275\u0275advance(5);
      i03.\u0275\u0275conditional((tmp_2_0 = ctx.reviewerTier()) ? 5 : -1, tmp_2_0);
      i03.\u0275\u0275advance(2);
      i03.\u0275\u0275conditional(ctx.loading() ? 7 : !ctx.appeal() ? 8 : 9);
    }
  }, dependencies: [CommonModule3, i13.NgClass, i13.NgComponentOutlet, i13.NgForOf, i13.NgIf, i13.NgTemplateOutlet, i13.NgStyle, i13.NgSwitch, i13.NgSwitchCase, i13.NgSwitchDefault, i13.NgPlural, i13.NgPluralCase, FormsModule3, i23.\u0275NgNoValidate, i23.NgSelectOption, i23.\u0275NgSelectMultipleOption, i23.DefaultValueAccessor, i23.NumberValueAccessor, i23.RangeValueAccessor, i23.CheckboxControlValueAccessor, i23.SelectControlValueAccessor, i23.SelectMultipleControlValueAccessor, i23.RadioControlValueAccessor, i23.NgControlStatus, i23.NgControlStatusGroup, i23.RequiredValidator, i23.MinLengthValidator, i23.MaxLengthValidator, i23.PatternValidator, i23.CheckboxRequiredValidator, i23.EmailValidator, i23.MinValidator, i23.MaxValidator, i23.NgModel, i23.NgModelGroup, i23.NgForm, AaHearingComponent, AaOrderComponent, StatusBadgeComponent, AppShellComponent, CommentThreadComponent, WorkflowActionBarComponent, ComplaintSummaryComponent, ContextRailComponent, i13.AsyncPipe, i13.UpperCasePipe, i13.LowerCasePipe, i13.JsonPipe, i13.SlicePipe, i13.DecimalPipe, i13.PercentPipe, i13.TitleCasePipe, i13.CurrencyPipe, i13.DatePipe, i13.I18nPluralPipe, i13.I18nSelectPipe, i13.KeyValuePipe, TranslatePipe], styles: ["\n.back-btn[_ngcontent-%COMP%] {\n  background: none;\n  border: none;\n  color: var(--brand-primary-strong);\n  font-size: 20px;\n  line-height: 1;\n  cursor: pointer;\n}\n.tier-badge[_ngcontent-%COMP%] {\n  background: var(--brand-primary-bg);\n  color: var(--brand-primary-strong);\n  padding: 2px 8px;\n  border-radius: 4px;\n  font-size: 11px;\n}\n.loading[_ngcontent-%COMP%], \n.error-state[_ngcontent-%COMP%] {\n  text-align: center;\n  padding: 60px;\n  color: var(--text-muted);\n  font-size: 16px;\n}\n.detail-layout[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr 1fr 48px;\n  gap: 20px;\n  align-items: start;\n}\n.detail-panel[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 16px;\n}\n.appeal-header[_ngcontent-%COMP%] {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 1px solid var(--border-subtle);\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.appeal-header[_ngcontent-%COMP%]   .header-left[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 12px;\n  flex-wrap: wrap;\n}\n.appeal-header[_ngcontent-%COMP%]   h3[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: 18px;\n  color: var(--brand-primary-strong);\n}\n.appeal-header[_ngcontent-%COMP%]   .header-meta[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n  font-size: 12px;\n  color: var(--text-muted);\n  text-align: right;\n}\n.immutable-tag[_ngcontent-%COMP%] {\n  display: inline-block;\n  padding: 2px 6px;\n  border-radius: 3px;\n  font-size: 9px;\n  font-weight: 700;\n  color: var(--text-subtle);\n  background: var(--surface-subtle);\n  letter-spacing: 0.5px;\n  border: 1px solid var(--border-subtle);\n}\n.detail-section[_ngcontent-%COMP%] {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 1px solid var(--border-subtle);\n}\n.detail-section[_ngcontent-%COMP%]   h4[_ngcontent-%COMP%] {\n  margin: 0 0 16px;\n  font-size: 14px;\n  color: var(--text-heading);\n  border-bottom: 1px solid var(--surface-subtle);\n  padding-bottom: 8px;\n}\n.detail-section.order-section[_ngcontent-%COMP%] {\n  border-left: 4px solid var(--brand-primary-strong);\n}\n.field-grid[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr 1fr;\n  gap: 14px;\n}\n.field[_ngcontent-%COMP%]   label[_ngcontent-%COMP%] {\n  display: block;\n  font-size: 11px;\n  text-transform: uppercase;\n  color: #888;\n  font-weight: 600;\n  margin-bottom: 4px;\n}\n.field[_ngcontent-%COMP%]   span[_ngcontent-%COMP%], \n.field[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  font-size: 14px;\n  color: var(--text-body);\n  margin: 0;\n}\n.field.full-width[_ngcontent-%COMP%] {\n  grid-column: span 2;\n}\n.complaint-link[_ngcontent-%COMP%] {\n  color: var(--brand-primary);\n  font-weight: 500;\n}\n.description-text[_ngcontent-%COMP%] {\n  white-space: pre-wrap;\n  line-height: 1.5;\n  background: var(--surface-subtle);\n  padding: 12px;\n  border-radius: 6px;\n  font-size: 13px;\n}\n.outcome-badge[_ngcontent-%COMP%] {\n  display: inline-block;\n  padding: 3px 10px;\n  border-radius: 4px;\n  font-size: 12px;\n  font-weight: 600;\n}\n.outcome-badge[data-outcome=UPHELD][_ngcontent-%COMP%] {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.outcome-badge[data-outcome=MODIFIED][_ngcontent-%COMP%] {\n  background: var(--brand-primary-bg-strong);\n  color: var(--brand-primary-strong);\n}\n.outcome-badge[data-outcome=SET_ASIDE][_ngcontent-%COMP%] {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n}\n.outcome-badge[data-outcome=REMANDED][_ngcontent-%COMP%] {\n  background: var(--accent-pink-bg);\n  color: var(--accent-pink-fg);\n}\n.outcome-badge[data-outcome=DISMISSED][_ngcontent-%COMP%] {\n  background: var(--state-danger-bg);\n  color: var(--state-danger-fg);\n}\n.hearing-history[_ngcontent-%COMP%] {\n  margin-top: 16px;\n}\n.hearing-history[_ngcontent-%COMP%]   h5[_ngcontent-%COMP%] {\n  font-size: 13px;\n  color: var(--text-secondary);\n  margin: 0 0 8px;\n}\n.history-table[_ngcontent-%COMP%] {\n  width: 100%;\n  border-collapse: collapse;\n  font-size: 12px;\n}\n.history-table[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  padding: 8px 10px;\n  background: var(--surface-sunken);\n  text-align: left;\n  font-weight: 600;\n  color: var(--text-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.history-table[_ngcontent-%COMP%]   td[_ngcontent-%COMP%] {\n  padding: 8px 10px;\n  border-bottom: 1px solid var(--surface-subtle);\n  color: var(--text-body);\n}\n.timeline-header[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.timeline-header[_ngcontent-%COMP%]   h4[_ngcontent-%COMP%] {\n  margin: 0 0 16px;\n  font-size: 14px;\n  color: var(--text-heading);\n  border-bottom: 1px solid var(--surface-subtle);\n  padding-bottom: 8px;\n}\n.loading-inline[_ngcontent-%COMP%] {\n  font-size: 13px;\n  color: var(--text-muted);\n  padding: 8px 0;\n}\n.empty-timeline[_ngcontent-%COMP%] {\n  color: var(--text-subtle);\n  font-size: 13px;\n  font-style: italic;\n}\n.timeline[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 0;\n}\n.timeline-item[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 12px;\n  padding: 10px 0;\n  border-left: 2px solid var(--border-subtle);\n  margin-left: 8px;\n  padding-left: 16px;\n  position: relative;\n}\n.timeline-dot[_ngcontent-%COMP%] {\n  position: absolute;\n  left: -7px;\n  top: 14px;\n  width: 12px;\n  height: 12px;\n  border-radius: 50%;\n  background: var(--brand-primary-strong);\n  border: 2px solid white;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.timeline-dot[_ngcontent-%COMP%]   .action-icon[_ngcontent-%COMP%] {\n  font-size: 8px;\n}\n.timeline-content[_ngcontent-%COMP%] {\n  flex: 1;\n}\n.timeline-main[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.timeline-action[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 13px;\n  color: var(--text-body);\n}\n.timeline-time[_ngcontent-%COMP%] {\n  font-size: 11px;\n  color: #999;\n}\n.timeline-actor[_ngcontent-%COMP%] {\n  font-size: 11px;\n  color: var(--text-muted);\n  display: block;\n}\n.timeline-remarks[_ngcontent-%COMP%] {\n  font-size: 12px;\n  color: var(--text-secondary);\n  margin: 4px 0;\n  font-style: italic;\n}\n.timeline-flow[_ngcontent-%COMP%] {\n  font-size: 11px;\n  color: #888;\n}\n.action-panel[_ngcontent-%COMP%] {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 2px solid var(--brand-primary-bg);\n  position: sticky;\n  top: 24px;\n}\n.action-panel[_ngcontent-%COMP%]   h4[_ngcontent-%COMP%] {\n  margin: 0 0 4px;\n  color: var(--brand-primary-strong);\n  font-size: 16px;\n}\n.action-panel[_ngcontent-%COMP%]   .action-hint[_ngcontent-%COMP%] {\n  font-size: 12px;\n  color: var(--text-muted);\n  margin: 0 0 16px;\n}\n.admin-actions[_ngcontent-%COMP%] {\n  margin-top: 16px;\n  display: flex;\n  flex-direction: column;\n  gap: 8px;\n}\n.form-field[_ngcontent-%COMP%] {\n  margin-bottom: 12px;\n}\n.form-field[_ngcontent-%COMP%]   label[_ngcontent-%COMP%] {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%], \n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field[_ngcontent-%COMP%]   select[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%]:focus-visible, \n.form-field[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n  border-color: var(--brand-primary-strong);\n}\n.form-field[_ngcontent-%COMP%]   textarea[_ngcontent-%COMP%] {\n  resize: vertical;\n}\n.field[_ngcontent-%COMP%]   .field-label[_ngcontent-%COMP%] {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.sla-banner[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  flex-wrap: wrap;\n  padding: 8px 12px;\n  border-radius: 6px;\n  font-size: 12px;\n  margin-top: 8px;\n  background: var(--state-success-bg);\n  color: #1b5e20;\n  border: 1px solid #a5d6a7;\n}\n.sla-banner.at-risk[_ngcontent-%COMP%] {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n  border-color: #ffcc80;\n}\n.sla-banner.breached[_ngcontent-%COMP%] {\n  background: var(--state-danger-bg);\n  color: #b71c1c;\n  border-color: #ef9a9a;\n  font-weight: 700;\n}\n.sla-banner[_ngcontent-%COMP%]   .sla-days[_ngcontent-%COMP%] {\n  font-weight: 700;\n}\n.result-msg[_ngcontent-%COMP%] {\n  margin-top: 16px;\n  padding: 12px;\n  border-radius: 6px;\n  font-size: 13px;\n}\n.result-msg.success[_ngcontent-%COMP%] {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.result-msg.error[_ngcontent-%COMP%] {\n  background: #fbe9e7;\n  color: #d32f2f;\n}\n.closed-banner[_ngcontent-%COMP%] {\n  text-align: center;\n  padding: 24px;\n}\n.closed-banner[_ngcontent-%COMP%]   .closed-icon[_ngcontent-%COMP%] {\n  font-size: 40px;\n  color: var(--state-success-fg);\n  display: block;\n  margin-bottom: 8px;\n}\n.closed-banner[_ngcontent-%COMP%]   h4[_ngcontent-%COMP%] {\n  margin: 0 0 8px;\n  color: var(--state-success-fg);\n}\n.closed-banner[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  margin: 0;\n  color: var(--text-muted);\n  font-size: 13px;\n}\n@media (max-width: 1024px) {\n  .detail-layout[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr 48px;\n  }\n  .detail-panel[_ngcontent-%COMP%], \n   .action-panel[_ngcontent-%COMP%] {\n    grid-column: 1;\n  }\n  .action-panel[_ngcontent-%COMP%] {\n    position: static;\n  }\n}\n.override-tag[_ngcontent-%COMP%] {\n  display: inline-block;\n  margin-left: 6px;\n  padding: 1px 5px;\n  border-radius: 3px;\n  font-size: 9px;\n  font-weight: 700;\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n  color: var(--state-warning-fg);\n  background: var(--state-warning-bg);\n  cursor: help;\n}\n/*# sourceMappingURL=aa-appeal-detail.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i03.\u0275setClassMetadata(AaAppealDetailComponent, [{
    type: Component3,
    args: [{ selector: "app-aa-appeal-detail", standalone: true, imports: [CommonModule3, FormsModule3, AaHearingComponent, AaOrderComponent, StatusBadgeComponent, AppShellComponent, CommentThreadComponent, WorkflowActionBarComponent, ComplaintSummaryComponent, ContextRailComponent, TranslatePipe], template: `<app-shell [titleKey]="'aa.detail.title'" [roleKey]="roleLabelKey()">\r
  <div shell-actions>\r
    <button type="button" class="back-btn" data-testid="back-to-dashboard" (click)="goBack()">\r
      <span aria-hidden="true">&larr;</span>\r
    </button>\r
    @if (reviewerTier(); as tier) {\r
      <span class="tier-badge" data-testid="reviewer-tier">\r
        {{ 'aa.detail.reviewer_tier' | translate }}: {{ tier }}\r
      </span>\r
    }\r
  </div>\r
\r
  <div class="aa-detail">\r
    @if (loading()) {\r
      <div class="loading" data-testid="detail-loading">{{ 'aa.detail.loading' | translate }}</div>\r
    } @else if (!appeal()) {\r
      <div class="error-state" data-testid="detail-not-found">{{ 'aa.detail.not_found' | translate }}</div>\r
    } @else {\r
      <!-- Appeal Summary Strip \u2014 shared app-complaint-summary, as on RBIO, CEPC and the CRPC screens.\r
           This screen had no strip: the identifying facts were spread across .appeal-header and three\r
           field sections, so the same appeal read differently here and on the queue that linked to it. -->\r
      <app-complaint-summary [items]="summaryItems()" (back)="goBack()" />\r
\r
      <!--\r
        \u2550\u2550\u2550 MAIN CONTENT: THREE REGIONS \u2550\u2550\u2550\r
        LEFT is the appeal as filed (read-only facts), CENTER is the actions and the hearing/order forms,\r
        RIGHT is a thin rail holding the timeline and the comment thread \u2014 material a bench officer\r
        CONSULTS rather than works in. Both were previously stacked full-width in the left column, so\r
        reading the timeline scrolled the action cards out of sight.\r
\r
        \`.detail-layout\` is a SPEC CONTRACT: \`e2e/aa/{workflow,hearing,order}.spec.ts\` all wait on\r
        \`.aa-detail .detail-layout\` as their "screen has loaded" signal, and \`.detail-panel\` /\r
        \`.action-panel\` name the regions those specs reach into. The new region classes are ADDITIVE.\r
      -->\r
      <div class="detail-layout detail-content">\r
        <!-- Left: Appeal Details -->\r
        <div class="detail-panel left-panel">\r
          <!-- Header -->\r
          <div class="appeal-header">\r
            <div class="header-left">\r
              <h3>{{ appeal().appealNumber }}</h3>\r
              <app-status-badge [status]="appeal().classification" keyPrefix="classification" />\r
              @if (appeal().classificationOverridden) {\r
                <span class="override-tag" [title]="appeal().classificationOverrideReason || ''">\r
                  {{ 'aa.overridden' | translate }}\r
                </span>\r
              }\r
              <!-- Three .status-badge elements render on this screen (classification, this one, and the\r
                   terminal banner's), so that class alone is ambiguous. This test id names the APPEAL's\r
                   own status, which is what workflow assertions are actually about. -->\r
              <app-status-badge [status]="appeal().status" data-testid="appeal-status" />\r
            </div>\r
            <div class="header-meta">\r
              <span>{{ 'aa.detail.filed' | translate }}: {{ appeal().filedAt | date:'dd MMM yyyy' }}</span>\r
            </div>\r
          </div>\r
\r
          <!--\r
            Stage SLA, exactly as the server computed it. daysRemaining is NEGATIVE once the deadline has\r
            passed, so the overdue branch renders its absolute value against a different key.\r
          -->\r
          @if (sla()?.tracked) {\r
            <div class="sla-banner"\r
                 [class.error-banner]="sla()!.breached"\r
                 [class.warning-banner]="!sla()!.breached"\r
                 [attr.data-testid]="sla()!.breached ? 'sla-overdue' : 'sla-status'"\r
                 role="status"\r
                 aria-live="polite">\r
              @if (sla()!.breached) {\r
                <span class="sla-icon" aria-hidden="true">&#9888;</span>\r
              }\r
              <span class="sla-status-label">{{ sla()!.statusKey | translate }}</span>\r
              <span class="sla-deadline">\r
                {{ 'aa.detail.sla_deadline' | translate }}:\r
                {{ sla()!.deadline ? (sla()!.deadline | date:'dd MMM yyyy') : ('aa.detail.not_available' | translate) }}\r
              </span>\r
              @if (sla()!.daysRemaining !== null) {\r
                @if (sla()!.breached) {\r
                  <span class="sla-days">\r
                    {{ 'aa.detail.sla_overdue_by' | translate }}: {{ 0 - sla()!.daysRemaining! }}\r
                  </span>\r
                } @else {\r
                  <span class="sla-days">\r
                    {{ 'aa.detail.sla_days_remaining' | translate }}: {{ sla()!.daysRemaining }}\r
                  </span>\r
                }\r
              }\r
            </div>\r
          }\r
\r
          <!-- Appeal Info -->\r
          <section class="detail-section">\r
            <h4>{{ 'aa.detail.appeal_information' | translate }}</h4>\r
            <div class="field-grid">\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.ground_for_appeal' | translate }}</span>\r
                <span data-testid="appeal-ground">{{ appeal().appealGround || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.relief_sought' | translate }}</span>\r
                <span data-testid="relief-sought">{{ appeal().reliefSought || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.filed_date' | translate }}</span>\r
                <span data-testid="filed-at">{{ appeal().filedAt | date:'dd MMM yyyy' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.original_complaint' | translate }}</span>\r
                <span class="complaint-link" data-testid="original-complaint">{{ appeal().originalComplaintNumber || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.closure_clause' | translate }}</span>\r
                <span data-testid="closure-clause">{{ appeal().closureClause || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.mode_of_receipt' | translate }}</span>\r
                <span data-testid="mode-of-receipt">{{ appeal().modeOfReceipt || '\u2014' }}</span>\r
              </div>\r
              @if (appeal().reasonForDelay) {\r
                <div class="field full-width">\r
                  <span class="field-label">{{ 'aa.detail.reason_for_delay' | translate }}</span>\r
                  <p class="description-text" data-testid="reason-for-delay">{{ appeal().reasonForDelay }}</p>\r
                </div>\r
              }\r
            </div>\r
          </section>\r
\r
          <!-- Appellant Info -->\r
          <section class="detail-section">\r
            <h4>{{ 'aa.detail.appellant_details' | translate }}</h4>\r
            <div class="field-grid">\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.name' | translate }}</span>\r
                <span data-testid="appellant-name">{{ appeal().appellantName || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.email' | translate }}</span>\r
                <span data-testid="appellant-email">{{ appeal().appellantEmail || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.phone' | translate }}</span>\r
                <span data-testid="appellant-phone">{{ appeal().appellantPhone || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.entity' | translate }}</span>\r
                <span data-testid="entity-code">{{ appeal().entityCode || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.filed_by' | translate }}</span>\r
                <span data-testid="appeal-filed-by">{{ appeal().appealFiledBy || '\u2014' }}</span>\r
              </div>\r
            </div>\r
          </section>\r
\r
          <!-- Assignment -->\r
          <section class="detail-section">\r
            <h4>{{ 'aa.detail.assignment' | translate }}</h4>\r
            <div class="field-grid">\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.assigned_role' | translate }}</span>\r
                <span data-testid="assigned-role">{{ appeal().assignedRole || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.assigned_officer' | translate }}</span>\r
                <span data-testid="assigned-officer">{{ appeal().assignedOfficer || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.workflow_stage' | translate }}</span>\r
                <span data-testid="workflow-stage">{{ appeal().workflowStage || '\u2014' }}</span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.priority' | translate }}</span>\r
                <span data-testid="priority">{{ appeal().priority || '\u2014' }}</span>\r
              </div>\r
            </div>\r
          </section>\r
\r
          <!-- Hearing -->\r
          <!--\r
            \`-summary\` suffixes because app-aa-hearing's own date and venue CONTROLS already own\r
            data-testid="hearing-date" / "hearing-venue". Both render at once when the hearing panel is\r
            open, and the bare ids made every getByTestId on them a strict-mode violation \u2014 so a spec\r
            trying to fill the form resolved two elements and could not proceed.\r
          -->\r
          <section class="detail-section">\r
            <h4>{{ 'aa.detail.hearing' | translate }}</h4>\r
            <div class="field-grid">\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.hearing_date' | translate }}</span>\r
                <span data-testid="hearing-date-summary">\r
                  {{ appeal().hearingDate ? (appeal().hearingDate | date:'dd MMM yyyy, HH:mm') : ('aa.detail.not_scheduled' | translate) }}\r
                </span>\r
              </div>\r
              <div class="field">\r
                <span class="field-label">{{ 'aa.detail.hearing_venue' | translate }}</span>\r
                <span data-testid="hearing-venue-summary">{{ appeal().hearingVenue || '\u2014' }}</span>\r
              </div>\r
            </div>\r
          </section>\r
\r
          <!--\r
            Order details are FLAT on the detail payload; there is no nested \`order\` object, which is why\r
            this section never rendered before.\r
          -->\r
          @if (appeal().orderOutcome) {\r
            <section class="detail-section order-section" data-testid="order-section">\r
              <h4>{{ 'aa.detail.order' | translate }}</h4>\r
              <div class="field-grid">\r
                <div class="field">\r
                  <span class="field-label">{{ 'aa.detail.order_outcome' | translate }}</span>\r
                  <span class="outcome-badge" data-testid="order-outcome" [attr.data-outcome]="appeal().orderOutcome">\r
                    {{ appeal().orderOutcome }}\r
                  </span>\r
                </div>\r
                <div class="field">\r
                  <span class="field-label">{{ 'aa.detail.order_date' | translate }}</span>\r
                  <span data-testid="order-date">\r
                    {{ appeal().orderDate ? (appeal().orderDate | date:'dd MMM yyyy') : '\u2014' }}\r
                  </span>\r
                </div>\r
                <div class="field">\r
                  <span class="field-label">{{ 'aa.detail.award_amount' | translate }}</span>\r
                  <span data-testid="award-modified-amount">\r
                    {{ appeal().awardModifiedAmount ? '\u20B9' + appeal().awardModifiedAmount : '\u2014' }}\r
                  </span>\r
                </div>\r
              </div>\r
              <div class="field full-width">\r
                <span class="field-label">{{ 'aa.detail.order_summary' | translate }}</span>\r
                <p class="description-text" data-testid="order-summary">{{ appeal().orderSummary || '\u2014' }}</p>\r
              </div>\r
            </section>\r
          }\r
\r
          <!-- Closure -->\r
          @if (appeal().closureCause || appeal().closedAt) {\r
            <section class="detail-section" data-testid="closure-section">\r
              <h4>{{ 'aa.detail.closure' | translate }}</h4>\r
              <div class="field-grid">\r
                <div class="field">\r
                  <span class="field-label">{{ 'aa.detail.closure_cause' | translate }}</span>\r
                  <span data-testid="closure-cause">{{ appeal().closureCause || '\u2014' }}</span>\r
                </div>\r
                <div class="field">\r
                  <span class="field-label">{{ 'aa.detail.closed_at' | translate }}</span>\r
                  <span data-testid="closed-at">\r
                    {{ appeal().closedAt ? (appeal().closedAt | date:'dd MMM yyyy, HH:mm') : '\u2014' }}\r
                  </span>\r
                </div>\r
              </div>\r
            </section>\r
          }\r
\r
        </div>\r
\r
        <!-- Center: the actions -->\r
        <!-- \`.action-panel\` is the class the AA specs reach the action region through. \`.right-panel\` is\r
             the region name the reference layout uses for the CENTER column \u2014 misleading, but it is what\r
             the RBIO screen and its layout spec already call it, and one vocabulary beats an accurate\r
             one nobody else shares. -->\r
        <div class="action-panel right-panel">\r
          <!--\r
            OUTSIDE the terminal/actions branches, because the outcome of an action must outlive the\r
            actions. Reject and Dismiss both move the appeal to a terminal state, so the re-fetch that\r
            follows flipped this panel to the banner below and destroyed the very confirmation the\r
            officer had just earned \u2014 the write succeeded and the screen said nothing about it.\r
          -->\r
          @if (actionResult()) {\r
            <div class="result-msg" role="status" aria-live="polite" data-testid="action-result"\r
                 [class.success]="actionSuccess()" [class.error]="!actionSuccess()">\r
              {{ actionResult() | translate }}\r
            </div>\r
          }\r
\r
          <!-- \`terminal-banner\` matches the isTerminalState() predicate that gates it. It was\r
               data-testid="no-actions", which no spec referenced, while the S3B suite asserted on\r
               \`terminal-banner\` \u2014 so the one banner had two names and the assertion could never pass. -->\r
          @if (isTerminalState()) {\r
            <div class="closed-banner" data-testid="terminal-banner">\r
              <span class="closed-icon" aria-hidden="true">&#10003;</span>\r
              <h4>{{ 'aa.detail.no_actions_available' | translate }}</h4>\r
              <app-status-badge [status]="appeal().status" />\r
              <p>{{ 'aa.detail.no_actions_hint' | translate }}</p>\r
            </div>\r
          } @else {\r
            <h4>{{ 'aa.detail.available_actions' | translate }}</h4>\r
            <p class="action-hint">{{ 'aa.detail.available_actions_hint' | translate }}</p>\r
\r
            <!--\r
              Server-driven: the cards are exactly the ids the API returned in availableActions, rendered\r
              through their translation keys. Nothing here decides eligibility.\r
\r
              SCHEDULE_HEARING and PASS_ORDER never reach the generic confirm step \u2014 selectAction routes\r
              them to app-aa-hearing / app-aa-order and clears the selection, so the bar renders its cards\r
              and no form. Those two screens own their own remarks and their own submit.\r
            -->\r
            <app-workflow-action-bar\r
              [actions]="availableActions()"\r
              [selectedId]="selectedAction()?.id ?? null"\r
              [processing]="processing()"\r
              [(remarks)]="remarks"\r
              [showFormTitle]="true"\r
              [fields]="targetPicker"\r
              remarksLabelKey="aa.detail.remarks"\r
              remarksPlaceholderKey="aa.detail.remarks_placeholder"\r
              commitLabelKey="aa.detail.confirm"\r
              processingLabelKey="aa.detail.processing"\r
              cancelLabelKey="aa.detail.cancel"\r
              (select)="selectAction($event)"\r
              (commit)="submitAction()"\r
              (cancel)="cancelAction()" />\r
\r
            <ng-template #targetPicker let-action>\r
              @if (action.requiresTarget && action.targetType === 'user') {\r
                <div class="form-field">\r
                  <label for="action-target-user">{{ 'aa.detail.assign_to' | translate }}</label>\r
                  <select id="action-target-user" data-testid="action-target-user" [(ngModel)]="targetUser">\r
                    <option value="">{{ 'aa.detail.select_officer' | translate }}</option>\r
                    @for (officer of aaOfficers(); track officer.id) {\r
                      <option [value]="officer.id">{{ officer.name }}</option>\r
                    }\r
                  </select>\r
                </div>\r
              }\r
            </ng-template>\r
\r
            <!-- Hearing Panel -->\r
            @if (showHearingPanel()) {\r
              <app-aa-hearing [appeal]="appeal()" (hearingScheduled)="onHearingScheduled()" (cancelled)="cancelAction()"></app-aa-hearing>\r
            }\r
\r
            <!-- Order Panel -->\r
            @if (showOrderPanel()) {\r
              <app-aa-order [appeal]="appeal()" (orderPassed)="onOrderPassed()" (cancelled)="cancelAction()"></app-aa-order>\r
            }\r
\r
          }\r
        </div>\r
\r
        <!--\r
          \u2550\u2550\u2550 RIGHT RAIL \u2550\u2550\u2550\r
          The timeline and the comment thread. Both were full-width blocks at the bottom of the left\r
          column, which is what pushed the action cards below the fold on an appeal with any history.\r
\r
          TWO panels, not three: this screen has NO attachments panel because there is no data source for\r
          one. The appeal detail payload carries no attachment list, and the only AA attachment endpoint\r
          is the DRAFT assessment's (app-aa-draft-assessment reads draft().attachments), which is a\r
          different entity on a different screen. An attachments icon here would open a panel that could\r
          only ever say "none" \u2014 indistinguishable from an appeal genuinely filed without documents, and\r
          exactly the kind of UI-complete-but-wired-to-nothing panel this pass exists to remove. It wants\r
          a \`GET /api/v1/appeals/{n}/attachments\` first.\r
        -->\r
        <aside app-context-rail [panels]="railPanels" [(open)]="railOpen" [body]="railBody"></aside>\r
\r
        <ng-template #railBody let-open>\r
          @switch (open) {\r
            @case ('timeline') {\r
              @if (timelineLoading()) {\r
                <div class="loading-inline" data-testid="timeline-loading">{{ 'aa.detail.timeline_loading' | translate }}</div>\r
              } @else if (timeline().length === 0) {\r
                <p class="empty-timeline" data-testid="timeline-empty">{{ 'aa.detail.timeline_empty' | translate }}</p>\r
              } @else {\r
                <div class="timeline" data-testid="timeline">\r
                  @for (entry of timeline(); track entry.timestamp) {\r
                    <div class="timeline-item">\r
                      <div class="timeline-dot">\r
                        <span class="action-icon" aria-hidden="true">{{ getTimelineIcon(entry.action) }}</span>\r
                      </div>\r
                      <div class="timeline-content">\r
                        <div class="timeline-main">\r
                          <span class="timeline-action">{{ getTimelineLabelKey(entry.action) | translate }}</span>\r
                          <span class="timeline-time">{{ entry.timestamp | date:'dd MMM yyyy HH:mm' }}</span>\r
                        </div>\r
                        @if (entry.performedBy) {\r
                          <span class="timeline-actor">{{ entry.performedBy }}</span>\r
                        }\r
                        @if (entry.remarks) {\r
                          <p class="timeline-remarks">{{ entry.remarks }}</p>\r
                        }\r
                        <span class="timeline-flow">\r
                          {{ entry.fromStatus }} <span aria-hidden="true">&rarr;</span> {{ entry.toStatus }}\r
                        </span>\r
                      </div>\r
                    </div>\r
                  }\r
                </div>\r
              }\r
            }\r
            @case ('comments') {\r
              <!--\r
                Keyed on the ORIGINAL COMPLAINT, not the appeal. The complaint is the one entity every\r
                officer works around, so an RBIO note written before the appeal was filed is exactly the\r
                context a bench officer needs; a thread scoped to the appeal number would hide it.\r
                Absent when the appeal carries no complaint reference \u2014 an appeal against an order with\r
                no originating complaint has nothing to thread on.\r
              -->\r
              @if (appeal().originalComplaintNumber) {\r
                <app-comment-thread\r
                  [complaintNumber]="appeal().originalComplaintNumber"\r
                  [audienceRoles]="commentAudienceRoles" />\r
              } @else {\r
                <!-- \`not_available\` rather than a new comments-specific key: TranslationService returns an\r
                     unseeded key VERBATIM, so inventing one would print "aa.detail.comments_unavailable"\r
                     to a bench officer in all eleven locales. -->\r
                <p class="empty-timeline">{{ 'aa.detail.not_available' | translate }}</p>\r
              }\r
            }\r
          }\r
        </ng-template>\r
      </div>\r
    }\r
  </div>\r
</app-shell>\r
`, styles: ["/* src/app/components/aa/aa-appeal-detail/aa-appeal-detail.component.scss */\n.back-btn {\n  background: none;\n  border: none;\n  color: var(--brand-primary-strong);\n  font-size: 20px;\n  line-height: 1;\n  cursor: pointer;\n}\n.tier-badge {\n  background: var(--brand-primary-bg);\n  color: var(--brand-primary-strong);\n  padding: 2px 8px;\n  border-radius: 4px;\n  font-size: 11px;\n}\n.loading,\n.error-state {\n  text-align: center;\n  padding: 60px;\n  color: var(--text-muted);\n  font-size: 16px;\n}\n.detail-layout {\n  display: grid;\n  grid-template-columns: 1fr 1fr 48px;\n  gap: 20px;\n  align-items: start;\n}\n.detail-panel {\n  display: flex;\n  flex-direction: column;\n  gap: 16px;\n}\n.appeal-header {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 1px solid var(--border-subtle);\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.appeal-header .header-left {\n  display: flex;\n  align-items: center;\n  gap: 12px;\n  flex-wrap: wrap;\n}\n.appeal-header h3 {\n  margin: 0;\n  font-size: 18px;\n  color: var(--brand-primary-strong);\n}\n.appeal-header .header-meta {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n  font-size: 12px;\n  color: var(--text-muted);\n  text-align: right;\n}\n.immutable-tag {\n  display: inline-block;\n  padding: 2px 6px;\n  border-radius: 3px;\n  font-size: 9px;\n  font-weight: 700;\n  color: var(--text-subtle);\n  background: var(--surface-subtle);\n  letter-spacing: 0.5px;\n  border: 1px solid var(--border-subtle);\n}\n.detail-section {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 1px solid var(--border-subtle);\n}\n.detail-section h4 {\n  margin: 0 0 16px;\n  font-size: 14px;\n  color: var(--text-heading);\n  border-bottom: 1px solid var(--surface-subtle);\n  padding-bottom: 8px;\n}\n.detail-section.order-section {\n  border-left: 4px solid var(--brand-primary-strong);\n}\n.field-grid {\n  display: grid;\n  grid-template-columns: 1fr 1fr;\n  gap: 14px;\n}\n.field label {\n  display: block;\n  font-size: 11px;\n  text-transform: uppercase;\n  color: #888;\n  font-weight: 600;\n  margin-bottom: 4px;\n}\n.field span,\n.field p {\n  font-size: 14px;\n  color: var(--text-body);\n  margin: 0;\n}\n.field.full-width {\n  grid-column: span 2;\n}\n.complaint-link {\n  color: var(--brand-primary);\n  font-weight: 500;\n}\n.description-text {\n  white-space: pre-wrap;\n  line-height: 1.5;\n  background: var(--surface-subtle);\n  padding: 12px;\n  border-radius: 6px;\n  font-size: 13px;\n}\n.outcome-badge {\n  display: inline-block;\n  padding: 3px 10px;\n  border-radius: 4px;\n  font-size: 12px;\n  font-weight: 600;\n}\n.outcome-badge[data-outcome=UPHELD] {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.outcome-badge[data-outcome=MODIFIED] {\n  background: var(--brand-primary-bg-strong);\n  color: var(--brand-primary-strong);\n}\n.outcome-badge[data-outcome=SET_ASIDE] {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n}\n.outcome-badge[data-outcome=REMANDED] {\n  background: var(--accent-pink-bg);\n  color: var(--accent-pink-fg);\n}\n.outcome-badge[data-outcome=DISMISSED] {\n  background: var(--state-danger-bg);\n  color: var(--state-danger-fg);\n}\n.hearing-history {\n  margin-top: 16px;\n}\n.hearing-history h5 {\n  font-size: 13px;\n  color: var(--text-secondary);\n  margin: 0 0 8px;\n}\n.history-table {\n  width: 100%;\n  border-collapse: collapse;\n  font-size: 12px;\n}\n.history-table th {\n  padding: 8px 10px;\n  background: var(--surface-sunken);\n  text-align: left;\n  font-weight: 600;\n  color: var(--text-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.history-table td {\n  padding: 8px 10px;\n  border-bottom: 1px solid var(--surface-subtle);\n  color: var(--text-body);\n}\n.timeline-header {\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.timeline-header h4 {\n  margin: 0 0 16px;\n  font-size: 14px;\n  color: var(--text-heading);\n  border-bottom: 1px solid var(--surface-subtle);\n  padding-bottom: 8px;\n}\n.loading-inline {\n  font-size: 13px;\n  color: var(--text-muted);\n  padding: 8px 0;\n}\n.empty-timeline {\n  color: var(--text-subtle);\n  font-size: 13px;\n  font-style: italic;\n}\n.timeline {\n  display: flex;\n  flex-direction: column;\n  gap: 0;\n}\n.timeline-item {\n  display: flex;\n  gap: 12px;\n  padding: 10px 0;\n  border-left: 2px solid var(--border-subtle);\n  margin-left: 8px;\n  padding-left: 16px;\n  position: relative;\n}\n.timeline-dot {\n  position: absolute;\n  left: -7px;\n  top: 14px;\n  width: 12px;\n  height: 12px;\n  border-radius: 50%;\n  background: var(--brand-primary-strong);\n  border: 2px solid white;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.timeline-dot .action-icon {\n  font-size: 8px;\n}\n.timeline-content {\n  flex: 1;\n}\n.timeline-main {\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n}\n.timeline-action {\n  font-weight: 600;\n  font-size: 13px;\n  color: var(--text-body);\n}\n.timeline-time {\n  font-size: 11px;\n  color: #999;\n}\n.timeline-actor {\n  font-size: 11px;\n  color: var(--text-muted);\n  display: block;\n}\n.timeline-remarks {\n  font-size: 12px;\n  color: var(--text-secondary);\n  margin: 4px 0;\n  font-style: italic;\n}\n.timeline-flow {\n  font-size: 11px;\n  color: #888;\n}\n.action-panel {\n  background: white;\n  border-radius: 8px;\n  padding: 20px;\n  border: 2px solid var(--brand-primary-bg);\n  position: sticky;\n  top: 24px;\n}\n.action-panel h4 {\n  margin: 0 0 4px;\n  color: var(--brand-primary-strong);\n  font-size: 16px;\n}\n.action-panel .action-hint {\n  font-size: 12px;\n  color: var(--text-muted);\n  margin: 0 0 16px;\n}\n.admin-actions {\n  margin-top: 16px;\n  display: flex;\n  flex-direction: column;\n  gap: 8px;\n}\n.form-field {\n  margin-bottom: 12px;\n}\n.form-field label {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.form-field select,\n.form-field textarea,\n.form-field input {\n  width: 100%;\n  padding: 8px 10px;\n  border: 1px solid var(--border-subtle);\n  border-radius: 6px;\n  font-size: 13px;\n  font-family: inherit;\n}\n.form-field select:focus-visible,\n.form-field textarea:focus-visible,\n.form-field input:focus-visible {\n  outline: 2px solid var(--brand-primary-strong);\n  outline-offset: 1px;\n  border-color: var(--brand-primary-strong);\n}\n.form-field textarea {\n  resize: vertical;\n}\n.field .field-label {\n  display: block;\n  font-size: 12px;\n  font-weight: 600;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n.sla-banner {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  flex-wrap: wrap;\n  padding: 8px 12px;\n  border-radius: 6px;\n  font-size: 12px;\n  margin-top: 8px;\n  background: var(--state-success-bg);\n  color: #1b5e20;\n  border: 1px solid #a5d6a7;\n}\n.sla-banner.at-risk {\n  background: var(--state-warning-bg);\n  color: var(--state-warning-fg);\n  border-color: #ffcc80;\n}\n.sla-banner.breached {\n  background: var(--state-danger-bg);\n  color: #b71c1c;\n  border-color: #ef9a9a;\n  font-weight: 700;\n}\n.sla-banner .sla-days {\n  font-weight: 700;\n}\n.result-msg {\n  margin-top: 16px;\n  padding: 12px;\n  border-radius: 6px;\n  font-size: 13px;\n}\n.result-msg.success {\n  background: var(--state-success-bg);\n  color: var(--state-success-fg);\n}\n.result-msg.error {\n  background: #fbe9e7;\n  color: #d32f2f;\n}\n.closed-banner {\n  text-align: center;\n  padding: 24px;\n}\n.closed-banner .closed-icon {\n  font-size: 40px;\n  color: var(--state-success-fg);\n  display: block;\n  margin-bottom: 8px;\n}\n.closed-banner h4 {\n  margin: 0 0 8px;\n  color: var(--state-success-fg);\n}\n.closed-banner p {\n  margin: 0;\n  color: var(--text-muted);\n  font-size: 13px;\n}\n@media (max-width: 1024px) {\n  .detail-layout {\n    grid-template-columns: 1fr 48px;\n  }\n  .detail-panel,\n  .action-panel {\n    grid-column: 1;\n  }\n  .action-panel {\n    position: static;\n  }\n}\n.override-tag {\n  display: inline-block;\n  margin-left: 6px;\n  padding: 1px 5px;\n  border-radius: 3px;\n  font-size: 9px;\n  font-weight: 700;\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n  color: var(--state-warning-fg);\n  background: var(--state-warning-bg);\n  cursor: help;\n}\n/*# sourceMappingURL=aa-appeal-detail.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i03.\u0275setClassDebugInfo(AaAppealDetailComponent, { className: "AaAppealDetailComponent", filePath: "src/app/components/aa/aa-appeal-detail/aa-appeal-detail.component.ts", lineNumber: 70 });
})();
(() => {
  const id = "src%2Fapp%2Fcomponents%2Faa%2Faa-appeal-detail%2Faa-appeal-detail.component.ts%40AaAppealDetailComponent";
  function AaAppealDetailComponent_HmrLoad(t) {
    import(
      /* @vite-ignore */
      __vite__injectQuery(i03.\u0275\u0275getReplaceMetadataURL(id, t, import.meta.url), 'import')
    ).then((m) => m.default && i03.\u0275\u0275replaceMetadata(AaAppealDetailComponent, m.default, [i03, i13, i23], [CommonModule3, FormsModule3, AaHearingComponent, AaOrderComponent, StatusBadgeComponent, AppShellComponent, CommentThreadComponent, WorkflowActionBarComponent, ComplaintSummaryComponent, ContextRailComponent, TranslatePipe, Component3], import.meta, id));
  }
  (typeof ngDevMode === "undefined" || ngDevMode) && AaAppealDetailComponent_HmrLoad(Date.now());
  (typeof ngDevMode === "undefined" || ngDevMode) && (import.meta.hot && import.meta.hot.on("angular:component-update", (d) => d.id === id && AaAppealDetailComponent_HmrLoad(d.timestamp)));
})();
export {
  AaAppealDetailComponent
};


//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbInNyYy9hcHAvY29tcG9uZW50cy9hYS9hYS1hcHBlYWwtZGV0YWlsL2FhLWFwcGVhbC1kZXRhaWwuY29tcG9uZW50LnRzIiwic3JjL2FwcC9jb21wb25lbnRzL2FhL2FhLWFwcGVhbC1kZXRhaWwvYWEtYXBwZWFsLWRldGFpbC5jb21wb25lbnQuaHRtbCIsInNyYy9hcHAvY29tcG9uZW50cy9hYS9hYS1oZWFyaW5nL2FhLWhlYXJpbmcuY29tcG9uZW50LnRzIiwic3JjL2FwcC9jb21wb25lbnRzL2FhL2FhLWhlYXJpbmcvYWEtaGVhcmluZy5jb21wb25lbnQuaHRtbCIsInNyYy9hcHAvY29tcG9uZW50cy9hYS9hYS1vcmRlci9hYS1vcmRlci5jb21wb25lbnQudHMiLCJzcmMvYXBwL2NvbXBvbmVudHMvYWEvYWEtb3JkZXIvYWEtb3JkZXIuY29tcG9uZW50Lmh0bWwiXSwic291cmNlc0NvbnRlbnQiOlsiaW1wb3J0IHsgQ29tcG9uZW50LCBPbkluaXQsIGluamVjdCwgc2lnbmFsLCBjb21wdXRlZCB9IGZyb20gJ0Bhbmd1bGFyL2NvcmUnO1xyXG5pbXBvcnQgeyBDb21tb25Nb2R1bGUgfSBmcm9tICdAYW5ndWxhci9jb21tb24nO1xyXG5pbXBvcnQgeyBGb3Jtc01vZHVsZSB9IGZyb20gJ0Bhbmd1bGFyL2Zvcm1zJztcclxuaW1wb3J0IHsgUm91dGVyLCBBY3RpdmF0ZWRSb3V0ZSB9IGZyb20gJ0Bhbmd1bGFyL3JvdXRlcic7XHJcbmltcG9ydCB7IEh0dHBDbGllbnQgfSBmcm9tICdAYW5ndWxhci9jb21tb24vaHR0cCc7XHJcbmltcG9ydCB7IEtleWNsb2FrQXV0aFNlcnZpY2UgfSBmcm9tICcuLi8uLi8uLi9zZXJ2aWNlcy9rZXljbG9hay1hdXRoLnNlcnZpY2UnO1xyXG5pbXBvcnQgeyBlbnZpcm9ubWVudCB9IGZyb20gJy4uLy4uLy4uLy4uL2Vudmlyb25tZW50cy9lbnZpcm9ubWVudCc7XHJcbmltcG9ydCB7IEFhSGVhcmluZ0NvbXBvbmVudCB9IGZyb20gJy4uL2FhLWhlYXJpbmcvYWEtaGVhcmluZy5jb21wb25lbnQnO1xyXG5pbXBvcnQgeyBBYU9yZGVyQ29tcG9uZW50IH0gZnJvbSAnLi4vYWEtb3JkZXIvYWEtb3JkZXIuY29tcG9uZW50JztcclxuaW1wb3J0IHsgU3RhdHVzQmFkZ2VDb21wb25lbnQgfSBmcm9tICcuLi8uLi9zaGFyZWQvc3RhdHVzLWJhZGdlL3N0YXR1cy1iYWRnZS5jb21wb25lbnQnO1xyXG5pbXBvcnQgeyBBcHBTaGVsbENvbXBvbmVudCB9IGZyb20gJy4uLy4uL3NoYXJlZC9hcHAtc2hlbGwvYXBwLXNoZWxsLmNvbXBvbmVudCc7XHJcbmltcG9ydCB7IENvbW1lbnRUaHJlYWRDb21wb25lbnQgfSBmcm9tICcuLi8uLi9zaGFyZWQvY29tbWVudC10aHJlYWQvY29tbWVudC10aHJlYWQuY29tcG9uZW50JztcclxuaW1wb3J0IHsgQ29tbWVudEF1ZGllbmNlT3B0aW9uIH0gZnJvbSAnLi4vLi4vc2hhcmVkL2NvbW1lbnQtdGhyZWFkL2NvbW1lbnQtdGhyZWFkLnR5cGVzJztcclxuaW1wb3J0IHsgV29ya2Zsb3dBY3Rpb25CYXJDb21wb25lbnQgfSBmcm9tICcuLi8uLi9zaGFyZWQvd29ya2Zsb3ctYWN0aW9uLWJhci93b3JrZmxvdy1hY3Rpb24tYmFyLmNvbXBvbmVudCc7XHJcbmltcG9ydCB7IFdvcmtmbG93QWN0aW9uLCBXb3JrZmxvd0FjdGlvblN0eWxlIH0gZnJvbSAnLi4vLi4vc2hhcmVkL3dvcmtmbG93LWFjdGlvbi1iYXIvd29ya2Zsb3ctYWN0aW9uLWJhci50eXBlcyc7XHJcbmltcG9ydCB7IENvbXBsYWludFN1bW1hcnlDb21wb25lbnQgfSBmcm9tICcuLi8uLi9zaGFyZWQvY29tcGxhaW50LXN1bW1hcnkvY29tcGxhaW50LXN1bW1hcnkuY29tcG9uZW50JztcclxuaW1wb3J0IHsgQ29tcGxhaW50U3VtbWFyeUl0ZW0gfSBmcm9tICcuLi8uLi9zaGFyZWQvY29tcGxhaW50LXN1bW1hcnkvY29tcGxhaW50LXN1bW1hcnkudHlwZXMnO1xyXG5pbXBvcnQgeyBDb250ZXh0UmFpbENvbXBvbmVudCB9IGZyb20gJy4uLy4uL3NoYXJlZC9jb250ZXh0LXJhaWwvY29udGV4dC1yYWlsLmNvbXBvbmVudCc7XHJcbmltcG9ydCB7IENvbnRleHRSYWlsUGFuZWwgfSBmcm9tICcuLi8uLi9zaGFyZWQvY29udGV4dC1yYWlsL2NvbnRleHQtcmFpbC50eXBlcyc7XHJcbmltcG9ydCB7IFRyYW5zbGF0ZVBpcGUgfSBmcm9tICcuLi8uLi8uLi9waXBlcy90cmFuc2xhdGUucGlwZSc7XHJcblxyXG50eXBlIEFhUm9sZSA9ICdBQV9ETycgfCAnQUFfUkVWSUVXRVInIHwgJ0FBX1NFQ1JFVEFSSUFUJyB8ICdBQV9BRE1JTic7XHJcblxyXG4vKipcclxuICogVGhlIHJhaWwgcGFuZWxzIHRoaXMgc2NyZWVuIGNhbiBwb3B1bGF0ZS5cclxuICpcclxuICogTm8gJ2F0dGFjaG1lbnRzJzogbm90aGluZyBzZXJ2ZXMgYW4gYXBwZWFsJ3MgYXR0YWNobWVudCBsaXN0LiBTZWUgdGhlIHJhaWwgY29tbWVudCBpbiB0aGUgdGVtcGxhdGUuXHJcbiAqL1xyXG50eXBlIFJhaWxLZXkgPSAndGltZWxpbmUnIHwgJ2NvbW1lbnRzJztcclxuXHJcbi8qKiBUaGUgc2hhcmVkIGFjdGlvbiBjb250cmFjdCBwbHVzIHRoZSB0YXJnZXQgcGlja2VyIG9ubHkgdGhpcyBtb2R1bGUgcmVuZGVycy4gKi9cclxuaW50ZXJmYWNlIEFjdGlvbkRlZiBleHRlbmRzIFdvcmtmbG93QWN0aW9uIHtcclxuICAvKiogVHJhbnNsYXRpb24ga2V5cywgbm90IHRleHQ6IGV2ZXJ5IHVzZXItZmFjaW5nIHN0cmluZyBpbiB0aGlzIG1vZHVsZSByZXNvbHZlcyB0aHJvdWdoIHRoZSBBUEkuICovXHJcbiAgbGFiZWxLZXk6IHN0cmluZztcclxuICBkZXNjcmlwdGlvbktleTogc3RyaW5nO1xyXG4gIHN0eWxlOiBXb3JrZmxvd0FjdGlvblN0eWxlO1xyXG4gIHJlcXVpcmVzUmVtYXJrczogYm9vbGVhbjtcclxuICByZXF1aXJlc1RhcmdldD86IGJvb2xlYW47XHJcbiAgdGFyZ2V0VHlwZT86ICd1c2VyJyB8ICdkYXRlJztcclxufVxyXG5cclxuLyoqIFNlcnZlci1jb21wdXRlZCBzdGFnZSBTTEEsIGFzIHJldHVybmVkIG9uIHRoZSBhcHBlYWwgZGV0YWlsLiAqL1xyXG5pbnRlcmZhY2UgQXBwZWFsU2xhIHtcclxuICBzdGFnZTogc3RyaW5nO1xyXG4gIHN0YWdlQWxsb3dlZERheXM6IG51bWJlcjtcclxuICB0cmFja2VkOiBib29sZWFuO1xyXG4gIGRlYWRsaW5lOiBzdHJpbmcgfCBudWxsO1xyXG4gIC8qKiBOZWdhdGl2ZSB3aGVuIG92ZXJkdWUuICovXHJcbiAgZGF5c1JlbWFpbmluZzogbnVtYmVyIHwgbnVsbDtcclxuICBicmVhY2hlZDogYm9vbGVhbjtcclxuICBzdGF0dXNLZXk6IHN0cmluZztcclxufVxyXG5cclxuaW50ZXJmYWNlIFRpbWVsaW5lRW50cnkge1xyXG4gIGFjdGlvbjogc3RyaW5nO1xyXG4gIGZyb21TdGF0dXM6IHN0cmluZztcclxuICB0b1N0YXR1czogc3RyaW5nO1xyXG4gIHRpbWVzdGFtcDogc3RyaW5nO1xyXG4gIHJlbWFya3M6IHN0cmluZztcclxuICBwZXJmb3JtZWRCeT86IHN0cmluZztcclxufVxyXG5cclxuQENvbXBvbmVudCh7XHJcbiAgc2VsZWN0b3I6ICdhcHAtYWEtYXBwZWFsLWRldGFpbCcsXHJcbiAgc3RhbmRhbG9uZTogdHJ1ZSxcclxuICBpbXBvcnRzOiBbQ29tbW9uTW9kdWxlLCBGb3Jtc01vZHVsZSwgQWFIZWFyaW5nQ29tcG9uZW50LCBBYU9yZGVyQ29tcG9uZW50LCBTdGF0dXNCYWRnZUNvbXBvbmVudCwgQXBwU2hlbGxDb21wb25lbnQsIENvbW1lbnRUaHJlYWRDb21wb25lbnQsIFdvcmtmbG93QWN0aW9uQmFyQ29tcG9uZW50LCBDb21wbGFpbnRTdW1tYXJ5Q29tcG9uZW50LCBDb250ZXh0UmFpbENvbXBvbmVudCwgVHJhbnNsYXRlUGlwZV0sXHJcbiAgdGVtcGxhdGVVcmw6ICcuL2FhLWFwcGVhbC1kZXRhaWwuY29tcG9uZW50Lmh0bWwnLFxyXG4gIHN0eWxlVXJsOiAnLi9hYS1hcHBlYWwtZGV0YWlsLmNvbXBvbmVudC5zY3NzJ1xyXG59KVxyXG5leHBvcnQgY2xhc3MgQWFBcHBlYWxEZXRhaWxDb21wb25lbnQgaW1wbGVtZW50cyBPbkluaXQge1xyXG4gIHByaXZhdGUgcm91dGVyID0gaW5qZWN0KFJvdXRlcik7XHJcbiAgcHJpdmF0ZSByb3V0ZSA9IGluamVjdChBY3RpdmF0ZWRSb3V0ZSk7XHJcbiAgcHJpdmF0ZSBodHRwID0gaW5qZWN0KEh0dHBDbGllbnQpO1xyXG4gIGF1dGggPSBpbmplY3QoS2V5Y2xvYWtBdXRoU2VydmljZSk7XHJcblxyXG4gIGFwcGVhbCA9IHNpZ25hbDxhbnk+KG51bGwpO1xyXG4gIGxvYWRpbmcgPSBzaWduYWwodHJ1ZSk7XHJcbiAgcHJvY2Vzc2luZyA9IHNpZ25hbChmYWxzZSk7XHJcbiAgdXNlclJvbGUgPSBzaWduYWw8QWFSb2xlPignQUFfRE8nKTtcclxuXHJcbiAgdGltZWxpbmUgPSBzaWduYWw8VGltZWxpbmVFbnRyeVtdPihbXSk7XHJcbiAgdGltZWxpbmVMb2FkaW5nID0gc2lnbmFsKHRydWUpO1xyXG5cclxuICBzZWxlY3RlZEFjdGlvbiA9IHNpZ25hbDxBY3Rpb25EZWYgfCBudWxsPihudWxsKTtcclxuICByZW1hcmtzID0gJyc7XHJcblxyXG4gIC8qKlxyXG4gICAqIFJFU1RSSUNURUQgdGFyZ2V0cyB0aGUgY29tcG9zZXIgb2ZmZXJzIG9uIGFuIGFwcGVhbC5cclxuICAgKlxyXG4gICAqIFRoZSBBQSBsYWRkZXIgb25seSwgZXZlbiB0aG91Z2ggdGhlIHRocmVhZCBpcyBrZXllZCBvbiB0aGUgb3JpZ2luYWwgY29tcGxhaW50IGFuZCBSQklPIG9mZmljZXJzIGNhblxyXG4gICAqIHRoZXJlZm9yZSByZWFkIGl0OiBhbiBBQSBvZmZpY2VyIGFkZHJlc3NpbmcgUkJJT19PRkZJQ0VSIHdvdWxkIGJlIHJvdXRpbmcgcm91bmQgdGhlIGFwcGVhbCwgd2hpY2hcclxuICAgKiBpcyBhIHdvcmtmbG93IGFjdGlvbiwgbm90IGEgY29tbWVudC5cclxuICAgKi9cclxuICByZWFkb25seSBjb21tZW50QXVkaWVuY2VSb2xlczogcmVhZG9ubHkgQ29tbWVudEF1ZGllbmNlT3B0aW9uW10gPSBbXHJcbiAgICB7IHZhbHVlOiAnQUFfRE8nLCBsYWJlbDogJ0FBIERlYWxpbmcgT2ZmaWNpYWwnIH0sXHJcbiAgICB7IHZhbHVlOiAnQUFfUkVWSUVXRVInLCBsYWJlbDogJ0FBIFJldmlld2VyJyB9LFxyXG4gICAgeyB2YWx1ZTogJ0FBX1NFQ1JFVEFSSUFUJywgbGFiZWw6ICdBQSBTZWNyZXRhcmlhdCcgfVxyXG4gIF07XHJcbiAgdGFyZ2V0VXNlciA9ICcnO1xyXG4gIGhlYXJpbmdEYXRlID0gJyc7XHJcbiAgaGVhcmluZ1ZlbnVlID0gJyc7XHJcblxyXG4gIGFjdGlvblJlc3VsdCA9IHNpZ25hbCgnJyk7XHJcbiAgYWN0aW9uU3VjY2VzcyA9IHNpZ25hbChmYWxzZSk7XHJcblxyXG4gIC8vIFN1Yi1jb21wb25lbnQgcGFuZWxzXHJcbiAgc2hvd0hlYXJpbmdQYW5lbCA9IHNpZ25hbChmYWxzZSk7XHJcbiAgc2hvd09yZGVyUGFuZWwgPSBzaWduYWwoZmFsc2UpO1xyXG5cclxuICAvLyBPZmZpY2VycyBmb3IgcmVhc3NpZ25tZW50XHJcbiAgYWFPZmZpY2VycyA9IHNpZ25hbDx7IGlkOiBzdHJpbmc7IG5hbWU6IHN0cmluZyB9W10+KFtdKTtcclxuXHJcbiAgLyoqXHJcbiAgICogVHJhbnNsYXRpb24ga2V5cyBmb3IgdGhlIGFjdGluZyByb2xlLCByZXVzaW5nIHRoZSBhYS5yb2xlXyoga2V5cyBTMSBhbHJlYWR5IHNlZWRlZCBpbiBhbGwgdGVuXHJcbiAgICogbG9jYWxlcyByYXRoZXIgdGhhbiB0aGUgRW5nbGlzaCBsaXRlcmFscyB0aGF0IHdlcmUgaGVyZS5cclxuICAgKi9cclxuICBwcml2YXRlIHN0YXRpYyByZWFkb25seSBST0xFX0xBQkVMX0tFWVM6IFJlY29yZDxBYVJvbGUsIHN0cmluZz4gPSB7XHJcbiAgICAnQUFfRE8nOiAnYWEucm9sZV9kbycsXHJcbiAgICAnQUFfUkVWSUVXRVInOiAnYWEucm9sZV9yZXZpZXdlcicsXHJcbiAgICAnQUFfU0VDUkVUQVJJQVQnOiAnYWEucm9sZV9zZWNyZXRhcmlhdCcsXHJcbiAgICAnQUFfQURNSU4nOiAnYWEucm9sZV9hZG1pbidcclxuICB9O1xyXG5cclxuICByb2xlTGFiZWxLZXkgPSBjb21wdXRlZCgoKSA9PiBBYUFwcGVhbERldGFpbENvbXBvbmVudC5ST0xFX0xBQkVMX0tFWVNbdGhpcy51c2VyUm9sZSgpXSk7XHJcblxyXG4gIC8qKlxyXG4gICAqIFByZXNlbnRhdGlvbiBtZXRhZGF0YSBPTkxZLiBXaGV0aGVyIGFuIGFjdGlvbiBpcyBvZmZlcmVkIGlzIGRlY2lkZWQgYnkgdGhlIFNFUlZFUjsgdGhpcyBtYXAgbWVyZWx5XHJcbiAgICogc2F5cyBob3cgdG8gcmVuZGVyIG9uZSB0aGUgc2VydmVyIGFscmVhZHkgb2ZmZXJlZC5cclxuICAgKlxyXG4gICAqIFRoaXMgcmVwbGFjZWQgYSBjbGllbnQtc2lkZSBkZXJpdmF0aW9uIHRoYXQgZ2F0ZWQgb24gc2l4IHN0YXR1c2VzIHRoZSBiYWNrZW5kIGNhbm5vdCBwcm9kdWNlXHJcbiAgICogKGFjY2VwdGVkLCBhc3NpZ25lZCwgaGVhcmluZ19jb21wbGV0ZWQsIG9yZGVyX3Jlc2VydmVkLCBkb2N1bWVudHNfcmVxdWVzdGVkLCBkaXNtaXNzZWQpLiBUaGUgcmVhbFxyXG4gICAqIHN0YXR1cyBhZnRlciBBQ0NFUFQgaXMgdW5kZXJfcmV2aWV3LCBzbyBhbiBBQV9SRVZJRVdFUiB3YXMgc2hvd24gWkVSTyBhY3Rpb25zIGFuZCB0aGUgd29ya2Zsb3dcclxuICAgKiBkZWFkLWVuZGVkIGF0IHN0ZXAgdHdvLiBBbnkgY2xpZW50LXNpZGUgYWN0aW9uIGxpc3QgY2FuIGRyaWZ0IGZyb20gd2hhdCB0aGUgc2VydmVyIHdpbGwgYWNjZXB0O1xyXG4gICAqIHRoaXMgb25lIGhhZCwgc2lsZW50bHksIGluIHByb2R1Y3Rpb24uXHJcbiAgICovXHJcbiAgcHJpdmF0ZSBzdGF0aWMgcmVhZG9ubHkgQUNUSU9OX1BSRVNFTlRBVElPTjogUmVjb3JkPHN0cmluZywgT21pdDxBY3Rpb25EZWYsICdpZCc+PiA9IHtcclxuICAgIEFDQ0VQVDogeyBsYWJlbEtleTogJ2FhLmFjdGlvbi5hY2NlcHQnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5hY2NlcHRfZGVzYycsIHN0eWxlOiAncHJpbWFyeScsIHJlcXVpcmVzUmVtYXJrczogZmFsc2UgfSxcclxuICAgIFJFSkVDVDogeyBsYWJlbEtleTogJ2FhLmFjdGlvbi5yZWplY3QnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5yZWplY3RfZGVzYycsIHN0eWxlOiAnY2xvc2UnLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIEFTU0lHTl9UT19CRU5DSDogeyBsYWJlbEtleTogJ2FhLmFjdGlvbi5hc3NpZ25fdG9fYmVuY2gnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5hc3NpZ25fdG9fYmVuY2hfZGVzYycsIHN0eWxlOiAnZm9yd2FyZCcsIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSB9LFxyXG4gICAgUkVRVUVTVF9ET0NVTUVOVFM6IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24ucmVxdWVzdF9kb2N1bWVudHMnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5yZXF1ZXN0X2RvY3VtZW50c19kZXNjJywgc3R5bGU6ICdpbmZvJywgcmVxdWlyZXNSZW1hcmtzOiB0cnVlIH0sXHJcbiAgICBQUkVQQVJFX0JSSUVGOiB7IGxhYmVsS2V5OiAnYWEuYWN0aW9uLnByZXBhcmVfYnJpZWYnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5wcmVwYXJlX2JyaWVmX2Rlc2MnLCBzdHlsZTogJ2luZm8nLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIEVTQ0FMQVRFX1RPX1RJRVIyOiB7IGxhYmVsS2V5OiAnYWEuYWN0aW9uLmVzY2FsYXRlX3RvX3RpZXIyJywgZGVzY3JpcHRpb25LZXk6ICdhYS5hY3Rpb24uZXNjYWxhdGVfdG9fdGllcjJfZGVzYycsIHN0eWxlOiAnZXNjYWxhdGUnLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIFNDSEVEVUxFX0hFQVJJTkc6IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24uc2NoZWR1bGVfaGVhcmluZycsIGRlc2NyaXB0aW9uS2V5OiAnYWEuYWN0aW9uLnNjaGVkdWxlX2hlYXJpbmdfZGVzYycsIHN0eWxlOiAncHJpbWFyeScsIHJlcXVpcmVzUmVtYXJrczogZmFsc2UgfSxcclxuICAgIEZPUldBUkRfVE9fQVVUSE9SSVRZOiB7IGxhYmVsS2V5OiAnYWEuYWN0aW9uLmZvcndhcmRfdG9fYXV0aG9yaXR5JywgZGVzY3JpcHRpb25LZXk6ICdhYS5hY3Rpb24uZm9yd2FyZF90b19hdXRob3JpdHlfZGVzYycsIHN0eWxlOiAnZm9yd2FyZCcsIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSB9LFxyXG4gICAgU0VORF9CQUNLX1JFR0lTVFJBUjogeyBsYWJlbEtleTogJ2FhLmFjdGlvbi5zZW5kX2JhY2tfcmVnaXN0cmFyJywgZGVzY3JpcHRpb25LZXk6ICdhYS5hY3Rpb24uc2VuZF9iYWNrX3JlZ2lzdHJhcl9kZXNjJywgc3R5bGU6ICdyZXR1cm4nLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIFBBU1NfT1JERVI6IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24ucGFzc19vcmRlcicsIGRlc2NyaXB0aW9uS2V5OiAnYWEuYWN0aW9uLnBhc3Nfb3JkZXJfZGVzYycsIHN0eWxlOiAncHJpbWFyeScsIHJlcXVpcmVzUmVtYXJrczogZmFsc2UgfSxcclxuICAgIFJFTUFORF9UT19PTUJVRFNNQU46IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24ucmVtYW5kX3RvX29tYnVkc21hbicsIGRlc2NyaXB0aW9uS2V5OiAnYWEuYWN0aW9uLnJlbWFuZF90b19vbWJ1ZHNtYW5fZGVzYycsIHN0eWxlOiAnZXNjYWxhdGUnLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIERJU01JU1M6IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24uZGlzbWlzcycsIGRlc2NyaXB0aW9uS2V5OiAnYWEuYWN0aW9uLmRpc21pc3NfZGVzYycsIHN0eWxlOiAnY2xvc2UnLCByZXF1aXJlc1JlbWFya3M6IHRydWUgfSxcclxuICAgIFJFQVNTSUdOOiB7IGxhYmVsS2V5OiAnYWEuYWN0aW9uLnJlYXNzaWduJywgZGVzY3JpcHRpb25LZXk6ICdhYS5hY3Rpb24ucmVhc3NpZ25fZGVzYycsIHN0eWxlOiAnaW5mbycsIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSwgcmVxdWlyZXNUYXJnZXQ6IHRydWUsIHRhcmdldFR5cGU6ICd1c2VyJyB9LFxyXG4gICAgQ0xPU0U6IHsgbGFiZWxLZXk6ICdhYS5hY3Rpb24uY2xvc2UnLCBkZXNjcmlwdGlvbktleTogJ2FhLmFjdGlvbi5jbG9zZV9kZXNjJywgc3R5bGU6ICdjbG9zZScsIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSB9LFxyXG4gICAgUkVPUEVOOiB7IGxhYmVsS2V5OiAnYWEuYWN0aW9uLnJlb3BlbicsIGRlc2NyaXB0aW9uS2V5OiAnYWEuYWN0aW9uLnJlb3Blbl9kZXNjJywgc3R5bGU6ICdlc2NhbGF0ZScsIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSB9LFxyXG4gIH07XHJcblxyXG4gIC8qKlxyXG4gICAqIEV4YWN0bHkgd2hhdCB0aGUgc2VydmVyIHNheXMgdGhpcyBjYWxsZXIgbWF5IGRvLCBpbiB0aGUgc2VydmVyJ3Mgb3JkZXIuXHJcbiAgICpcclxuICAgKiBBbiBhY3Rpb24gdGhlIHNlcnZlciBvZmZlcnMgYnV0IHRoaXMgbWFwIGRvZXMgbm90IGtub3cgaG93IHRvIHJlbmRlciBpcyBzdGlsbCBzaG93biwgbGFiZWxsZWQgd2l0aFxyXG4gICAqIGl0cyByYXcgaWQg4oCUIGZhaWxpbmcgdmlzaWJsZSBiZWF0cyBoaWRpbmcgYSBsZWdpdGltYXRlIGFjdGlvbiBiZWNhdXNlIHRoZSBmcm9udGVuZCBpcyBvdXQgb2YgZGF0ZS5cclxuICAgKi9cclxuICBhdmFpbGFibGVBY3Rpb25zID0gY29tcHV0ZWQ8QWN0aW9uRGVmW10+KCgpID0+XHJcbiAgICAoKHRoaXMuYXBwZWFsKCk/LmF2YWlsYWJsZUFjdGlvbnMgYXMgc3RyaW5nW10gfCB1bmRlZmluZWQpID8/IFtdKS5tYXAoaWQgPT4gKHtcclxuICAgICAgaWQsXHJcbiAgICAgIC4uLihBYUFwcGVhbERldGFpbENvbXBvbmVudC5BQ1RJT05fUFJFU0VOVEFUSU9OW2lkXSA/PyB7XHJcbiAgICAgICAgbGFiZWxLZXk6IGlkLFxyXG4gICAgICAgIGRlc2NyaXB0aW9uS2V5OiAnJyxcclxuICAgICAgICBzdHlsZTogJ2luZm8nLFxyXG4gICAgICAgIHJlcXVpcmVzUmVtYXJrczogdHJ1ZSxcclxuICAgICAgfSksXHJcbiAgICB9KSkpO1xyXG5cclxuICAvKiogU2VydmVyLWNvbXB1dGVkIFNMQS4gTmV2ZXIgcmVjYWxjdWxhdGVkIGhlcmU6IHdvcmtpbmctZGF5IG1hdGhzIGJlbG9uZ3Mgd2l0aCB0aGUgaG9saWRheSBtYXN0ZXIuICovXHJcbiAgc2xhID0gY29tcHV0ZWQ8QXBwZWFsU2xhIHwgbnVsbD4oKCkgPT4gdGhpcy5hcHBlYWwoKT8uc2xhID8/IG51bGwpO1xyXG5cclxuICBzbGFPdmVyZHVlID0gY29tcHV0ZWQoKCkgPT4gdGhpcy5zbGEoKT8uYnJlYWNoZWQgPT09IHRydWUpO1xyXG5cclxuICAvKipcclxuICAgKiBUaGUgc2hhcmVkIHN1bW1hcnkgc3RyaXAncyBmYWN0cy5cclxuICAgKlxyXG4gICAqIFJldXNlcyB0aGUgYGFhLmRldGFpbC4qYCBrZXlzIHRoaXMgdGVtcGxhdGUgYWxyZWFkeSBzZWVkcyByYXRoZXIgdGhhbiB0aGUgYHVpLmNvbC4qYCBmYW1pbHkgdGhlXHJcbiAgICogY29tcGxhaW50IHNjcmVlbnMgdXNlOiBhbiBhcHBlYWwncyBpZGVudGlmeWluZyBmYWN0cyBhcmUgYSBkaWZmZXJlbnQgdm9jYWJ1bGFyeSAoYXBwZWFsIG51bWJlcixcclxuICAgKiBjbGFzc2lmaWNhdGlvbiwgb3JpZ2luYWwgY29tcGxhaW50KSBhbmQgb25seSB0aGUgQUEga2V5cyBleGlzdCBpbiB0aGUgYnVuZGxlcyBmb3IgdGhlbS5cclxuICAgKlxyXG4gICAqIE5vIFNMQSBpdGVtIGV2ZW4gdGhvdWdoIHRoZSBhcHBlYWwgY2FycmllcyBvbmU6IGBzbGEuc3RhdHVzS2V5YCBpcyBhIHRyYW5zbGF0aW9uIGtleSBhbmQgdGhlIHN0cmlwJ3NcclxuICAgKiAnc2xhJyBraW5kIHRha2VzIGEgcmVuZGVyZWQgc3RyaW5nIHBsdXMgaXRzIG93biBzZXZlcml0eSwgc28gdGhlIGJhbm5lciBpbiB0aGUgbGVmdCByZWdpb24g4oCUIHdoaWNoXHJcbiAgICogYWxzbyBzaG93cyB0aGUgZGVhZGxpbmUgYW5kIHRoZSBvdmVyZHVlIGRheSBjb3VudCDigJQgc3RheXMgdGhlIHNpbmdsZSBwbGFjZSB0aGUgU0xBIGlzIHN0YXRlZC5cclxuICAgKi9cclxuICByZWFkb25seSBzdW1tYXJ5SXRlbXMgPSBjb21wdXRlZDxyZWFkb25seSBDb21wbGFpbnRTdW1tYXJ5SXRlbVtdPigoKSA9PiB7XHJcbiAgICBjb25zdCBhID0gdGhpcy5hcHBlYWwoKTtcclxuICAgIGlmICghYSkgcmV0dXJuIFtdO1xyXG4gICAgcmV0dXJuIFtcclxuICAgICAgeyBsYWJlbEtleTogJ2FhLmRldGFpbC50aXRsZScsIHZhbHVlOiBhLmFwcGVhbE51bWJlciwgaWNvbjogJ3BpLWZpbGUnIH0sXHJcbiAgICAgIHsgbGFiZWxLZXk6ICdhYS5kZXRhaWwubmFtZScsIHZhbHVlOiBhLmFwcGVsbGFudE5hbWUsIGljb246ICdwaS11c2VyJywgdG9uZTogJ293bmVyJyB9LFxyXG4gICAgICB7IGxhYmVsS2V5OiAnYWEuZGV0YWlsLmVudGl0eScsIHZhbHVlOiBhLmVudGl0eUNvZGUsIGljb246ICdwaS1idWlsZGluZycgfSxcclxuICAgICAgeyBsYWJlbEtleTogJ2FhLmRldGFpbC53b3JrZmxvd19zdGFnZScsIHZhbHVlOiBhLnN0YXR1cywga2luZDogJ3N0YXR1cycsIGljb246ICdwaS1mbGFnJyB9LFxyXG4gICAgICB7IGxhYmVsS2V5OiAnYWEuZGV0YWlsLm9yaWdpbmFsX2NvbXBsYWludCcsIHZhbHVlOiBhLm9yaWdpbmFsQ29tcGxhaW50TnVtYmVyLCBpY29uOiAncGktbGluaycgfSxcclxuICAgICAgeyBsYWJlbEtleTogJ2FhLmRldGFpbC5hc3NpZ25lZF9vZmZpY2VyJywgdmFsdWU6IGEuYXNzaWduZWRPZmZpY2VyLCBpY29uOiAncGktdXNlcnMnIH1cclxuICAgIF07XHJcbiAgfSk7XHJcblxyXG4gIC8vIOKVkOKVkOKVkCBSaWdodCBjb250ZXh0IHJhaWwg4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQ4pWQXHJcbiAgLy8gVGhlIHRpbWVsaW5lIGFuZCB0aGUgY29tbWVudCB0aHJlYWQgYXJlIG1hdGVyaWFsIGEgYmVuY2ggb2ZmaWNlciBDT05TVUxUUzsgdGhlIGFjdGlvbiBjYXJkcyBhbmQgdGhlXHJcbiAgLy8gaGVhcmluZy9vcmRlciBmb3JtcyBhcmUgd2hhdCB0aGV5IHdvcmsgaW4uIEJvdGggdXNlZCB0byBzaXQgZnVsbC13aWR0aCBhdCB0aGUgYm90dG9tIG9mIHRoZSBsZWZ0XHJcbiAgLy8gY29sdW1uLCBzbyByZWFkaW5nIHRoZSBoaXN0b3J5IHNjcm9sbGVkIHRoZSBhY3Rpb25zIG9mZiBzY3JlZW4uXHJcblxyXG4gIHJhaWxPcGVuID0gc2lnbmFsPFJhaWxLZXkgfCBudWxsPihudWxsKTtcclxuXHJcbiAgcmVhZG9ubHkgcmFpbFBhbmVsczogcmVhZG9ubHkgQ29udGV4dFJhaWxQYW5lbDxSYWlsS2V5PltdID0gW1xyXG4gICAgeyBrZXk6ICd0aW1lbGluZScsIGxhYmVsOiAnVGltZWxpbmUnLCBpY29uOiAncGktaGlzdG9yeScgfSxcclxuICAgIHsga2V5OiAnY29tbWVudHMnLCBsYWJlbDogJ0NvbW1lbnRzJywgaWNvbjogJ3BpLWNvbW1lbnRzJyB9XHJcbiAgXTtcclxuXHJcbiAgYXN5bmMgbmdPbkluaXQoKSB7XHJcbiAgICBjb25zdCBhdXRoZW50aWNhdGVkID0gYXdhaXQgdGhpcy5hdXRoLmluaXQoKTtcclxuICAgIGlmICghYXV0aGVudGljYXRlZCkge1xyXG4gICAgICB0aGlzLnJvdXRlci5uYXZpZ2F0ZShbJy9zdGFmZi9sb2dpbiddKTtcclxuICAgICAgcmV0dXJuO1xyXG4gICAgfVxyXG5cclxuICAgIGNvbnN0IHJvbGVzID0gdGhpcy5hdXRoLmdldFJvbGVzKCk7XHJcbiAgICBpZiAocm9sZXMuaW5jbHVkZXMoJ0FBX0FETUlOJykpIHRoaXMudXNlclJvbGUuc2V0KCdBQV9BRE1JTicpO1xyXG4gICAgZWxzZSBpZiAocm9sZXMuaW5jbHVkZXMoJ0FBX1NFQ1JFVEFSSUFUJykpIHRoaXMudXNlclJvbGUuc2V0KCdBQV9TRUNSRVRBUklBVCcpO1xyXG4gICAgZWxzZSBpZiAocm9sZXMuaW5jbHVkZXMoJ0FBX1JFVklFV0VSJykpIHRoaXMudXNlclJvbGUuc2V0KCdBQV9SRVZJRVdFUicpO1xyXG4gICAgZWxzZSB0aGlzLnVzZXJSb2xlLnNldCgnQUFfRE8nKTtcclxuXHJcbiAgICBjb25zdCBhcHBlYWxOdW1iZXIgPSB0aGlzLnJvdXRlLnNuYXBzaG90LnBhcmFtc1snYXBwZWFsTnVtYmVyJ107XHJcbiAgICB0aGlzLmxvYWRBcHBlYWwoYXBwZWFsTnVtYmVyKTtcclxuICAgIHRoaXMubG9hZFRpbWVsaW5lKGFwcGVhbE51bWJlcik7XHJcbiAgICB0aGlzLmxvYWRPZmZpY2VycygpO1xyXG4gIH1cclxuXHJcbiAgcHJpdmF0ZSBsb2FkQXBwZWFsKGFwcGVhbE51bWJlcjogc3RyaW5nKSB7XHJcbiAgICB0aGlzLmxvYWRpbmcuc2V0KHRydWUpO1xyXG4gICAgdGhpcy5odHRwLmdldDxhbnk+KGAke2Vudmlyb25tZW50LmFwaUJhc2VVcmx9L2FwaS92MS9hcHBlYWxzLyR7YXBwZWFsTnVtYmVyfWApLnN1YnNjcmliZSh7XHJcbiAgICAgIG5leHQ6IChyZXMpID0+IHtcclxuICAgICAgICB0aGlzLmFwcGVhbC5zZXQocmVzPy5kYXRhIHx8IG51bGwpO1xyXG4gICAgICAgIHRoaXMubG9hZGluZy5zZXQoZmFsc2UpO1xyXG4gICAgICB9LFxyXG4gICAgICBlcnJvcjogKCkgPT4ge1xyXG4gICAgICAgIHRoaXMuYXBwZWFsLnNldChudWxsKTtcclxuICAgICAgICB0aGlzLmxvYWRpbmcuc2V0KGZhbHNlKTtcclxuICAgICAgfVxyXG4gICAgfSk7XHJcbiAgfVxyXG5cclxuICBwcml2YXRlIGxvYWRUaW1lbGluZShhcHBlYWxOdW1iZXI6IHN0cmluZykge1xyXG4gICAgdGhpcy50aW1lbGluZUxvYWRpbmcuc2V0KHRydWUpO1xyXG4gICAgdGhpcy5odHRwLmdldDxhbnk+KGAke2Vudmlyb25tZW50LmFwaUJhc2VVcmx9L2FwaS92MS9hcHBlYWxzLyR7YXBwZWFsTnVtYmVyfS90aW1lbGluZWApLnN1YnNjcmliZSh7XHJcbiAgICAgIG5leHQ6IChyZXMpID0+IHtcclxuICAgICAgICAvLyBUaGUgbGlzdCBpcyBuZXN0ZWQgdW5kZXIgZGF0YS50aW1lbGluZSBhbmQgdGhlIGluc3RhbnQgaXMgbmFtZWQgcGVyZm9ybWVkQXQ7IGFzc2lnbmluZ1xyXG4gICAgICAgIC8vIHJlcy5kYXRhIGhhbmRlZCBAZm9yIGFuIG9iamVjdCwgd2hpY2ggdGhyZXcgYmVmb3JlIGFueSBvZiB0aGUgcGFuZWwgcmVuZGVyZWQuXHJcbiAgICAgICAgY29uc3QgZW50cmllcyA9IEFycmF5LmlzQXJyYXkocmVzPy5kYXRhPy50aW1lbGluZSkgPyByZXMuZGF0YS50aW1lbGluZSA6IFtdO1xyXG4gICAgICAgIHRoaXMudGltZWxpbmUuc2V0KGVudHJpZXMubWFwKChlOiBhbnkpID0+ICh7IC4uLmUsIHRpbWVzdGFtcDogZS5wZXJmb3JtZWRBdCA/PyBlLnRpbWVzdGFtcCB9KSkpO1xyXG4gICAgICAgIHRoaXMudGltZWxpbmVMb2FkaW5nLnNldChmYWxzZSk7XHJcbiAgICAgIH0sXHJcbiAgICAgIGVycm9yOiAoKSA9PiB7XHJcbiAgICAgICAgdGhpcy50aW1lbGluZS5zZXQoW10pO1xyXG4gICAgICAgIHRoaXMudGltZWxpbmVMb2FkaW5nLnNldChmYWxzZSk7XHJcbiAgICAgIH1cclxuICAgIH0pO1xyXG4gIH1cclxuXHJcbiAgcHJpdmF0ZSBsb2FkT2ZmaWNlcnMoKSB7XHJcbiAgICB0aGlzLmh0dHAuZ2V0PGFueT4oYCR7ZW52aXJvbm1lbnQuYXBpQmFzZVVybH0vYXBpL3YxL2tleWNsb2FrL3VzZXJzL2J5LXJvbGU/cm9sZT1BQV9SRVZJRVdFUmApLnN1YnNjcmliZSh7XHJcbiAgICAgIG5leHQ6IChyZXMpID0+IHtcclxuICAgICAgICBjb25zdCB1c2VycyA9IChyZXMgfHwgW10pLm1hcCgodTogYW55KSA9PiAoeyBpZDogdS51c2VybmFtZSB8fCB1LnVzZXJJZCwgbmFtZTogdS5kaXNwbGF5TmFtZSB8fCBgJHt1LmZpcnN0TmFtZX0gJHt1Lmxhc3ROYW1lfWAgfSkpO1xyXG4gICAgICAgIHRoaXMuYWFPZmZpY2Vycy5zZXQodXNlcnMpO1xyXG4gICAgICB9LFxyXG4gICAgICBlcnJvcjogKCkgPT4gdGhpcy5hYU9mZmljZXJzLnNldChbXSlcclxuICAgIH0pO1xyXG4gIH1cclxuXHJcbiAgc2VsZWN0QWN0aW9uKGFjdGlvbjogQWN0aW9uRGVmKSB7XHJcbiAgICBpZiAoYWN0aW9uLmlkID09PSAnUEFTU19PUkRFUicpIHtcclxuICAgICAgdGhpcy5zaG93T3JkZXJQYW5lbC5zZXQodHJ1ZSk7XHJcbiAgICAgIHRoaXMuc2hvd0hlYXJpbmdQYW5lbC5zZXQoZmFsc2UpO1xyXG4gICAgICB0aGlzLnNlbGVjdGVkQWN0aW9uLnNldChudWxsKTtcclxuICAgICAgcmV0dXJuO1xyXG4gICAgfVxyXG4gICAgaWYgKGFjdGlvbi5pZCA9PT0gJ1NDSEVEVUxFX0hFQVJJTkcnKSB7XHJcbiAgICAgIHRoaXMuc2hvd0hlYXJpbmdQYW5lbC5zZXQodHJ1ZSk7XHJcbiAgICAgIHRoaXMuc2hvd09yZGVyUGFuZWwuc2V0KGZhbHNlKTtcclxuICAgICAgdGhpcy5zZWxlY3RlZEFjdGlvbi5zZXQobnVsbCk7XHJcbiAgICAgIHJldHVybjtcclxuICAgIH1cclxuICAgIHRoaXMuc2hvd0hlYXJpbmdQYW5lbC5zZXQoZmFsc2UpO1xyXG4gICAgdGhpcy5zaG93T3JkZXJQYW5lbC5zZXQoZmFsc2UpO1xyXG4gICAgdGhpcy5zZWxlY3RlZEFjdGlvbi5zZXQoYWN0aW9uKTtcclxuICAgIHRoaXMucmVtYXJrcyA9ICcnO1xyXG4gICAgdGhpcy50YXJnZXRVc2VyID0gJyc7XHJcbiAgICB0aGlzLmFjdGlvblJlc3VsdC5zZXQoJycpO1xyXG4gIH1cclxuXHJcbiAgY2FuY2VsQWN0aW9uKCkge1xyXG4gICAgdGhpcy5zZWxlY3RlZEFjdGlvbi5zZXQobnVsbCk7XHJcbiAgICB0aGlzLnNob3dIZWFyaW5nUGFuZWwuc2V0KGZhbHNlKTtcclxuICAgIHRoaXMuc2hvd09yZGVyUGFuZWwuc2V0KGZhbHNlKTtcclxuICAgIHRoaXMucmVtYXJrcyA9ICcnO1xyXG4gIH1cclxuXHJcbiAgc3VibWl0QWN0aW9uKCkge1xyXG4gICAgY29uc3QgYWN0aW9uID0gdGhpcy5zZWxlY3RlZEFjdGlvbigpO1xyXG4gICAgaWYgKCFhY3Rpb24pIHJldHVybjtcclxuXHJcbiAgICBjb25zdCBhcHBlYWxOdW1iZXIgPSB0aGlzLmFwcGVhbCgpPy5hcHBlYWxOdW1iZXI7XHJcbiAgICBpZiAoIWFwcGVhbE51bWJlcikgcmV0dXJuO1xyXG5cclxuICAgIHRoaXMucHJvY2Vzc2luZy5zZXQodHJ1ZSk7XHJcblxyXG4gICAgY29uc3QgYm9keTogYW55ID0ge1xyXG4gICAgICBhY3Rpb246IGFjdGlvbi5pZCxcclxuICAgICAgcmVtYXJrczogdGhpcy5yZW1hcmtzLFxyXG4gICAgICBhY3RvcjogdGhpcy5hdXRoLmN1cnJlbnRVc2VyKCk/LnVzZXJuYW1lIHx8ICcnLFxyXG4gICAgICB0YXJnZXRVc2VyOiB0aGlzLnRhcmdldFVzZXIsXHJcbiAgICAgIGhlYXJpbmdEYXRlOiB0aGlzLmhlYXJpbmdEYXRlLFxyXG4gICAgICBoZWFyaW5nVmVudWU6IHRoaXMuaGVhcmluZ1ZlbnVlXHJcbiAgICB9O1xyXG5cclxuICAgIHRoaXMuaHR0cC5wb3N0PGFueT4oXHJcbiAgICAgIGAke2Vudmlyb25tZW50LmFwaUJhc2VVcmx9L2FwaS92MS9hcHBlYWxzLyR7YXBwZWFsTnVtYmVyfS9hY3Rpb25gLFxyXG4gICAgICBib2R5XHJcbiAgICApLnN1YnNjcmliZSh7XHJcbiAgICAgIG5leHQ6IChyZXMpID0+IHtcclxuICAgICAgICB0aGlzLnByb2Nlc3Npbmcuc2V0KGZhbHNlKTtcclxuXHJcbiAgICAgICAgLy8gQSBSRUZVU0VEIGFjdGlvbiBhcnJpdmVzIGFzIEhUVFAgMjAwIHdpdGggc3VjY2VzczpmYWxzZSDigJQgdGhhdCBpcyB0aGlzIEFQSSdzIGNvbnZlbnRpb24gZm9yIGFcclxuICAgICAgICAvLyByZWplY3RlZCB3cml0ZSwgc28gQW5ndWxhcidzIGVycm9yIGNhbGxiYWNrIG5ldmVyIGZpcmVzLiBUcmVhdGluZyBhbnkgMjAwIGFzIHN1Y2Nlc3MgaXMgd2h5XHJcbiAgICAgICAgLy8gdGhpcyBzY3JlZW4gcmVwb3J0ZWQgXCJPcmRlciBwYXNzZWQgc3VjY2Vzc2Z1bGx5XCIgZm9yIG9yZGVycyB0aGUgc2VydmVyIGhhZCB0aHJvd24gYXdheS5cclxuICAgICAgICBpZiAocmVzPy5zdWNjZXNzID09PSBmYWxzZSkge1xyXG4gICAgICAgICAgdGhpcy5hY3Rpb25TdWNjZXNzLnNldChmYWxzZSk7XHJcbiAgICAgICAgICB0aGlzLmFjdGlvblJlc3VsdC5zZXQocmVzLm1lc3NhZ2VLZXkgfHwgcmVzLm1lc3NhZ2UgfHwgJ2FhLmFjdGlvbi5mYWlsZWQnKTtcclxuICAgICAgICAgIHJldHVybjtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIHRoaXMuYWN0aW9uU3VjY2Vzcy5zZXQodHJ1ZSk7XHJcbiAgICAgICAgdGhpcy5hY3Rpb25SZXN1bHQuc2V0KCdhYS5hY3Rpb24uY29tcGxldGVkJyk7XHJcbiAgICAgICAgdGhpcy5zZWxlY3RlZEFjdGlvbi5zZXQobnVsbCk7XHJcbiAgICAgICAgdGhpcy5sb2FkQXBwZWFsKGFwcGVhbE51bWJlcik7XHJcbiAgICAgICAgdGhpcy5sb2FkVGltZWxpbmUoYXBwZWFsTnVtYmVyKTtcclxuICAgICAgICB0aGlzLnJlc3RvcmVGb2N1c1RvQWN0aW9ucygpO1xyXG4gICAgICB9LFxyXG4gICAgICBlcnJvcjogKGVycikgPT4ge1xyXG4gICAgICAgIHRoaXMuYWN0aW9uU3VjY2Vzcy5zZXQoZmFsc2UpO1xyXG4gICAgICAgIC8vIDQwOSBDT05GTElDVCBtZWFucyB0aGUgYXBwZWFsIG1vdmVkIG9uOiB0aGUgc2VydmVyIHJldHVybnMgdGhlIGFjdGlvbnMgdGhhdCBBUkUgbGVnYWwgbm93LCBzb1xyXG4gICAgICAgIC8vIHJlZnJlc2hpbmcgc2hvd3MgdGhlIGNhbGxlciB0aGUgdHJ1dGggaW5zdGVhZCBvZiBsZWF2aW5nIGEgc3RhbGUgY2FyZCBzZXQgb24gc2NyZWVuLlxyXG4gICAgICAgIGlmIChlcnI/LnN0YXR1cyA9PT0gNDA5KSB7XHJcbiAgICAgICAgICB0aGlzLmFjdGlvblJlc3VsdC5zZXQoZXJyLmVycm9yPy5tZXNzYWdlS2V5IHx8ICdhYS53b3JrZmxvdy5lcnJvcl9pbGxlZ2FsX3RyYW5zaXRpb24nKTtcclxuICAgICAgICAgIHRoaXMubG9hZEFwcGVhbChhcHBlYWxOdW1iZXIpO1xyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICB0aGlzLmFjdGlvblJlc3VsdC5zZXQoZXJyLmVycm9yPy5tZXNzYWdlS2V5IHx8IGVyci5lcnJvcj8ubWVzc2FnZSB8fCAnYWEuYWN0aW9uLmZhaWxlZCcpO1xyXG4gICAgICAgIH1cclxuICAgICAgICB0aGlzLnByb2Nlc3Npbmcuc2V0KGZhbHNlKTtcclxuICAgICAgfVxyXG4gICAgfSk7XHJcbiAgfVxyXG5cclxuICAvKipcclxuICAgKiBSZXR1cm5zIGZvY3VzIHRvIHRoZSBhY3Rpb24gbGlzdCBhZnRlciBhIHBhbmVsIGNsb3Nlcy5cclxuICAgKlxyXG4gICAqIFRoZSBwYW5lbHMgYXJlIEBpZi1nYXRlZCBpbmxpbmUgYmxvY2tzLCBzbyB3aGVuIG9uZSBpcyByZW1vdmVkIHRoZSBmb2N1c2VkIGVsZW1lbnQgdmFuaXNoZXMgYW5kIGZvY3VzXHJcbiAgICogZmFsbHMgdG8gdGhlIGRvY3VtZW50IGJvZHkg4oCUIGEga2V5Ym9hcmQgdXNlciBsb3NlcyB0aGVpciBwbGFjZSBlbnRpcmVseS5cclxuICAgKi9cclxuICBwcml2YXRlIHJlc3RvcmVGb2N1c1RvQWN0aW9ucygpOiB2b2lkIHtcclxuICAgIHNldFRpbWVvdXQoKCkgPT4ge1xyXG4gICAgICBjb25zdCB0YXJnZXQgPSBkb2N1bWVudC5xdWVyeVNlbGVjdG9yPEhUTUxFbGVtZW50PignW2RhdGEtdGVzdGlkPVwiYWN0aW9uLWxpc3RcIl0gYnV0dG9uJyk7XHJcbiAgICAgIHRhcmdldD8uZm9jdXMoKTtcclxuICAgIH0pO1xyXG4gIH1cclxuXHJcbiAgb25IZWFyaW5nU2NoZWR1bGVkKCkge1xyXG4gICAgdGhpcy5zaG93SGVhcmluZ1BhbmVsLnNldChmYWxzZSk7XHJcbiAgICBjb25zdCBhcHBlYWxOdW1iZXIgPSB0aGlzLmFwcGVhbCgpPy5hcHBlYWxOdW1iZXI7XHJcbiAgICBpZiAoYXBwZWFsTnVtYmVyKSB7XHJcbiAgICAgIHRoaXMubG9hZEFwcGVhbChhcHBlYWxOdW1iZXIpO1xyXG4gICAgICB0aGlzLmxvYWRUaW1lbGluZShhcHBlYWxOdW1iZXIpO1xyXG4gICAgfVxyXG4gIH1cclxuXHJcbiAgb25PcmRlclBhc3NlZCgpIHtcclxuICAgIHRoaXMuc2hvd09yZGVyUGFuZWwuc2V0KGZhbHNlKTtcclxuICAgIGNvbnN0IGFwcGVhbE51bWJlciA9IHRoaXMuYXBwZWFsKCk/LmFwcGVhbE51bWJlcjtcclxuICAgIGlmIChhcHBlYWxOdW1iZXIpIHtcclxuICAgICAgdGhpcy5sb2FkQXBwZWFsKGFwcGVhbE51bWJlcik7XHJcbiAgICAgIHRoaXMubG9hZFRpbWVsaW5lKGFwcGVhbE51bWJlcik7XHJcbiAgICB9XHJcbiAgfVxyXG5cclxuICAvKipcclxuICAgKiBUcnVlIHdoZW4gdGhlIHNlcnZlciBvZmZlcnMgdGhpcyBjYWxsZXIgbm90aGluZy5cclxuICAgKlxyXG4gICAqIERlcml2ZWQgZnJvbSB0aGUgc2VydmVyJ3Mgb3duIGFuc3dlciByYXRoZXIgdGhhbiBhIGhhcmRjb2RlZCB0ZXJtaW5hbC1zdGF0dXMgbGlzdC4gVGhlIG9sZCBsaXN0XHJcbiAgICogaW5jbHVkZWQgYGRpc21pc3NlZGAsIHdoaWNoIHRoZSBiYWNrZW5kIG5ldmVyIHNldHMgXFx1MjAxNCBESVNNSVNTIHByb2R1Y2VzIGBjbG9zZWRgIFxcdTIwMTQgc28gaXQgd2FzIGJvdGhcclxuICAgKiB3cm9uZyBhbmQgYSBzZWNvbmQgcGxhY2UgdGhlIHZvY2FidWxhcnkgY291bGQgZHJpZnQuXHJcbiAgICovXHJcbiAgaXNUZXJtaW5hbFN0YXRlKCk6IGJvb2xlYW4ge1xyXG4gICAgcmV0dXJuIHRoaXMuYXZhaWxhYmxlQWN0aW9ucygpLmxlbmd0aCA9PT0gMDtcclxuICB9XHJcblxyXG4gIGdldFRpbWVsaW5lSWNvbihhY3Rpb246IHN0cmluZyk6IHN0cmluZyB7XHJcbiAgICBjb25zdCBpY29uczogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgICAgJ0ZJTEVEJzogJ1xcdXsxRjRFNX0nLFxyXG4gICAgICAnQUNDRVBUJzogJ1xcdTI3MDUnLFxyXG4gICAgICAnUkVKRUNUJzogJ1xcdTI3NEMnLFxyXG4gICAgICAvLyBLZXllZCBvbiB0aGUgcmVhbCBhY3Rpb24gaWRzIGZyb20gQWFXb3JrZmxvd1RyYW5zaXRpb24uIFRoZXNlIHdlcmUgQVNTSUdOX0JFTkNIIC9cclxuICAgICAgLy8gRk9SV0FSRF9BVVRIT1JJVFkgLyBSRU1BTkRfT01CVURTTUFOIFxcdTIwMTQgbmFtZXMgdGhlIHNlcnZlciBuZXZlciBlbWl0cyBcXHUyMDE0IHNvIHRob3NlIHJvd3Mgc2lsZW50bHlcclxuICAgICAgLy8gZmVsbCB0aHJvdWdoIHRvIHRoZSBkZWZhdWx0IGljb24gYW5kIGEgcmF3IGFjdGlvbiBzdHJpbmcuXHJcbiAgICAgICdBU1NJR05fVE9fQkVOQ0gnOiAnXFx1ezFGNEU0fScsXHJcbiAgICAgICdSRVFVRVNUX0RPQ1VNRU5UUyc6ICdcXHUyNzUzJyxcclxuICAgICAgJ1NDSEVEVUxFX0hFQVJJTkcnOiAnXFx1ezFGNEM1fScsXHJcbiAgICAgICdQUkVQQVJFX0JSSUVGJzogJ1xcdXsxRjRERH0nLFxyXG4gICAgICAnRVNDQUxBVEVfVE9fVElFUjInOiAnXFx1MkIwNlxcdUZFMEYnLFxyXG4gICAgICAnRk9SV0FSRF9UT19BVVRIT1JJVFknOiAnXFx1MjdBMVxcdUZFMEYnLFxyXG4gICAgICAnU0VORF9CQUNLX1JFR0lTVFJBUic6ICdcXHUyMUE5XFx1RkUwRicsXHJcbiAgICAgICdQQVNTX09SREVSJzogJ1xcdXsxRjREQ30nLFxyXG4gICAgICAnUkVNQU5EX1RPX09NQlVEU01BTic6ICdcXHV7MUY1MDF9JyxcclxuICAgICAgJ0RJU01JU1MnOiAnXFx1ezFGNkFCfScsXHJcbiAgICAgICdSRUFTU0lHTic6ICdcXHV7MUY1MDF9JyxcclxuICAgICAgJ0NMT1NFJzogJ1xcdXsxRjUxMn0nLFxyXG4gICAgICAnUkVPUEVOJzogJ1xcdXsxRjUwNH0nLFxyXG4gICAgfTtcclxuICAgIHJldHVybiBpY29uc1thY3Rpb25dIHx8ICdcXHV7MUY0Q0J9JztcclxuICB9XHJcblxyXG4gIC8qKiBUcmFuc2xhdGlvbiBrZXkgZm9yIGEgdGltZWxpbmUgYWN0aW9uOyB0aGUgcmF3IGlkIGlzIHRoZSBmYWxsYmFjayBzbyBhIG5ldyBhY3Rpb24gc3RpbGwgcmVhZHMuICovXHJcbiAgZ2V0VGltZWxpbmVMYWJlbEtleShhY3Rpb246IHN0cmluZyk6IHN0cmluZyB7XHJcbiAgICBjb25zdCBrbm93biA9IFtcclxuICAgICAgJ0ZJTEVEJywgJ0FDQ0VQVCcsICdSRUpFQ1QnLCAnQVNTSUdOX1RPX0JFTkNIJywgJ1JFUVVFU1RfRE9DVU1FTlRTJywgJ1NDSEVEVUxFX0hFQVJJTkcnLFxyXG4gICAgICAnUFJFUEFSRV9CUklFRicsICdFU0NBTEFURV9UT19USUVSMicsICdGT1JXQVJEX1RPX0FVVEhPUklUWScsICdTRU5EX0JBQ0tfUkVHSVNUUkFSJyxcclxuICAgICAgJ1BBU1NfT1JERVInLCAnUkVNQU5EX1RPX09NQlVEU01BTicsICdESVNNSVNTJywgJ1JFQVNTSUdOJywgJ0NMT1NFJywgJ1JFT1BFTicsXHJcbiAgICBdO1xyXG4gICAgcmV0dXJuIGtub3duLmluY2x1ZGVzKGFjdGlvbikgPyBgYWEudGltZWxpbmUuJHthY3Rpb24udG9Mb3dlckNhc2UoKX1gIDogYWN0aW9uO1xyXG4gIH1cclxuXHJcbiAgLyoqXHJcbiAgICogVGhlIGFjdGluZyByZXZpZXdlcidzIHRpZXIsIGZyb20gdGhlIHRva2VuIGNsYWltLlxyXG4gICAqXHJcbiAgICogcmV2aWV3ZXJfdGllciBpcyBhIHJlYWwgY2xhaW0gKGFhX3Jldmlld2VyXzAwMSA9IDEsIGFhX3Jldmlld2VyXzAwMiA9IDIpIHRoYXQgbm90aGluZyBpbiB0aGUgVUkgaGFzXHJcbiAgICogZXZlciBzdXJmYWNlZCwgc28gYSB0aWVyLTEgcmV2aWV3ZXIgaGFkIG5vIHdheSB0byBrbm93IGVzY2FsYXRpb24gd2FzIG9wZW4gdG8gdGhlbS5cclxuICAgKi9cclxuICByZXZpZXdlclRpZXIgPSBjb21wdXRlZDxzdHJpbmcgfCBudWxsPigoKSA9PiB7XHJcbiAgICBpZiAodGhpcy51c2VyUm9sZSgpICE9PSAnQUFfUkVWSUVXRVInKSB7XHJcbiAgICAgIHJldHVybiBudWxsO1xyXG4gICAgfVxyXG4gICAgY29uc3QgY2xhaW1zID0gdGhpcy5hdXRoLmN1cnJlbnRVc2VyKCkgYXMgUmVjb3JkPHN0cmluZywgdW5rbm93bj4gfCBudWxsO1xyXG4gICAgY29uc3QgdGllciA9IGNsYWltcz8uWydyZXZpZXdlcl90aWVyJ107XHJcbiAgICByZXR1cm4gdGllciA9PSBudWxsID8gbnVsbCA6IFN0cmluZyh0aWVyKTtcclxuICB9KTtcclxuXHJcbiAgZ29CYWNrKCkge1xyXG4gICAgdGhpcy5yb3V0ZXIubmF2aWdhdGUoWycvYWEvZGFzaGJvYXJkJ10pO1xyXG4gIH1cclxufVxyXG4iLCI8YXBwLXNoZWxsIFt0aXRsZUtleV09XCInYWEuZGV0YWlsLnRpdGxlJ1wiIFtyb2xlS2V5XT1cInJvbGVMYWJlbEtleSgpXCI+XHJcbiAgPGRpdiBzaGVsbC1hY3Rpb25zPlxyXG4gICAgPGJ1dHRvbiB0eXBlPVwiYnV0dG9uXCIgY2xhc3M9XCJiYWNrLWJ0blwiIGRhdGEtdGVzdGlkPVwiYmFjay10by1kYXNoYm9hcmRcIiAoY2xpY2spPVwiZ29CYWNrKClcIj5cclxuICAgICAgPHNwYW4gYXJpYS1oaWRkZW49XCJ0cnVlXCI+JmxhcnI7PC9zcGFuPlxyXG4gICAgPC9idXR0b24+XHJcbiAgICBAaWYgKHJldmlld2VyVGllcigpOyBhcyB0aWVyKSB7XHJcbiAgICAgIDxzcGFuIGNsYXNzPVwidGllci1iYWRnZVwiIGRhdGEtdGVzdGlkPVwicmV2aWV3ZXItdGllclwiPlxyXG4gICAgICAgIHt7ICdhYS5kZXRhaWwucmV2aWV3ZXJfdGllcicgfCB0cmFuc2xhdGUgfX06IHt7IHRpZXIgfX1cclxuICAgICAgPC9zcGFuPlxyXG4gICAgfVxyXG4gIDwvZGl2PlxyXG5cclxuICA8ZGl2IGNsYXNzPVwiYWEtZGV0YWlsXCI+XHJcbiAgICBAaWYgKGxvYWRpbmcoKSkge1xyXG4gICAgICA8ZGl2IGNsYXNzPVwibG9hZGluZ1wiIGRhdGEtdGVzdGlkPVwiZGV0YWlsLWxvYWRpbmdcIj57eyAnYWEuZGV0YWlsLmxvYWRpbmcnIHwgdHJhbnNsYXRlIH19PC9kaXY+XHJcbiAgICB9IEBlbHNlIGlmICghYXBwZWFsKCkpIHtcclxuICAgICAgPGRpdiBjbGFzcz1cImVycm9yLXN0YXRlXCIgZGF0YS10ZXN0aWQ9XCJkZXRhaWwtbm90LWZvdW5kXCI+e3sgJ2FhLmRldGFpbC5ub3RfZm91bmQnIHwgdHJhbnNsYXRlIH19PC9kaXY+XHJcbiAgICB9IEBlbHNlIHtcclxuICAgICAgPCEtLSBBcHBlYWwgU3VtbWFyeSBTdHJpcCDigJQgc2hhcmVkIGFwcC1jb21wbGFpbnQtc3VtbWFyeSwgYXMgb24gUkJJTywgQ0VQQyBhbmQgdGhlIENSUEMgc2NyZWVucy5cclxuICAgICAgICAgICBUaGlzIHNjcmVlbiBoYWQgbm8gc3RyaXA6IHRoZSBpZGVudGlmeWluZyBmYWN0cyB3ZXJlIHNwcmVhZCBhY3Jvc3MgLmFwcGVhbC1oZWFkZXIgYW5kIHRocmVlXHJcbiAgICAgICAgICAgZmllbGQgc2VjdGlvbnMsIHNvIHRoZSBzYW1lIGFwcGVhbCByZWFkIGRpZmZlcmVudGx5IGhlcmUgYW5kIG9uIHRoZSBxdWV1ZSB0aGF0IGxpbmtlZCB0byBpdC4gLS0+XHJcbiAgICAgIDxhcHAtY29tcGxhaW50LXN1bW1hcnkgW2l0ZW1zXT1cInN1bW1hcnlJdGVtcygpXCIgKGJhY2spPVwiZ29CYWNrKClcIiAvPlxyXG5cclxuICAgICAgPCEtLVxyXG4gICAgICAgIOKVkOKVkOKVkCBNQUlOIENPTlRFTlQ6IFRIUkVFIFJFR0lPTlMg4pWQ4pWQ4pWQXHJcbiAgICAgICAgTEVGVCBpcyB0aGUgYXBwZWFsIGFzIGZpbGVkIChyZWFkLW9ubHkgZmFjdHMpLCBDRU5URVIgaXMgdGhlIGFjdGlvbnMgYW5kIHRoZSBoZWFyaW5nL29yZGVyIGZvcm1zLFxyXG4gICAgICAgIFJJR0hUIGlzIGEgdGhpbiByYWlsIGhvbGRpbmcgdGhlIHRpbWVsaW5lIGFuZCB0aGUgY29tbWVudCB0aHJlYWQg4oCUIG1hdGVyaWFsIGEgYmVuY2ggb2ZmaWNlclxyXG4gICAgICAgIENPTlNVTFRTIHJhdGhlciB0aGFuIHdvcmtzIGluLiBCb3RoIHdlcmUgcHJldmlvdXNseSBzdGFja2VkIGZ1bGwtd2lkdGggaW4gdGhlIGxlZnQgY29sdW1uLCBzb1xyXG4gICAgICAgIHJlYWRpbmcgdGhlIHRpbWVsaW5lIHNjcm9sbGVkIHRoZSBhY3Rpb24gY2FyZHMgb3V0IG9mIHNpZ2h0LlxyXG5cclxuICAgICAgICBgLmRldGFpbC1sYXlvdXRgIGlzIGEgU1BFQyBDT05UUkFDVDogYGUyZS9hYS97d29ya2Zsb3csaGVhcmluZyxvcmRlcn0uc3BlYy50c2AgYWxsIHdhaXQgb25cclxuICAgICAgICBgLmFhLWRldGFpbCAuZGV0YWlsLWxheW91dGAgYXMgdGhlaXIgXCJzY3JlZW4gaGFzIGxvYWRlZFwiIHNpZ25hbCwgYW5kIGAuZGV0YWlsLXBhbmVsYCAvXHJcbiAgICAgICAgYC5hY3Rpb24tcGFuZWxgIG5hbWUgdGhlIHJlZ2lvbnMgdGhvc2Ugc3BlY3MgcmVhY2ggaW50by4gVGhlIG5ldyByZWdpb24gY2xhc3NlcyBhcmUgQURESVRJVkUuXHJcbiAgICAgIC0tPlxyXG4gICAgICA8ZGl2IGNsYXNzPVwiZGV0YWlsLWxheW91dCBkZXRhaWwtY29udGVudFwiPlxyXG4gICAgICAgIDwhLS0gTGVmdDogQXBwZWFsIERldGFpbHMgLS0+XHJcbiAgICAgICAgPGRpdiBjbGFzcz1cImRldGFpbC1wYW5lbCBsZWZ0LXBhbmVsXCI+XHJcbiAgICAgICAgICA8IS0tIEhlYWRlciAtLT5cclxuICAgICAgICAgIDxkaXYgY2xhc3M9XCJhcHBlYWwtaGVhZGVyXCI+XHJcbiAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJoZWFkZXItbGVmdFwiPlxyXG4gICAgICAgICAgICAgIDxoMz57eyBhcHBlYWwoKS5hcHBlYWxOdW1iZXIgfX08L2gzPlxyXG4gICAgICAgICAgICAgIDxhcHAtc3RhdHVzLWJhZGdlIFtzdGF0dXNdPVwiYXBwZWFsKCkuY2xhc3NpZmljYXRpb25cIiBrZXlQcmVmaXg9XCJjbGFzc2lmaWNhdGlvblwiIC8+XHJcbiAgICAgICAgICAgICAgQGlmIChhcHBlYWwoKS5jbGFzc2lmaWNhdGlvbk92ZXJyaWRkZW4pIHtcclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwib3ZlcnJpZGUtdGFnXCIgW3RpdGxlXT1cImFwcGVhbCgpLmNsYXNzaWZpY2F0aW9uT3ZlcnJpZGVSZWFzb24gfHwgJydcIj5cclxuICAgICAgICAgICAgICAgICAge3sgJ2FhLm92ZXJyaWRkZW4nIHwgdHJhbnNsYXRlIH19XHJcbiAgICAgICAgICAgICAgICA8L3NwYW4+XHJcbiAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgIDwhLS0gVGhyZWUgLnN0YXR1cy1iYWRnZSBlbGVtZW50cyByZW5kZXIgb24gdGhpcyBzY3JlZW4gKGNsYXNzaWZpY2F0aW9uLCB0aGlzIG9uZSwgYW5kIHRoZVxyXG4gICAgICAgICAgICAgICAgICAgdGVybWluYWwgYmFubmVyJ3MpLCBzbyB0aGF0IGNsYXNzIGFsb25lIGlzIGFtYmlndW91cy4gVGhpcyB0ZXN0IGlkIG5hbWVzIHRoZSBBUFBFQUwnc1xyXG4gICAgICAgICAgICAgICAgICAgb3duIHN0YXR1cywgd2hpY2ggaXMgd2hhdCB3b3JrZmxvdyBhc3NlcnRpb25zIGFyZSBhY3R1YWxseSBhYm91dC4gLS0+XHJcbiAgICAgICAgICAgICAgPGFwcC1zdGF0dXMtYmFkZ2UgW3N0YXR1c109XCJhcHBlYWwoKS5zdGF0dXNcIiBkYXRhLXRlc3RpZD1cImFwcGVhbC1zdGF0dXNcIiAvPlxyXG4gICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgPGRpdiBjbGFzcz1cImhlYWRlci1tZXRhXCI+XHJcbiAgICAgICAgICAgICAgPHNwYW4+e3sgJ2FhLmRldGFpbC5maWxlZCcgfCB0cmFuc2xhdGUgfX06IHt7IGFwcGVhbCgpLmZpbGVkQXQgfCBkYXRlOidkZCBNTU0geXl5eScgfX08L3NwYW4+XHJcbiAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgPC9kaXY+XHJcblxyXG4gICAgICAgICAgPCEtLVxyXG4gICAgICAgICAgICBTdGFnZSBTTEEsIGV4YWN0bHkgYXMgdGhlIHNlcnZlciBjb21wdXRlZCBpdC4gZGF5c1JlbWFpbmluZyBpcyBORUdBVElWRSBvbmNlIHRoZSBkZWFkbGluZSBoYXNcclxuICAgICAgICAgICAgcGFzc2VkLCBzbyB0aGUgb3ZlcmR1ZSBicmFuY2ggcmVuZGVycyBpdHMgYWJzb2x1dGUgdmFsdWUgYWdhaW5zdCBhIGRpZmZlcmVudCBrZXkuXHJcbiAgICAgICAgICAtLT5cclxuICAgICAgICAgIEBpZiAoc2xhKCk/LnRyYWNrZWQpIHtcclxuICAgICAgICAgICAgPGRpdiBjbGFzcz1cInNsYS1iYW5uZXJcIlxyXG4gICAgICAgICAgICAgICAgIFtjbGFzcy5lcnJvci1iYW5uZXJdPVwic2xhKCkhLmJyZWFjaGVkXCJcclxuICAgICAgICAgICAgICAgICBbY2xhc3Mud2FybmluZy1iYW5uZXJdPVwiIXNsYSgpIS5icmVhY2hlZFwiXHJcbiAgICAgICAgICAgICAgICAgW2F0dHIuZGF0YS10ZXN0aWRdPVwic2xhKCkhLmJyZWFjaGVkID8gJ3NsYS1vdmVyZHVlJyA6ICdzbGEtc3RhdHVzJ1wiXHJcbiAgICAgICAgICAgICAgICAgcm9sZT1cInN0YXR1c1wiXHJcbiAgICAgICAgICAgICAgICAgYXJpYS1saXZlPVwicG9saXRlXCI+XHJcbiAgICAgICAgICAgICAgQGlmIChzbGEoKSEuYnJlYWNoZWQpIHtcclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwic2xhLWljb25cIiBhcmlhLWhpZGRlbj1cInRydWVcIj4mIzk4ODg7PC9zcGFuPlxyXG4gICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cInNsYS1zdGF0dXMtbGFiZWxcIj57eyBzbGEoKSEuc3RhdHVzS2V5IHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwic2xhLWRlYWRsaW5lXCI+XHJcbiAgICAgICAgICAgICAgICB7eyAnYWEuZGV0YWlsLnNsYV9kZWFkbGluZScgfCB0cmFuc2xhdGUgfX06XHJcbiAgICAgICAgICAgICAgICB7eyBzbGEoKSEuZGVhZGxpbmUgPyAoc2xhKCkhLmRlYWRsaW5lIHwgZGF0ZTonZGQgTU1NIHl5eXknKSA6ICgnYWEuZGV0YWlsLm5vdF9hdmFpbGFibGUnIHwgdHJhbnNsYXRlKSB9fVxyXG4gICAgICAgICAgICAgIDwvc3Bhbj5cclxuICAgICAgICAgICAgICBAaWYgKHNsYSgpIS5kYXlzUmVtYWluaW5nICE9PSBudWxsKSB7XHJcbiAgICAgICAgICAgICAgICBAaWYgKHNsYSgpIS5icmVhY2hlZCkge1xyXG4gICAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cInNsYS1kYXlzXCI+XHJcbiAgICAgICAgICAgICAgICAgICAge3sgJ2FhLmRldGFpbC5zbGFfb3ZlcmR1ZV9ieScgfCB0cmFuc2xhdGUgfX06IHt7IDAgLSBzbGEoKSEuZGF5c1JlbWFpbmluZyEgfX1cclxuICAgICAgICAgICAgICAgICAgPC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgfSBAZWxzZSB7XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwic2xhLWRheXNcIj5cclxuICAgICAgICAgICAgICAgICAgICB7eyAnYWEuZGV0YWlsLnNsYV9kYXlzX3JlbWFpbmluZycgfCB0cmFuc2xhdGUgfX06IHt7IHNsYSgpIS5kYXlzUmVtYWluaW5nIH19XHJcbiAgICAgICAgICAgICAgICAgIDwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgfVxyXG5cclxuICAgICAgICAgIDwhLS0gQXBwZWFsIEluZm8gLS0+XHJcbiAgICAgICAgICA8c2VjdGlvbiBjbGFzcz1cImRldGFpbC1zZWN0aW9uXCI+XHJcbiAgICAgICAgICAgIDxoND57eyAnYWEuZGV0YWlsLmFwcGVhbF9pbmZvcm1hdGlvbicgfCB0cmFuc2xhdGUgfX08L2g0PlxyXG4gICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGQtZ3JpZFwiPlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwuZ3JvdW5kX2Zvcl9hcHBlYWwnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJhcHBlYWwtZ3JvdW5kXCI+e3sgYXBwZWFsKCkuYXBwZWFsR3JvdW5kIHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwucmVsaWVmX3NvdWdodCcgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBkYXRhLXRlc3RpZD1cInJlbGllZi1zb3VnaHRcIj57eyBhcHBlYWwoKS5yZWxpZWZTb3VnaHQgfHwgJ+KAlCcgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5maWxlZF9kYXRlJyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiZmlsZWQtYXRcIj57eyBhcHBlYWwoKS5maWxlZEF0IHwgZGF0ZTonZGQgTU1NIHl5eXknIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwub3JpZ2luYWxfY29tcGxhaW50JyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiY29tcGxhaW50LWxpbmtcIiBkYXRhLXRlc3RpZD1cIm9yaWdpbmFsLWNvbXBsYWludFwiPnt7IGFwcGVhbCgpLm9yaWdpbmFsQ29tcGxhaW50TnVtYmVyIHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwuY2xvc3VyZV9jbGF1c2UnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJjbG9zdXJlLWNsYXVzZVwiPnt7IGFwcGVhbCgpLmNsb3N1cmVDbGF1c2UgfHwgJ+KAlCcgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5tb2RlX29mX3JlY2VpcHQnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJtb2RlLW9mLXJlY2VpcHRcIj57eyBhcHBlYWwoKS5tb2RlT2ZSZWNlaXB0IHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIEBpZiAoYXBwZWFsKCkucmVhc29uRm9yRGVsYXkpIHtcclxuICAgICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZCBmdWxsLXdpZHRoXCI+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLnJlYXNvbl9mb3JfZGVsYXknIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgICA8cCBjbGFzcz1cImRlc2NyaXB0aW9uLXRleHRcIiBkYXRhLXRlc3RpZD1cInJlYXNvbi1mb3ItZGVsYXlcIj57eyBhcHBlYWwoKS5yZWFzb25Gb3JEZWxheSB9fTwvcD5cclxuICAgICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICA8L3NlY3Rpb24+XHJcblxyXG4gICAgICAgICAgPCEtLSBBcHBlbGxhbnQgSW5mbyAtLT5cclxuICAgICAgICAgIDxzZWN0aW9uIGNsYXNzPVwiZGV0YWlsLXNlY3Rpb25cIj5cclxuICAgICAgICAgICAgPGg0Pnt7ICdhYS5kZXRhaWwuYXBwZWxsYW50X2RldGFpbHMnIHwgdHJhbnNsYXRlIH19PC9oND5cclxuICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkLWdyaWRcIj5cclxuICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLm5hbWUnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJhcHBlbGxhbnQtbmFtZVwiPnt7IGFwcGVhbCgpLmFwcGVsbGFudE5hbWUgfHwgJ+KAlCcgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5lbWFpbCcgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBkYXRhLXRlc3RpZD1cImFwcGVsbGFudC1lbWFpbFwiPnt7IGFwcGVhbCgpLmFwcGVsbGFudEVtYWlsIHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwucGhvbmUnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJhcHBlbGxhbnQtcGhvbmVcIj57eyBhcHBlYWwoKS5hcHBlbGxhbnRQaG9uZSB8fCAn4oCUJyB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLmVudGl0eScgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBkYXRhLXRlc3RpZD1cImVudGl0eS1jb2RlXCI+e3sgYXBwZWFsKCkuZW50aXR5Q29kZSB8fCAn4oCUJyB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLmZpbGVkX2J5JyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiYXBwZWFsLWZpbGVkLWJ5XCI+e3sgYXBwZWFsKCkuYXBwZWFsRmlsZWRCeSB8fCAn4oCUJyB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICA8L3NlY3Rpb24+XHJcblxyXG4gICAgICAgICAgPCEtLSBBc3NpZ25tZW50IC0tPlxyXG4gICAgICAgICAgPHNlY3Rpb24gY2xhc3M9XCJkZXRhaWwtc2VjdGlvblwiPlxyXG4gICAgICAgICAgICA8aDQ+e3sgJ2FhLmRldGFpbC5hc3NpZ25tZW50JyB8IHRyYW5zbGF0ZSB9fTwvaDQ+XHJcbiAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZC1ncmlkXCI+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5hc3NpZ25lZF9yb2xlJyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiYXNzaWduZWQtcm9sZVwiPnt7IGFwcGVhbCgpLmFzc2lnbmVkUm9sZSB8fCAn4oCUJyB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLmFzc2lnbmVkX29mZmljZXInIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJhc3NpZ25lZC1vZmZpY2VyXCI+e3sgYXBwZWFsKCkuYXNzaWduZWRPZmZpY2VyIHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwud29ya2Zsb3dfc3RhZ2UnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJ3b3JrZmxvdy1zdGFnZVwiPnt7IGFwcGVhbCgpLndvcmtmbG93U3RhZ2UgfHwgJ+KAlCcgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5wcmlvcml0eScgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBkYXRhLXRlc3RpZD1cInByaW9yaXR5XCI+e3sgYXBwZWFsKCkucHJpb3JpdHkgfHwgJ+KAlCcgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgPC9zZWN0aW9uPlxyXG5cclxuICAgICAgICAgIDwhLS0gSGVhcmluZyAtLT5cclxuICAgICAgICAgIDwhLS1cclxuICAgICAgICAgICAgYC1zdW1tYXJ5YCBzdWZmaXhlcyBiZWNhdXNlIGFwcC1hYS1oZWFyaW5nJ3Mgb3duIGRhdGUgYW5kIHZlbnVlIENPTlRST0xTIGFscmVhZHkgb3duXHJcbiAgICAgICAgICAgIGRhdGEtdGVzdGlkPVwiaGVhcmluZy1kYXRlXCIgLyBcImhlYXJpbmctdmVudWVcIi4gQm90aCByZW5kZXIgYXQgb25jZSB3aGVuIHRoZSBoZWFyaW5nIHBhbmVsIGlzXHJcbiAgICAgICAgICAgIG9wZW4sIGFuZCB0aGUgYmFyZSBpZHMgbWFkZSBldmVyeSBnZXRCeVRlc3RJZCBvbiB0aGVtIGEgc3RyaWN0LW1vZGUgdmlvbGF0aW9uIOKAlCBzbyBhIHNwZWNcclxuICAgICAgICAgICAgdHJ5aW5nIHRvIGZpbGwgdGhlIGZvcm0gcmVzb2x2ZWQgdHdvIGVsZW1lbnRzIGFuZCBjb3VsZCBub3QgcHJvY2VlZC5cclxuICAgICAgICAgIC0tPlxyXG4gICAgICAgICAgPHNlY3Rpb24gY2xhc3M9XCJkZXRhaWwtc2VjdGlvblwiPlxyXG4gICAgICAgICAgICA8aDQ+e3sgJ2FhLmRldGFpbC5oZWFyaW5nJyB8IHRyYW5zbGF0ZSB9fTwvaDQ+XHJcbiAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZC1ncmlkXCI+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5oZWFyaW5nX2RhdGUnIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJoZWFyaW5nLWRhdGUtc3VtbWFyeVwiPlxyXG4gICAgICAgICAgICAgICAgICB7eyBhcHBlYWwoKS5oZWFyaW5nRGF0ZSA/IChhcHBlYWwoKS5oZWFyaW5nRGF0ZSB8IGRhdGU6J2RkIE1NTSB5eXl5LCBISDptbScpIDogKCdhYS5kZXRhaWwubm90X3NjaGVkdWxlZCcgfCB0cmFuc2xhdGUpIH19XHJcbiAgICAgICAgICAgICAgICA8L3NwYW4+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImZpZWxkLWxhYmVsXCI+e3sgJ2FhLmRldGFpbC5oZWFyaW5nX3ZlbnVlJyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiaGVhcmluZy12ZW51ZS1zdW1tYXJ5XCI+e3sgYXBwZWFsKCkuaGVhcmluZ1ZlbnVlIHx8ICfigJQnIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgIDwvc2VjdGlvbj5cclxuXHJcbiAgICAgICAgICA8IS0tXHJcbiAgICAgICAgICAgIE9yZGVyIGRldGFpbHMgYXJlIEZMQVQgb24gdGhlIGRldGFpbCBwYXlsb2FkOyB0aGVyZSBpcyBubyBuZXN0ZWQgYG9yZGVyYCBvYmplY3QsIHdoaWNoIGlzIHdoeVxyXG4gICAgICAgICAgICB0aGlzIHNlY3Rpb24gbmV2ZXIgcmVuZGVyZWQgYmVmb3JlLlxyXG4gICAgICAgICAgLS0+XHJcbiAgICAgICAgICBAaWYgKGFwcGVhbCgpLm9yZGVyT3V0Y29tZSkge1xyXG4gICAgICAgICAgICA8c2VjdGlvbiBjbGFzcz1cImRldGFpbC1zZWN0aW9uIG9yZGVyLXNlY3Rpb25cIiBkYXRhLXRlc3RpZD1cIm9yZGVyLXNlY3Rpb25cIj5cclxuICAgICAgICAgICAgICA8aDQ+e3sgJ2FhLmRldGFpbC5vcmRlcicgfCB0cmFuc2xhdGUgfX08L2g0PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZC1ncmlkXCI+XHJcbiAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwub3JkZXJfb3V0Y29tZScgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwib3V0Y29tZS1iYWRnZVwiIGRhdGEtdGVzdGlkPVwib3JkZXItb3V0Y29tZVwiIFthdHRyLmRhdGEtb3V0Y29tZV09XCJhcHBlYWwoKS5vcmRlck91dGNvbWVcIj5cclxuICAgICAgICAgICAgICAgICAgICB7eyBhcHBlYWwoKS5vcmRlck91dGNvbWUgfX1cclxuICAgICAgICAgICAgICAgICAgPC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwub3JkZXJfZGF0ZScgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwib3JkZXItZGF0ZVwiPlxyXG4gICAgICAgICAgICAgICAgICAgIHt7IGFwcGVhbCgpLm9yZGVyRGF0ZSA/IChhcHBlYWwoKS5vcmRlckRhdGUgfCBkYXRlOidkZCBNTU0geXl5eScpIDogJ+KAlCcgfX1cclxuICAgICAgICAgICAgICAgICAgPC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwuYXdhcmRfYW1vdW50JyB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgICAgPHNwYW4gZGF0YS10ZXN0aWQ9XCJhd2FyZC1tb2RpZmllZC1hbW91bnRcIj5cclxuICAgICAgICAgICAgICAgICAgICB7eyBhcHBlYWwoKS5hd2FyZE1vZGlmaWVkQW1vdW50ID8gJ+KCuScgKyBhcHBlYWwoKS5hd2FyZE1vZGlmaWVkQW1vdW50IDogJ+KAlCcgfX1cclxuICAgICAgICAgICAgICAgICAgPC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkIGZ1bGwtd2lkdGhcIj5cclxuICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLm9yZGVyX3N1bW1hcnknIHwgdHJhbnNsYXRlIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgPHAgY2xhc3M9XCJkZXNjcmlwdGlvbi10ZXh0XCIgZGF0YS10ZXN0aWQ9XCJvcmRlci1zdW1tYXJ5XCI+e3sgYXBwZWFsKCkub3JkZXJTdW1tYXJ5IHx8ICfigJQnIH19PC9wPlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICA8L3NlY3Rpb24+XHJcbiAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgPCEtLSBDbG9zdXJlIC0tPlxyXG4gICAgICAgICAgQGlmIChhcHBlYWwoKS5jbG9zdXJlQ2F1c2UgfHwgYXBwZWFsKCkuY2xvc2VkQXQpIHtcclxuICAgICAgICAgICAgPHNlY3Rpb24gY2xhc3M9XCJkZXRhaWwtc2VjdGlvblwiIGRhdGEtdGVzdGlkPVwiY2xvc3VyZS1zZWN0aW9uXCI+XHJcbiAgICAgICAgICAgICAgPGg0Pnt7ICdhYS5kZXRhaWwuY2xvc3VyZScgfCB0cmFuc2xhdGUgfX08L2g0PlxyXG4gICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJmaWVsZC1ncmlkXCI+XHJcbiAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZmllbGRcIj5cclxuICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJmaWVsZC1sYWJlbFwiPnt7ICdhYS5kZXRhaWwuY2xvc3VyZV9jYXVzZScgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiY2xvc3VyZS1jYXVzZVwiPnt7IGFwcGVhbCgpLmNsb3N1cmVDYXVzZSB8fCAn4oCUJyB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImZpZWxkXCI+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiZmllbGQtbGFiZWxcIj57eyAnYWEuZGV0YWlsLmNsb3NlZF9hdCcgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwiY2xvc2VkLWF0XCI+XHJcbiAgICAgICAgICAgICAgICAgICAge3sgYXBwZWFsKCkuY2xvc2VkQXQgPyAoYXBwZWFsKCkuY2xvc2VkQXQgfCBkYXRlOidkZCBNTU0geXl5eSwgSEg6bW0nKSA6ICfigJQnIH19XHJcbiAgICAgICAgICAgICAgICAgIDwvc3Bhbj5cclxuICAgICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgICA8L3NlY3Rpb24+XHJcbiAgICAgICAgICB9XHJcblxyXG4gICAgICAgIDwvZGl2PlxyXG5cclxuICAgICAgICA8IS0tIENlbnRlcjogdGhlIGFjdGlvbnMgLS0+XHJcbiAgICAgICAgPCEtLSBgLmFjdGlvbi1wYW5lbGAgaXMgdGhlIGNsYXNzIHRoZSBBQSBzcGVjcyByZWFjaCB0aGUgYWN0aW9uIHJlZ2lvbiB0aHJvdWdoLiBgLnJpZ2h0LXBhbmVsYCBpc1xyXG4gICAgICAgICAgICAgdGhlIHJlZ2lvbiBuYW1lIHRoZSByZWZlcmVuY2UgbGF5b3V0IHVzZXMgZm9yIHRoZSBDRU5URVIgY29sdW1uIOKAlCBtaXNsZWFkaW5nLCBidXQgaXQgaXMgd2hhdFxyXG4gICAgICAgICAgICAgdGhlIFJCSU8gc2NyZWVuIGFuZCBpdHMgbGF5b3V0IHNwZWMgYWxyZWFkeSBjYWxsIGl0LCBhbmQgb25lIHZvY2FidWxhcnkgYmVhdHMgYW4gYWNjdXJhdGVcclxuICAgICAgICAgICAgIG9uZSBub2JvZHkgZWxzZSBzaGFyZXMuIC0tPlxyXG4gICAgICAgIDxkaXYgY2xhc3M9XCJhY3Rpb24tcGFuZWwgcmlnaHQtcGFuZWxcIj5cclxuICAgICAgICAgIDwhLS1cclxuICAgICAgICAgICAgT1VUU0lERSB0aGUgdGVybWluYWwvYWN0aW9ucyBicmFuY2hlcywgYmVjYXVzZSB0aGUgb3V0Y29tZSBvZiBhbiBhY3Rpb24gbXVzdCBvdXRsaXZlIHRoZVxyXG4gICAgICAgICAgICBhY3Rpb25zLiBSZWplY3QgYW5kIERpc21pc3MgYm90aCBtb3ZlIHRoZSBhcHBlYWwgdG8gYSB0ZXJtaW5hbCBzdGF0ZSwgc28gdGhlIHJlLWZldGNoIHRoYXRcclxuICAgICAgICAgICAgZm9sbG93cyBmbGlwcGVkIHRoaXMgcGFuZWwgdG8gdGhlIGJhbm5lciBiZWxvdyBhbmQgZGVzdHJveWVkIHRoZSB2ZXJ5IGNvbmZpcm1hdGlvbiB0aGVcclxuICAgICAgICAgICAgb2ZmaWNlciBoYWQganVzdCBlYXJuZWQg4oCUIHRoZSB3cml0ZSBzdWNjZWVkZWQgYW5kIHRoZSBzY3JlZW4gc2FpZCBub3RoaW5nIGFib3V0IGl0LlxyXG4gICAgICAgICAgLS0+XHJcbiAgICAgICAgICBAaWYgKGFjdGlvblJlc3VsdCgpKSB7XHJcbiAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJyZXN1bHQtbXNnXCIgcm9sZT1cInN0YXR1c1wiIGFyaWEtbGl2ZT1cInBvbGl0ZVwiIGRhdGEtdGVzdGlkPVwiYWN0aW9uLXJlc3VsdFwiXHJcbiAgICAgICAgICAgICAgICAgW2NsYXNzLnN1Y2Nlc3NdPVwiYWN0aW9uU3VjY2VzcygpXCIgW2NsYXNzLmVycm9yXT1cIiFhY3Rpb25TdWNjZXNzKClcIj5cclxuICAgICAgICAgICAgICB7eyBhY3Rpb25SZXN1bHQoKSB8IHRyYW5zbGF0ZSB9fVxyXG4gICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgIH1cclxuXHJcbiAgICAgICAgICA8IS0tIGB0ZXJtaW5hbC1iYW5uZXJgIG1hdGNoZXMgdGhlIGlzVGVybWluYWxTdGF0ZSgpIHByZWRpY2F0ZSB0aGF0IGdhdGVzIGl0LiBJdCB3YXNcclxuICAgICAgICAgICAgICAgZGF0YS10ZXN0aWQ9XCJuby1hY3Rpb25zXCIsIHdoaWNoIG5vIHNwZWMgcmVmZXJlbmNlZCwgd2hpbGUgdGhlIFMzQiBzdWl0ZSBhc3NlcnRlZCBvblxyXG4gICAgICAgICAgICAgICBgdGVybWluYWwtYmFubmVyYCDigJQgc28gdGhlIG9uZSBiYW5uZXIgaGFkIHR3byBuYW1lcyBhbmQgdGhlIGFzc2VydGlvbiBjb3VsZCBuZXZlciBwYXNzLiAtLT5cclxuICAgICAgICAgIEBpZiAoaXNUZXJtaW5hbFN0YXRlKCkpIHtcclxuICAgICAgICAgICAgPGRpdiBjbGFzcz1cImNsb3NlZC1iYW5uZXJcIiBkYXRhLXRlc3RpZD1cInRlcm1pbmFsLWJhbm5lclwiPlxyXG4gICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwiY2xvc2VkLWljb25cIiBhcmlhLWhpZGRlbj1cInRydWVcIj4mIzEwMDAzOzwvc3Bhbj5cclxuICAgICAgICAgICAgICA8aDQ+e3sgJ2FhLmRldGFpbC5ub19hY3Rpb25zX2F2YWlsYWJsZScgfCB0cmFuc2xhdGUgfX08L2g0PlxyXG4gICAgICAgICAgICAgIDxhcHAtc3RhdHVzLWJhZGdlIFtzdGF0dXNdPVwiYXBwZWFsKCkuc3RhdHVzXCIgLz5cclxuICAgICAgICAgICAgICA8cD57eyAnYWEuZGV0YWlsLm5vX2FjdGlvbnNfaGludCcgfCB0cmFuc2xhdGUgfX08L3A+XHJcbiAgICAgICAgICAgIDwvZGl2PlxyXG4gICAgICAgICAgfSBAZWxzZSB7XHJcbiAgICAgICAgICAgIDxoND57eyAnYWEuZGV0YWlsLmF2YWlsYWJsZV9hY3Rpb25zJyB8IHRyYW5zbGF0ZSB9fTwvaDQ+XHJcbiAgICAgICAgICAgIDxwIGNsYXNzPVwiYWN0aW9uLWhpbnRcIj57eyAnYWEuZGV0YWlsLmF2YWlsYWJsZV9hY3Rpb25zX2hpbnQnIHwgdHJhbnNsYXRlIH19PC9wPlxyXG5cclxuICAgICAgICAgICAgPCEtLVxyXG4gICAgICAgICAgICAgIFNlcnZlci1kcml2ZW46IHRoZSBjYXJkcyBhcmUgZXhhY3RseSB0aGUgaWRzIHRoZSBBUEkgcmV0dXJuZWQgaW4gYXZhaWxhYmxlQWN0aW9ucywgcmVuZGVyZWRcclxuICAgICAgICAgICAgICB0aHJvdWdoIHRoZWlyIHRyYW5zbGF0aW9uIGtleXMuIE5vdGhpbmcgaGVyZSBkZWNpZGVzIGVsaWdpYmlsaXR5LlxyXG5cclxuICAgICAgICAgICAgICBTQ0hFRFVMRV9IRUFSSU5HIGFuZCBQQVNTX09SREVSIG5ldmVyIHJlYWNoIHRoZSBnZW5lcmljIGNvbmZpcm0gc3RlcCDigJQgc2VsZWN0QWN0aW9uIHJvdXRlc1xyXG4gICAgICAgICAgICAgIHRoZW0gdG8gYXBwLWFhLWhlYXJpbmcgLyBhcHAtYWEtb3JkZXIgYW5kIGNsZWFycyB0aGUgc2VsZWN0aW9uLCBzbyB0aGUgYmFyIHJlbmRlcnMgaXRzIGNhcmRzXHJcbiAgICAgICAgICAgICAgYW5kIG5vIGZvcm0uIFRob3NlIHR3byBzY3JlZW5zIG93biB0aGVpciBvd24gcmVtYXJrcyBhbmQgdGhlaXIgb3duIHN1Ym1pdC5cclxuICAgICAgICAgICAgLS0+XHJcbiAgICAgICAgICAgIDxhcHAtd29ya2Zsb3ctYWN0aW9uLWJhclxyXG4gICAgICAgICAgICAgIFthY3Rpb25zXT1cImF2YWlsYWJsZUFjdGlvbnMoKVwiXHJcbiAgICAgICAgICAgICAgW3NlbGVjdGVkSWRdPVwic2VsZWN0ZWRBY3Rpb24oKT8uaWQgPz8gbnVsbFwiXHJcbiAgICAgICAgICAgICAgW3Byb2Nlc3NpbmddPVwicHJvY2Vzc2luZygpXCJcclxuICAgICAgICAgICAgICBbKHJlbWFya3MpXT1cInJlbWFya3NcIlxyXG4gICAgICAgICAgICAgIFtzaG93Rm9ybVRpdGxlXT1cInRydWVcIlxyXG4gICAgICAgICAgICAgIFtmaWVsZHNdPVwidGFyZ2V0UGlja2VyXCJcclxuICAgICAgICAgICAgICByZW1hcmtzTGFiZWxLZXk9XCJhYS5kZXRhaWwucmVtYXJrc1wiXHJcbiAgICAgICAgICAgICAgcmVtYXJrc1BsYWNlaG9sZGVyS2V5PVwiYWEuZGV0YWlsLnJlbWFya3NfcGxhY2Vob2xkZXJcIlxyXG4gICAgICAgICAgICAgIGNvbW1pdExhYmVsS2V5PVwiYWEuZGV0YWlsLmNvbmZpcm1cIlxyXG4gICAgICAgICAgICAgIHByb2Nlc3NpbmdMYWJlbEtleT1cImFhLmRldGFpbC5wcm9jZXNzaW5nXCJcclxuICAgICAgICAgICAgICBjYW5jZWxMYWJlbEtleT1cImFhLmRldGFpbC5jYW5jZWxcIlxyXG4gICAgICAgICAgICAgIChzZWxlY3QpPVwic2VsZWN0QWN0aW9uKCRldmVudClcIlxyXG4gICAgICAgICAgICAgIChjb21taXQpPVwic3VibWl0QWN0aW9uKClcIlxyXG4gICAgICAgICAgICAgIChjYW5jZWwpPVwiY2FuY2VsQWN0aW9uKClcIiAvPlxyXG5cclxuICAgICAgICAgICAgPG5nLXRlbXBsYXRlICN0YXJnZXRQaWNrZXIgbGV0LWFjdGlvbj5cclxuICAgICAgICAgICAgICBAaWYgKGFjdGlvbi5yZXF1aXJlc1RhcmdldCAmJiBhY3Rpb24udGFyZ2V0VHlwZSA9PT0gJ3VzZXInKSB7XHJcbiAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwiZm9ybS1maWVsZFwiPlxyXG4gICAgICAgICAgICAgICAgICA8bGFiZWwgZm9yPVwiYWN0aW9uLXRhcmdldC11c2VyXCI+e3sgJ2FhLmRldGFpbC5hc3NpZ25fdG8nIHwgdHJhbnNsYXRlIH19PC9sYWJlbD5cclxuICAgICAgICAgICAgICAgICAgPHNlbGVjdCBpZD1cImFjdGlvbi10YXJnZXQtdXNlclwiIGRhdGEtdGVzdGlkPVwiYWN0aW9uLXRhcmdldC11c2VyXCIgWyhuZ01vZGVsKV09XCJ0YXJnZXRVc2VyXCI+XHJcbiAgICAgICAgICAgICAgICAgICAgPG9wdGlvbiB2YWx1ZT1cIlwiPnt7ICdhYS5kZXRhaWwuc2VsZWN0X29mZmljZXInIHwgdHJhbnNsYXRlIH19PC9vcHRpb24+XHJcbiAgICAgICAgICAgICAgICAgICAgQGZvciAob2ZmaWNlciBvZiBhYU9mZmljZXJzKCk7IHRyYWNrIG9mZmljZXIuaWQpIHtcclxuICAgICAgICAgICAgICAgICAgICAgIDxvcHRpb24gW3ZhbHVlXT1cIm9mZmljZXIuaWRcIj57eyBvZmZpY2VyLm5hbWUgfX08L29wdGlvbj5cclxuICAgICAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICAgIDwvc2VsZWN0PlxyXG4gICAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICA8L25nLXRlbXBsYXRlPlxyXG5cclxuICAgICAgICAgICAgPCEtLSBIZWFyaW5nIFBhbmVsIC0tPlxyXG4gICAgICAgICAgICBAaWYgKHNob3dIZWFyaW5nUGFuZWwoKSkge1xyXG4gICAgICAgICAgICAgIDxhcHAtYWEtaGVhcmluZyBbYXBwZWFsXT1cImFwcGVhbCgpXCIgKGhlYXJpbmdTY2hlZHVsZWQpPVwib25IZWFyaW5nU2NoZWR1bGVkKClcIiAoY2FuY2VsbGVkKT1cImNhbmNlbEFjdGlvbigpXCI+PC9hcHAtYWEtaGVhcmluZz5cclxuICAgICAgICAgICAgfVxyXG5cclxuICAgICAgICAgICAgPCEtLSBPcmRlciBQYW5lbCAtLT5cclxuICAgICAgICAgICAgQGlmIChzaG93T3JkZXJQYW5lbCgpKSB7XHJcbiAgICAgICAgICAgICAgPGFwcC1hYS1vcmRlciBbYXBwZWFsXT1cImFwcGVhbCgpXCIgKG9yZGVyUGFzc2VkKT1cIm9uT3JkZXJQYXNzZWQoKVwiIChjYW5jZWxsZWQpPVwiY2FuY2VsQWN0aW9uKClcIj48L2FwcC1hYS1vcmRlcj5cclxuICAgICAgICAgICAgfVxyXG5cclxuICAgICAgICAgIH1cclxuICAgICAgICA8L2Rpdj5cclxuXHJcbiAgICAgICAgPCEtLVxyXG4gICAgICAgICAg4pWQ4pWQ4pWQIFJJR0hUIFJBSUwg4pWQ4pWQ4pWQXHJcbiAgICAgICAgICBUaGUgdGltZWxpbmUgYW5kIHRoZSBjb21tZW50IHRocmVhZC4gQm90aCB3ZXJlIGZ1bGwtd2lkdGggYmxvY2tzIGF0IHRoZSBib3R0b20gb2YgdGhlIGxlZnRcclxuICAgICAgICAgIGNvbHVtbiwgd2hpY2ggaXMgd2hhdCBwdXNoZWQgdGhlIGFjdGlvbiBjYXJkcyBiZWxvdyB0aGUgZm9sZCBvbiBhbiBhcHBlYWwgd2l0aCBhbnkgaGlzdG9yeS5cclxuXHJcbiAgICAgICAgICBUV08gcGFuZWxzLCBub3QgdGhyZWU6IHRoaXMgc2NyZWVuIGhhcyBOTyBhdHRhY2htZW50cyBwYW5lbCBiZWNhdXNlIHRoZXJlIGlzIG5vIGRhdGEgc291cmNlIGZvclxyXG4gICAgICAgICAgb25lLiBUaGUgYXBwZWFsIGRldGFpbCBwYXlsb2FkIGNhcnJpZXMgbm8gYXR0YWNobWVudCBsaXN0LCBhbmQgdGhlIG9ubHkgQUEgYXR0YWNobWVudCBlbmRwb2ludFxyXG4gICAgICAgICAgaXMgdGhlIERSQUZUIGFzc2Vzc21lbnQncyAoYXBwLWFhLWRyYWZ0LWFzc2Vzc21lbnQgcmVhZHMgZHJhZnQoKS5hdHRhY2htZW50cyksIHdoaWNoIGlzIGFcclxuICAgICAgICAgIGRpZmZlcmVudCBlbnRpdHkgb24gYSBkaWZmZXJlbnQgc2NyZWVuLiBBbiBhdHRhY2htZW50cyBpY29uIGhlcmUgd291bGQgb3BlbiBhIHBhbmVsIHRoYXQgY291bGRcclxuICAgICAgICAgIG9ubHkgZXZlciBzYXkgXCJub25lXCIg4oCUIGluZGlzdGluZ3Vpc2hhYmxlIGZyb20gYW4gYXBwZWFsIGdlbnVpbmVseSBmaWxlZCB3aXRob3V0IGRvY3VtZW50cywgYW5kXHJcbiAgICAgICAgICBleGFjdGx5IHRoZSBraW5kIG9mIFVJLWNvbXBsZXRlLWJ1dC13aXJlZC10by1ub3RoaW5nIHBhbmVsIHRoaXMgcGFzcyBleGlzdHMgdG8gcmVtb3ZlLiBJdCB3YW50c1xyXG4gICAgICAgICAgYSBgR0VUIC9hcGkvdjEvYXBwZWFscy97bn0vYXR0YWNobWVudHNgIGZpcnN0LlxyXG4gICAgICAgIC0tPlxyXG4gICAgICAgIDxhc2lkZSBhcHAtY29udGV4dC1yYWlsIFtwYW5lbHNdPVwicmFpbFBhbmVsc1wiIFsob3BlbildPVwicmFpbE9wZW5cIiBbYm9keV09XCJyYWlsQm9keVwiPjwvYXNpZGU+XHJcblxyXG4gICAgICAgIDxuZy10ZW1wbGF0ZSAjcmFpbEJvZHkgbGV0LW9wZW4+XHJcbiAgICAgICAgICBAc3dpdGNoIChvcGVuKSB7XHJcbiAgICAgICAgICAgIEBjYXNlICgndGltZWxpbmUnKSB7XHJcbiAgICAgICAgICAgICAgQGlmICh0aW1lbGluZUxvYWRpbmcoKSkge1xyXG4gICAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cImxvYWRpbmctaW5saW5lXCIgZGF0YS10ZXN0aWQ9XCJ0aW1lbGluZS1sb2FkaW5nXCI+e3sgJ2FhLmRldGFpbC50aW1lbGluZV9sb2FkaW5nJyB8IHRyYW5zbGF0ZSB9fTwvZGl2PlxyXG4gICAgICAgICAgICAgIH0gQGVsc2UgaWYgKHRpbWVsaW5lKCkubGVuZ3RoID09PSAwKSB7XHJcbiAgICAgICAgICAgICAgICA8cCBjbGFzcz1cImVtcHR5LXRpbWVsaW5lXCIgZGF0YS10ZXN0aWQ9XCJ0aW1lbGluZS1lbXB0eVwiPnt7ICdhYS5kZXRhaWwudGltZWxpbmVfZW1wdHknIHwgdHJhbnNsYXRlIH19PC9wPlxyXG4gICAgICAgICAgICAgIH0gQGVsc2Uge1xyXG4gICAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cInRpbWVsaW5lXCIgZGF0YS10ZXN0aWQ9XCJ0aW1lbGluZVwiPlxyXG4gICAgICAgICAgICAgICAgICBAZm9yIChlbnRyeSBvZiB0aW1lbGluZSgpOyB0cmFjayBlbnRyeS50aW1lc3RhbXApIHtcclxuICAgICAgICAgICAgICAgICAgICA8ZGl2IGNsYXNzPVwidGltZWxpbmUtaXRlbVwiPlxyXG4gICAgICAgICAgICAgICAgICAgICAgPGRpdiBjbGFzcz1cInRpbWVsaW5lLWRvdFwiPlxyXG4gICAgICAgICAgICAgICAgICAgICAgICA8c3BhbiBjbGFzcz1cImFjdGlvbi1pY29uXCIgYXJpYS1oaWRkZW49XCJ0cnVlXCI+e3sgZ2V0VGltZWxpbmVJY29uKGVudHJ5LmFjdGlvbikgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJ0aW1lbGluZS1jb250ZW50XCI+XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIDxkaXYgY2xhc3M9XCJ0aW1lbGluZS1tYWluXCI+XHJcbiAgICAgICAgICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJ0aW1lbGluZS1hY3Rpb25cIj57eyBnZXRUaW1lbGluZUxhYmVsS2V5KGVudHJ5LmFjdGlvbikgfCB0cmFuc2xhdGUgfX08L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgICAgICAgICAgPHNwYW4gY2xhc3M9XCJ0aW1lbGluZS10aW1lXCI+e3sgZW50cnkudGltZXN0YW1wIHwgZGF0ZTonZGQgTU1NIHl5eXkgSEg6bW0nIH19PC9zcGFuPlxyXG4gICAgICAgICAgICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICAgICAgICAgICAgQGlmIChlbnRyeS5wZXJmb3JtZWRCeSkge1xyXG4gICAgICAgICAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwidGltZWxpbmUtYWN0b3JcIj57eyBlbnRyeS5wZXJmb3JtZWRCeSB9fTwvc3Bhbj5cclxuICAgICAgICAgICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgICAgICAgICBAaWYgKGVudHJ5LnJlbWFya3MpIHtcclxuICAgICAgICAgICAgICAgICAgICAgICAgICA8cCBjbGFzcz1cInRpbWVsaW5lLXJlbWFya3NcIj57eyBlbnRyeS5yZW1hcmtzIH19PC9wPlxyXG4gICAgICAgICAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwidGltZWxpbmUtZmxvd1wiPlxyXG4gICAgICAgICAgICAgICAgICAgICAgICAgIHt7IGVudHJ5LmZyb21TdGF0dXMgfX0gPHNwYW4gYXJpYS1oaWRkZW49XCJ0cnVlXCI+JnJhcnI7PC9zcGFuPiB7eyBlbnRyeS50b1N0YXR1cyB9fVxyXG4gICAgICAgICAgICAgICAgICAgICAgICA8L3NwYW4+XHJcbiAgICAgICAgICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICAgICAgICA8L2Rpdj5cclxuICAgICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgPC9kaXY+XHJcbiAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIEBjYXNlICgnY29tbWVudHMnKSB7XHJcbiAgICAgICAgICAgICAgPCEtLVxyXG4gICAgICAgICAgICAgICAgS2V5ZWQgb24gdGhlIE9SSUdJTkFMIENPTVBMQUlOVCwgbm90IHRoZSBhcHBlYWwuIFRoZSBjb21wbGFpbnQgaXMgdGhlIG9uZSBlbnRpdHkgZXZlcnlcclxuICAgICAgICAgICAgICAgIG9mZmljZXIgd29ya3MgYXJvdW5kLCBzbyBhbiBSQklPIG5vdGUgd3JpdHRlbiBiZWZvcmUgdGhlIGFwcGVhbCB3YXMgZmlsZWQgaXMgZXhhY3RseSB0aGVcclxuICAgICAgICAgICAgICAgIGNvbnRleHQgYSBiZW5jaCBvZmZpY2VyIG5lZWRzOyBhIHRocmVhZCBzY29wZWQgdG8gdGhlIGFwcGVhbCBudW1iZXIgd291bGQgaGlkZSBpdC5cclxuICAgICAgICAgICAgICAgIEFic2VudCB3aGVuIHRoZSBhcHBlYWwgY2FycmllcyBubyBjb21wbGFpbnQgcmVmZXJlbmNlIOKAlCBhbiBhcHBlYWwgYWdhaW5zdCBhbiBvcmRlciB3aXRoXHJcbiAgICAgICAgICAgICAgICBubyBvcmlnaW5hdGluZyBjb21wbGFpbnQgaGFzIG5vdGhpbmcgdG8gdGhyZWFkIG9uLlxyXG4gICAgICAgICAgICAgIC0tPlxyXG4gICAgICAgICAgICAgIEBpZiAoYXBwZWFsKCkub3JpZ2luYWxDb21wbGFpbnROdW1iZXIpIHtcclxuICAgICAgICAgICAgICAgIDxhcHAtY29tbWVudC10aHJlYWRcclxuICAgICAgICAgICAgICAgICAgW2NvbXBsYWludE51bWJlcl09XCJhcHBlYWwoKS5vcmlnaW5hbENvbXBsYWludE51bWJlclwiXHJcbiAgICAgICAgICAgICAgICAgIFthdWRpZW5jZVJvbGVzXT1cImNvbW1lbnRBdWRpZW5jZVJvbGVzXCIgLz5cclxuICAgICAgICAgICAgICB9IEBlbHNlIHtcclxuICAgICAgICAgICAgICAgIDwhLS0gYG5vdF9hdmFpbGFibGVgIHJhdGhlciB0aGFuIGEgbmV3IGNvbW1lbnRzLXNwZWNpZmljIGtleTogVHJhbnNsYXRpb25TZXJ2aWNlIHJldHVybnMgYW5cclxuICAgICAgICAgICAgICAgICAgICAgdW5zZWVkZWQga2V5IFZFUkJBVElNLCBzbyBpbnZlbnRpbmcgb25lIHdvdWxkIHByaW50IFwiYWEuZGV0YWlsLmNvbW1lbnRzX3VuYXZhaWxhYmxlXCJcclxuICAgICAgICAgICAgICAgICAgICAgdG8gYSBiZW5jaCBvZmZpY2VyIGluIGFsbCBlbGV2ZW4gbG9jYWxlcy4gLS0+XHJcbiAgICAgICAgICAgICAgICA8cCBjbGFzcz1cImVtcHR5LXRpbWVsaW5lXCI+e3sgJ2FhLmRldGFpbC5ub3RfYXZhaWxhYmxlJyB8IHRyYW5zbGF0ZSB9fTwvcD5cclxuICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgIH1cclxuICAgICAgICA8L25nLXRlbXBsYXRlPlxyXG4gICAgICA8L2Rpdj5cclxuICAgIH1cclxuICA8L2Rpdj5cclxuPC9hcHAtc2hlbGw+XHJcbiIsImltcG9ydCB7IENvbXBvbmVudCwgSW5wdXQsIE91dHB1dCwgRXZlbnRFbWl0dGVyLCBPbkluaXQsIGluamVjdCwgc2lnbmFsIH0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBDb21tb25Nb2R1bGUgfSBmcm9tICdAYW5ndWxhci9jb21tb24nO1xuaW1wb3J0IHsgRm9ybXNNb2R1bGUgfSBmcm9tICdAYW5ndWxhci9mb3Jtcyc7XG5pbXBvcnQgeyBIdHRwQ2xpZW50IH0gZnJvbSAnQGFuZ3VsYXIvY29tbW9uL2h0dHAnO1xuaW1wb3J0IHsgZW52aXJvbm1lbnQgfSBmcm9tICcuLi8uLi8uLi8uLi9lbnZpcm9ubWVudHMvZW52aXJvbm1lbnQnO1xuaW1wb3J0IHsgVHJhbnNsYXRlUGlwZSB9IGZyb20gJy4uLy4uLy4uL3BpcGVzL3RyYW5zbGF0ZS5waXBlJztcblxuLyoqIE9uZSBoZWFyaW5nIGV2ZW50LCBhcyByZXR1cm5lZCBieSB0aGUgaGVhcmluZ3MgZW5kcG9pbnQuICovXG5pbnRlcmZhY2UgSGVhcmluZ1JlY29yZCB7XG4gIGlkOiBudW1iZXI7XG4gIHNlcXVlbmNlTm86IG51bWJlcjtcbiAgZXZlbnRUeXBlOiBzdHJpbmc7XG4gIGRhdGU6IHN0cmluZztcbiAgdmVudWU6IHN0cmluZyB8IG51bGw7XG4gIG1vZGU6IHN0cmluZyB8IG51bGw7XG4gIG91dGNvbWU6IHN0cmluZyB8IG51bGw7XG4gIG91dGNvbWVSZW1hcmtzOiBzdHJpbmcgfCBudWxsO1xuICByZWFzb246IHN0cmluZyB8IG51bGw7XG4gIHBlcmZvcm1lZEJ5OiBzdHJpbmcgfCBudWxsO1xuICBwZXJmb3JtZWRCeVJvbGU6IHN0cmluZyB8IG51bGw7XG4gIHBlcmZvcm1lZEF0OiBzdHJpbmc7XG4gIHN1cGVyc2VkZWQ6IGJvb2xlYW47XG59XG5cbi8qKlxuICogU2NoZWR1bGVzIGFuZCByZXNjaGVkdWxlcyBoZWFyaW5ncywgYW5kIHNob3dzIHRoZSBoZWFyaW5nIGhpc3RvcnkuXG4gKlxuICogVGFsa3MgdG8gdGhlIGRlZGljYXRlZCBoZWFyaW5ncyBlbmRwb2ludCByYXRoZXIgdGhhbiB0aGUgZ2VuZXJpYyAvYWN0aW9uIHJvdXRlLiBUaHJlZSB0aGluZ3MgZm9sbG93XG4gKiBmcm9tIHRoYXQsIG5vbmUgb2YgdGhlbSBjb3NtZXRpYzpcbiAqICAgLSBIaXN0b3J5IGlzIHJlYWwuIFJlc2NoZWR1bGluZyBBUFBFTkRTIGEgcmVjb3JkIGluc3RlYWQgb2Ygb3ZlcndyaXRpbmcgQXBwZWFsLmhlYXJpbmdEYXRlLCBzbyBhXG4gKiAgICAgdmFjYXRlZCBzaXR0aW5nIHJlbWFpbnMgb24gdGhlIHJlY29yZCDigJQgd2hpY2ggZm9yIGEgc3RhdHV0b3J5IGhlYXJpbmcgaXMgdGhlIHBvaW50LlxuICogICAtIFRoZSBzZXJ2ZXIgY2hlY2tzIGZvciBhbiBvZmZpY2VyIGRvdWJsZS1ib29raW5nIGFuZCBhbnN3ZXJzIDQwOSwgc28gYSBjbGFzaCBpcyByZWZ1c2VkIHJhdGhlclxuICogICAgIHRoYW4gc2lsZW50bHkgYWNjZXB0ZWQuXG4gKiAgIC0gTm90aWNlcyBhcmUgUkVDT1JERUQsIG5vdCBzZW50LiBUaGVyZSBpcyBubyBlbWFpbCBvciBTTVMgZ2F0ZXdheSBpbiB0aGlzIGRlcGxveW1lbnQsIGFuZCB0aGVcbiAqICAgICBzZXJ2ZXIgc2F5cyBzbyBleHBsaWNpdGx5IChub3RpY2VTdGF0dXMgUEVORElORywgZ2F0ZXdheUF2YWlsYWJsZSBmYWxzZSkuIFRoZSBwcmV2aW91cyBjb3B5IHRvbGRcbiAqICAgICB0aGUgdXNlciBcIk5vdGljZXMgd2lsbCBiZSBzZW50IHRvIHNlbGVjdGVkIHBhcnRpZXNcIiB3aGlsZSB0aGUgc2VydmVyIGlnbm9yZWQgdGhlIGZpZWxkIGVudGlyZWx5LlxuICovXG5AQ29tcG9uZW50KHtcbiAgc2VsZWN0b3I6ICdhcHAtYWEtaGVhcmluZycsXG4gIHN0YW5kYWxvbmU6IHRydWUsXG4gIGltcG9ydHM6IFtDb21tb25Nb2R1bGUsIEZvcm1zTW9kdWxlLCBUcmFuc2xhdGVQaXBlXSxcbiAgdGVtcGxhdGVVcmw6ICcuL2FhLWhlYXJpbmcuY29tcG9uZW50Lmh0bWwnLFxuICBzdHlsZVVybDogJy4vYWEtaGVhcmluZy5jb21wb25lbnQuc2Nzcydcbn0pXG5leHBvcnQgY2xhc3MgQWFIZWFyaW5nQ29tcG9uZW50IGltcGxlbWVudHMgT25Jbml0IHtcbiAgQElucHV0KCkgYXBwZWFsOiBhbnk7XG4gIEBPdXRwdXQoKSBoZWFyaW5nU2NoZWR1bGVkID0gbmV3IEV2ZW50RW1pdHRlcjx2b2lkPigpO1xuICBAT3V0cHV0KCkgY2FuY2VsbGVkID0gbmV3IEV2ZW50RW1pdHRlcjx2b2lkPigpO1xuXG4gIHByaXZhdGUgaHR0cCA9IGluamVjdChIdHRwQ2xpZW50KTtcblxuICBzY2hlZHVsaW5nID0gc2lnbmFsKGZhbHNlKTtcbiAgZXJyb3JLZXkgPSBzaWduYWwoJycpO1xuICBzdWNjZXNzS2V5ID0gc2lnbmFsKCcnKTtcbiAgc2hvd1ByZXZpZXcgPSBzaWduYWwoZmFsc2UpO1xuXG4gIGhpc3RvcnkgPSBzaWduYWw8SGVhcmluZ1JlY29yZFtdPihbXSk7XG4gIGhpc3RvcnlMb2FkaW5nID0gc2lnbmFsKHRydWUpO1xuICAvKiogVGhlIHNpdHRpbmcgY3VycmVudGx5IGluIGZvcmNlLCBvciBudWxsIHdoZW4gbm9uZSBpcyBmaXhlZC4gKi9cbiAgb3BlcmF0aXZlID0gc2lnbmFsPEhlYXJpbmdSZWNvcmQgfCBudWxsPihudWxsKTtcblxuICBoZWFyaW5nRGF0ZSA9ICcnO1xuICBoZWFyaW5nVGltZSA9ICcnO1xuICBoZWFyaW5nVmVudWUgPSAnJztcbiAgLyoqIFNlbnQgc2VwYXJhdGVseSBmcm9tIHRoZSB2ZW51ZTogdGhlIHNlcnZlciBub3JtYWxpc2VzIG1vZGUsIGFuZCBhIHZlbnVlIHN0cmluZyBjYW5ub3QgZXhwcmVzcyBpdC4gKi9cbiAgaGVhcmluZ01vZGUgPSAnSU5fUEVSU09OJztcbiAgLyoqIEEgcmVhc29uIGlzIG1hbmRhdG9yeSB3aGVuIGEgc2l0dGluZyBhbHJlYWR5IGV4aXN0cyDigJQgdmFjYXRpbmcgb25lIHdpdGhvdXQgYSByZWFzb24gaXMgbm90IGF1ZGl0YWJsZS4gKi9cbiAgcmVhc29uID0gJyc7XG4gIHBhcnRpZXNUb05vdGlmeTogc3RyaW5nW10gPSBbJ2FwcGVsbGFudCcsICdyZXNwb25kZW50J107XG5cbiAgLyoqIFZlbnVlIGxhYmVscyBhcmUga2V5czsgJ1ZpcnR1YWwnIGlzIGRlbGliZXJhdGVseSBOT1QgaGVyZSDigJQgdGhhdCBpcyB0aGUgTU9ERSwgbm90IGEgdmVudWUuICovXG4gIHZlbnVlT3B0aW9ucyA9IFtcbiAgICB7IHZhbHVlOiAnUkJJIEhlYWQgT2ZmaWNlLCBNdW1iYWkgLSBDb25mZXJlbmNlIFJvb20gQScsIGxhYmVsS2V5OiAnYWEuaGVhcmluZy52ZW51ZV9ob19tdW1iYWlfYScgfSxcbiAgICB7IHZhbHVlOiAnUkJJIFJlZ2lvbmFsIE9mZmljZSAtIEhlYXJpbmcgUm9vbSAxJywgbGFiZWxLZXk6ICdhYS5oZWFyaW5nLnZlbnVlX3JvXzEnIH0sXG4gICAgeyB2YWx1ZTogJ1JCSSBSZWdpb25hbCBPZmZpY2UgLSBIZWFyaW5nIFJvb20gMicsIGxhYmVsS2V5OiAnYWEuaGVhcmluZy52ZW51ZV9yb18yJyB9LFxuICAgIHsgdmFsdWU6ICdPdGhlcicsIGxhYmVsS2V5OiAnYWEuaGVhcmluZy52ZW51ZV9vdGhlcicgfSxcbiAgXTtcblxuICBtb2RlT3B0aW9ucyA9IFtcbiAgICB7IHZhbHVlOiAnSU5fUEVSU09OJywgbGFiZWxLZXk6ICdhYS5oZWFyaW5nLm1vZGVfaW5fcGVyc29uJyB9LFxuICAgIHsgdmFsdWU6ICdWSURFTycsIGxhYmVsS2V5OiAnYWEuaGVhcmluZy5tb2RlX3ZpZGVvJyB9LFxuICAgIHsgdmFsdWU6ICdIWUJSSUQnLCBsYWJlbEtleTogJ2FhLmhlYXJpbmcubW9kZV9oeWJyaWQnIH0sXG4gIF07XG5cbiAgbmdPbkluaXQoKTogdm9pZCB7XG4gICAgdGhpcy5sb2FkSGlzdG9yeSgpO1xuICB9XG5cbiAgLyoqIFRydWUgd2hlbiBhIHNpdHRpbmcgaXMgYWxyZWFkeSBmaXhlZCwgc28gdGhpcyBzY2hlZHVsaW5nIGlzIGEgUkVTQ0hFRFVMRSBhbmQgbmVlZHMgYSByZWFzb24uICovXG4gIGdldCBpc1Jlc2NoZWR1bGUoKTogYm9vbGVhbiB7XG4gICAgcmV0dXJuIHRoaXMub3BlcmF0aXZlKCkgIT09IG51bGw7XG4gIH1cblxuICBwcml2YXRlIGxvYWRIaXN0b3J5KCk6IHZvaWQge1xuICAgIGNvbnN0IGFwcGVhbE51bWJlciA9IHRoaXMuYXBwZWFsPy5hcHBlYWxOdW1iZXI7XG4gICAgaWYgKCFhcHBlYWxOdW1iZXIpIHtcbiAgICAgIHRoaXMuaGlzdG9yeUxvYWRpbmcuc2V0KGZhbHNlKTtcbiAgICAgIHJldHVybjtcbiAgICB9XG4gICAgdGhpcy5oaXN0b3J5TG9hZGluZy5zZXQodHJ1ZSk7XG4gICAgdGhpcy5odHRwLmdldDxhbnk+KGAke2Vudmlyb25tZW50LmFwaUJhc2VVcmx9L2FwaS92MS9hcHBlYWxzLyR7YXBwZWFsTnVtYmVyfS9oZWFyaW5nc2ApXG4gICAgICAuc3Vic2NyaWJlKHtcbiAgICAgICAgbmV4dDogKHJlcykgPT4ge1xuICAgICAgICAgIHRoaXMuaGlzdG9yeS5zZXQocmVzPy5oZWFyaW5nSGlzdG9yeSA/PyBbXSk7XG4gICAgICAgICAgdGhpcy5vcGVyYXRpdmUuc2V0KHJlcz8ub3BlcmF0aXZlID8/IG51bGwpO1xuICAgICAgICAgIHRoaXMuaGlzdG9yeUxvYWRpbmcuc2V0KGZhbHNlKTtcbiAgICAgICAgfSxcbiAgICAgICAgZXJyb3I6ICgpID0+IHtcbiAgICAgICAgICAvLyBObyBtb2NrIGZhbGxiYWNrOiBhbiBlbXB0eSBoaXN0b3J5IHRoYXQgaXMgYWN0dWFsbHkgYSBmYWlsZWQgZmV0Y2ggd291bGQgcmVhZCBhcyBcIm5vXG4gICAgICAgICAgLy8gaGVhcmluZ3MgZXZlciBmaXhlZFwiLCB3aGljaCBpcyBhIG1hdGVyaWFsbHkgZGlmZmVyZW50IGFuZCBtaXNsZWFkaW5nIHN0YXRlbWVudC5cbiAgICAgICAgICB0aGlzLmhpc3Rvcnkuc2V0KFtdKTtcbiAgICAgICAgICB0aGlzLmVycm9yS2V5LnNldCgnYWEuaGVhcmluZy5lcnJvcl9oaXN0b3J5X3VuYXZhaWxhYmxlJyk7XG4gICAgICAgICAgdGhpcy5oaXN0b3J5TG9hZGluZy5zZXQoZmFsc2UpO1xuICAgICAgICB9XG4gICAgICB9KTtcbiAgfVxuXG4gIHRvZ2dsZVBhcnR5KHBhcnR5OiBzdHJpbmcpIHtcbiAgICBjb25zdCBpbmRleCA9IHRoaXMucGFydGllc1RvTm90aWZ5LmluZGV4T2YocGFydHkpO1xuICAgIGlmIChpbmRleCA+IC0xKSB7XG4gICAgICB0aGlzLnBhcnRpZXNUb05vdGlmeS5zcGxpY2UoaW5kZXgsIDEpO1xuICAgIH0gZWxzZSB7XG4gICAgICB0aGlzLnBhcnRpZXNUb05vdGlmeS5wdXNoKHBhcnR5KTtcbiAgICB9XG4gIH1cblxuICBpc1BhcnR5U2VsZWN0ZWQocGFydHk6IHN0cmluZyk6IGJvb2xlYW4ge1xuICAgIHJldHVybiB0aGlzLnBhcnRpZXNUb05vdGlmeS5pbmNsdWRlcyhwYXJ0eSk7XG4gIH1cblxuICBwcmV2aWV3Tm90aWNlKCkge1xuICAgIGlmICghdGhpcy52YWxpZGF0ZSgpKSB7XG4gICAgICByZXR1cm47XG4gICAgfVxuICAgIHRoaXMuZXJyb3JLZXkuc2V0KCcnKTtcbiAgICB0aGlzLnNob3dQcmV2aWV3LnNldCh0cnVlKTtcbiAgfVxuXG4gIHByaXZhdGUgdmFsaWRhdGUoKTogYm9vbGVhbiB7XG4gICAgaWYgKCF0aGlzLmhlYXJpbmdEYXRlKSB7XG4gICAgICB0aGlzLmVycm9yS2V5LnNldCgnYWEuaGVhcmluZy5lcnJvcl9kYXRlX3JlcXVpcmVkJyk7XG4gICAgICByZXR1cm4gZmFsc2U7XG4gICAgfVxuICAgIGlmICghdGhpcy5oZWFyaW5nVGltZSkge1xuICAgICAgdGhpcy5lcnJvcktleS5zZXQoJ2FhLmhlYXJpbmcuZXJyb3JfdGltZV9yZXF1aXJlZCcpO1xuICAgICAgcmV0dXJuIGZhbHNlO1xuICAgIH1cbiAgICBpZiAoIXRoaXMuaGVhcmluZ1ZlbnVlKSB7XG4gICAgICB0aGlzLmVycm9yS2V5LnNldCgnYWEuaGVhcmluZy5lcnJvcl92ZW51ZV9yZXF1aXJlZCcpO1xuICAgICAgcmV0dXJuIGZhbHNlO1xuICAgIH1cbiAgICBpZiAodGhpcy5pc1Jlc2NoZWR1bGUgJiYgIXRoaXMucmVhc29uLnRyaW0oKSkge1xuICAgICAgdGhpcy5lcnJvcktleS5zZXQoJ2FhLmhlYXJpbmcuZXJyb3JfcmVhc29uX3JlcXVpcmVkJyk7XG4gICAgICByZXR1cm4gZmFsc2U7XG4gICAgfVxuICAgIHRoaXMuZXJyb3JLZXkuc2V0KCcnKTtcbiAgICByZXR1cm4gdHJ1ZTtcbiAgfVxuXG4gIHNjaGVkdWxlSGVhcmluZygpIHtcbiAgICBpZiAoIXRoaXMudmFsaWRhdGUoKSkge1xuICAgICAgcmV0dXJuO1xuICAgIH1cbiAgICB0aGlzLnNjaGVkdWxpbmcuc2V0KHRydWUpO1xuICAgIGNvbnN0IGFwcGVhbE51bWJlciA9IHRoaXMuYXBwZWFsPy5hcHBlYWxOdW1iZXI7XG5cbiAgICBjb25zdCBib2R5OiBSZWNvcmQ8c3RyaW5nLCB1bmtub3duPiA9IHtcbiAgICAgIC8vIEEgZnVsbCBJU08gZGF0ZXRpbWUuIFRoZSBlbmRwb2ludCB0b2xlcmF0ZXMgZGF0ZS1vbmx5IGFuZCBkZWZhdWx0cyB0aGUgdGltZSwgYnV0IGFuIHVuc3RhdGVkXG4gICAgICAvLyBkZWZhdWx0IG9uIGEgaGVhcmluZyB0aW1lIGlzIGV4YWN0bHkgdGhlIHNvcnQgb2YgdGhpbmcgYSBwYXJ0eSB0dXJucyB1cCB3cm9uZyBmb3IuXG4gICAgICBoZWFyaW5nRGF0ZTogYCR7dGhpcy5oZWFyaW5nRGF0ZX1UJHt0aGlzLmhlYXJpbmdUaW1lfTowMGAsXG4gICAgICBoZWFyaW5nVmVudWU6IHRoaXMuaGVhcmluZ1ZlbnVlLFxuICAgICAgaGVhcmluZ01vZGU6IHRoaXMuaGVhcmluZ01vZGUsXG4gICAgICBwYXJ0aWVzVG9Ob3RpZnk6IHRoaXMucGFydGllc1RvTm90aWZ5LFxuICAgIH07XG4gICAgaWYgKHRoaXMuaXNSZXNjaGVkdWxlKSB7XG4gICAgICBib2R5WydyZWFzb24nXSA9IHRoaXMucmVhc29uLnRyaW0oKTtcbiAgICB9XG5cbiAgICB0aGlzLmh0dHAucG9zdDxhbnk+KFxuICAgICAgYCR7ZW52aXJvbm1lbnQuYXBpQmFzZVVybH0vYXBpL3YxL2FwcGVhbHMvJHthcHBlYWxOdW1iZXJ9L2hlYXJpbmdzYCxcbiAgICAgIGJvZHlcbiAgICApLnN1YnNjcmliZSh7XG4gICAgICBuZXh0OiAocmVzKSA9PiB7XG4gICAgICAgIHRoaXMuc2NoZWR1bGluZy5zZXQoZmFsc2UpO1xuICAgICAgICBpZiAocmVzPy5zdWNjZXNzID09PSBmYWxzZSkge1xuICAgICAgICAgIHRoaXMuZXJyb3JLZXkuc2V0KHJlcy5tZXNzYWdlS2V5IHx8ICdhYS5oZWFyaW5nLmVycm9yX2ZhaWxlZCcpO1xuICAgICAgICAgIHRoaXMuc2hvd1ByZXZpZXcuc2V0KGZhbHNlKTtcbiAgICAgICAgICByZXR1cm47XG4gICAgICAgIH1cbiAgICAgICAgLy8gVGhlIHNlcnZlcidzIG93biBrZXksIHdoaWNoIHNheXMgbm90aWNlcyB3ZXJlIFJFQ09SREVELiBOZXZlciBzdWJzdGl0dXRlZCBmb3IgYSBjbGFpbSB0aGF0XG4gICAgICAgIC8vIHRoZXkgd2VyZSBkZWxpdmVyZWQuXG4gICAgICAgIHRoaXMuc3VjY2Vzc0tleS5zZXQocmVzPy5tZXNzYWdlS2V5IHx8ICdhYS5oZWFyaW5nLnNjaGVkdWxlZF9ub3RpY2VzX3JlY29yZGVkJyk7XG4gICAgICAgIHRoaXMubG9hZEhpc3RvcnkoKTtcbiAgICAgICAgc2V0VGltZW91dCgoKSA9PiB0aGlzLmhlYXJpbmdTY2hlZHVsZWQuZW1pdCgpLCAxMjAwKTtcbiAgICAgIH0sXG4gICAgICBlcnJvcjogKGVycikgPT4ge1xuICAgICAgICB0aGlzLnNjaGVkdWxpbmcuc2V0KGZhbHNlKTtcbiAgICAgICAgdGhpcy5zaG93UHJldmlldy5zZXQoZmFsc2UpO1xuICAgICAgICAvLyA0MDkgaXMgYSBkb3VibGUtYm9va2luZyBvZiB0aGUgcHJlc2lkaW5nIG9mZmljZXIsIHdoaWNoIGlzIGEgcmVhbCBjb25mbGljdCB0aGUgb2ZmaWNlciBtdXN0XG4gICAgICAgIC8vIHJlc29sdmUg4oCUIG5vdCBhIHZhbGlkYXRpb24gc2xpcC5cbiAgICAgICAgaWYgKGVycj8uc3RhdHVzID09PSA0MDkpIHtcbiAgICAgICAgICB0aGlzLmVycm9yS2V5LnNldChlcnIuZXJyb3I/Lm1lc3NhZ2VLZXkgfHwgJ2FhLmhlYXJpbmcuZXJyb3JfY29uZmxpY3QnKTtcbiAgICAgICAgICByZXR1cm47XG4gICAgICAgIH1cbiAgICAgICAgdGhpcy5lcnJvcktleS5zZXQoZXJyLmVycm9yPy5tZXNzYWdlS2V5IHx8IGVyci5lcnJvcj8ubWVzc2FnZSB8fCAnYWEuaGVhcmluZy5lcnJvcl9mYWlsZWQnKTtcbiAgICAgIH1cbiAgICB9KTtcbiAgfVxuXG4gIGNhbmNlbCgpIHtcbiAgICB0aGlzLmNhbmNlbGxlZC5lbWl0KCk7XG4gIH1cbn1cbiIsIjxkaXYgY2xhc3M9XCJoZWFyaW5nLXBhbmVsXCI+XG4gIDxoNT5cbiAgICBAaWYgKGlzUmVzY2hlZHVsZSkge1xuICAgICAge3sgJ2FhLmhlYXJpbmcucmVzY2hlZHVsZV90aXRsZScgfCB0cmFuc2xhdGUgfX1cbiAgICB9IEBlbHNlIHtcbiAgICAgIHt7ICdhYS5oZWFyaW5nLnRpdGxlJyB8IHRyYW5zbGF0ZSB9fVxuICAgIH1cbiAgPC9oNT5cblxuICBAaWYgKHN1Y2Nlc3NLZXkoKSkge1xuICAgIDxkaXYgY2xhc3M9XCJzdWNjZXNzLW1zZ1wiIHJvbGU9XCJzdGF0dXNcIiBhcmlhLWxpdmU9XCJwb2xpdGVcIiBkYXRhLXRlc3RpZD1cImhlYXJpbmctc3VjY2Vzc1wiPlxuICAgICAge3sgc3VjY2Vzc0tleSgpIHwgdHJhbnNsYXRlIH19XG4gICAgPC9kaXY+XG4gIH0gQGVsc2UgaWYgKHNob3dQcmV2aWV3KCkpIHtcbiAgICA8ZGl2IGNsYXNzPVwibm90aWNlLXByZXZpZXdcIiBkYXRhLXRlc3RpZD1cIm5vdGljZS1wcmV2aWV3XCI+XG4gICAgICA8ZGl2IGNsYXNzPVwibm90aWNlLWhlYWRlclwiPlxuICAgICAgICA8c3Ryb25nPnt7ICdhYS5oZWFyaW5nLm5vdGljZV9wcmV2aWV3JyB8IHRyYW5zbGF0ZSB9fTwvc3Ryb25nPlxuICAgICAgPC9kaXY+XG4gICAgICA8ZGl2IGNsYXNzPVwibm90aWNlLWJvZHlcIj5cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEuaGVhcmluZy5hcHBlYWxfbm8nIHwgdHJhbnNsYXRlIH19Ojwvc3Ryb25nPiB7eyBhcHBlYWw/LmFwcGVhbE51bWJlciB9fTwvcD5cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEuaGVhcmluZy5kYXRlJyB8IHRyYW5zbGF0ZSB9fTo8L3N0cm9uZz4ge3sgaGVhcmluZ0RhdGUgfCBkYXRlOidkZCBNTU1NIHl5eXknIH19PC9wPlxuICAgICAgICA8cD48c3Ryb25nPnt7ICdhYS5oZWFyaW5nLnRpbWUnIHwgdHJhbnNsYXRlIH19Ojwvc3Ryb25nPiB7eyBoZWFyaW5nVGltZSB9fTwvcD5cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEuaGVhcmluZy52ZW51ZScgfCB0cmFuc2xhdGUgfX06PC9zdHJvbmc+IHt7IGhlYXJpbmdWZW51ZSB9fTwvcD5cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEuaGVhcmluZy5tb2RlJyB8IHRyYW5zbGF0ZSB9fTo8L3N0cm9uZz4ge3sgaGVhcmluZ01vZGUgfX08L3A+XG4gICAgICAgIDxwPjxzdHJvbmc+e3sgJ2FhLmhlYXJpbmcucGFydGllc190b19ub3RpZnknIHwgdHJhbnNsYXRlIH19Ojwvc3Ryb25nPjwvcD5cbiAgICAgICAgPHVsIGRhdGEtdGVzdGlkPVwicHJldmlldy1wYXJ0aWVzXCI+XG4gICAgICAgICAgQGZvciAocGFydHkgb2YgcGFydGllc1RvTm90aWZ5OyB0cmFjayBwYXJ0eSkge1xuICAgICAgICAgICAgPGxpPnt7IHBhcnR5IHwgdGl0bGVjYXNlIH19PC9saT5cbiAgICAgICAgICB9XG4gICAgICAgIDwvdWw+XG4gICAgICAgIEBpZiAoaXNSZXNjaGVkdWxlICYmIHJlYXNvbikge1xuICAgICAgICAgIDxwPjxzdHJvbmc+e3sgJ2FhLmhlYXJpbmcucmVhc29uJyB8IHRyYW5zbGF0ZSB9fTo8L3N0cm9uZz4ge3sgcmVhc29uIH19PC9wPlxuICAgICAgICB9XG4gICAgICA8L2Rpdj5cblxuICAgICAgPCEtLSBTYXlzIFJFQ09SREVELCBub3Qgc2VudC4gVGhlcmUgaXMgbm8gZW1haWwgb3IgU01TIGdhdGV3YXkgaW4gdGhpcyBkZXBsb3ltZW50LCBhbmQgdGhlIHNlcnZlclxuICAgICAgICAgICByZXR1cm5zIG5vdGljZVN0YXR1cz1QRU5ESU5HIHRvIHNheSBleGFjdGx5IHRoYXQuIC0tPlxuICAgICAgPHAgY2xhc3M9XCJub3RpY2UtY2F2ZWF0XCIgZGF0YS10ZXN0aWQ9XCJub3RpY2UtY2F2ZWF0XCI+XG4gICAgICAgIHt7ICdhYS5oZWFyaW5nLm5vdGljZV9yZWNvcmRlZF9jYXZlYXQnIHwgdHJhbnNsYXRlIH19XG4gICAgICA8L3A+XG5cbiAgICAgIDxkaXYgY2xhc3M9XCJub3RpY2UtYWN0aW9uc1wiPlxuICAgICAgICA8YnV0dG9uIHR5cGU9XCJidXR0b25cIiBjbGFzcz1cInN1Ym1pdC1idG5cIiBkYXRhLXRlc3RpZD1cImNvbmZpcm0taGVhcmluZ1wiXG4gICAgICAgICAgICAgICAgW2Rpc2FibGVkXT1cInNjaGVkdWxpbmcoKVwiIFthdHRyLmFyaWEtYnVzeV09XCJzY2hlZHVsaW5nKClcIlxuICAgICAgICAgICAgICAgIChjbGljayk9XCJzY2hlZHVsZUhlYXJpbmcoKVwiPlxuICAgICAgICAgIEBpZiAoc2NoZWR1bGluZygpKSB7XG4gICAgICAgICAgICB7eyAnYWEuaGVhcmluZy5zY2hlZHVsaW5nJyB8IHRyYW5zbGF0ZSB9fVxuICAgICAgICAgIH0gQGVsc2Uge1xuICAgICAgICAgICAge3sgJ2FhLmhlYXJpbmcuY29uZmlybScgfCB0cmFuc2xhdGUgfX1cbiAgICAgICAgICB9XG4gICAgICAgIDwvYnV0dG9uPlxuICAgICAgICA8YnV0dG9uIHR5cGU9XCJidXR0b25cIiBjbGFzcz1cImNhbmNlbC1idG5cIiBkYXRhLXRlc3RpZD1cImVkaXQtaGVhcmluZ1wiXG4gICAgICAgICAgICAgICAgKGNsaWNrKT1cInNob3dQcmV2aWV3LnNldChmYWxzZSlcIj57eyAnYWEuaGVhcmluZy5lZGl0JyB8IHRyYW5zbGF0ZSB9fTwvYnV0dG9uPlxuICAgICAgPC9kaXY+XG4gICAgPC9kaXY+XG4gIH0gQGVsc2Uge1xuICAgIEBpZiAoaXNSZXNjaGVkdWxlKSB7XG4gICAgICA8ZGl2IGNsYXNzPVwid2FybmluZy1iYW5uZXJcIiBkYXRhLXRlc3RpZD1cInJlc2NoZWR1bGUtd2FybmluZ1wiPlxuICAgICAgICB7eyAnYWEuaGVhcmluZy5yZXNjaGVkdWxlX3dhcm5pbmcnIHwgdHJhbnNsYXRlIH19XG4gICAgICA8L2Rpdj5cbiAgICB9XG5cbiAgICA8ZGl2IGNsYXNzPVwiZm9ybS1maWVsZFwiPlxuICAgICAgPGxhYmVsIGZvcj1cImhlYXJpbmctZGF0ZVwiPnt7ICdhYS5oZWFyaW5nLmRhdGUnIHwgdHJhbnNsYXRlIH19PC9sYWJlbD5cbiAgICAgIDxpbnB1dCBpZD1cImhlYXJpbmctZGF0ZVwiIHR5cGU9XCJkYXRlXCIgZGF0YS10ZXN0aWQ9XCJoZWFyaW5nLWRhdGVcIiBbKG5nTW9kZWwpXT1cImhlYXJpbmdEYXRlXCIgLz5cbiAgICA8L2Rpdj5cblxuICAgIDxkaXYgY2xhc3M9XCJmb3JtLWZpZWxkXCI+XG4gICAgICA8bGFiZWwgZm9yPVwiaGVhcmluZy10aW1lXCI+e3sgJ2FhLmhlYXJpbmcudGltZScgfCB0cmFuc2xhdGUgfX08L2xhYmVsPlxuICAgICAgPGlucHV0IGlkPVwiaGVhcmluZy10aW1lXCIgdHlwZT1cInRpbWVcIiBkYXRhLXRlc3RpZD1cImhlYXJpbmctdGltZVwiIFsobmdNb2RlbCldPVwiaGVhcmluZ1RpbWVcIiAvPlxuICAgIDwvZGl2PlxuXG4gICAgPGRpdiBjbGFzcz1cImZvcm0tZmllbGRcIj5cbiAgICAgIDxsYWJlbCBmb3I9XCJoZWFyaW5nLXZlbnVlXCI+e3sgJ2FhLmhlYXJpbmcudmVudWUnIHwgdHJhbnNsYXRlIH19PC9sYWJlbD5cbiAgICAgIDxzZWxlY3QgaWQ9XCJoZWFyaW5nLXZlbnVlXCIgZGF0YS10ZXN0aWQ9XCJoZWFyaW5nLXZlbnVlXCIgWyhuZ01vZGVsKV09XCJoZWFyaW5nVmVudWVcIj5cbiAgICAgICAgPG9wdGlvbiB2YWx1ZT1cIlwiPnt7ICdhYS5oZWFyaW5nLnNlbGVjdF92ZW51ZScgfCB0cmFuc2xhdGUgfX08L29wdGlvbj5cbiAgICAgICAgQGZvciAodiBvZiB2ZW51ZU9wdGlvbnM7IHRyYWNrIHYudmFsdWUpIHtcbiAgICAgICAgICA8b3B0aW9uIFt2YWx1ZV09XCJ2LnZhbHVlXCI+e3sgdi5sYWJlbEtleSB8IHRyYW5zbGF0ZSB9fTwvb3B0aW9uPlxuICAgICAgICB9XG4gICAgICA8L3NlbGVjdD5cbiAgICA8L2Rpdj5cblxuICAgIDwhLS0gTW9kZSBpcyBzZXBhcmF0ZSBmcm9tIHZlbnVlOiB0aGUgc2VydmVyIG5vcm1hbGlzZXMgaXQgdG8gSU5fUEVSU09OL1ZJREVPL0hZQlJJRCwgc28gYSB2ZW51ZVxuICAgICAgICAgc3RyaW5nIG9mIFwiVmlydHVhbCAoVmlkZW8gQ29uZmVyZW5jZSlcIiBjb3VsZCBuZXZlciBoYXZlIHNldCBpdC4gLS0+XG4gICAgPGRpdiBjbGFzcz1cImZvcm0tZmllbGRcIj5cbiAgICAgIDxsYWJlbCBmb3I9XCJoZWFyaW5nLW1vZGVcIj57eyAnYWEuaGVhcmluZy5tb2RlJyB8IHRyYW5zbGF0ZSB9fTwvbGFiZWw+XG4gICAgICA8c2VsZWN0IGlkPVwiaGVhcmluZy1tb2RlXCIgZGF0YS10ZXN0aWQ9XCJoZWFyaW5nLW1vZGVcIiBbKG5nTW9kZWwpXT1cImhlYXJpbmdNb2RlXCI+XG4gICAgICAgIEBmb3IgKG0gb2YgbW9kZU9wdGlvbnM7IHRyYWNrIG0udmFsdWUpIHtcbiAgICAgICAgICA8b3B0aW9uIFt2YWx1ZV09XCJtLnZhbHVlXCI+e3sgbS5sYWJlbEtleSB8IHRyYW5zbGF0ZSB9fTwvb3B0aW9uPlxuICAgICAgICB9XG4gICAgICA8L3NlbGVjdD5cbiAgICA8L2Rpdj5cblxuICAgIDxmaWVsZHNldCBjbGFzcz1cImZvcm0tZmllbGRcIj5cbiAgICAgIDxsZWdlbmQ+e3sgJ2FhLmhlYXJpbmcucGFydGllc190b19ub3RpZnknIHwgdHJhbnNsYXRlIH19PC9sZWdlbmQ+XG4gICAgICA8ZGl2IGNsYXNzPVwiY2hlY2tib3gtZ3JvdXBcIj5cbiAgICAgICAgPGxhYmVsIGNsYXNzPVwiY2hlY2tib3gtaXRlbVwiIGZvcj1cInBhcnR5LWFwcGVsbGFudFwiXG4gICAgICAgICAgICAgICBbY2xhc3MuY2hlY2tlZF09XCJpc1BhcnR5U2VsZWN0ZWQoJ2FwcGVsbGFudCcpXCI+XG4gICAgICAgICAgPGlucHV0IGlkPVwicGFydHktYXBwZWxsYW50XCIgdHlwZT1cImNoZWNrYm94XCIgZGF0YS10ZXN0aWQ9XCJwYXJ0eS1hcHBlbGxhbnRcIlxuICAgICAgICAgICAgICAgICBbY2hlY2tlZF09XCJpc1BhcnR5U2VsZWN0ZWQoJ2FwcGVsbGFudCcpXCIgKGNoYW5nZSk9XCJ0b2dnbGVQYXJ0eSgnYXBwZWxsYW50JylcIj5cbiAgICAgICAgICB7eyAnYWEuaGVhcmluZy5wYXJ0eV9hcHBlbGxhbnQnIHwgdHJhbnNsYXRlIH19XG4gICAgICAgIDwvbGFiZWw+XG4gICAgICAgIDxsYWJlbCBjbGFzcz1cImNoZWNrYm94LWl0ZW1cIiBmb3I9XCJwYXJ0eS1yZXNwb25kZW50XCJcbiAgICAgICAgICAgICAgIFtjbGFzcy5jaGVja2VkXT1cImlzUGFydHlTZWxlY3RlZCgncmVzcG9uZGVudCcpXCI+XG4gICAgICAgICAgPGlucHV0IGlkPVwicGFydHktcmVzcG9uZGVudFwiIHR5cGU9XCJjaGVja2JveFwiIGRhdGEtdGVzdGlkPVwicGFydHktcmVzcG9uZGVudFwiXG4gICAgICAgICAgICAgICAgIFtjaGVja2VkXT1cImlzUGFydHlTZWxlY3RlZCgncmVzcG9uZGVudCcpXCIgKGNoYW5nZSk9XCJ0b2dnbGVQYXJ0eSgncmVzcG9uZGVudCcpXCI+XG4gICAgICAgICAge3sgJ2FhLmhlYXJpbmcucGFydHlfcmVzcG9uZGVudCcgfCB0cmFuc2xhdGUgfX1cbiAgICAgICAgPC9sYWJlbD5cbiAgICAgICAgPGxhYmVsIGNsYXNzPVwiY2hlY2tib3gtaXRlbVwiIGZvcj1cInBhcnR5LW9tYnVkc21hblwiXG4gICAgICAgICAgICAgICBbY2xhc3MuY2hlY2tlZF09XCJpc1BhcnR5U2VsZWN0ZWQoJ29tYnVkc21hbicpXCI+XG4gICAgICAgICAgPGlucHV0IGlkPVwicGFydHktb21idWRzbWFuXCIgdHlwZT1cImNoZWNrYm94XCIgZGF0YS10ZXN0aWQ9XCJwYXJ0eS1vbWJ1ZHNtYW5cIlxuICAgICAgICAgICAgICAgICBbY2hlY2tlZF09XCJpc1BhcnR5U2VsZWN0ZWQoJ29tYnVkc21hbicpXCIgKGNoYW5nZSk9XCJ0b2dnbGVQYXJ0eSgnb21idWRzbWFuJylcIj5cbiAgICAgICAgICB7eyAnYWEuaGVhcmluZy5wYXJ0eV9vbWJ1ZHNtYW4nIHwgdHJhbnNsYXRlIH19XG4gICAgICAgIDwvbGFiZWw+XG4gICAgICA8L2Rpdj5cbiAgICA8L2ZpZWxkc2V0PlxuXG4gICAgQGlmIChpc1Jlc2NoZWR1bGUpIHtcbiAgICAgIDxkaXYgY2xhc3M9XCJmb3JtLWZpZWxkXCI+XG4gICAgICAgIDxsYWJlbCBmb3I9XCJoZWFyaW5nLXJlYXNvblwiPnt7ICdhYS5oZWFyaW5nLnJlYXNvbicgfCB0cmFuc2xhdGUgfX08L2xhYmVsPlxuICAgICAgICA8dGV4dGFyZWEgaWQ9XCJoZWFyaW5nLXJlYXNvblwiIHJvd3M9XCIzXCIgZGF0YS10ZXN0aWQ9XCJoZWFyaW5nLXJlYXNvblwiXG4gICAgICAgICAgICAgICAgICBbKG5nTW9kZWwpXT1cInJlYXNvblwiXG4gICAgICAgICAgICAgICAgICBbYXR0ci5wbGFjZWhvbGRlcl09XCInYWEuaGVhcmluZy5yZWFzb25fcGxhY2Vob2xkZXInIHwgdHJhbnNsYXRlXCI+PC90ZXh0YXJlYT5cbiAgICAgIDwvZGl2PlxuICAgIH1cblxuICAgIEBpZiAoZXJyb3JLZXkoKSkge1xuICAgICAgPGRpdiBjbGFzcz1cImVycm9yLW1zZ1wiIHJvbGU9XCJhbGVydFwiIGFyaWEtbGl2ZT1cImFzc2VydGl2ZVwiIGRhdGEtdGVzdGlkPVwiaGVhcmluZy1lcnJvclwiPlxuICAgICAgICB7eyBlcnJvcktleSgpIHwgdHJhbnNsYXRlIH19XG4gICAgICA8L2Rpdj5cbiAgICB9XG5cbiAgICA8ZGl2IGNsYXNzPVwiZm9ybS1hY3Rpb25zXCI+XG4gICAgICA8YnV0dG9uIHR5cGU9XCJidXR0b25cIiBjbGFzcz1cInByZXZpZXctYnRuXCIgZGF0YS10ZXN0aWQ9XCJwcmV2aWV3LW5vdGljZVwiXG4gICAgICAgICAgICAgIChjbGljayk9XCJwcmV2aWV3Tm90aWNlKClcIj57eyAnYWEuaGVhcmluZy5wcmV2aWV3X2FjdGlvbicgfCB0cmFuc2xhdGUgfX08L2J1dHRvbj5cbiAgICAgIDxidXR0b24gdHlwZT1cImJ1dHRvblwiIGNsYXNzPVwiY2FuY2VsLWJ0blwiIGRhdGEtdGVzdGlkPVwiY2FuY2VsLWhlYXJpbmdcIlxuICAgICAgICAgICAgICAoY2xpY2spPVwiY2FuY2VsKClcIj57eyAnYWEuaGVhcmluZy5jYW5jZWwnIHwgdHJhbnNsYXRlIH19PC9idXR0b24+XG4gICAgPC9kaXY+XG4gIH1cblxuICA8IS0tIFJlYWwgaGVhcmluZyBoaXN0b3J5IGZyb20gdGhlIGhlYXJpbmdzIGVuZHBvaW50LiBBIHJlc2NoZWR1bGUgYXBwZW5kcyByYXRoZXIgdGhhbiBvdmVyd3JpdGVzLCBzb1xuICAgICAgIGEgdmFjYXRlZCBzaXR0aW5nIHN0YXlzIHZpc2libGUg4oCUIHRoZSBwcmV2aW91cyBiaW5kaW5nIHdhcyB0byBhIGZpZWxkIG5vIGVuZHBvaW50IHJldHVybmVkLCBzb1xuICAgICAgIHRoaXMgdGFibGUgd2FzIHBlcm1hbmVudGx5IGVtcHR5LiAtLT5cbiAgPGRpdiBjbGFzcz1cInBhc3QtaGVhcmluZ3NcIj5cbiAgICA8aDY+e3sgJ2FhLmhlYXJpbmcuaGlzdG9yeScgfCB0cmFuc2xhdGUgfX08L2g2PlxuICAgIEBpZiAoaGlzdG9yeUxvYWRpbmcoKSkge1xuICAgICAgPHAgY2xhc3M9XCJsb2FkaW5nXCIgZGF0YS10ZXN0aWQ9XCJoaXN0b3J5LWxvYWRpbmdcIj5cbiAgICAgICAgPGkgY2xhc3M9XCJwaSBwaS1zcGluIHBpLXNwaW5uZXJcIiBhcmlhLWhpZGRlbj1cInRydWVcIj48L2k+XG4gICAgICA8L3A+XG4gICAgfSBAZWxzZSBpZiAoaGlzdG9yeSgpLmxlbmd0aCA9PT0gMCkge1xuICAgICAgPHAgY2xhc3M9XCJlbXB0eVwiIGRhdGEtdGVzdGlkPVwiaGlzdG9yeS1lbXB0eVwiPnt7ICdhYS5oZWFyaW5nLmhpc3RvcnlfZW1wdHknIHwgdHJhbnNsYXRlIH19PC9wPlxuICAgIH0gQGVsc2Uge1xuICAgICAgPHRhYmxlIGNsYXNzPVwiaGlzdG9yeS10YWJsZVwiIGRhdGEtdGVzdGlkPVwiaGVhcmluZy1oaXN0b3J5XCI+XG4gICAgICAgIDx0aGVhZD5cbiAgICAgICAgICA8dHI+XG4gICAgICAgICAgICA8dGg+e3sgJ2FhLmhlYXJpbmcuZGF0ZScgfCB0cmFuc2xhdGUgfX08L3RoPlxuICAgICAgICAgICAgPHRoPnt7ICdhYS5oZWFyaW5nLnZlbnVlJyB8IHRyYW5zbGF0ZSB9fTwvdGg+XG4gICAgICAgICAgICA8dGg+e3sgJ2FhLmhlYXJpbmcuZXZlbnQnIHwgdHJhbnNsYXRlIH19PC90aD5cbiAgICAgICAgICAgIDx0aD57eyAnYWEuaGVhcmluZy5vdXRjb21lJyB8IHRyYW5zbGF0ZSB9fTwvdGg+XG4gICAgICAgICAgPC90cj5cbiAgICAgICAgPC90aGVhZD5cbiAgICAgICAgPHRib2R5PlxuICAgICAgICAgIDwhLS0gdHJhY2sgaC5pZCwgbm90IGguZGF0ZTogYSByZXNjaGVkdWxlIHRvIHRoZSBzYW1lIGRhdGUgd291bGQgY29sbGlkZSBvbiBhIGRhdGUga2V5LiAtLT5cbiAgICAgICAgICBAZm9yIChoIG9mIGhpc3RvcnkoKTsgdHJhY2sgaC5pZCkge1xuICAgICAgICAgICAgPHRyIFtjbGFzcy5zdXBlcnNlZGVkXT1cImguc3VwZXJzZWRlZFwiIFthdHRyLmRhdGEtdGVzdGlkXT1cIidoZWFyaW5nLXJvdy0nICsgaC5pZFwiPlxuICAgICAgICAgICAgICA8dGQ+e3sgaC5kYXRlIHwgZGF0ZTonZGQgTU1NIHl5eXksIEhIOm1tJyB9fTwvdGQ+XG4gICAgICAgICAgICAgIDx0ZD57eyBoLnZlbnVlIHx8ICfigJQnIH19PC90ZD5cbiAgICAgICAgICAgICAgPHRkPnt7IGguZXZlbnRUeXBlIH19PC90ZD5cbiAgICAgICAgICAgICAgPHRkPnt7IGgub3V0Y29tZSB8fCAn4oCUJyB9fTwvdGQ+XG4gICAgICAgICAgICA8L3RyPlxuICAgICAgICAgIH1cbiAgICAgICAgPC90Ym9keT5cbiAgICAgIDwvdGFibGU+XG4gICAgfVxuICA8L2Rpdj5cbjwvZGl2PlxuIiwiaW1wb3J0IHsgQ29tcG9uZW50LCBJbnB1dCwgT3V0cHV0LCBFdmVudEVtaXR0ZXIsIGluamVjdCwgc2lnbmFsIH0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBDb21tb25Nb2R1bGUgfSBmcm9tICdAYW5ndWxhci9jb21tb24nO1xuaW1wb3J0IHsgRm9ybXNNb2R1bGUgfSBmcm9tICdAYW5ndWxhci9mb3Jtcyc7XG5pbXBvcnQgeyBIdHRwQ2xpZW50IH0gZnJvbSAnQGFuZ3VsYXIvY29tbW9uL2h0dHAnO1xuaW1wb3J0IHsgZW52aXJvbm1lbnQgfSBmcm9tICcuLi8uLi8uLi8uLi9lbnZpcm9ubWVudHMvZW52aXJvbm1lbnQnO1xuaW1wb3J0IHsgVHJhbnNsYXRlUGlwZSB9IGZyb20gJy4uLy4uLy4uL3BpcGVzL3RyYW5zbGF0ZS5waXBlJztcblxudHlwZSBPcmRlck91dGNvbWUgPSAnVVBIRUxEJyB8ICdNT0RJRklFRCcgfCAnU0VUX0FTSURFJyB8ICdSRU1BTkRFRCcgfCAnRElTTUlTU0VEJztcblxuLyoqXG4gKiBJc3N1ZXMgdGhlIGZpbmFsIG9yZGVyIG9uIGFuIGFwcGVhbC5cbiAqXG4gKiBQb3N0cyB0byB0aGUgZGVkaWNhdGVkIG9yZGVyIGVuZHBvaW50IHJhdGhlciB0aGFuIHRoZSBnZW5lcmljIC9hY3Rpb24gcm91dGUuIFRoYXQgbWF0dGVycyBmb3IgbW9yZVxuICogdGhhbiB0aWRpbmVzczogdGhlIG9yZGVyIGVuZHBvaW50IHZhbGlkYXRlcyB0aGUgb3V0Y29tZSBhZ2FpbnN0IHRoZSBmaXZlIHBlcm1pdHRlZCB2YWx1ZXMsIHBlcnNpc3RzIGFuXG4gKiBpbW11dGFibGUgb3JkZXIgcmVjb3JkIHdpdGggYSByZXZpc2lvbiBudW1iZXIsIGFuZCByZXR1cm5zIHRoZSBzdG9yZWQgb3JkZXIg4oCUIHdoZXJlYXMgL2FjdGlvbiBhY2NlcHRlZFxuICogYW55IHN0cmluZyBhcyBhbiBvdXRjb21lIGFuZCBzdG9yZWQgaXQgdW52YWxpZGF0ZWQuXG4gKlxuICogVGhlIHByZXZpb3VzIHZlcnNpb24gc2VudCBgb3V0Y29tZWAvYG1vZGlmaWVkQW1vdW50YCB3aGlsZSAvYWN0aW9uIHJlYWQgYG9yZGVyT3V0Y29tZWAvXG4gKiBgYXdhcmRNb2RpZmllZEFtb3VudGAsIHNvIGV2ZXJ5IHN1Ym1pc3Npb24gd2FzIHJlamVjdGVkLiBXb3JzZSwgdGhlIHJlamVjdGlvbiBhcnJpdmVkIGFzIEhUVFAgMjAwIHdpdGhcbiAqIHN1Y2Nlc3M6ZmFsc2UsIHdoaWNoIEFuZ3VsYXIgZG9lcyBub3QgdHJlYXQgYXMgYW4gZXJyb3Ig4oCUIHNvIHRoaXMgc2NyZWVuIHJlcG9ydGVkIFwiT3JkZXIgcGFzc2VkXG4gKiBzdWNjZXNzZnVsbHlcIiBmb3Igb3JkZXJzIHRoZSBzZXJ2ZXIgaGFkIHRocm93biBhd2F5LiBCb3RoIGFyZSBmaXhlZCBoZXJlLlxuICovXG5AQ29tcG9uZW50KHtcbiAgc2VsZWN0b3I6ICdhcHAtYWEtb3JkZXInLFxuICBzdGFuZGFsb25lOiB0cnVlLFxuICBpbXBvcnRzOiBbQ29tbW9uTW9kdWxlLCBGb3Jtc01vZHVsZSwgVHJhbnNsYXRlUGlwZV0sXG4gIHRlbXBsYXRlVXJsOiAnLi9hYS1vcmRlci5jb21wb25lbnQuaHRtbCcsXG4gIHN0eWxlVXJsOiAnLi9hYS1vcmRlci5jb21wb25lbnQuc2Nzcydcbn0pXG5leHBvcnQgY2xhc3MgQWFPcmRlckNvbXBvbmVudCB7XG4gIEBJbnB1dCgpIGFwcGVhbDogYW55O1xuICBAT3V0cHV0KCkgb3JkZXJQYXNzZWQgPSBuZXcgRXZlbnRFbWl0dGVyPHZvaWQ+KCk7XG4gIEBPdXRwdXQoKSBjYW5jZWxsZWQgPSBuZXcgRXZlbnRFbWl0dGVyPHZvaWQ+KCk7XG5cbiAgcHJpdmF0ZSBodHRwID0gaW5qZWN0KEh0dHBDbGllbnQpO1xuXG4gIHN1Ym1pdHRpbmcgPSBzaWduYWwoZmFsc2UpO1xuICBlcnJvcktleSA9IHNpZ25hbCgnJyk7XG4gIHN1Y2Nlc3NLZXkgPSBzaWduYWwoJycpO1xuICBzaG93UHJldmlldyA9IHNpZ25hbChmYWxzZSk7XG5cbiAgb3V0Y29tZTogT3JkZXJPdXRjb21lIHwgJycgPSAnJztcbiAgYXdhcmRBbW91bnQ6IG51bWJlciB8IG51bGwgPSBudWxsO1xuICBvcmRlclN1bW1hcnkgPSAnJztcblxuICAvKiogTGFiZWxzIGFyZSB0cmFuc2xhdGlvbiBrZXlzOyB0aGUgdmFsdWVzIGFyZSB0aGUgc2VydmVyJ3Mgdm9jYWJ1bGFyeSBhbmQgbXVzdCBub3QgYmUgbG9jYWxpc2VkLiAqL1xuICBvdXRjb21lczogeyB2YWx1ZTogT3JkZXJPdXRjb21lOyBsYWJlbEtleTogc3RyaW5nOyBkZXNjcmlwdGlvbktleTogc3RyaW5nIH1bXSA9IFtcbiAgICB7IHZhbHVlOiAnVVBIRUxEJywgbGFiZWxLZXk6ICdhYS5vcmRlci5vdXRjb21lX3VwaGVsZCcsIGRlc2NyaXB0aW9uS2V5OiAnYWEub3JkZXIub3V0Y29tZV91cGhlbGRfZGVzYycgfSxcbiAgICB7IHZhbHVlOiAnTU9ESUZJRUQnLCBsYWJlbEtleTogJ2FhLm9yZGVyLm91dGNvbWVfbW9kaWZpZWQnLCBkZXNjcmlwdGlvbktleTogJ2FhLm9yZGVyLm91dGNvbWVfbW9kaWZpZWRfZGVzYycgfSxcbiAgICB7IHZhbHVlOiAnU0VUX0FTSURFJywgbGFiZWxLZXk6ICdhYS5vcmRlci5vdXRjb21lX3NldF9hc2lkZScsIGRlc2NyaXB0aW9uS2V5OiAnYWEub3JkZXIub3V0Y29tZV9zZXRfYXNpZGVfZGVzYycgfSxcbiAgICB7IHZhbHVlOiAnUkVNQU5ERUQnLCBsYWJlbEtleTogJ2FhLm9yZGVyLm91dGNvbWVfcmVtYW5kZWQnLCBkZXNjcmlwdGlvbktleTogJ2FhLm9yZGVyLm91dGNvbWVfcmVtYW5kZWRfZGVzYycgfSxcbiAgICB7IHZhbHVlOiAnRElTTUlTU0VEJywgbGFiZWxLZXk6ICdhYS5vcmRlci5vdXRjb21lX2Rpc21pc3NlZCcsIGRlc2NyaXB0aW9uS2V5OiAnYWEub3JkZXIub3V0Y29tZV9kaXNtaXNzZWRfZGVzYycgfSxcbiAgXTtcblxuICAvKipcbiAgICogT3V0Y29tZXMgdGhhdCBjYW4gY2FycnkgYSBtb25ldGFyeSBhd2FyZC5cbiAgICpcbiAgICogTWlycm9ycyB0aGUgc2VydmVyJ3Mgb3duIHJ1bGU6IGFuIGF3YXJkIHN1Ym1pdHRlZCB3aXRoIGFueSBvdGhlciBvdXRjb21lIGlzIHNpbGVudGx5IGRyb3BwZWQsIHNvXG4gICAqIG9mZmVyaW5nIHRoZSBmaWVsZCB3b3VsZCBpbnZpdGUgYW4gb2ZmaWNlciB0byBlbnRlciBhIGZpZ3VyZSB0aGF0IG5ldmVyIGdldHMgc3RvcmVkLlxuICAgKi9cbiAgZ2V0IGF3YXJkQmVhcmluZygpOiBib29sZWFuIHtcbiAgICByZXR1cm4gdGhpcy5vdXRjb21lID09PSAnTU9ESUZJRUQnIHx8IHRoaXMub3V0Y29tZSA9PT0gJ1VQSEVMRCc7XG4gIH1cblxuICBwcmV2aWV3T3JkZXIoKSB7XG4gICAgdGhpcy5lcnJvcktleS5zZXQoJycpO1xuICAgIGlmICghdGhpcy5vdXRjb21lKSB7XG4gICAgICB0aGlzLmVycm9yS2V5LnNldCgnYWEub3JkZXIuZXJyb3Jfb3V0Y29tZV9yZXF1aXJlZCcpO1xuICAgICAgcmV0dXJuO1xuICAgIH1cbiAgICBpZiAoIXRoaXMub3JkZXJTdW1tYXJ5LnRyaW0oKSkge1xuICAgICAgdGhpcy5lcnJvcktleS5zZXQoJ2FhLm9yZGVyLmVycm9yX3N1bW1hcnlfcmVxdWlyZWQnKTtcbiAgICAgIHJldHVybjtcbiAgICB9XG4gICAgaWYgKHRoaXMuYXdhcmRCZWFyaW5nICYmIHRoaXMuYXdhcmRBbW91bnQgIT09IG51bGwgJiYgdGhpcy5hd2FyZEFtb3VudCA8IDApIHtcbiAgICAgIHRoaXMuZXJyb3JLZXkuc2V0KCdhYS5vcmRlci5lcnJvcl9hbW91bnRfaW52YWxpZCcpO1xuICAgICAgcmV0dXJuO1xuICAgIH1cbiAgICB0aGlzLnNob3dQcmV2aWV3LnNldCh0cnVlKTtcbiAgfVxuXG4gIHN1Ym1pdE9yZGVyKCkge1xuICAgIHRoaXMuZXJyb3JLZXkuc2V0KCcnKTtcbiAgICB0aGlzLnN1Ym1pdHRpbmcuc2V0KHRydWUpO1xuXG4gICAgY29uc3QgYXBwZWFsTnVtYmVyID0gdGhpcy5hcHBlYWw/LmFwcGVhbE51bWJlcjtcbiAgICBjb25zdCBib2R5OiBSZWNvcmQ8c3RyaW5nLCB1bmtub3duPiA9IHtcbiAgICAgIG91dGNvbWU6IHRoaXMub3V0Y29tZSxcbiAgICAgIG9yZGVyU3VtbWFyeTogdGhpcy5vcmRlclN1bW1hcnksXG4gICAgfTtcbiAgICAvLyBPbmx5IHNlbnQgd2hlbiB0aGUgb3V0Y29tZSBjYW4gYWN0dWFsbHkgY2Fycnkgb25lLlxuICAgIGlmICh0aGlzLmF3YXJkQmVhcmluZyAmJiB0aGlzLmF3YXJkQW1vdW50ICE9PSBudWxsKSB7XG4gICAgICBib2R5Wydhd2FyZEFtb3VudCddID0gdGhpcy5hd2FyZEFtb3VudDtcbiAgICB9XG5cbiAgICB0aGlzLmh0dHAucG9zdDxhbnk+KFxuICAgICAgYCR7ZW52aXJvbm1lbnQuYXBpQmFzZVVybH0vYXBpL3YxL2FwcGVhbHMvJHthcHBlYWxOdW1iZXJ9L29yZGVyYCxcbiAgICAgIGJvZHlcbiAgICApLnN1YnNjcmliZSh7XG4gICAgICBuZXh0OiAocmVzKSA9PiB7XG4gICAgICAgIHRoaXMuc3VibWl0dGluZy5zZXQoZmFsc2UpO1xuXG4gICAgICAgIC8vIEEgcmVmdXNlZCB3cml0ZSBhcnJpdmVzIGFzIDIwMCB3aXRoIHN1Y2Nlc3M6ZmFsc2UuIFJlcG9ydGluZyBpdCBhcyBzdWNjZXNzIGlzIGhvdyBhIHBhc3NlZFxuICAgICAgICAvLyBvcmRlciB0aGF0IHdhcyBuZXZlciBzdG9yZWQgbG9va2VkIGxpa2UgYSBjb21wbGV0ZWQgb25lLlxuICAgICAgICBpZiAocmVzPy5zdWNjZXNzID09PSBmYWxzZSkge1xuICAgICAgICAgIHRoaXMuZXJyb3JLZXkuc2V0KHJlcy5tZXNzYWdlS2V5IHx8ICdhYS5vcmRlci5lcnJvcl9mYWlsZWQnKTtcbiAgICAgICAgICB0aGlzLnNob3dQcmV2aWV3LnNldChmYWxzZSk7XG4gICAgICAgICAgcmV0dXJuO1xuICAgICAgICB9XG5cbiAgICAgICAgdGhpcy5zdWNjZXNzS2V5LnNldChyZXM/Lm1lc3NhZ2VLZXkgfHwgJ2FhLm9yZGVyLnBhc3NlZCcpO1xuICAgICAgICBzZXRUaW1lb3V0KCgpID0+IHRoaXMub3JkZXJQYXNzZWQuZW1pdCgpLCAxMjAwKTtcbiAgICAgIH0sXG4gICAgICBlcnJvcjogKGVycikgPT4ge1xuICAgICAgICB0aGlzLnN1Ym1pdHRpbmcuc2V0KGZhbHNlKTtcbiAgICAgICAgdGhpcy5zaG93UHJldmlldy5zZXQoZmFsc2UpO1xuICAgICAgICB0aGlzLmVycm9yS2V5LnNldChlcnIuZXJyb3I/Lm1lc3NhZ2VLZXkgfHwgZXJyLmVycm9yPy5tZXNzYWdlIHx8ICdhYS5vcmRlci5lcnJvcl9mYWlsZWQnKTtcbiAgICAgIH1cbiAgICB9KTtcbiAgfVxuXG4gIGNhbmNlbCgpIHtcbiAgICB0aGlzLmNhbmNlbGxlZC5lbWl0KCk7XG4gIH1cbn1cbiIsIjxkaXYgY2xhc3M9XCJvcmRlci1wYW5lbFwiPlxuICA8aDU+e3sgJ2FhLm9yZGVyLnRpdGxlJyB8IHRyYW5zbGF0ZSB9fTwvaDU+XG5cbiAgQGlmIChzdWNjZXNzS2V5KCkpIHtcbiAgICA8ZGl2IGNsYXNzPVwic3VjY2Vzcy1tc2dcIiByb2xlPVwic3RhdHVzXCIgYXJpYS1saXZlPVwicG9saXRlXCIgZGF0YS10ZXN0aWQ9XCJvcmRlci1zdWNjZXNzXCI+XG4gICAgICB7eyBzdWNjZXNzS2V5KCkgfCB0cmFuc2xhdGUgfX1cbiAgICA8L2Rpdj5cbiAgfSBAZWxzZSBpZiAoc2hvd1ByZXZpZXcoKSkge1xuICAgIDwhLS0gUHJldmlldyBiZWZvcmUgYW4gaXJyZXZlcnNpYmxlIGFjdDogYW4gaXNzdWVkIG9yZGVyIGNhbm5vdCBiZSBlZGl0ZWQsIG9ubHkgc3VwZXJzZWRlZCBieSBhXG4gICAgICAgICBsaW5rZWQgY29ycmVjdGlvbiwgc28gdGhlIG9mZmljZXIgY29uZmlybXMgdGhlIGV4YWN0IHRleHQgZmlyc3QuIC0tPlxuICAgIDxkaXYgY2xhc3M9XCJvcmRlci1wcmV2aWV3XCIgZGF0YS10ZXN0aWQ9XCJvcmRlci1wcmV2aWV3XCI+XG4gICAgICA8ZGl2IGNsYXNzPVwicHJldmlldy1oZWFkZXJcIj5cbiAgICAgICAgPHN0cm9uZz57eyAnYWEub3JkZXIucHJldmlldycgfCB0cmFuc2xhdGUgfX08L3N0cm9uZz5cbiAgICAgIDwvZGl2PlxuICAgICAgPGRpdiBjbGFzcz1cInByZXZpZXctYm9keVwiPlxuICAgICAgICA8cD48c3Ryb25nPnt7ICdhYS5vcmRlci5hcHBlYWxfbm8nIHwgdHJhbnNsYXRlIH19Ojwvc3Ryb25nPiB7eyBhcHBlYWw/LmFwcGVhbE51bWJlciB9fTwvcD5cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEub3JkZXIuY2xhc3NpZmljYXRpb24nIHwgdHJhbnNsYXRlIH19Ojwvc3Ryb25nPiB7eyBhcHBlYWw/LmNsYXNzaWZpY2F0aW9uIH19PC9wPlxuICAgICAgICA8cD48c3Ryb25nPnt7ICdhYS5vcmRlci5vdXRjb21lJyB8IHRyYW5zbGF0ZSB9fTo8L3N0cm9uZz5cbiAgICAgICAgICA8c3BhbiBjbGFzcz1cIm91dGNvbWUtYmFkZ2VcIiBbYXR0ci5kYXRhLW91dGNvbWVdPVwib3V0Y29tZVwiIGRhdGEtdGVzdGlkPVwicHJldmlldy1vdXRjb21lXCI+XG4gICAgICAgICAgICB7eyBvdXRjb21lIH19XG4gICAgICAgICAgPC9zcGFuPlxuICAgICAgICA8L3A+XG4gICAgICAgIEBpZiAoYXdhcmRCZWFyaW5nICYmIGF3YXJkQW1vdW50ICE9PSBudWxsKSB7XG4gICAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEub3JkZXIuYXdhcmRfYW1vdW50JyB8IHRyYW5zbGF0ZSB9fTo8L3N0cm9uZz5cbiAgICAgICAgICAgIDxzcGFuIGRhdGEtdGVzdGlkPVwicHJldmlldy1hd2FyZFwiPiYjODM3Nzt7eyBhd2FyZEFtb3VudCB9fTwvc3Bhbj5cbiAgICAgICAgICA8L3A+XG4gICAgICAgIH1cbiAgICAgICAgPHA+PHN0cm9uZz57eyAnYWEub3JkZXIuc3VtbWFyeScgfCB0cmFuc2xhdGUgfX06PC9zdHJvbmc+PC9wPlxuICAgICAgICA8ZGl2IGNsYXNzPVwicHJldmlldy1zdW1tYXJ5XCIgZGF0YS10ZXN0aWQ9XCJwcmV2aWV3LXN1bW1hcnlcIj57eyBvcmRlclN1bW1hcnkgfX08L2Rpdj5cbiAgICAgIDwvZGl2PlxuICAgICAgPGRpdiBjbGFzcz1cInByZXZpZXctYWN0aW9uc1wiPlxuICAgICAgICA8YnV0dG9uIHR5cGU9XCJidXR0b25cIiBjbGFzcz1cInN1Ym1pdC1idG5cIiBkYXRhLXRlc3RpZD1cImNvbmZpcm0tb3JkZXJcIlxuICAgICAgICAgICAgICAgIFtkaXNhYmxlZF09XCJzdWJtaXR0aW5nKClcIiBbYXR0ci5hcmlhLWJ1c3ldPVwic3VibWl0dGluZygpXCJcbiAgICAgICAgICAgICAgICAoY2xpY2spPVwic3VibWl0T3JkZXIoKVwiPlxuICAgICAgICAgIEBpZiAoc3VibWl0dGluZygpKSB7XG4gICAgICAgICAgICB7eyAnYWEub3JkZXIuc3VibWl0dGluZycgfCB0cmFuc2xhdGUgfX1cbiAgICAgICAgICB9IEBlbHNlIHtcbiAgICAgICAgICAgIHt7ICdhYS5vcmRlci5jb25maXJtJyB8IHRyYW5zbGF0ZSB9fVxuICAgICAgICAgIH1cbiAgICAgICAgPC9idXR0b24+XG4gICAgICAgIDxidXR0b24gdHlwZT1cImJ1dHRvblwiIGNsYXNzPVwiY2FuY2VsLWJ0blwiIGRhdGEtdGVzdGlkPVwiZWRpdC1vcmRlclwiXG4gICAgICAgICAgICAgICAgKGNsaWNrKT1cInNob3dQcmV2aWV3LnNldChmYWxzZSlcIj57eyAnYWEub3JkZXIuZWRpdCcgfCB0cmFuc2xhdGUgfX08L2J1dHRvbj5cbiAgICAgIDwvZGl2PlxuICAgIDwvZGl2PlxuICB9IEBlbHNlIHtcbiAgICA8ZmllbGRzZXQgY2xhc3M9XCJmb3JtLWZpZWxkXCI+XG4gICAgICA8bGVnZW5kPnt7ICdhYS5vcmRlci5vdXRjb21lJyB8IHRyYW5zbGF0ZSB9fTwvbGVnZW5kPlxuICAgICAgPGRpdiBjbGFzcz1cIm91dGNvbWUtb3B0aW9uc1wiIHJvbGU9XCJyYWRpb2dyb3VwXCJcbiAgICAgICAgICAgW2F0dHIuYXJpYS1sYWJlbF09XCInYWEub3JkZXIub3V0Y29tZScgfCB0cmFuc2xhdGVcIj5cbiAgICAgICAgQGZvciAobyBvZiBvdXRjb21lczsgdHJhY2sgby52YWx1ZSkge1xuICAgICAgICAgIDxsYWJlbCBjbGFzcz1cIm91dGNvbWUtb3B0aW9uXCIgW2NsYXNzLnNlbGVjdGVkXT1cIm91dGNvbWUgPT09IG8udmFsdWVcIlxuICAgICAgICAgICAgICAgICBbYXR0ci5mb3JdPVwiJ291dGNvbWUtJyArIG8udmFsdWVcIj5cbiAgICAgICAgICAgIDxpbnB1dCB0eXBlPVwicmFkaW9cIiBuYW1lPVwib3V0Y29tZVwiIFtpZF09XCInb3V0Y29tZS0nICsgby52YWx1ZVwiXG4gICAgICAgICAgICAgICAgICAgW2F0dHIuZGF0YS10ZXN0aWRdPVwiJ291dGNvbWUtJyArIG8udmFsdWVcIlxuICAgICAgICAgICAgICAgICAgIFt2YWx1ZV09XCJvLnZhbHVlXCIgWyhuZ01vZGVsKV09XCJvdXRjb21lXCI+XG4gICAgICAgICAgICA8ZGl2IGNsYXNzPVwib3V0Y29tZS1pbmZvXCI+XG4gICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwib3V0Y29tZS1sYWJlbFwiPnt7IG8ubGFiZWxLZXkgfCB0cmFuc2xhdGUgfX08L3NwYW4+XG4gICAgICAgICAgICAgIDxzcGFuIGNsYXNzPVwib3V0Y29tZS1kZXNjXCI+e3sgby5kZXNjcmlwdGlvbktleSB8IHRyYW5zbGF0ZSB9fTwvc3Bhbj5cbiAgICAgICAgICAgIDwvZGl2PlxuICAgICAgICAgIDwvbGFiZWw+XG4gICAgICAgIH1cbiAgICAgIDwvZGl2PlxuICAgIDwvZmllbGRzZXQ+XG5cbiAgICA8IS0tIFNob3duIGZvciBVUEhFTEQgYXMgd2VsbCBhcyBNT0RJRklFRDogdGhlIHNlcnZlciBhY2NlcHRzIGFuIGF3YXJkIG9uIGJvdGgsIGFuZCBoaWRpbmcgaXQgZm9yXG4gICAgICAgICBVUEhFTEQgd291bGQgbWFrZSBhIGxlZ2l0aW1hdGUgYXdhcmQgaW1wb3NzaWJsZSB0byBlbnRlci4gLS0+XG4gICAgQGlmIChhd2FyZEJlYXJpbmcpIHtcbiAgICAgIDxkaXYgY2xhc3M9XCJmb3JtLWZpZWxkXCI+XG4gICAgICAgIDxsYWJlbCBmb3I9XCJhd2FyZC1hbW91bnRcIj57eyAnYWEub3JkZXIuYXdhcmRfYW1vdW50JyB8IHRyYW5zbGF0ZSB9fTwvbGFiZWw+XG4gICAgICAgIDxpbnB1dCBpZD1cImF3YXJkLWFtb3VudFwiIHR5cGU9XCJudW1iZXJcIiBtaW49XCIwXCIgZGF0YS10ZXN0aWQ9XCJhd2FyZC1hbW91bnRcIlxuICAgICAgICAgICAgICAgWyhuZ01vZGVsKV09XCJhd2FyZEFtb3VudFwiXG4gICAgICAgICAgICAgICBbYXR0ci5wbGFjZWhvbGRlcl09XCInYWEub3JkZXIuYXdhcmRfYW1vdW50X3BsYWNlaG9sZGVyJyB8IHRyYW5zbGF0ZVwiIC8+XG4gICAgICA8L2Rpdj5cbiAgICB9XG5cbiAgICA8ZGl2IGNsYXNzPVwiZm9ybS1maWVsZFwiPlxuICAgICAgPGxhYmVsIGZvcj1cIm9yZGVyLXN1bW1hcnlcIj57eyAnYWEub3JkZXIuc3VtbWFyeScgfCB0cmFuc2xhdGUgfX08L2xhYmVsPlxuICAgICAgPHRleHRhcmVhIGlkPVwib3JkZXItc3VtbWFyeVwiIHJvd3M9XCI1XCIgZGF0YS10ZXN0aWQ9XCJvcmRlci1zdW1tYXJ5XCJcbiAgICAgICAgICAgICAgICBbKG5nTW9kZWwpXT1cIm9yZGVyU3VtbWFyeVwiXG4gICAgICAgICAgICAgICAgW2F0dHIucGxhY2Vob2xkZXJdPVwiJ2FhLm9yZGVyLnN1bW1hcnlfcGxhY2Vob2xkZXInIHwgdHJhbnNsYXRlXCI+PC90ZXh0YXJlYT5cbiAgICA8L2Rpdj5cblxuICAgIEBpZiAoZXJyb3JLZXkoKSkge1xuICAgICAgPGRpdiBjbGFzcz1cImVycm9yLW1zZ1wiIHJvbGU9XCJhbGVydFwiIGFyaWEtbGl2ZT1cImFzc2VydGl2ZVwiIGRhdGEtdGVzdGlkPVwib3JkZXItZXJyb3JcIj5cbiAgICAgICAge3sgZXJyb3JLZXkoKSB8IHRyYW5zbGF0ZSB9fVxuICAgICAgPC9kaXY+XG4gICAgfVxuXG4gICAgPGRpdiBjbGFzcz1cImZvcm0tYWN0aW9uc1wiPlxuICAgICAgPGJ1dHRvbiB0eXBlPVwiYnV0dG9uXCIgY2xhc3M9XCJwcmV2aWV3LWJ0blwiIGRhdGEtdGVzdGlkPVwicHJldmlldy1vcmRlclwiXG4gICAgICAgICAgICAgIChjbGljayk9XCJwcmV2aWV3T3JkZXIoKVwiPnt7ICdhYS5vcmRlci5wcmV2aWV3X2FjdGlvbicgfCB0cmFuc2xhdGUgfX08L2J1dHRvbj5cbiAgICAgIDxidXR0b24gdHlwZT1cImJ1dHRvblwiIGNsYXNzPVwiY2FuY2VsLWJ0blwiIGRhdGEtdGVzdGlkPVwiY2FuY2VsLW9yZGVyXCJcbiAgICAgICAgICAgICAgKGNsaWNrKT1cImNhbmNlbCgpXCI+e3sgJ2FhLm9yZGVyLmNhbmNlbCcgfCB0cmFuc2xhdGUgfX08L2J1dHRvbj5cbiAgICA8L2Rpdj5cbiAgfVxuPC9kaXY+XG4iXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7O0FBQUEsU0FBUyxhQUFBQSxZQUFtQixVQUFBQyxTQUFRLFVBQUFDLFNBQVEsZ0JBQWdCO0FBQzVELFNBQVMsZ0JBQUFDLHFCQUFvQjtBQUM3QixTQUFTLGVBQUFDLG9CQUFtQjtBQUM1QixTQUFTLFFBQVEsc0JBQXNCO0FBQ3ZDLFNBQVMsY0FBQUMsbUJBQWtCOzs7QUVKM0IsU0FBUyxXQUFXLE9BQU8sUUFBUSxjQUFzQixRQUFRLGNBQWM7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QUEsY0FBQSxDQUFBLEdBQUEsUUFBQSxFQUFBLFFBQUEsU0FBQSxHQUFBLFNBQUEsRUFBQSxhQUFBLGVBQUEsV0FBQSxZQUFBLEdBQUEsT0FBQSxHQUFBLE1BQUEsR0FBQSxRQUFBLENBQUEsQ0FBQSxHQUFBLGFBQUEsR0FBQSxDQUFBLFFBQUEsVUFBQSxhQUFBLFVBQUEsZUFBQSxpQkFBQSxHQUFBLGFBQUEsR0FBQSxDQUFBLGVBQUEsaUJBQUEsR0FBQSxlQUFBLEdBQUEsQ0FBQSxHQUFBLGdCQUFBLEdBQUEsQ0FBQSxHQUFBLGNBQUEsR0FBQSxDQUFBLGVBQUEsbUJBQUEsR0FBQSxlQUFBLEdBQUEsQ0FBQSxlQUFBLG1CQUFBLEdBQUEsaUJBQUEsR0FBQSxDQUFBLEdBQUEsaUJBQUEsR0FBQSxDQUFBLFFBQUEsVUFBQSxlQUFBLGlCQUFBLEdBQUEsY0FBQSxHQUFBLFNBQUEsVUFBQSxHQUFBLENBQUEsUUFBQSxVQUFBLGVBQUEsY0FBQSxHQUFBLGNBQUEsR0FBQSxPQUFBLEdBQUEsQ0FBQSxlQUFBLGVBQUEsR0FBQSxDQUFBLEdBQUEsWUFBQSxHQUFBLENBQUEsUUFBQSxjQUFBLEdBQUEsaUJBQUEsR0FBQSxDQUFBLEdBQUEsa0JBQUEsR0FBQSxVQUFBLEdBQUEsQ0FBQSxPQUFBLGVBQUEsR0FBQSxDQUFBLE1BQUEsaUJBQUEsUUFBQSxLQUFBLGVBQUEsaUJBQUEsR0FBQSxpQkFBQSxTQUFBLEdBQUEsQ0FBQSxRQUFBLFNBQUEsYUFBQSxhQUFBLGVBQUEsZUFBQSxHQUFBLFdBQUEsR0FBQSxDQUFBLEdBQUEsY0FBQSxHQUFBLENBQUEsUUFBQSxVQUFBLGVBQUEsaUJBQUEsR0FBQSxlQUFBLEdBQUEsT0FBQSxHQUFBLENBQUEsUUFBQSxVQUFBLGVBQUEsZ0JBQUEsR0FBQSxjQUFBLEdBQUEsT0FBQSxHQUFBLENBQUEsR0FBQSxnQkFBQSxHQUFBLENBQUEsUUFBQSxTQUFBLFFBQUEsV0FBQSxHQUFBLGlCQUFBLE1BQUEsU0FBQSxTQUFBLEdBQUEsQ0FBQSxHQUFBLGNBQUEsR0FBQSxDQUFBLEdBQUEs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LGFBQUEsR0FBQSxDQUFBLGFBQUEsa0JBQUEsR0FBQSxRQUFBLEdBQUEsQ0FBQSxHQUFBLGdCQUFBLEdBQUEsT0FBQSxHQUFBLENBQUEsZUFBQSxpQkFBQSxHQUFBLFFBQUEsR0FBQSxDQUFBLEdBQUEsYUFBQSxHQUFBLENBQUEsUUFBQSxVQUFBLGFBQUEsVUFBQSxHQUFBLGNBQUEsR0FBQSxnQkFBQSxnQkFBQSxHQUFBLENBQUEsR0FBQSxnQkFBQSxHQUFBLENBQUEsR0FBQSxZQUFBLEdBQUEsQ0FBQSxHQUFBLE9BQUEsR0FBQSxDQUFBLEdBQUEsYUFBQSxHQUFBLENBQUEsZUFBQSxlQUFBLEdBQUEsQ0FBQSxlQUFBLGVBQUEsR0FBQSxDQUFBLGVBQUEsVUFBQSxHQUFBLENBQUEsZUFBQSxzQkFBQSxHQUFBLGdCQUFBLEdBQUEsQ0FBQSxlQUFBLGdCQUFBLEdBQUEsQ0FBQSxlQUFBLGlCQUFBLEdBQUEsQ0FBQSxHQUFBLFNBQUEsWUFBQSxHQUFBLENBQUEsZUFBQSxnQkFBQSxHQUFBLENBQUEsZUFBQSxpQkFBQSxHQUFBLENBQUEsZUFBQSxpQkFBQSxHQUFBLENBQUEsZUFBQSxhQUFBLEdBQUEsQ0FBQSxlQUFBLGlCQUFBLEdBQUEsQ0FBQSxlQUFBLGVBQUEsR0FBQSxDQUFBLGVBQUEsa0JBQUEsR0FBQSxDQUFBLGVBQUEsZ0JBQUEsR0FBQSxDQUFBLGVBQUEsVUFBQSxHQUFBLENBQUEsZUFBQSxzQkFBQSxHQUFBLENBQUEsZUFBQSx1QkFBQSxHQUFBLENBQUEsZUFBQSxpQkFBQSxHQUFBLGtCQUFBLGVBQUEsR0FBQSxDQUFBLGVBQUEsbUJBQUEsR0FBQSxnQkFBQSxHQUFBLENBQUEsR0FBQSxnQkFBQSxhQUFBLEdBQUEsQ0FBQSxRQUFBLFVBQUEsYUFBQSxVQUFBLGVBQUEsaUJBQUEsR0FBQSxjQUFBLEdBQUEsV0FBQSxPQUFBLEdBQUEsQ0FBQSxlQUFBLG1CQUFBLEdBQUEsZUFBQSxHQUFBLENBQUEsb0JBQUEsSUFBQSxHQUFBLGNBQUEsVUFBQSxRQUFBLE1BQUEsR0FBQSxDQUFBLFFBQUEsVUFBQSxhQUFBLFVBQUEsR0FBQSxZQUFBLEdBQUEsQ0FBQSxlQUFBLFFBQUEsR0FBQSxVQUFBLEdBQUEsQ0FBQSxHQUFBLGtCQUFBLEdBQUEsQ0FBQSxHQUFBLGNBQUEsR0FBQSxDQUFBLEdBQUEsVUFBQSxHQUFBLENBQUEsZUFBQSxvQkFBQSxHQUFBLGtCQUFBLEdBQUEsQ0FBQSxlQUFBLGlCQUFBLEdBQUEsZUFBQSxHQUFBLENBQUEsZUFBQSxZQUFBLEdBQUEsQ0FBQSxlQUFBLHVCQUFBLEdBQUEsQ0FBQSxlQUFBLGlCQUFBLEdBQUEsa0JBQUEsR0FBQSxDQUFBLGVBQUEsZUFBQSxHQUFBLENBQUEsZUFBQSxXQUFBLEdBQUEsQ0FBQSxRQUFBLFVBQUEsYUFBQSxVQUFBLGVBQUEsaUJBQUEsR0FBQSxZQUFBLEdBQUEsQ0FBQSxlQUFBLFFBQUEsR0FBQSxhQUFBLEdBQUEsQ0FBQSxHQUFBLFFBQUEsR0FBQSxDQUFBLEdBQUEsYUFBQSxHQUFBLENBQUEsbUJBQUEscUJBQUEseUJBQUEsaUNBQUEsa0JBQUEscUJBQUEsc0JBQUEsd0JBQUEsa0JBQUEsb0JBQUEsR0FBQSxpQkFBQSxVQUFBLFVBQUEsVUFBQSxXQUFBLGNBQUEsY0FBQSxXQUFBLGlCQUFBLFFBQUEsR0FBQSxDQUFBLEdBQUEsUUFBQSxHQUFBLENBQUEsR0FBQSxZQUFBLEdBQUEsQ0FBQSxPQUFBLG9CQUFBLEdBQUEsQ0FBQSxNQUFBLHNCQUFBLGVBQUEsc0JBQUEsR0FBQSxpQkFBQSxTQUFBLEdBQUEsQ0FBQSxTQUFBLEVBQUEsR0FBQSxDQUFBLEdBQUEsT0FBQSxHQUFBLENBQUEsR0FBQSxvQkFBQSxhQUFBLFFBQUEsR0FBQSxDQUFBLEdBQUEsZUFBQSxhQUFBLFFBQUEsR0FBQSxDQUFBLGVBQUEsb0JBQUEsR0FBQSxnQkFBQSxHQUFBLENBQUEsZUFBQSxrQkFBQSxHQUFBLGdCQUFBLEdBQUEsQ0FBQSxlQUFBLFlBQUEsR0FBQSxVQUFBLEdBQUEsQ0FBQSxHQUFBLGVBQUEsR0FBQSxDQUFBLEdBQUEsY0FBQSxHQUFBLENBQUEsZUFBQSxRQUFBLEdBQUEsYUFBQSxHQUFBLENBQUEsR0FBQSxrQkFBQSxHQUFBLENBQUEsR0FBQSxlQUFBLEdBQUEsQ0FBQSxHQUFBLGlCQUFBLEdBQUEsQ0FBQSxHQUFBLGVBQUEsR0FBQSxDQUFBLEdBQUEsZ0JBQUEsR0FBQSxDQUFBLEdBQUEsa0JBQUEsR0FBQSxDQUFBLEdBQUEsZUFBQSxHQUFBLENBQUEsR0FBQSxtQkFBQSxlQUFBLEdBQUEsQ0FBQSxHQUFBLGdCQUFBLENBQUEsR0FBQSxVQUFBLFNBQUEsaUNBQUEsSUFBQSxLQUFBO0FBQUEsUUFBQSxLQUFBLEdBQUE7QUNyRXBDLE1BQUEsNkJBQUEsR0FBQSxhQUFBLENBQUEsRUFBcUUsR0FBQSxPQUFBLENBQUEsRUFDaEQsR0FBQSxVQUFBLENBQUE7QUFDc0QsTUFBQSx5QkFBQSxTQUFBLFNBQUEsMkRBQUE7QUFBQSxlQUFTLElBQUEsT0FBQTtNQUFRLENBQUE7QUFDdEYsTUFBQSw2QkFBQSxHQUFBLFFBQUEsQ0FBQTtBQUF5QixNQUFBLHFCQUFBLEdBQUEsUUFBQTtBQUFNLE1BQUEsMkJBQUEsRUFBTztBQUV4QyxNQUFBLGtDQUFBLEdBQUEsZ0RBQUEsR0FBQSxHQUFBLFFBQUEsQ0FBQTtBQUtGLE1BQUEsMkJBQUE7QUFFQSxNQUFBLDZCQUFBLEdBQUEsT0FBQSxDQUFBO0FBQ0UsTUFBQSxrQ0FBQSxHQUFBLGdEQUFBLEdBQUEsR0FBQSxPQUFBLENBQUEsRUFBaUIsR0FBQSxnREFBQSxHQUFBLEdBQUEsT0FBQSxDQUFBLEVBRU8sR0FBQSxnREFBQSxLQUFBLEdBQUE7QUE0WTFCLE1BQUEsMkJBQUEsRUFBTTs7OztBQTNaRyxNQUFBLHlCQUFBLFlBQUEsaUJBQUEsRUFBOEIsV0FBQSxJQUFBLGFBQUEsQ0FBQTtBQUtyQyxNQUFBLHdCQUFBLENBQUE7QUFBQSxNQUFBLDZCQUFBLFVBQUEsSUFBQSxhQUFBLEtBQUEsSUFBQSxJQUFBLE9BQUE7QUFRQSxNQUFBLHdCQUFBLENBQUE7QUFBQSxNQUFBLDRCQUFBLElBQUEsUUFBQSxJQUFBLElBQUEsQ0FBQSxJQUFBLE9BQUEsSUFBQSxJQUFBLENBQUE7O29CRG9EUUMsZUFBWSxhQUFBLHVCQUFBLGFBQUEsVUFBQSxzQkFBQSxhQUFBLGNBQUEsa0JBQUEscUJBQUEsY0FBQSxrQkFBRUMsY0FBVyx3QkFBQSxvQkFBQSxrQ0FBQSwwQkFBQSx5QkFBQSx3QkFBQSxrQ0FBQSxnQ0FBQSx3Q0FBQSwrQkFBQSxxQkFBQSwwQkFBQSx1QkFBQSx3QkFBQSx3QkFBQSxzQkFBQSwrQkFBQSxvQkFBQSxrQkFBQSxrQkFBQSxhQUFBLGtCQUFBLFlBQUUsb0JBQW9CLGtCQUFrQixzQkFBc0IsbUJBQW1CLHdCQUF3Qiw0QkFBNEIsMkJBQTJCLHNCQUFvQixlQUFBLG1CQUFBLG1CQUFBLGNBQUEsZUFBQSxpQkFBQSxpQkFBQSxtQkFBQSxrQkFBQSxjQUFBLG9CQUFBLG9CQUFBLGtCQUFFLGFBQWEsR0FBQSxRQUFBLENBQUEsMnRVQUFBLEVBQUEsQ0FBQTs7O2dGQUkzTix5QkFBdUIsQ0FBQTtVQVBuQ0M7dUJBQ1csd0JBQXNCLFlBQ3BCLE1BQUksU0FDUCxDQUFDRixlQUFjQyxjQUFhLG9CQUFvQixrQkFBa0Isc0JBQXNCLG1CQUFtQix3QkFBd0IsNEJBQTRCLDJCQUEyQixzQkFBc0IsYUFBYSxHQUFDLFVBQUE7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7O0dBQUEsUUFBQSxDQUFBLG16UUFBQSxFQUFBLENBQUE7Ozs7aUZBSTVOLHlCQUF1QixFQUFBLFdBQUEsMkJBQUEsVUFBQSx3RUFBQSxZQUFBLEdBQUEsQ0FBQTtBQUFBLEdBQUE7Ozs7Ozs7K0RBQXZCLHlCQUF1QixFQUFBLFNBQUEsQ0FBQUUsS0FBQUMsS0FBQUMsR0FBQSxHQUFBLENBQUFMLGVBQUFDLGNBQUEsb0JBQUEsa0JBQUEsc0JBQUEsbUJBQUEsd0JBQUEsNEJBQUEsMkJBQUEsc0JBQUEsZUFBQUMsVUFBQSxHQUFBLGFBQUEsRUFBQSxDQUFBO0VBQUE7QUFBQSxHQUFBLE9BQUEsY0FBQSxlQUFBLGNBQUEsZ0NBQUEsS0FBQSxJQUFBLENBQUE7QUFBQSxHQUFBLE9BQUEsY0FBQSxlQUFBLGVBQUEsWUFBQSxPQUFBLFlBQUEsSUFBQSxHQUFBLDRCQUFBLE9BQUEsRUFBQSxPQUFBLE1BQUEsZ0NBQUEsRUFBQSxTQUFBLENBQUE7QUFBQSxHQUFBOyIsIm5hbWVzIjpbIkNvbXBvbmVudCIsImluamVjdCIsInNpZ25hbCIsIkNvbW1vbk1vZHVsZSIsIkZvcm1zTW9kdWxlIiwiSHR0cENsaWVudCIsIkNvbXBvbmVudCIsIklucHV0IiwiT3V0cHV0IiwiRXZlbnRFbWl0dGVyIiwiaW5qZWN0Iiwic2lnbmFsIiwiQ29tbW9uTW9kdWxlIiwiRm9ybXNNb2R1bGUiLCJIdHRwQ2xpZW50IiwiX2ZvclRyYWNrMCIsIkV2ZW50RW1pdHRlciIsImluamVjdCIsIkh0dHBDbGllbnQiLCJzaWduYWwiLCJDb21tb25Nb2R1bGUiLCJGb3Jtc01vZHVsZSIsIkNvbXBvbmVudCIsIklucHV0IiwiT3V0cHV0IiwiaTAiLCJpMSIsImkyIiwiX2ZvclRyYWNrMCIsIl9mb3JUcmFjazEiLCJpbmplY3QiLCJIdHRwQ2xpZW50Iiwic2lnbmFsIiwiQ29tbW9uTW9kdWxlIiwiRm9ybXNNb2R1bGUiLCJDb21wb25lbnQiLCJpMCIsImkxIiwiaTIiXX0=