# Go-Live Checklist — Ushirika Welfare Organization

Read this before flipping the platform over to real members and real money. Work through it
top to bottom; don't skip items just because they look done — verify live, not from memory.

## 1. Remove the payment simulation stub

Added 2026-08-05 to unblock live testing of payment-gated flows (dues, registration fee,
benevolence enrollment, MGR contributions) without live Stripe keys. **Must be removed before
go-live** — once real Stripe keys are wired up, a SUPERADMIN account (or a compromised one) could
use this tool to mark real charges as "paid" without any money actually moving, i.e. free
memberships/payments on demand. It is not something to leave behind "just in case."

Remove:
- Backend: `src/main/java/com/mdau/ushirika/module/payment/controller/AdminPaymentSimulationController.java`
  (whole file) and its two methods on `PaymentBasketService` (`simulateSuccess`, `listPendingBaskets`)
  if nothing else references them.
- Frontend: the "Payment Simulator" card in `src/routes/admin/developer.tsx`, plus
  `listPendingPaymentBaskets`/`simulatePaymentSuccess` in `src/lib/api/client.ts` and the
  `PaymentBasketSummary` type in `src/lib/api/types.ts`.

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

- Public `/membership` page and the portal dashboard's "dues unpaid" banner still describe paying
  via "Zelle, Cash App, or bank transfer" — stale copy from before the Stripe-only migration.
  Update to reflect the real (Stripe-only) payment flow, or explicitly decide manual payment
  methods are still offered and wire up a real path for them.

---

*Add to this list as new go-live blockers surface — don't let it go stale.*
