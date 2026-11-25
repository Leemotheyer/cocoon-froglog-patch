# Froglog smart folder — worker implementation brief

**Repo:** cocoon-froglog-patch (Cocoon Shell 3.06 + Froglog)  
**Goal:** Add a native Cocoon **smart folder type** that lists **Cocoon library games** matching a **Froglog-backed filter**, so Froglog and Cocoon feel like one shell.  
**Audience:** Implementing agent with dex/smali + Java experience on this patch.

---

## 1. Executive summary

Implement a new smart folder type (**Froglog**) with a user-selectable **mode**:

| Mode (UI label) | Froglog source |
|-----------------|----------------|
| **Up Next** | `GET /api/wishlist` |
| **In progress** | Library games `In Progress` + live **`active`** |
| **Completed** | Library games `Completed` (non-live only) |
| **Dropped** | Library games `DNF` / `dnf` + live **`dormant`** |

Membership = Cocoon `gameId`s whose title/platform **match** entries in that filtered Froglog set.

**Matching is read-only:** never call `FroglogStore.link`, `unlink`, or `decline`. **Do** read existing `link_*` prefs and treat them as **highest priority**.

**Scale:** Cocoon libraries can have **thousands** of games; matching must stay fast (indexed passes + cached member sets).

**Offline:** Folder must **open and browse** using the **last cached** Froglog API payloads; refresh when network returns.

---

## 2. Non-goals

- Do **not** auto-create or change Cocoon→Froglog mappings.
- Do **not** change session sync, presence, or queue behavior.
- Do **not** require the user to maintain regex rules.
- Do **not** depend on public `GET /users/:name/games` for signed-in folders (use signed-in **`GET /games`** + **`GET /live-service`** so **private** library rows count).

---

## 3. Product behavior

### 3.1 Creating / editing a folder

- **Start → New → Smart folder** (same flow as Platform / Genre / Custom).
- New type: **Froglog** (icon: reuse `froglog_title_icon` or friends frog).
- Required setting: **Mode** dropdown — Up Next | In progress | Completed | Dropped.
- Persist mode on the folder entity (see §6).
- Editing the folder changes mode; contents recompute.

### 3.2 Signed out

- Folder opens **empty** (or mapping-only subset if you choose — **default: empty**).
- Message: sign in via Froglog pod; optional action to open pod.

### 3.3 Offline

- Use **disk cache** of last successful `/games`, `/live-service`, `/wishlist` (see §5).
- Recompute membership against **local** Cocoon DB + cached Froglog + local mappings.
- Show non-blocking **“Offline · updated &lt;time&gt;”** (user timezone) when serving stale cache.
- No network required to **browse**; updates when online + refresh runs.

### 3.4 Live service rules (confirmed)

| `live_service_status` | Folder |
|----------------------|--------|
| **`active`** | **In progress** |
| **`dormant`** | **Dropped** |

Regular (non-live) games use `status` / `dnf` only for Completed / In progress / Dropped.

---

## 4. Froglog filter specification

Implement a pure function:

`List<FroglogEntry> filterByMode(mode, gamesJson, liveJson, wishlistJson)`

Each **`FroglogEntry`** should carry at least:

- `target` — `game:<id>` or `live:<id>` (wishlist: use `wishlist:<id>` or `game:0` + title only if no library id)
- `title`, `platform` (nullable), `live` boolean
- Raw status fields for debugging

### 4.1 Mode rules

**Up Next**

- Parse `wishlistJson` array only.
- Every row with non-empty `title` is in the set.
- Usually **no** `game:<id>` until user adds to library; matching is mostly tier-2 fuzzy.

**In progress**

- From `gamesJson`: include if `!live` and `status` equals **`In Progress`** (case-insensitive trim).
- From `liveJson`: include if `live_service_status` equals **`active`**.

**Completed**

- From `gamesJson` only: `!live` and `status` equals **`Completed`**.
- Exclude all live-service rows from this mode.

**Dropped**

- From `gamesJson`: `!live` and (`status` equals **`DNF`** OR `dnf == true`).
- From `liveJson`: `live_service_status` equals **`dormant`**.

**Exclude from all modes (unless Froglog adds them later):** treat **`Imported`** and empty status like today’s pod filters — **not** In progress / Completed / Dropped unless explicitly listed above.

Reuse parsing patterns from `FroglogGames.collect` / `accepts` where possible; extend with live dormant → dropped and wishlist source.

---

## 5. Offline cache

Add **`FroglogLibraryCache`** (or similar) in Java:

| Key | Content |
|-----|---------|
| `cache_games` | Raw body `GET /api/games` |
| `cache_live` | Raw body `GET /api/live-service` |
| `cache_wishlist` | Raw body `GET /api/wishlist` |
| `cache_username` | Must match current `FroglogStore.username` or invalidate |
| `cache_at_ms` | Last successful fetch time |

Storage: `SharedPreferences` (`froglog_widget`) or separate file under app private storage.

**Refresh when online:**

- Sign-in success
- Opening any Froglog smart folder if cache older than TTL (suggest **30 min**, align with pod/presence cadence)
- Optional: after library scrape (if hook found)

**Fetch:** use existing `FroglogClient.request` with token; on failure keep previous cache.

**Sign-out:** clear token; either clear cache or keep per-user cache keyed by username (document choice — prefer **clear on sign-out** for privacy).

---

## 6. Matching algorithm (two tiers)

Public API for Cocoon hook:

```text
Set<Long> resolveMemberGameIds(Context context, String mode)
boolean isMember(Context context, String mode, long cocoonGameId)  // optional; can use cached set
```

### 6.1 Tier 1 — Stored mappings (priority)

Read once per resolve:

- `FroglogStore.mappings(context, fallback)` or `FroglogLinks.read(prefs.getAll(), fallback)`.
- Build **`Map<String, List<String>> frogTarget → linkKeys`** where value is `game:<id>` / `live:<id>` and key is mapping link key (without `link_` prefix).

Build **Cocoon snapshot** once (§6.3):

- **`Map<String, List<Long>> linkKey → cocoonGameIds`**

For each **`FroglogEntry`** in the mode-filtered set:

- If `entry.target` is in mapping index, for each `linkKey` add all `cocoonGameIds` for that linkKey to **`result`**.

Skip **`DECLINED`** (`no`) link keys entirely — they must not appear via tier 1 or tier 2.

**Never write prefs in this path.**

### 6.2 Tier 2 — Fuzzy (read-only)

For each Cocoon row **not** already in `result`:

- If `FroglogStore.declined(title, platform)` → skip.
- Try **exact** hit: `HashMap<normalizeTitle(frogTitle), entries>` built from filtered Froglog list.
- Else **`FroglogMatch.best(filteredAsFroglogGames, title, platform)`** with threshold unchanged (**score ≥ 80**).
- On hit, add `gameId` to `result`. **Do not** call `FroglogStore.link`.

**Ambiguity:**

- One Froglog entry → multiple Cocoon games over threshold: include **all** (multi-platform libraries).
- Multiple Froglog entries → one Cocoon game: include **once**.

### 6.3 Cocoon snapshot (performance)

Single read-only query (extend `CocoonLibrary`):

- All non-hidden games (match Cocoon smart-folder conventions once discovered in dex).
- Fields: `gameId`, `title`, `displayName`, `platformId`.
- For matching, try **both** title and `displayName` as separate rows **or** pick one canonical rule and document it (recommend: match **primary title** first; if no hit, try **displayName** if different).

Precompute per row:

- `linkKey = FroglogMatch.linkKey(title, platformId)`
- `normalizedTitle = FroglogMatch.normalizeTitle(title)`

Target: **O(C)** build, **O(C)** exact map, tier-1 **O(F × mappingsPerTarget)** with small constants.

Avoid **O(C × F)** full scoring: fuzzy only against **filtered F** (typically ≪ C), and only for **unmapped** Cocoon rows.

### 6.4 Result cache

Cache **`Set<Long>`** per:

```text
(mode, cocoonLibraryRevision, froglogCacheHash, mappingsHash)
```

- **`cocoonLibraryRevision`:** cheap fingerprint (e.g. count + max `lastPlayed` + sample hash from `games` table).
- **`froglogCacheHash`:** hash of three cached JSON strings.
- **`mappingsHash`:** hash of all `link_*` keys/values.

Invalidate when any component changes. Recompute on background thread if resolve takes &gt; ~16ms; folder UI can show previous set with spinner on first open after invalidation.

---

## 7. Cocoon integration (dex / smali)

This patch does **not** yet touch folders. Follow patterns in `froglog/tools/PatchCatalog.java` (friends tab, pods, catalog, menu).

### 7.1 Discovery spike (required first PR step)

Decode `work/cocoon-306.apk` → find and **document in this file or `SMART_FOLDER_DEX_NOTES.md`**:

1. Smart folder **type enum** (Favorites, Platform, Genre, Custom, …).
2. **Folder entity** / Room table: where type + custom rule live.
3. **Membership evaluation** method(s): “list game IDs for smart folder X” or “is game Y in folder X”.
4. **Create/Edit folder UI** compose/screens: where to add Froglog type + mode dropdown.
5. Whether smart folders store **computed shortcuts** or **query on open** (drives cache + invalidation).

Search terms: `SmartFolder`, `smart folder`, `Custom`, regex, `FolderType`, `Genre`, `Favorites`.

### 7.2 Patch plan

| Area | Action |
|------|--------|
| Enum | Add `FROGLOG` constant + `<clinit>` entry (mirror `FROGLOG` friends tab patch). |
| Persistence | Store `froglogMode` — prefer existing JSON **`rule` / config** field if available: e.g. `{"froglogMode":"IN_PROGRESS"}`. Avoid Room migration if possible. |
| UI | When type == FROGLOG, show mode picker; hide regex field. |
| Evaluator | If folder is Froglog type, call `FroglogSmartFolder.resolveMemberGameIds(context, mode)` instead of built-in rule. |
| Strings | `strings.xml`: type name, mode labels, offline hint. |

Bridge class (smali → Java):

```text
Lrip/moth/cocoonshell/froglog/FroglogSmartFolder;
  resolveMemberGameIds(Landroid/content/Context;Ljava/lang/String;)Ljava/util/Set;
```

Use **`classes7.dex`** for new Java (same as rest of Froglog).

### 7.3 Threading

If evaluator runs on main thread, **`resolveMemberGameIds` must use cached `Set<Long>`** and only trigger async refresh. Never block UI on network.

---

## 8. New Java modules (suggested)

| Class | Responsibility |
|-------|----------------|
| `FroglogSmartFolderMode` | Enum + persistence string constants |
| `FroglogSmartFolderFilter` | `filterByMode` on JSON |
| `FroglogLibraryCache` | Fetch + disk cache + TTL |
| `FroglogSmartFolderMatcher` | Tier 1 + tier 2 + indexes |
| `FroglogSmartFolder` | Facade: cache refresh, resolve, offline banner text |
| Extend `CocoonLibrary` | `snapshotForMatching(Context)` → list of rows |

Keep **network and Android** in facade/cache; keep **filter + matcher** unit-testable without Android (pass JSON + mapping maps + cocoon rows).

---

## 9. Existing code to reuse

| Need | Location |
|------|----------|
| API base + auth | `FroglogClient.BASE`, `FroglogStore.token` |
| Title/platform normalize + score | `FroglogMatch` |
| Mapping read | `FroglogLinks`, `FroglogStore.mappings`, `mappedTarget`, `declined` |
| Parse library JSON → games | `FroglogGames.collect` |
| Status labels live/dormant | `FroglogGames.statusLabel` |
| Wishlist fetch | `FroglogClient` (`GET /wishlist`, `wishlistTitles`) |
| Cocoon DB read pattern | `CocoonLibrary.sessionsSince` / `all` |

---

## 10. Testing

### 10.1 Unit tests (no Android)

Add to `froglog/build.sh` javac list + run main:

- **`FroglogSmartFolderFilterTest`** — fixtures for each mode including live active/dormant, DNF, wishlist.
- **`FroglogSmartFolderMatcherTest`** — tier 1 beats tier 2; declined excluded; multi-platform; 5k cocoon rows perf smoke (&lt; 500ms on CI JVM for cached indexes).

### 10.2 Manual / device

- Signed in, online: create each mode folder; spot-check vs Froglog pod filters.
- Mapping: map Cocoon A → Froglog game G; G in Completed folder → A appears even if fuzzy would fail.
- Declined: game excluded.
- Offline: airplane mode, folder still populated from cache; banner visible.
- Sign out: folder empty + message.
- Large library: no ANR opening folder.

---

## 11. Acceptance criteria

- [ ] New smart folder type **Froglog** in Cocoon folder picker with four modes.
- [ ] Folder lists only Cocoon games matching filtered Froglog set per §4.
- [ ] **Tier 1 mappings** take precedence; **no new mappings** written by folder code.
- [ ] **Live active → In progress**; **live dormant → Dropped**.
- [ ] **Offline browse** with cached Froglog data; **online refresh** updates contents.
- [ ] Opening folder with **~5k** Cocoon games does not ANR (cached member set).
- [ ] `./froglog/build.sh` passes including new unit tests.
- [ ] README bullet under “What it adds” (one short paragraph).

---

## 12. Implementation phases (recommended)

| Phase | Deliverable |
|-------|-------------|
| **0** | Dex discovery doc + spike hook logging “FROGLOG folder evaluate” |
| **1** | `FroglogSmartFolderFilter` + `FroglogSmartFolderMatcher` + tests |
| **2** | `FroglogLibraryCache` + wire refresh on sign-in / folder open |
| **3** | Enum + persistence + evaluator hook (one mode: Up Next) |
| **4** | All four modes + folder UI mode picker |
| **5** | Result cache + background recompute + offline banner |
| **6** | README, manual test notes, polish strings |

---

## 13. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Obfuscated folder API changes between Cocoon versions | Pin to 3.06; document types in `SMART_FOLDER_DEX_NOTES.md` |
| Room migration required | Prefer encoding mode in existing `rule` JSON |
| Main-thread evaluator | Mandatory member-set cache |
| Wishlist titles ≠ ROM names | Expected; mappings fix priority cases |
| Cache staleness | TTL + “Offline · updated …” |

---

## 14. UI copy (English)

- Type: **Froglog**
- Modes: **Up Next**, **In progress**, **Completed**, **Dropped**
- In progress subtitle (optional): *Includes active live games*
- Dropped subtitle (optional): *Includes dropped and dormant live games*
- Offline: **Offline — Froglog list from &lt;date/time&gt;**
- Not signed in: **Sign in to Froglog to load this folder**

---

## 15. Reference: conversation decisions

- Smart folder fills from Froglog; **no auto-mapping**.
- **Read mappings first**, then `FroglogMatch` fuzzy.
- **Efficient** matching for thousands of Cocoon games (indexes + cached sets).
- **Offline** browse with cached API payloads.
- **Live active = In progress**; **live dormant = Dropped**.

---

*End of brief. Update §7.1 with concrete class/method names after the discovery spike.*
