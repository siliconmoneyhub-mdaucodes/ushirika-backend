# Ushirika Welfare Organization — Project Handoff

Written 2026-08-05 at the end of a long Claude Code session, for continuity into a new
session/account. Read this file first before doing anything else on this project.

## What this is

**Ushirika Welfare Organization (UWO)** is a real nonprofit — a Kenyan/Luhya community welfare
association based in the Dallas–Fort Worth, TX area. This is their production membership
management platform: applications, onboarding, dues, welfare programs (Benevolence bereavement
fund, MGR merry-go-round table-banking, custom programs), meetings/attendance, elections, forums,
messaging, and Stripe-based payments.

The user (Mdau) is the sole developer, building this solo with Claude Code. **Timeline pressure is
real**: as of 2026-08-05, admins need the system usable within about a week so they can learn the
platform before demoing it to members. The org's annual general meeting is about a month out, after
which the admins will supply the final, board-approved Constitution and Bylaws text (what's live
now is an interim version transcribed from PDFs the user provided, explicitly marked "pending
review at the upcoming annual meeting").

## Repos

- **Backend**: `J:\backend\ushirika-backend` — Spring Boot 3.x / Java 17. Git remote
  `pettz910-prog/ushirika-backend` (redirects to `siliconmoneyhub-mdaucodes/ushirika-backend`),
  branch `master`. Deployed on **Railway** at
  `https://ushirika-backend-production-e040.up.railway.app` — pushing to `master` auto-deploys.
- **Frontend**: `J:\frontend\ushirika-main\ushirika-connect-main` — TanStack Start / React /
  TypeScript. Git remote `MdauCodes/ushirika-connect`, branch `main`. Deployed at
  `https://ushirikacommunity.site` — pushing to `main` auto-deploys.
- Admin panel lives inside the frontend app at `/admin/*`. Public site, member portal
  (`/portal/*`), and applicant onboarding (`/onboarding`) are all the same frontend app, gated by
  role.

## Conventions that matter

- **No Flyway/Liquibase.** Migrations are hand-written SQL in `backend/migrations/V0XX__*.sql`,
  numbered sequentially (latest is V029). They are **not auto-applied** — historically the pattern
  was "give the user each statement to paste into Railway's Postgres console one at a time." As of
  this session, prefer **app-code seeding via `DataInitializer.java`** instead when data needs to
  survive future edits without being reset on redeploy (see the Constitution/Bylaws section below
  for why, and the established `existsByX()` guard pattern already used for superadmin, test
  member, contribution plans, and programs).
- **Git author requirements**: backend commits use `MdauCodes <mdaucodes@gmail.com>` on `master`;
  frontend commits use `Mdau Codes <mdaucodes@gmail.com>` (note the space) on `main`. Use
  `git commit --author=`.
- **Git push can be slow/flaky** in this environment — GitHub Credential Manager sometimes needs
  30s–5min to refresh a token before a push completes; a `git push` that appears to hang or time
  out is very often *not actually stuck*, just slow. Retry rather than assume it failed
  (`dangerouslyDisableSandbox: true` is required on the Bash tool for pushes to work at all here).
- **Backend response envelope convention**: almost every controller wraps responses in
  `ApiResponse.ok(...)` → `{success, message, data}`, and the frontend's generic `call()`/
  `callList()` helpers in `src/lib/api/client.ts` universally assume that envelope (they do
  `json.data as T`). **The `constitution` module violates this convention** — see Known Bug below.
  Don't copy that module's pattern; wrap new endpoints in `ApiResponse` like everything else.
- **Credential handling**: never ask the user for passwords/tokens. For admin-only actions, either
  ask the user to do it themselves in the browser, or hand them copy-pasteable SQL/curl. The
  Railway Postgres console has (at least) two different input surfaces — a table/data search-filter
  box and a proper "Query" page — pasting a non-`SELECT` statement into the search box silently
  mangles it (produces a bogus "syntax error near LIMIT"). If SQL needs to run, prefer the Console
  tab or an explicit "Query" page over the Data tab's search box; better yet, avoid the console
  entirely by seeding through app code and a deploy (see below).
- **PDF text extraction**: `pdftotext` is available via mingw64/Git-Bash in this environment.
  Extracting with `-layout` and then reflowing with a small Node script (strip repeated
  header/footer lines, merge line-wrapped continuations back into their list item, preserve
  `Article`/`Section` headings and numbered/lettered/roman-numeral list markers) produced clean,
  faithful text from the org's real Constitution/Bylaws PDFs. Don't editorialize/fix typos in the
  source document — preserve it faithfully, including the org's own inconsistencies.

## What's been built, in order

### Payments (Phase 0–3, earlier in this session)
Unified `PaymentBasket` system replacing ad-hoc payment flows: multi-line-item Stripe checkout,
additive-credit methods for Benevolence/MGR, `GET /payments/my/outstanding` aggregator, dedicated
ledgers (`DUES`, `BENEVOLENCE_ENROLLMENT`, `BENEVOLENCE_REPLENISHMENT`, `MGR_CONTRIBUTION`,
`GENERAL_CONTRIBUTION`, `PROGRAM_APPLICATION_PREPAY`, `REGISTRATION_FEE`). Removed the old
admin-manual-entry and member-self-report payment paths entirely (`PeerPayment` self-report system,
fine self-report, replenishment self-attest) in favor of Stripe-verified payments only. Frontend:
`portal/payments.tsx` ("Pay My Balances") is the one member-facing payment page now.

### Content & operations features
- **Gallery**: album creation + media upload UI, fixed public route mismatch, fixed
  publish/unpublish HTTP method mismatch, fixed a real bug where deleting photos/albums only
  removed DB rows and left orphaned files on Cloudinary (`CommunityAlbumService` now calls
  `cloudinary.uploader().destroy(...)`, matching the pattern in `MediaService`/`LeadershipService`).
- **Events**: fixed status-update method signature bug, wired the Edit UI to the existing update
  endpoint, added a frontend status gate so Edit doesn't show on COMPLETED/CANCELLED events.
- **Attendance** (3 phases): `Meeting`/`AttendanceRecord` entities, HMAC rotating QR check-in code
  with GPS validation, auto-fine wiring for late/absent via `FineService`, admin QR
  config+full-screen display, member camera-scan check-in flow, an `AttendanceExcuse` system for
  members to explain absences with an admin review queue. OpenStreetMap Nominatim (free, no API
  key) replaced raw lat/lng entry for admin meeting-location search.
- **Messaging**: a full member↔admin/coordinator messaging module (backend + frontend), threads
  optionally scoped to a program.
- **Branding**: renamed "Ushirika Welfare Foundation/DFW" → "Ushirika Welfare Organization"
  everywhere. Global "Join Now"/"Login" FAB that adapts based on auth state.

### The big one: Onboarding & Member Profile Refactor (9 phases, all shipped)

**Why**: the old onboarding wizard never actually collected real identity/address/next-of-kin data
— a placeholder `MemberProfile` was created with dummy values ("Pending", 1900-01-01, etc.) and
nothing ever replaced them. This refactor makes onboarding the single real source of that data.

- **Phase 1** (V026): `Gender` narrowed to `MALE`/`FEMALE` only. New `Country` enum (`KENYA`,
  `UGANDA`). `MemberProfile` address fields replaced with real structured columns:
  `street, city, zipCode, country` + conditional sub-region fields (`kenyaCounty/SubCounty/Village`
  or `ugandaProvince/County/Village`). All nullable at the DB level — completeness is enforced at
  the application layer in `OnboardingService.submitRegistration()`, not DB constraints.
- **Phase 2** (V027): `NextOfKin`/`EmergencyContact` as proper child entities, fixed at exactly 2
  each via a `position SMALLINT CHECK (position IN (1,2))` + unique constraint, `FetchType.EAGER`
  (deliberate — `open-in-view: false` means lazy access outside a transaction would throw).
- **Phase 3** (V028): Constitution acceptance tracking (mirrors existing bylaws acceptance), a
  `requireCompleteProfile()` gate in `submitRegistration()`, removed the onboarding-time "join
  programs" step and its backend endpoints entirely.
- **Phase 4**: new per-step backend endpoints — `POST /onboarding/{identity-info,address-info,
  next-of-kin,emergency-contacts}` — each independently submittable/resumable.
- **Phase 5**: onboarding wizard split into step components under
  `frontend/src/components/onboarding/steps/`. New step order: **Account Setup → Identity →
  Address → Next of Kin & Emergency Contacts → References → Constitution → Bylaws → Registration
  Fee.** Constitution/Bylaws each get their own step.
- **Phase 6**: `portal/profile.tsx` rebuilt to match the real backend shape, reusing shared
  `AddressFields`/`KinContactPairFields` components (also used by onboarding).
- **Phase 7** (V029): `ProgramApplication` gained `beneficiaries`/`notes` columns. New
  `MemberProgramController` (`/programs`, MEMBER-role) lets *verified members* browse and apply to
  programs directly from the portal — distinct from (now-removed) onboarding-time program
  selection. Guarded to CUSTOM-type programs only; MGR/Benevolence keep their own dedicated join
  flows.
- **Phase 8**: `portal/programs.tsx` — lists programs with per-member state; MGR/Benevolence
  deep-link to their existing pages, CUSTOM programs get an inline apply form + status badges +
  (once approved) links to messaging and Pay My Balances.
- **Phase 9 / UX correction** (the most recent work): the user explicitly corrected the initial
  approach — **Constitution/Bylaws must render as real text you scroll through, not an embedded
  PDF.** The original Phase 5 implementation used `react-pdf` (`PdfConsentViewer.tsx`); this was
  **ripped out entirely** (dependency uninstalled, ~1MB bundle savings) and replaced with
  `TextConsentViewer.tsx`, which renders `GoverningDocument.contentText` as plain scrollable text
  and gates the "I agree and consent" checkbox on actually reaching the bottom (same scroll-gate
  logic, just no PDF renderer). Every wizard step past Account Setup also gained a **Back** button,
  and the data-entry steps (Identity/Address/KinContacts/References) now **prefill** from
  `getFullProfile()`/`getOnboardingStatus()` so navigating back and forth doesn't lose data. Also
  fixed a real bug found along the way: `OnboardingService.submitAdditionalInfo()` was silently
  discarding the reference1/2 name+member-ID fields instead of persisting them.

### Getting the real Constitution/Bylaws text live

The user provided the org's actual Constitution and Bylaws as PDFs. Extracted with `pdftotext
-layout` + a Node reflow script (strips page header/footer noise, merges wrapped lines, preserves
heading/list structure) into clean text — final versions live in
`backend/src/main/resources/seed/constitution.txt` and `bylaws.txt`.

First attempt was hand-run SQL via Railway's Postgres console (the constitution `INSERT` succeeded,
but the bylaws `UPDATE` hit the search-box-vs-query-page issue above). The user then asked for a
seed-once approach instead so future admin edits never get reset. Implemented as a guarded startup
seed in `DataInitializer.seedGoverningDocuments()`:
- Constitution: skip entirely if any `CONSTITUTION` row already exists (any status).
- Bylaws: if a row's title still contains `"PLACEHOLDER"`, replace its title/description/content
  and publish it; once an admin edits it (changing the title), the guard stops matching and future
  deploys never touch it again. If no bylaws row exists at all, create one fresh.

This deployed successfully (verified live via the public API). **One cleanup item remains**: there
are now two duplicate, identical `CONSTITUTION` rows in the DB (both from the user's manual SQL
attempts before switching to the seed approach — my seed correctly saw one already existed and
skipped, so it did not cause the duplicate). Delete one via the admin panel's trash icon — except
the admin panel can't currently list documents at all, see below.

## 🔴 Known bug — found but not yet fixed, top priority for next session

**The `/admin/constitution` page shows "No governing documents yet." even though the documents
exist and the public API returns them correctly.** Root cause confirmed by reading the actual
source (not just grep, which has a known rendering quirk with backslashes in this environment —
always verify with Read before trusting a Grep hit that looks like a path bug):

- `ConstitutionController` (`/public/constitution`) and `AdminConstitutionController`
  (`/admin/constitution`) both return a **raw, unwrapped `List<GoverningDocumentDto>`** —
  `ResponseEntity.ok(service.listAll())` — with no `ApiResponse` envelope.
- The frontend's generic `call<T>()` helper (`src/lib/api/client.ts` ~line 267) **unconditionally**
  does `const json = await res.json(); return json.data as T;` — it always expects
  `{success, message, data}` and reaches into `.data`. Given a raw JSON array, `json.data` is
  `undefined`.
- `callList()` treats a `null`/`undefined` result as an empty list → the frontend silently renders
  "no documents," even though the request succeeded (HTTP 200) and the backend genuinely returned
  data.

This affects **both** `/admin/constitution` (confirmed live — the screenshot the user shared) and,
almost certainly, `/public/constitution` → `listPublicDocuments()` → **`TextConsentViewer`, i.e.
the onboarding Constitution/Bylaws steps built in Phase 5/9**. That component's actual fetch path
through the frontend has never been verified end-to-end in a real browser session (only verified
via direct `curl` against the raw API, and via `tsc`/build success) — so there's a real risk the
onboarding flow's Constitution/Bylaws steps currently show "this document isn't available yet"
to real applicants despite the data being correctly seeded.

**The fix**: make `ConstitutionController` and `AdminConstitutionController` wrap every response in
`ApiResponse.ok(...)` like every other controller in the codebase, matching the established
convention — don't special-case the frontend. Six methods total across the two controllers
(`listPublished`, `listAll`, `create`, `update`, `publish`, `unpublish`, `delete`). After fixing,
**verify live in a browser**: the admin panel should list all 3 (well, will be 2 after the
duplicate cleanup) documents, and the onboarding wizard's Constitution/Bylaws steps should render
real text with working scroll-gated consent.

## What's next, in priority order

1. **Fix the `ApiResponse` wrapping bug above.** This blocks both admin document management and
   (likely) the onboarding consent flow — do this before anything else.
2. Delete the duplicate Constitution row via the admin panel (or SQL, once you're confident about
   which console surface to use) after the panel is working again.
3. **Redesign the transactional email templates** to visually match the live website — matching
   color theme, branding, layout. The user offered to share website screenshots for reference; ask
   for them if not already provided. Find the existing email templates (search `EmailService` and
   wherever HTML email bodies are constructed — likely `module/notification`) before designing.
4. **Full live end-to-end testing of onboarding**, using a real email address the user will
   provide when asked: submit an application → admin sends the form → applicant walks the entire
   onboarding wizard (all 8 steps, including reading Constitution/Bylaws and back-navigation) →
   registration fee payment → admin approves membership → confirm portal access. This was blocked
   on the email redesign per the user's explicit ordering ("before the live testing, we must work
   on the email designs... after the redesign... we should do the live testing end to end").
5. Two previously-flagged, still-open background cleanup items (non-blocking, spawned as
   dismissable task chips earlier this session, may or may not still be visible in the harness):
   - Orphaned `ProgramApplicationService.applyToPrograms()`/`listMyApplications()` and
     `ApplyToProgramsRequest` — no longer called since onboarding-time program selection was
     removed in Phase 3.
   - Dead `submitMembershipApplication()` function + stale `MemberProfile` TS type in
     `frontend/src/lib/api/client.ts`/`types.ts` — references an address/county shape the backend
     dropped months ago, zero call sites.
   - Also still open from earlier in the session: orphaned `submitFinePayment` client function; a
     silent 403-vs-401 session-expiry bug on admin routes (never actually diagnosed, just noted).

## Test credentials (production DB — this is real data the org is actively testing with)

- Seeded **MEMBER**: `member@ushirikawelfare.org` / `Member@2025!` — "Wekesa Wanjala", has a full
  profile (address, 2 next-of-kin, 2 emergency contacts) after live-testing edits this session.
- Superadmin credentials are **not** the `DataInitializer` defaults — the real production env vars
  override them and are not known to Claude; ask the user for admin access when needed, or have
  them perform admin-only actions themselves in the browser.
- All current test/seed data (per the user) will be deleted before real launch — safe to use
  freely for testing.

## How to pick this back up in a new session/account

1. Open Claude Code in either `J:\backend\ushirika-backend` or
   `J:\frontend\ushirika-main\ushirika-connect-main` (this file is duplicated in both repos' roots
   as `HANDOFF.md`).
2. Read this file in full before making changes.
3. Start with the known bug above — it's real, confirmed, and blocking.
4. The user prefers **one phase/task at a time**, each verified (compile + test, and a live
   browser/API check where feasible) and committed before moving to the next — don't batch large
   changes across unrelated concerns.
