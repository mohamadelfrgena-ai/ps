# PS4 / PS5 Handoff — Plan-wide AC Prices and Timed Offers

- **Contract version:** 1
- **Supabase project:** `stbmescmexzcszyyambq`
- **Applies to:** PS4 and PS5 store clients
- **Server migration:** `supabase/52_plan_ac_discounts.sql`

## 1. Product rule

AC is an **alternative** way to unlock content that normally requires a subscription.

- Every published `Epic` item uses one shared Epic AC price.
- Every published `Legendary` item uses one shared Legendary AC price.
- Each plan can have an optional final offer price with an end instant.
- A paid-plan subscriber keeps normal subscription access and must not be charged AC.
- An AC unlock is permanent.
- The Thursday Epic gift remains free for that Libya calendar day.
- The server is authoritative for price, eligibility, offer expiry, wallet balance, and charging.

The console must never send a price. The purchase request contains only `p_item_id`.

## 2. Credentials and security

Use only the Supabase publishable/anon key in a console build.

Never ship any of the following:

- `service_role` key
- secret API key
- database password

For a public pricing snapshot, the `Authorization` bearer may be the publishable key or a current user access token. A purchase must use the linked user's current access token.

## 3. Canonical pricing snapshot

### Request

```http
POST /rest/v1/rpc/plan_ac_pricing_snapshot HTTP/1.1
Host: stbmescmexzcszyyambq.supabase.co
apikey: <publishable-key>
Authorization: Bearer <publishable-key-or-user-access-token>
Content-Type: application/json
Accept: application/json

{}
```

### Response shape

```json
{
  "ok": true,
  "version": 1,
  "timezone": "Africa/Tripoli",
  "server_now": "2026-10-07T18:00:00.123+00:00",
  "server_now_epoch_ms": 1791396000123,
  "revision": "2026-10-07 17:59:58.900001+00",
  "plans": [
    {
      "plan": "epic",
      "base_ac_price": 800,
      "sale_ac_price": 500,
      "effective_ac_price": 500,
      "discount_active": true,
      "discount_percent": 38,
      "sale_starts_at": "2026-10-07T17:59:58.9+00:00",
      "sale_ends_at": "2026-10-07T20:00:00+00:00",
      "sale_ends_at_epoch_ms": 1791403200000,
      "updated_at": "2026-10-07T17:59:58.9+00:00"
    },
    {
      "plan": "legendary",
      "base_ac_price": 1400,
      "sale_ac_price": null,
      "effective_ac_price": 1400,
      "discount_active": false,
      "discount_percent": 0,
      "sale_starts_at": null,
      "sale_ends_at": null,
      "sale_ends_at_epoch_ms": null,
      "updated_at": "2026-10-07T17:30:00+00:00"
    }
  ]
}
```

Do not calculate the canonical price from `content_items.ac_price` or `content_items.sale_ac_price` for Epic or Legendary items. Those legacy item fields are not this feature's source of truth.

## 4. Server-time countdown — never trust console wall time

When the snapshot response arrives, save a monotonic timestamp:

```text
received_monotonic_ms = monotonic_now_ms()
```

At render time:

```text
estimated_server_now_ms = server_now_epoch_ms
                        + (monotonic_now_ms() - received_monotonic_ms)

locally_active = discount_active
              && sale_ac_price != null
              && (sale_ends_at_epoch_ms == null
                  || estimated_server_now_ms < sale_ends_at_epoch_ms)

display_price = locally_active ? sale_ac_price : base_ac_price
```

Important rules:

1. Use a monotonic timer (`steady_clock`, platform process time, or SDL ticks), not the PS4/PS5 date/time setting.
2. If polling or Realtime disconnects, locally stop displaying the offer at its server-derived end instant. Never extend a stale offer.
3. A local countdown is presentation only. The purchase RPC independently decides whether the offer is active.
4. A response with `discount_active=false` must not be locally reactivated.

## 5. Synchronization from phone Admin to PS4/PS5

Use both mechanisms:

### A. Realtime invalidation

Subscribe to PostgreSQL changes for:

```text
schema: public
table: plan_ac_pricing
event: *
```

Treat a Realtime payload only as an invalidation signal. Debounce for approximately 150–300 ms, then refetch `plan_ac_pricing_snapshot`. Do not derive the effective price from the row payload because server time and expiry are part of the snapshot contract.

### B. Required polling fallback

- Poll the snapshot every **5 seconds** while Store, Deals, Search, or Item Details is visible.
- Poll every **15 seconds** elsewhere while the app is foregrounded.
- Refetch immediately after reconnect, resume, account relink, or a Realtime event.
- Use bounded exponential backoff only after network failures, but still invalidate a displayed sale locally at its known end.
- `revision` may be used to skip rebuilding UI models when nothing changed; always refresh the stored server-time anchor from a successful response.

The reference client in `ps4-addon-test/src/main.cpp` performs a five-second pricing poll. `supabase_client.cpp` parses the shared snapshot and overlays it on catalog items.

## 6. Catalog and UI behavior

For every Epic or Legendary item:

1. Match `content_items.plan` to the corresponding snapshot entry.
2. If `base_ac_price == 0`, hide/disable the AC alternative.
3. If the user's effective subscription includes the item, show normal included/download access; do not offer a charge.
4. Otherwise, show both choices:
   - subscribe/upgrade for the required plan;
   - unlock permanently with the current AC price.
5. During an active offer show:
   - base price with strikethrough;
   - final offer price prominently;
   - server-derived countdown;
   - optional `discount_percent` badge.
6. At expiry, remove strikethrough/countdown and show the base price even if the next network poll fails.
7. Preserve focus safely when the price refreshes. Do not close Item Details or reset scroll position.

## 7. Purchase RPC

### Request

```http
POST /rest/v1/rpc/ac_unlock_content HTTP/1.1
Host: stbmescmexzcszyyambq.supabase.co
apikey: <publishable-key>
Authorization: Bearer <linked-user-access-token>
Content-Type: application/json
Accept: application/json

{"p_item_id":"<content-item-uuid>"}
```

No other purchase field is allowed. In particular, do not send:

- price
- sale price
- discount percent
- wallet balance
- client time

### Successful charged response

```json
{
  "ok": true,
  "charged": 500,
  "balance": 2100,
  "plan": "epic",
  "base_ac_price": 800,
  "sale_ac_price": 500,
  "discount_active": true,
  "sale_ends_at": "2026-10-07T20:00:00+00:00",
  "server_now": "2026-10-07T18:01:03.2+00:00"
}
```

Use `charged` and `balance` from this response as authoritative. Refresh wallet history and item entitlement after success.

### Successful zero-charge cases

The same RPC may return `ok=true, charged=0` with one of these conditions:

- `weekly_free=true`
- `message=already_unlocked`
- `message=already_entitled`

Treat all three as successful entitlement. Continue to download rather than showing a payment error.

## 8. Error handling

| Server message | Console behavior |
|---|---|
| `sign_in_required` | Open/restart device linking. Never retry as anon. |
| `not_found` | Remove the unavailable item after catalog refresh. |
| `not_priced` | Hide AC action, refetch pricing, keep subscription action. |
| `not_enough_ac` | Show required/current balance if present and direct the user to wallet top-up on phone. |
| `wallet_not_found` | Refresh profile/wallet once; if unchanged, show a support error. |
| HTTP `401` | Refresh/relink session, then retry only after explicit user action. |
| HTTP `403` | Do not retry automatically; log the RPC and role context without tokens. |
| HTTP `404` for RPC | Backend migration is missing/wrong project; disable AC purchasing and report deployment fault. |
| HTTP `429` | Respect backoff; do not duplicate a purchase tap. |
| timeout / network error | Keep entitlement unchanged, re-enable action, and let the user retry. |

Never display a failed purchase as successful. Never decrement a locally cached wallet before the server confirms success.

## 9. Race and idempotency behavior

The server locks the plan-pricing row while choosing the price and locks the user's wallet while charging. It also uses a permanent request key per user/item.

Expected outcomes:

- Two simultaneous purchase requests: one charge, one `already_unlocked` result.
- Admin changes a price during purchase: the purchase resolves entirely against either the old locked row or the new row, never a mixed value.
- Offer expires between display and tap: the server may charge the current base price because it decides at request time. Keep polling/countdown accurate and show the authoritative `charged` result.
- User becomes subscribed during purchase: the server can return `already_entitled` with zero charge.
- Thursday gift activates during purchase: the server can return `weekly_free=true` with zero charge.

Do not add a client-generated transaction or request key and do not write directly to `ac_wallets` or `ac_transactions`.

## 10. Acceptance test matrix

Run these tests against a non-production user before release:

1. Epic base price appears on both PS4 and PS5 within five seconds of a phone save.
2. Legendary base price updates independently.
3. Start an Epic offer: old/new prices and countdown appear on Android, PS4, and PS5.
4. Disconnect network before expiry: countdown reaches zero and UI returns to base price.
5. Change the console date/time: countdown and expiry remain correct.
6. Buy before expiry: exactly the sale amount is charged once.
7. Buy after expiry: server returns the base charge; no stale sale is accepted.
8. Double-press purchase / send two concurrent requests: only one transaction exists.
9. Insufficient balance: no wallet or unlock row changes.
10. Existing Epic subscriber opens Epic content without AC charge.
11. Free user uses AC alternative for an Epic item and keeps permanent access afterward.
12. Thursday weekly Epic item returns zero charge.
13. Realtime disconnected: five-second polling still receives an Admin change.
14. Polling delayed: Realtime invalidation triggers an immediate snapshot refetch.
15. Expired access token: no anonymous purchase attempt and no local balance change.
16. `base_ac_price=0`: AC action is hidden while subscription remains available.

## 11. Release checklist

- [ ] Migration 52 applied to project `stbmescmexzcszyyambq`.
- [ ] Epic and Legendary base prices configured in phone Admin.
- [ ] Snapshot returns exactly two plan rows and server epoch time.
- [ ] Realtime subscription installed as invalidation only.
- [ ] Five-second foreground polling installed.
- [ ] Monotonic countdown installed.
- [ ] AC action is not blocked by the subscription-required state.
- [ ] Subscription action remains available beside AC.
- [ ] Purchase sends only `p_item_id`.
- [ ] All zero-charge success cases handled.
- [ ] Error table implemented and localized in all eight supported languages.
- [ ] Race/idempotency test passed on both PS4 and PS5.
