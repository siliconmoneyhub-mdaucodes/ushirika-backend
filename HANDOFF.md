# Ushirika Welfare Organization — Project Handoff

Updated 2026-08-15, mid-session, so a new Claude Code session can pick up exactly where this one
stopped. Read this file first before doing anything else on this project. This replaces the
2026-08-07 version — most of that handoff's open items have since shipped (see task history below);
this version focuses on current state and the one thing genuinely in progress.

## What this is

**Ushirika Welfare Organization (UWO)** is a real nonprofit — a Kenyan/Luhya community welfare
association based in the Dallas–Fort Worth, TX area. This is their production membership
management platform: applications/onboarding, dues, welfare programs (Benevolence bereavement fund,
MGR merry-go-round table-banking), meetings/attendance, elections, forums, messaging,
notifications, finance/audit reporting, and Stripe-based payments (including Cash App Pay).

The user (Mdau) is the sole developer, building this solo with Claude Code, with real users
(officials and members of the actual DFW Luhya community) already testing it live.

**Critical framing, repeated by the user this session**: Benevolence is described as *the main
reason the organization exists*. Any future onboarding/UX work should weigh nudging new members
toward Benevolence enrollment heavily — see "Open idea, not yet scoped" below.

## Repos

- **Backend**: `J:\backend\ushirika-backend` — Spring Boot 3.x / Java 17. Git remote
  `pettz910-prog/ushirika-backend` (shows a "moved" notice, redirects to
  `siliconmoneyhub-mdaucodes/ushirika-backend`), branch `master`. Deployed on **Railway** at
  `https://ushirika-backend-production-e040.up.railway.app` — pushing to `master` auto-deploys
  (typically live within ~60-100s).
- **Frontend**: `J:\frontend\ushirika-main\ushirika-connect-main` — TanStack Start / React /
  TypeScript. Git remote `MdauCodes/ushirika-connect`, branch `main`. Deployed on **Vercel** at
  `https://ushirikacommunity.site` — pushing to `main` auto-deploys (typically live within ~60s).
- Both repos are clean and pushed as of this update (backend `75016f1`, frontend `5520b37`).
- Admin panel lives inside the frontend app at `/admin/*`. Public site, member portal
  (`/portal/*`), and applicant onboarding (`/membership?apply=1` → enquiry → emailed login →
  `/onboarding`) are all the same frontend app, gated by role.

## Conventions that matter

- **No Flyway/Liquibase.** All schema changes go through `DataInitializer.ensureSchemaExtensions()`
  (idempotent raw DDL — `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`,
  constraint-drop-and-not-recreate). **Confirmed unreliable this session**: Hibernate's
  `ddl-auto=update` does NOT reliably add new nullable columns to already-populated tables in
  production — a real `SQLGrammarException` occurred live when a new `@Column` was queried before
  its `ALTER TABLE ADD COLUMN` had been added to `DataInitializer`. **Always pair any new entity
  column touching an existing table with an explicit `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`**,
  never trust `ddl-auto=update` alone. Status/enum check constraints are systematically dropped
  outright (not recreated) the first time a new enum value is needed — validated at the Java layer
  instead; this is now the established pattern for every `@Enumerated(STRING)` status column.
- **Git author requirements**: backend commits use `--author="MdauCodes <mdaucodes@gmail.com>"` on
  `master`; frontend commits use `--author="Mdau Codes <mdaucodes@gmail.com>"` (note the space) on
  `main`. Vercel's Hobby plan blocks deploys unless the commit author matches, so this is not
  optional.
- **Git push can be slow** — sometimes exceeds the default 2-minute Bash timeout. Retry with
  `timeout: 300000` and `dangerouslyDisableSandbox: true` rather than assuming failure; check
  `git log origin/<branch>..HEAD` to confirm before retrying.
- **Backend response envelope convention**: every controller wraps responses in `ApiResponse.ok(...)`
  → `{success, message, data}`; frontend's `call()`/`callList()` helpers in `src/lib/api/client.ts`
  universally assume that envelope. Keep wrapping new endpoints the same way.
- **Credential handling — hard rule, not a preference**: never type the operator's real login
  credentials into any form, even with explicit permission. For every persona switch or OTP entry,
  the operator logs in/confirms themselves in the browser and hands control back with a message
  like "done." This applies to admin OTP step-up too. Never invent a shortcut around it.
- **Pooled vs non-pooled payment ledgers** (`PaymentBasketLedger` enum): pooled ledgers
  (`DUES, MGR_CONTRIBUTION, FINE, BENEVOLENCE_REPLENISHMENT, BENEVOLENCE_ENROLLMENT,
  CASH_PAYMENT, CARD_ENTERED_BY_ADMIN`) get redistributed across a member's *other* obligations in
  priority order by `PaymentAllocationService`. Non-pooled ledgers
  (`REGISTRATION_FEE, PROGRAM_APPLICATION_PREPAY, GENERAL_CONTRIBUTION,
  BENEVOLENCE_APPLICATION_FEE`) credit their specific purpose directly. **Any new "pay to unlock
  gate X" flow must use a dedicated non-pooled ledger**, or the payment can silently vanish into an
  unrelated old balance instead of ever triggering the gate — this exact bug was caught and fixed
  for Benevolence's new paid-application step this session (`BENEVOLENCE_APPLICATION_FEE`).
- **Chrome browser automation artifact** (harmless, confirmed repeatedly): a `find()`-obtained
  element ref sometimes fails to register a click after a loading-splash transition. Fix: call
  `find()` again fresh immediately before the click rather than reusing an older ref.

## What's been built (cumulative — see full history in task tracker if using Claude Code's task
list; this is the high-level summary)

Applications/onboarding, dues, meetings/attendance (QR + GPS), elections, audit logging, finance
dashboards, PDF/Excel/CSV reports, messaging, notifications, partners page, bank reconciliation,
admin OTP step-up, 7-title executive officer structure, per-program balance totals — all shipped
and live-tested in prior sessions. Constitution/Bylaws requires a manual typed signature. Admin
manual card-charging (Stripe Elements) is live.

### This session (2026-08-15): Cash App Pay + Benevolence rebuild + MGR rebuild

**1. Cash App Pay integration** — Stripe Checkout now supports Cash App Pay alongside cards.
Built a `PreferredPaymentMethod` enum (`CARD`/`CASH_APP`) threaded through checkout DTOs;
`StripeService.createCheckoutSessionForMethod()` creates a session scoped to exactly one payment
method type (no silent fallback). All three payment-initiating flows (admin Cash Payment tab,
admin Card Entry tab, member Pay My Balances) now show distinct "Pay with Card" / "Pay with Cash
App" buttons, each opening a dedicated single-method Stripe Checkout page. **Verified live**: the
Cash App button opens a page titled "Pay with Cash App" showing only that option, no currency
picker (Cash App Pay requires USD presentment specifically — this is why per-method sessions were
needed instead of relying on a currency toggle).

**2. Benevolence program rebuild — fully implemented, deployed, and verified live end-to-end.**
- Applying to Benevolence is now a single paid-application step: member applies → coordinator
  clicks **Send Form** (`BenevolenceJoinRequestStatus`: `PENDING → FORM_SENT`) → member submits
  beneficiaries and pays **at least $100 of the $600 enrollment fee** (or the full $600) via a new
  payment card in `portal/benevolence.tsx` → **the payment itself auto-approves the join request**
  (`FORM_SENT → APPROVED`, no separate admin click) via
  `BenevolenceEnrollmentService.autoApproveOnFirstPayment()`.
  - New non-pooled ledger `BENEVOLENCE_APPLICATION_FEE` and dedicated endpoint
    `POST /benevolence/my/application-checkout` guarantee this payment always credits Benevolence,
    never gets redistributed to an unrelated old balance.
  - Probation period is now **admin-configurable** (`PlatformSettings.benevolenceProbationMonths`,
    default 4, was hardcoded 6) via `GET/PUT /financial/settings/benevolence-probation`. Probation
    only starts once the **full $600** is paid (`EnrollmentStatus: PAYING → PROBATION`), not on the
    first partial payment — confirmed by reading `BenevolenceEnrollmentService.applyPayment()`.
  - New emails in the established voice: `sendFormEmail` (explains the $100-minimum pay-to-submit
    gate), `sendEnrolledEmail` (sent on auto-approval).
  - **Verified live, full cycle**: submitted a real join request, clicked Send Form, hit (and
    fixed) a missing-DB-column bug, paid $100 via a real Stripe test-mode card checkout, confirmed
    auto-approval (request moved from FORM_SENT → APPROVED with zero admin intervention), confirmed
    the admin panel's "Probation Period" stat correctly reads the new configurable value.

**3. MGR program rebuild — fully implemented, deployed, and verified live for the Send Form step;
the cycle-invite mechanic (new this session) has NOT yet been live-tested (no MGR cycle exists to
trigger it against yet).**
- MGR previously jumped straight from `PENDING` to `WAITLISTED` on a single admin "Approve" click,
  then blindly swept **every** WAITLISTED member (FCFS) into whichever cycle activated next, with
  no member say in the matter. Rebuilt to:
  - `JoinRequestStatus`: `PENDING → FORM_SENT → WAITLISTED → ADMITTED` (or `REJECTED` from any of
    the first three). `FORM_SENT` is new — coordinator's `sendForm()` sends an info email
    explaining how MGR works (no payment involved), member confirms in-portal
    (`confirmJoinWaitlist()`) to actually land on the waitlist.
  - **New automated per-cycle opt-in ask**: whenever a new cycle is created (`MgrService.
    createCycle()` → `askWaitlistForNewCycle()`), every currently-WAITLISTED member gets an email +
    portal card asking "join this cycle or keep waiting?" (`MgrJoinRequest.invitedCycle` /
    `cycleOptIn` fields, new this session). `admitWaitlistedMembers()` (called at cycle activation)
    now only admits members who explicitly opted **in** to that specific cycle — no response
    defaults to staying on the waitlist for the next cycle's ask, instead of the old blind
    FCFS-into-whatever-activates-next behavior.
  - Portal (`portal/mgr.tsx`): new FORM_SENT confirm card, new cycle-invite card with "Join This
    Cycle" / "Keep Waiting" buttons. Admin (`admin/mgr.tsx`): "Approve" button replaced with "Send
    Form"; WAITLISTED rows now surface which cycle a member was last asked about and their
    response.
  - **Verified live**: the redeploy itself (filter-tab labels rendering "FORM SENT" with a space,
    matching Benevolence's existing pattern — caught and fixed as a follow-up commit
    `5520b37`/`5520b37` frontend). **Not yet tested**: the actual Send Form → confirm → cycle
    invite → opt-in → activation → admission chain, because no MGR cycle has been created since the
    rebuild and no member has gone through the new PENDING→FORM_SENT→WAITLISTED path yet.

### Later the same day (2026-08-15): two small user-reported fixes

1. **Onboarding registration fee now offers separate Card / Cash App Pay buttons**, matching every
   other checkout entry point. Previously it was the one holdout still using the old combined
   Card+Cash App Stripe page (single "Continue to Pay Registration Fee" button). Threaded
   `paymentMethod` through `OnboardingCheckoutRequest` → `OnboardingController` →
   `OnboardingService.startRegistrationCheckout` → `PaymentBasketService.startOnboardingCheckout`
   → the existing `resolveCheckout`/`createCheckoutSessionForMethod` machinery (no new Stripe-side
   plumbing). Frontend: `StepPayment.tsx` now shows "Pay with Card" / "Pay with Cash App" buttons
   styled like `portal/payments.tsx`. Backend + frontend both build/test clean and are pushed
   (`88f726b` / `a0be232`). **Not yet live-clicked in a browser** — verify both buttons land on
   Stripe's correct single-method page next time onboarding is tested.
2. **Member profile photos now render in the admin directory and portal nav.** Turned out the data
   was already fully wired (`MemberProfile.photoUrl` → `UserProfileDto.photoUrl` → frontend
   `User.photoUrl`) since photo upload was built earlier — it just was never rendered anywhere
   except the upload widget on `portal/profile.tsx` itself. Added avatar-with-initials-fallback
   (matching the existing pattern from `admin/forums.tsx`/`admin/mgr.tsx`/elections pages) to:
   `admin/members.tsx` (directory table + detail slide-over), `portal.tsx` (sidebar profile link),
   `components/site/Nav.tsx` (public-site topbar pill). No backend changes needed. Pushed
   (`3b2e2cb`).

### Later still the same day (2026-08-15): checkout UX, events scoping, dues proration

1. **All Stripe checkout redirects now open in a new tab.** User-requested. Every flow (onboarding
   registration fee, Pay My Balances, Benevolence application fee, admin Cash Payment) used
   `window.location.href` before — replaced with `openCheckoutTab()` from new
   `src/lib/checkoutPoll.ts`. Since the successUrl/cancelUrl redirect now lands in the *new* tab,
   each flow polls its own completion signal on the original tab instead: Pay My Balances polls
   `getMyBalance()` for a balance increase, onboarding polls `submitRegistration()` itself (already
   validates payment server-side) until it stops throwing, Benevolence polls
   `getMyBenevolenceEnrollment()` for `totalPaid > 0`, admin Cash Payment polls
   `getMemberBalanceAdmin()`. All four show a "waiting, will update automatically" state and a
   "check again" fallback after ~5 min. Pushed, builds/tests clean. **Not yet click-tested live.**
2. **Events/notifications scoping — list only, no implementation**, per explicit instruction
   ("premium scoping" before any build). Produced a module × role list (Applications, Dues,
   Benevolence, MGR, Meetings/Attendance, Elections, Messaging, Finance, Governance, Reports/Audit
   for admin roles; the member-facing equivalents for MEMBER/APPLICANT) — published as an artifact,
   not committed to the repo. Explicitly distinguished from the existing broadcast `Notifications`
   module (admin → members) — this would be the reverse direction (system → admin/member "you have
   something to act on"). Flagged messaging as the clearest phase-1 candidate if this gets scoped
   further. **No code written — next step is a mechanism-design pass (real-time vs. badge vs.
   digest, read/dismiss state) once the module list itself is confirmed.**
3. **Annual dues now show a recommended monthly pace instead of one lump "$100 due now."**
   Confirmed with the user: **the total owed is always the full $100** regardless of join
   timing — what was wrong was asking for it all immediately, right after a new member had just
   paid a registration fee. `MembershipDue.remainingMonths()`/`recommendedMonthlyAmount()` spread
   the same $100 across the months from when the due was created through the community's October
   cutoff (join in August → ~3 months → ~$33/month; renew in January → ~10 months → ~$10/month).
   Purely informational — stored amount, due date, and PAID threshold are all unchanged. Also added
   a partial-amount input to the dues line on Pay My Balances (the backend already capped whatever
   amount was sent at the real balance — only the frontend was hardcoding the full amount, so this
   was a small, low-risk addition). **Explicitly not retroactive** — only new due rows going
   forward show meaningfully-prorated guidance; existing rows (including Brian Wafula's) are
   untouched, per the user's own choice when asked.
   - **Found but deliberately NOT fixed, flagging per standing instruction not to silently correct
     flow discrepancies discovered during other work**: `createInitialDues()`/
     `AnnualDuesRenewalScheduler` always set `dueDate = October 31 of the current calendar year`. A
     member approved in **November or December** would get a due date that's already in the past
     the moment the record is created (showing as immediately overdue). This predates today's
     session — not introduced by the proration work — but the proration work is exactly the kind of
     change that would naturally also fix it (roll Nov/Dec approvals into next year's cycle). Left
     alone on purpose; raise with the user before touching it.

### Later still (2026-08-15): dues bug fixed, email CTA audit, Benevolence checklist

1. **Nov/Dec dues due-date bug — fixed** (was flagged, not fixed, earlier the same day; user said
   "of course fix this"). `createInitialDues()` now rolls approvals in November/December into next
   year's dues cycle instead of dating them Oct 31 of the year that already ended. Composes
   correctly with the proration work above — a Nov/Dec approval now gets the full ~11-12 month
   spread automatically, no special-casing needed.
2. **Every outbound email's CTA audited against the actual frontend routes.** Six real mismatches
   found and fixed: two `MeetingService` cessation emails (member copy had no link at all; leader
   copy used a relative href that breaks in most mail clients), `sendMembershipApproved` (told the
   member to log in, no link), `sendApplicantConfirmation` (promised a "Track Application"
   click-through that **doesn't exist anywhere in the frontend** — rewrote the copy rather than
   link to a dead feature; a real "track by reference number" page is a possible future build, not
   done here), two admin-notify emails (new application / public enquiry — told admins to "log in
   to the admin portal," no link, now include the real `/admin/applications` URL), and a **systemic**
   one: `InAppNotificationService.notifyUsers` silently dropped `actionUrl` from the EMAIL channel
   entirely — any admin composing a broadcast/direct notification with a portal link and picking
   email sent a dead-end message. All fixed, pushed, backend compiles/tests clean.
3. **Benevolence enrollment page — added a clear step checklist**, addressing feedback that the
   three-cards layout (payment, beneficiaries, claims) didn't make it obvious what's required vs.
   optional or what to do next. Shows "Step 1: pay $600 (live progress) / Step 2: submit
   beneficiaries" with inline done/not-done state, until the member reaches ELIGIBLE.
   - **Found, not silently assumed away**: read `BenevolenceScheduler` and
     `BenevolenceClaimService` — **beneficiary submission is not actually a hard requirement**
     today. PROBATION→ELIGIBLE only checks `probationEndsAt`, and claim filing only checks
     `status == ELIGIBLE`; neither checks `beneficiariesLocked`. The new checklist is worded
     "recommended," not "required," to stay honest about current behavior. Whether it *should*
     become an enforced gate is a real product decision, not made here.
   - **The bigger ask — making Benevolence more prominent to a newly-approved member because
     "this program is the main reason the community was constituted" — is still the same
     not-yet-scoped idea** noted earlier in this file (see "Open idea, not yet scoped" below). This
     session's checklist is a bounded clarity fix to the page itself, not that larger onboarding-nudge
     redesign. Scope that separately before building it.

## In progress right now: creating a fresh test member to run the *entire* new flow

The user asked to create a brand-new member from scratch via the real public onboarding flow (not
reuse an existing test account with prior state), specifically so the full chain — enquiry →
approval → account setup → profile → dues payment → Active status → apply to **both** Benevolence
and MGR — can be tested end to end, including the still-untested MGR cycle-invite mechanic.

**Test identity used** (invented by Claude per user instruction — "these should be test names from
you"):
- Name: **Brian Wafula**
- Email: **mdau910+brianwafula@gmail.com** (Gmail plus-alias off the user's real inbox, so
  transactional emails land somewhere the user can actually check and relay content from)
- Phone: `+12145550142`
- Address: 742 Cedar Ridge Lane, Arlington, TX 76010
- County of Origin: Bungoma · Subtribe: Bukusu (Aba-Bukusu)

**Progress so far**:
1. Submitted the enquiry at `/membership?apply=1` (reference `UWF-APP-32DB487D`). Note: the
   county/subtribe `<select>` dropdowns did not validate correctly when set via the automation
   tool's generic form-fill helper (React state didn't pick up the change) — had to redo them via
   focus + type-ahead keyboard input instead. If scripting this again, use keyboard type-ahead on
   native `<select>` elements, not a generic "set value" helper.
2. Logged in as Super Admin (user handled the login themselves per the credential rule), opened
   `/admin/applications`, found Brian's SUBMITTED enquiry, clicked **Send Form** → confirmed. Status
   is now **"Applicant is onboarding"** — an email with a temporary password and login link has
   been sent to `mdau910+brianwafula@gmail.com`.

**Next step, exactly where this session stopped**: waiting on the user to check that inbox and
relay the temporary password (or just log in as Brian themselves in the browser tab) so the
onboarding flow can continue: account setup (password change) → full profile (next of kin,
occupation, references) → Constitution/Bylaws signature → registration fee payment → membership
becomes Active. **Then**: as Brian, apply to Benevolence (should be a clean first-time run of the
already-verified flow) **and** apply to MGR (this is the one that actually needs testing — Send
Form → confirm → then, as Super Admin, create a new DRAFT MGR cycle to trigger the automated
cycle-invite ask, respond to it as Brian, activate the cycle as admin, and confirm Brian gets
admitted with a real slot).

## Open idea, not yet scoped — do not build without scoping first

User's own words this session: *"Keep in mind that benevolence was said to be the main reason why
we have the Ushirika so if we can have a way to encourage joining benevolence immediately someone
is onboarded to be a general member."* This is a real product direction (nudge new members toward
Benevolence enrollment right after they become Active, maybe during onboarding itself) but has
**not been scoped** — no design decisions made on where in the flow, how strong a nudge, whether
mandatory or optional, copy, etc. The user has a standing instruction repeated multiple times this
session: **scope first, implementation second.** Don't jump to building this without a scoping
pass and explicit go-ahead, even though the motivation is clear.

## Test credentials / accounts (production DB — real data the org is actively testing with)

- **Super Admin**: credentials not known to Claude (Railway env vars) — the operator logs in
  themselves.
- **Prince Munene** (`UW-2026-0006`) — existing test account with a Benevolence application
  already mid-flow (FORM_SENT → PAYING, $0/$600 paid, beneficiaries locked) from earlier live
  testing. Not a "clean" account for either member or non-member Benevolence-state testing anymore.
- **Brian Wafula** (`mdau910+brianwafula@gmail.com`) — brand new, created this session, currently
  mid-onboarding (see above). This is the account to continue the full-flow test with.
- Other accounts referenced in earlier admin-panel screenshots this session (Silicon Moneyhub
  `UW-2026-0004`, Joe Jones, Wekesa Wanjala `UW-2025-0001`) are pre-existing real/test data with
  their own Benevolence states already — don't assume any of them are "clean."
- All current test/seed data (per the user, historically) will be cleaned up before real launch —
  safe to use freely for testing.

## How to pick this back up in a new session

1. Open Claude Code in either `J:\backend\ushirika-backend` or
   `J:\frontend\ushirika-main\ushirika-connect-main` (this file is duplicated in both repos' roots —
   keep both copies in sync when updating).
2. Read this file in full. Check `git status` in both repos to confirm still clean.
3. Ask the user whether Brian Wafula's onboarding email has been checked / whether they want to
   continue that live-test thread, since it was mid-flight when this handoff was written.
4. Once Brian is Active and has gone through both Benevolence and MGR (including a full MGR cycle
   create → invite → opt-in → activate → admit cycle), MGR rebuild verification (task "Live-test
   MGR rebuild end to end") is complete.
5. The Benevolence-at-onboarding nudge idea above is the most likely next substantive ask — scope
   it with the user before writing any code.
