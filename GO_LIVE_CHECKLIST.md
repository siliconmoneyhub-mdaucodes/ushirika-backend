# Go-Live Checklist — Ushirika Welfare Organization

Read this before flipping the platform over to real members and real money. Work through it
top to bottom; don't skip items just because they look done — verify live, not from memory.

## 1. Remove or gate the payment simulation tool — two SEPARATE mechanisms, don't conflate them

**Corrected 2026-08-07** (an earlier pass at this doc, same day, wrongly conflated these two
things as one "gated by `STRIPE_ALLOW_DEV_FALLBACK`" tool — they are not the same code path):

- **`STRIPE_ALLOW_DEV_FALLBACK`** (`StripeService.java`) only controls whether *creating a checkout
  session* falls back to a fake `cs_dev_...` session when Stripe isn't configured. Default `false`.
  This one is legitimately permanent-by-design and fine to leave as-is — with real Stripe keys and
  the flag `false`/unset, checkout always goes through real Stripe.
- **`AdminPaymentSimulationController`** (`/admin/payments/simulate/baskets/{id}/success`) is a
  **completely separate endpoint with no flag check at all.** Read directly: `simulateSuccess()` in
  `PaymentBasketService.java` (~line 378) unconditionally marks any pending `PaymentBasket` as paid
  and runs it through the same allocation/email path as a real payment — gated *only* by
  `@PreAuthorize("hasRole('SUPERADMIN')")`, nothing else. **This still means what the original
  version of this checklist item said**: once real Stripe keys are live, a SUPERADMIN account (or a
  compromised one) can mark a real pending charge "paid" with zero money moving, regardless of what
  `STRIPE_ALLOW_DEV_FALLBACK` is set to.

Still present (verified 2026-08-07):
- Backend: `src/main/java/com/mdau/ushirika/module/payment/controller/AdminPaymentSimulationController.java`
  (whole file), plus `simulateSuccess`/`listPendingBaskets` on `PaymentBasketService`.
- Frontend: "Payment Simulator" card in `src/routes/admin/developer.tsx`, plus
  `listPendingPaymentBaskets`/`simulatePaymentSuccess` in `src/lib/api/client.ts` and the
  `PaymentBasketSummary` type in `src/lib/api/types.ts`.

**Before go-live**: either remove `AdminPaymentSimulationController` and its two service methods
entirely, or add a real gate to `simulateSuccess()` itself (e.g. only allow it when
`STRIPE_ALLOW_DEV_FALLBACK` is true, or wrap it in a separate `app.enable-payment-simulation` flag
defaulting to `false`) — a role check alone isn't enough once this is a live production system with
real superadmin accounts. This is a decision for the user, but don't leave it as-is unexamined.

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
