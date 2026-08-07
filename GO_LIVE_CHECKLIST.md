# Go-Live Checklist — Ushirika Welfare Organization

Read this before flipping the platform over to real members and real money. Work through it
top to bottom; don't skip items just because they look done — verify live, not from memory.

## 1. Confirm the payment simulation tool is disabled in production

**Updated 2026-08-07: this item's design changed.** The simulator was originally a stub to delete
before go-live; as of this window it's been redesigned as a **permanent SUPERADMIN-only tool**,
gated behind the `STRIPE_ALLOW_DEV_FALLBACK` env var (default `false`), paired with `StripeService`
only generating fake `cs_dev_...` sessions when that flag is true. The code and its javadoc now
both frame it as intentional, not leftover.

Still present (verified 2026-08-07):
- Backend: `src/main/java/com/mdau/ushirika/module/payment/controller/AdminPaymentSimulationController.java`
  — `@PreAuthorize("hasRole('SUPERADMIN')")`, endpoints `/admin/payments/simulate/baskets` and
  `/{id}/success`.
- Frontend: "Payment Simulator" card in `src/routes/admin/developer.tsx`, plus
  `listPendingPaymentBaskets`/`simulatePaymentSuccess` in `src/lib/api/client.ts` and the
  `PaymentBasketSummary` type in `src/lib/api/types.ts`.

**Before go-live**: confirm `STRIPE_ALLOW_DEV_FALLBACK` is `false` (or unset) in the production
Railway env, so a SUPERADMIN account can no longer mark real charges "paid" without money moving.
Whether to also hide the UI card entirely in production, or leave it as a dormant no-op tool behind
the env flag, is a decision for the user — don't remove the code without checking, since it's no
longer treated as a stub.

## 2. Switch Stripe from test/dev mode to live keys

Checkout sessions created during this testing round were `cs_dev_...` (Stripe's test/dev mode).
Confirm the production Stripe secret key, publishable key, and webhook signing secret in Railway
are the **live** ones, not test ones, before accepting real payments. Re-verify the webhook
endpoint is registered against the live Stripe account (webhook secrets differ between
test/live).

## 3. Clear all test/seed data

Per earlier agreement, all current test/seed data is safe to use freely during testing and will
be deleted before real launch. This includes (at minimum):
- Test member accounts created during live testing (e.g. Pius Mdau, Prince Munene, and any other
  applicants/members created purely for QA)
- Their associated PaymentBasket, MembershipApplication, MemberProfile, NextOfKin,
  EmergencyContact rows
- Any test meetings, fines, or other records created incidentally while testing those flows
- The seeded `DataInitializer` test member (`member@ushirikawelfare.org`) if it's not meant to
  persist into production

Do this via a real DB pass (Railway Query page, not the Data tab's search box — see main
HANDOFF.md for why), not by trying to delete through the admin UI one row at a time.

## 4. Final board-approved Constitution & Bylaws text

What's live now is an interim version transcribed from PDFs, explicitly marked "pending review
at the upcoming annual meeting." Once the AGM happens and the board approves final text, that
needs to replace the interim content in the Constitution admin panel (`/admin/constitution`).

## 5. Review other dev-only tooling

- `Developer → Email Delivery Test` (`/superadmin/dev/test-email`, `DevController.java`) — decide
  whether to keep (harmless, SUPERADMIN-gated, useful for ongoing debugging) or remove.
- `Demo Guide` admin nav item — check whether it's still relevant post-launch or should come down.

## 6. Confirm superadmin credentials are real

`DataInitializer` seeds a superadmin from `APP_SUPERADMIN_EMAIL`/`APP_SUPERADMIN_PASSWORD` env
vars **only on first creation** — confirm the real production superadmin's password has actually
been changed from any default/placeholder before members start signing up.

## 7. Known content issues to fix before members see them

- Public `/membership` page (`src/routes/membership.tsx` lines ~654/656/667) and the portal
  dashboard's "dues unpaid" banner (`src/routes/portal/index.tsx` line ~162), plus channel labels
  in `portal/meetings.tsx`/`portal/membership.tsx`, still describe paying via "Zelle, Cash App, or
  bank transfer." **Still open as of 2026-08-07** — and now more clearly wrong, since real
  cash-payment and admin card-entry flows shipped this window, so the copy should point at those
  instead of the old manual methods. Update to reflect the real payment flow.

---

*Add to this list as new go-live blockers surface — don't let it go stale.*
