# Ushirika Welfare Organization — Project Handoff

Updated 2026-08-07 (second pass, same day) after auditing two days of heavy, unsupervised feature
work (26 backend commits / 24 frontend commits since the previous 2026-08-05 handoff), then acting
on user-directed fixes from that audit. Verified against actual code and live production endpoints,
not carried forward from memory. Read this file first before doing anything else on this project.

## What this is

**Ushirika Welfare Organization (UWO)** is a real nonprofit — a Kenyan/Luhya community welfare
association based in the Dallas–Fort Worth, TX area. This is their production membership
management platform: applications, onboarding, dues, welfare programs (Benevolence bereavement
fund, MGR merry-go-round table-banking, custom programs), meetings/attendance, elections, forums,
messaging, notifications, finance/audit reporting, and Stripe-based payments.

The user (Mdau) is the sole developer, building this solo with Claude Code. Timeline pressure from
the 2026-08-05 handoff (admins need the system usable within about a week, AGM roughly a month
out) still applies — if anything the pace of the last two days suggests that deadline is close.

## Repos

- **Backend**: `J:\backend\ushirika-backend` — Spring Boot 3.x / Java 17. Git remote
  `pettz910-prog/ushirika-backend` (redirects to `siliconmoneyhub-mdaucodes/ushirika-backend`),
  branch `master`. Deployed on **Railway** at
  `https://ushirika-backend-production-e040.up.railway.app` — pushing to `master` auto-deploys.
- **Frontend**: `J:\frontend\ushirika-main\ushirika-connect-main` — TanStack Start / React /
  TypeScript. Git remote `MdauCodes/ushirika-connect`, branch `main`. Deployed at
  `https://ushirikacommunity.site` — pushing to `main` auto-deploys.
- Both repos are clean and pushed as of this update. The admin card-charging feature
  (`0b1b54b` backend / `3936807` frontend) is now fully committed and pushed on both sides — it was
  found half-finished (backend pushed, frontend uncommitted) during this audit and completed. **Not
  yet smoke-tested live in a browser** — `VITE_STRIPE_PUBLISHABLE_KEY` needs a real value in
  Railway/the frontend host's env before the Card Entry tab will do anything but show its "not
  configured" warning; confirm that's set, then test the full charge flow for real.
- Admin panel lives inside the frontend app at `/admin/*`. Public site, member portal
  (`/portal/*`), and applicant onboarding (`/onboarding`) are all the same frontend app, gated by
  role.

## Conventions that matter

- **No Flyway/Liquibase, and the `backend/migrations/V0XX__*.sql` files are now effectively
  inert/historical.** The last real file is still V029 (`program_application_beneficiaries.sql`) —
  no new migration files have been added since 2026-08-05 despite substantial schema growth.
  **All schema changes now go through `DataInitializer.ensureSchemaExtensions()`**, idempotent raw
  DDL (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, constraint drop-and-recreate) that
  runs on every boot. Confirmed it currently owns: `platform_settings`, `partners`,
  `member_credit_balances`, `conversation_threads`/`conversation_messages`,
  `election_seats.executive_tier`, `election_candidacies.video_url`, constitution/bylaws signature
  columns, registration-fee-waiver columns, meeting/event reminder-sent flags, and rebuilding the
  `users_role_check` / `in_app_notifications_category_check` constraints to include new
  roles/categories on every deploy (this constraint got out of sync with the `UserRole` enum twice
  already and crashed startup both times — see the election/roles section below). **Any future
  schema change should extend this method, not add a new `migrations/V0xx` file.**
- **Data seeding**: `DataInitializer.java` also seeds the superadmin and one test MEMBER account
  (see Test Credentials below) via `existsByX()` guards. **Test-official seeding for the new
  Secretary/Chief Whip/Compliance roles was added then deliberately removed** (`201f0dc`) — it kept
  crashing boot because the role-check constraint predated those roles. Per explicit user
  direction, those roles are now populated by **promoting real accounts during live testing**, not
  seeded test users. Don't re-add a seeder for them without checking with the user first.
- **Git author requirements**: backend commits use `MdauCodes <mdaucodes@gmail.com>` on `master`;
  frontend commits use `Mdau Codes <mdaucodes@gmail.com>` (note the space) on `main`. Use
  `git commit --author=`.
- **Git push can be slow/flaky** in this environment — GitHub Credential Manager sometimes needs
  30s–5min to refresh a token before a push completes; a `git push` that appears to hang or time
  out is very often *not actually stuck*, just slow. Retry rather than assume it failed
  (`dangerouslyDisableSandbox: true` is required on the Bash tool for pushes to work at all here).
- **Backend response envelope convention**: every controller wraps responses in `ApiResponse.ok(...)`
  → `{success, message, data}`, and the frontend's generic `call()`/`callList()` helpers in
  `src/lib/api/client.ts` universally assume that envelope. This convention is now consistently
  applied — the constitution-module violation described in the previous handoff is fixed (see
  Known Bug section below). Keep wrapping new endpoints the same way.
- **Credential handling**: never ask the user for passwords/tokens. For admin-only actions, either
  ask the user to do it themselves in the browser, or hand them copy-pasteable SQL/curl. The
  Railway Postgres console has (at least) two different input surfaces — a table/data search-filter
  box and a proper "Query" page — pasting a non-`SELECT` statement into the search box silently
  mangles it. Prefer the Console/Query page, or better, seed through app code and a deploy.
- **PDF text extraction**: `pdftotext` (via mingw64/Git-Bash) with `-layout`, then a small Node
  reflow script, produced clean Constitution/Bylaws text from the org's real PDFs — see
  `backend/src/main/resources/seed/constitution.txt` / `bylaws.txt` if this needs to be redone for
  the final board-approved text after the AGM.

## What's been built, in order

### Payments (Phase 0–3, prior session) → superseded by a unified balance engine (this window)
Original `PaymentBasket` system (multi-line-item Stripe checkout, dedicated ledgers) has now been
extended with a **single settlement point**: `module/payment/service/PaymentAllocationService.java`
pools all incoming money (Stripe, cash, admin-entered card) and applies it in priority order —
fines → dues → MGR → benevolence — backed by a new `MemberCreditBalance` entity/repository. Legacy
`PeerPayment` self-report controller/service/repository/DTO were deleted outright (`1a56bda`); only
the underlying `PeerPayment` entity/enums remain, referenced elsewhere.

### Content & operations features (prior session)
Gallery (album/media + Cloudinary orphan-file fix), Events (status-update fix, Edit gating),
Attendance (QR check-in, HMAC + GPS, auto-fines, excuse review queue), Messaging (member↔admin
threads), branding rename to "Ushirika Welfare Organization."

### Onboarding & Member Profile Refactor (9 phases, prior session) — now extended
Real identity/address/next-of-kin data collection, step-by-step resumable onboarding, text-based
(not PDF) Constitution/Bylaws consent. **This window added**: onboarding now requires a **manual
signature** (typed name, initials, date) to accept Constitution/Bylaws (`cece384`), not just a
scroll-to-bottom checkbox.

### New this window (2026-08-05 → 2026-08-07)
- **Elections**: full executive-officer election system — `module/election/` with
  `Election`/`ElectionSeat`/`ElectionCandidacy`/`ElectionResult`/`ElectionVoteReceipt`/
  `ElectionVoteTally` entities, `AdminElectionController`/`PortalElectionController`/
  `PublicElectionController`, `ElectionService`. Candidacies require manifesto/photo/video for
  executive-tier seats; a live vote feed exists on both admin and portal sides
  (`src/routes/admin/elections.tsx`, `src/routes/portal/elections.tsx`,
  `src/routes/portal/live-votes.tsx`). Fixed a `TransientPropertyValueException` on election
  creation and a case where `election_seats.executive_tier`/`election_candidacies.video_url`
  columns never actually reached production (schema-extension gap, now covered by
  `ensureSchemaExtensions()`).
- **New roles**: `SECRETARY`, `CHIEF_WHIP`, `COMPLIANCE` added to `UserRole` alongside the existing
  `SUPERADMIN, ADMIN, FINANCIAL_ADMIN, FINANCIAL_OFFICIAL, LEADERSHIP, MEMBER, APPLICANT`. Each has
  a distinct scope (records/meetings, discipline/attendance, governance/constitution respectively)
  and a role-aware dashboard on the frontend (`src/routes/admin/index.tsx`, `admin.tsx`). Session/
  profile role mapping and post-login redirects for these roles, and for financial roles, were
  fixed (`018b23a`, frontend `e625eb7`) — they were previously falling through to generic behavior.
- **Audit logging**: `module/audit/` (`AuditLog` entity, `AuditLogService`,
  `AdminAuditLogController`), extended to cover benevolence, loans, scholarships, elections, and
  role/credential changes. Frontend: `src/routes/admin/audit-logs.tsx` with badges/filters.
- **Finance dashboard**: cross-department view spanning dues/benevolence/loans/MGR —
  `module/dashboard/` (`FinanceDashboardDto`, `DashboardController`, `DashboardService`) backend
  side; `src/routes/admin/contributions.tsx`, `payment-records.tsx`, and a Finance section in
  `src/routes/admin/analytics.tsx` frontend side, plus a Finance home dashboard for
  Financial Admin/Official.
- **Reports**: Excel and PDF export added alongside existing CSV — `report/util/{PdfBuilder,
  XlsxBuilder,TableBuilder}.java`.
- **Notifications**: broadcast fixed to stop excluding members who hold an official role
  (`aa435f1`); extended beyond all-members-only to single-member and program-scoped targeting
  (backend `module/notification/`, frontend `src/routes/admin/notifications.tsx`). 24h/6h upcoming
  reminders added for meetings and events.
- **Messaging**: extended with staff-initiated conversations (with a "not private" warning to
  members) and admin/coordinator-started threads; backing tables (`conversation_threads`,
  `conversation_messages`) were missing in production and had to be added via
  `ensureSchemaExtensions()` (`c0b0e9a`).
- **Partners**: new public "Our Partners" page + admin management
  (`src/routes/admin/partners.tsx`, `src/routes/partners.tsx`), backed by a new `partners` table.
- **Admin card-charging (in progress, not fully shipped)**: admin can charge a member's own card
  directly via Stripe Elements. Backend committed (`0b1b54b`) but **unpushed**; frontend work
  (`startAdminCardEntry`/`CardEntryResult` in `client.ts`, wiring in `admin/contributions.tsx`) is
  **uncommitted**. Treat as incomplete until both sides are committed, pushed, and smoke-tested.
- **Misc fixes**: admin Create Member was silently skipping registration-fee tracking (fixed,
  backend `4898a02` / frontend now requires explicit fee acknowledgment `0e31f36`); a misleading
  fee-waiver checkbox in bulk application actions; promoted officials being silently downgraded
  back to plain member; Member Stories photo field was a raw URL paste box instead of a real
  upload; address-bar favicon; SMS channel disabled and WhatsApp tab marked "coming soon"
  (intentional stub, not an oversight).

## ✅ Previously known bug — now FIXED and verified LIVE

The 2026-08-05 handoff flagged `ConstitutionController`/`AdminConstitutionController` returning raw
unwrapped `List<GoverningDocumentDto>`, breaking `/admin/constitution` and likely the onboarding
consent viewer. **Confirmed fixed** (`8ef29b6`, "Fix: wrap constitution controllers in ApiResponse
envelope") — both controllers now return `ResponseEntity<ApiResponse<...>>` for all six endpoints.
**Re-verified live** this session by curling the real production endpoint
(`GET /api/public/constitution`): returns a proper `{success, message, data}` envelope with one
published `CONSTITUTION` and one published `BYLAWS` document. The frontend's `callList()` helper
correctly unwraps `.data`, so `/admin/constitution` and the onboarding `TextConsentViewer` are
confirmed working end-to-end at the API layer. (The onboarding UI itself — clicking through the
wizard in a real browser — still hasn't been click-tested; the data plumbing is proven sound.)

**Unresolved from the prior handoff**: whether the duplicate `CONSTITUTION` row was ever deleted
from the DB is **still unconfirmed** — the public endpoint only returns published docs so it can't
prove or disprove a duplicate exists; checking requires the admin panel's full list (SUPERADMIN
login) or a DB query, neither done this session.

## Session 2 (2026-08-07, this pass): what got fixed

Acting on direct user instructions after reviewing the audit above:

1. **Admin card-charging feature — reconciled, committed, pushed, both sides.** Backend `0b1b54b`
   and a completed frontend commit `3936807` are both on `master`/`main` now. Verified compatible
   (`POST /financial/cash-payments/card-entry` ↔ `startAdminCardEntry()`) and that both repos build
   clean (`mvnw compile`, `npm run build`) before pushing. **Still needs**: confirm
   `VITE_STRIPE_PUBLISHABLE_KEY` has a real value wherever the frontend is hosted (it's an empty
   placeholder in the local `.env`/`.env.production` — without it the Card Entry tab shows a "not
   configured" warning instead of the Stripe Elements form), then smoke-test an actual charge.
2. **Stale Zelle/Cash App/Venmo copy — partially removed, scope question surfaced for the rest.**
   Fixed everywhere a live Stripe/cash alternative already exists and is fully wired: the public
   `/membership` payment-method cards (`d028415`), the portal dashboard's dues-unpaid banner (now
   links to Pay My Balances), the generic card-payment-failure fallback message in `client.ts`
   (used to say "pay via Zelle/Venmo/Cash App," now says to arrange cash with an admin), and the
   internal `demo-guide.tsx` walkthrough steps for dues/fines.
   - **Confirmed safe to leave alone**: `channelLabel()` helpers in `portal/meetings.tsx` and
     `portal/membership.tsx` only label *historical* payment records (what channel a past payment
     actually used) — not an active "pay via Zelle" prompt. No live submission UI reads them.
   - **Found but deliberately NOT touched — needs a scoping decision, see below**: `donations.tsx`,
     `scholarship.tsx`, `portal/benevolence.tsx`'s replenishment-payment modal, and the whole
     `admin/payment-links.tsx` / `AdminPaymentLinksController` / `PublicPaymentLinksController` /
     `MemberContribution` backend apparatus. These are **real, live, functioning features** with
     **zero Stripe or cash alternative wired on the frontend** — donors and members currently pay
     through them via the Zelle/Venmo/CashApp self-report-and-admin-verify flow, and nothing else.
     Removing that copy/UI without first wiring a replacement would leave donors and benevolence-
     replenishing members with **no way to pay at all**. The backend's `PaymentBasketLedger` already
     has `GENERAL_CONTRIBUTION` and `BENEVOLENCE_REPLENISHMENT` cases fully implemented and ready to
     receive Stripe/cash/card-entry payments — the frontend for those two flows just never got built
     to call them. **This is a real feature-build task (wire donations.tsx and the benevolence
     replenishment modal into the existing PaymentBasket checkout), not a copy edit — needs explicit
     user sign-off on scope/priority before starting.**

## What's next, in priority order

1. **Get a decision on the donations/scholarship/benevolence-replenishment/payment-links scope**
   (see above) — likely the next real chunk of work once decided: wire `donations.tsx` and the
   `ReplenishmentPayModal` in `portal/benevolence.tsx` to the existing `GENERAL_CONTRIBUTION` /
   `BENEVOLENCE_REPLENISHMENT` PaymentBasket checkout, then retire `PaymentLink`/`MemberContribution`
   /`AdminPaymentLinksController` and the Payment Links admin page.
2. **Smoke-test the admin card-charging feature live** — confirm the Stripe publishable key is
   configured in production, then run a real (or Stripe test-mode) charge through the Card Entry tab
   end-to-end.
3. **Confirm/clear the duplicate Constitution row** in the DB — check the admin panel's full
   document list (SUPERADMIN login required) now that it's listing documents again.
4. **Full live end-to-end testing** — the user's standing instruction (confirmed 2026-08-03) was to
   do real-user live testing (real clicks, real emails) once Gallery/Events/Attendance shipped;
   those shipped before this window, and a huge amount has been built since without that pass
   happening. Given the AGM/timeline pressure, this is likely overdue — raise it with the user
   rather than assuming more features should ship first.
5. **Redesign transactional email templates** to match site branding — flagged 2026-08-05, not
   confirmed done or not done in this audit; check `EmailService`/`module/notification` before
   assuming either way.
6. See `GO_LIVE_CHECKLIST.md` for remaining pre-launch-only items (item 1's meaning changed this
   window — payment simulator is now a permanent gated tool behind `STRIPE_ALLOW_DEV_FALLBACK`, not
   a stub to delete; confirm that flag is `false` in production before go-live).
7. Previously-flagged, still-open minor cleanup (not re-verified this window, may be stale):
   orphaned `ProgramApplicationService.applyToPrograms()`/`listMyApplications()`, dead
   `submitMembershipApplication()`/`submitFinePayment()` client functions, an undiagnosed silent
   403-vs-401 session-expiry bug on admin routes.

## Test credentials (production DB — this is real data the org is actively testing with)

- Seeded **MEMBER**: `member@ushirikawelfare.org` / `Member@2025!` — "Wekesa Wanjala."
- Seeded **SUPERADMIN**: email/password come from `APP_SUPERADMIN_EMAIL`/`APP_SUPERADMIN_PASSWORD`
  Railway env vars, not known to Claude — ask the user or have them act in-browser.
- **No seeded test accounts exist for Secretary/Chief Whip/Compliance** — a seeder was added then
  deliberately removed (`201f0dc`) per user direction; those roles get populated by promoting real
  accounts during live testing, not from `DataInitializer`.
- All current test/seed data (per the user) will be deleted before real launch — safe to use freely
  for testing.

## How to pick this back up in a new session/account

1. Open Claude Code in either `J:\backend\ushirika-backend` or
   `J:\frontend\ushirika-main\ushirika-connect-main` (this file is duplicated in both repos' roots
   as `HANDOFF.md` — keep both copies in sync when updating).
2. Read this file in full before making changes. Also check `git status` in both repos immediately
   — as of this writing there's an unpushed backend commit and uncommitted frontend work that a new
   session needs to account for before doing anything else.
3. Start with item 1 above (reconcile the in-progress card-charging feature) unless the user
   redirects.
4. The user prefers **one phase/task at a time**, each verified (compile + test, and a live
   browser/API check where feasible) and committed before moving to the next — don't batch large
   changes across unrelated concerns. The pace of the last two days suggests this preference may be
   loosening under deadline pressure — confirm with the user rather than assuming.
