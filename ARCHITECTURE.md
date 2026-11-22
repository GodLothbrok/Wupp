# Connection, Rate-Limit & Protocol Architecture

This page breaks down every safeguard Wupp uses across the Kotlin app (`WaRealtimeService`, `StealthMatrixManager`) and native Rust bridge (`rustbridge/src/`, `whatsapp-rust`) to maintain connection stability and avoid rate-limiting on the multidevice protocol.

## 1. Companion Identity & Local Storage
- **Browser fingerprint (`client.rs`, `pair_with_phone_number`):** Wupp links as a single Chrome companion (`PlatformType::CHROME`) handling messaging, history sync, and native E2EE VoIP (`voip-mlow` / `voip-encoded` / `voip-relay-native`) over one linked session.
- **Persistent SQLite state (`wa_rust.db`):** Device keys, Signal sessions, LID/PN mappings, media metadata (`wa_media_meta`), contact cache (`wa_contacts`), sync flags (`wa_meta_flags`), and the avatar miss cache (`wa_avatar_miss`) live inside a shared WAL SQLite database.

## 2. Preventing `429: rate-overlimit` Sync Storms
- **Serialized 10-minute global cooldown (`sync_contacts_and_avatars`):** Full group (`list_participating`), channel (`list_subscribed`), and contact sweeps are protected by `contact_sync_active` and `last_sync_unix`, allowing at most one automatic global sweep every `10` minutes (`600` seconds).
- **Debounced avatar-only batches (`requestSingleAvatarIfNeeded`, `avatars:` prefix):** UI avatar requests are debounced over a `700ms` window into batches of up to `35` JIDs using the `avatars:` prefix, which skips `get_user_info` (`usync`) and queries `Preview`-only profile pictures with `350–650ms` randomized jitter.
- **15-minute channel freshness (`should_sync_newsletter`):** Subscribed channels track their last sync timestamp in `wa_meta_flags` so reconnects skip re-fetching post history for channels synced within the last `15` minutes, while pacing remaining fetches with `500–900ms` randomized jitter.
- **Global 60-second rate-limit circuit breaker (`trip_rate_limit`):** Any `429`, `503`, or rate-limit error immediately sets `rate_limit_until_unix` for `60` seconds (`180` seconds on stream `429`), halting background avatar, channel, and history recovery loops.

## 3. Bounded Concurrency, Jitter & Miss Caching
- **Bounded download semaphore (`auto_dl_sem`):** Automatic media downloads run through a `4`-permit Tokio semaphore with in-flight deduplication (`download_inflight`).
- **12–24 hour SQLite miss cache (`wa_avatar_miss`):** Accounts with no profile photo (`"none"`) are cached for `24` hours, privacy-restricted avatars (`"hidden"`) for `12` hours, and transient lookup errors for `15` minutes, with a one-time upgrade flag (`avatar_miss_cleared_v2`) so restarts never re-query missing avatars.

## 4. Per-Chat Cooldowns for History & Media Recovery
- **Coalesced on-demand history sync (`hist:{chat_jid}`):** Missing-media recovery coalesces `fetch_message_history` (`count = 50`) to at most **1 request per chat every `30` seconds** (paired with a `28s` Kotlin anchor guard), recovering all visible media on screen in a single batch.
- **Placeholder resend & group backfill guards:** Peer placeholder resend requests (`send_pdo_placeholder_resend_request`) are throttled per chat (`15s`) and per message (`60s`), and broken-sender group backfills (`request_group_history_backfill`) run at most once every `6` hours per group.
- **Contact verification cooldown (`last_verify_unix`):** Bulk number checks (`verify_numbers_on_whatsapp`) enforce a `30`-second cooldown and a `25`-number cap per batch.

## 5. Outbound Error 463 Companion Guard
- **Unknown-number send block (`StealthMatrixManager.canInitiateOutboundMessage`):** Before sending any text or media in a 1-on-1 chat (`WaRealtimeService.kt`), Wupp checks Room for prior message history.
- If a contact has **0 prior messages**, outbound sending is blocked locally (`ERROR_463_GUARD`) so you start new unknown-number conversations from your primary phone first. Groups, statuses, and newsletters remain unaffected.

## 6. Balanced Privacy Defaults & 14-Day Heartbeat
- **Non-extreme default receipts (`GlobalStealthConfig`):** Blue read ticks stay enabled by default (`hideReadReceipts = false`) unless you opt in to Ghost Read globally or per chat.
- **14-day primary phone tracker (`computeHeartbeatStatus`):** Wupp monitors the 14-day linked-device window and warns starting at day `10` (`WARNING_THRESHOLD_DAYS`) so your primary phone checks in before the session expires.

## Protocol Boundaries
Wupp connects via `whatsapp-rust` as an independent companion client. Standard protocol limits apply if an account sends bulk or automated unsolicited messages or triggers spam reports.
