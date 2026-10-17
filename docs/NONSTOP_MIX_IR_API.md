# NONSTOP MIX IR API (client contract, Issue #535)

Status: the client in this repository implements this contract. The server side
belongs in `BMS-Mania/IR` and is **not implemented yet**; until it is deployed,
the client shows "このIRサーバーはNONSTOP MIXにまだ対応していません" for every IR
action and keeps working locally (editing, saving, playing, records).

Pair this document with a server Issue in `BMS-Mania/IR` and record the paired
server commit/PR in oraja Issue #535 (AGENTS.md "Protocol changes").

## Transport

- `POST https://<arena-server-authority>/new/api/oraja/nonstop_mix.json`
  - The authority is taken from the player's configured BMS-IR Arena server URL,
    exactly like `/new/api/oraja/my_table.json`.
- Request and response bodies are JSON (`Content-Type: application/json; charset=utf-8`).
- Every request carries `"action"` and `"client_version"` (Arena client version).
- Authenticated requests also carry `"player_id"` (int) and `"passmd5"`
  (lower-case MD5 hex of the BMS-IR password), the same pair the My Difficulty
  Table endpoint already accepts. `list`, `get` and `ranking` send the pair when
  a login is configured (so `own` can be computed) but must also work without it.
- Response: `{"ok": true, ...}` on success; `{"ok": false, "error": "<code>"}`
  otherwise. HTTP 404 without a JSON body is shown as "server does not support
  NONSTOP MIX yet".
- Error codes the client translates: `authentication_failed`, `not_found`,
  `not_owner`, `invalid_recipe`, `revision_mismatch`, `client_not_allowed`,
  `rate_limited`, `unsupported`. Anything else is shown verbatim.
- Client limits: response ≤ 8 MiB; recipe ≤ 512 KiB; ≤ 64 tracks.

## Recipe (`recipe`)

The shared recipe is produced by `MixJson.toJson(plan, false)`. It never
contains audio, local paths or the local set id.

```json
{
  "format": "bmsir-nonstop-mix",
  "version": 1,
  "name": "Night drive", "description": "...", "author": "",
  "mode": "beat-7k",
  "judge": "NORMAL",            // VERY_EASY|EASY|NORMAL|HARD|VERY_HARD|AVERAGE|STRICTEST
  "totalScale": 1.0,            // 0.25..4
  "normalizeLoudness": true,
  "origin": "manual",           // or "auto:v1"
  "irId": "", "irRevision": 0,
  "tracks": [
    {"sha256": "64 hex", "md5": "32 hex", "title": "...", "subtitle": "", "artist": "...",
     "genre": "...", "bpm": 128.0, "level": 7,
     "inMs": 0, "outMs": -1,     // chart ms; -1 = to the end
     "gainDb": 0.0,              // -24..12
     "random": [1, 2],           // optional fixed #RANDOM branch
     "lockSong": false, "lockRange": false}
  ],
  "transitions": [              // tracks.length - 1 entries
    {"type": "BLEND",            // BLEND|CUT|FADE_OUT|FADE_IN
     "curve": "EQUAL_POWER",     // LINEAR|EQUAL_POWER|LATE|EARLY
     "overlapMs": 8000, "handoffMs": 4000, "gapMs": 0, "locked": false}
  ]
}
```

Server validation (reject with `invalid_recipe`):

- `format` and `version` as above (`version` ≤ 1 today).
- 1–64 tracks, `transitions.length == tracks.length - 1`.
- Every track has a 64-hex `sha256` or 32-hex `md5`.
- Numeric ranges as noted; strings length-limited (name 80, description 1000,
  title/artist 200, genre 100 code points).
- Optional but recommended: every track hash exists in the IR song table.

The client also sends `identity` (SHA-256 hex of what is played, see
`MixJson.playIdentity`) and `duration_ms` (estimate). Store them; scores are
bound to `(id, revision)` and the server should reject a score whose `identity`
differs from the stored revision's identity (`revision_mismatch`).

## Actions

### `list`

Request: `query` (≤ 80 chars, matches name/author/genre/track titles),
`mode` (`beat-7k` …, empty = all), `sort` (`new` | `popular` | `mine`),
`page` (0-based), `page_size` (≤ 50).

Response:

```json
{"ok": true, "total": 123, "sets": [
  {"id": "abc123", "revision": 2, "name": "...", "author_name": "...", "description": "...",
   "mode": "beat-7k", "track_count": 8, "duration_ms": 900000, "plays": 41,
   "updated_at": 1760000000000, "own": false, "genres": ["HOUSE"],
   "tracks": [{"sha256": "...", "md5": "...", "title": "...", "artist": "..."}]}
]}
```

`tracks` lets the client show which songs the player already has before
downloading the recipe. Unpublished sets are not listed (except `mine`).

### `get`

Request: `id`. Response: `{"ok": true, "set": {"id", "revision", "author_name",
"recipe": {…}}}` with the latest published revision.

### `publish` (login)

Request: `recipe`, `identity`, `duration_ms`, optional `id`.

- Without `id`: create a set owned by `player_id`, revision 1.
- With `id`: only the owner may publish; creates revision `n + 1`. Older
  revisions stay readable for their scores but `get`/`list` return the newest.

Response: `{"ok": true, "id": "...", "revision": n}`.

### `unpublish` (login)

Request: `id`. Owner only. Hides the set from `list`/`get`; scores are kept.

### `submit_score` (login)

Request:

```json
{"id": "...", "revision": 2, "identity": "sha256 hex",
 "ex_score": 1234, "max_ex_score": 2000, "clear": 5, "gauge": 2,
 "bad_poor": 12, "max_combo": 400, "notes": 1000, "finished": true,
 "tracks": [{"sha256": "...", "notes": 120, "judges": [PG, GR, GD, BD, PR, MS], "unreached": 0}]}
```

- Only finished full-set plays of a downloaded or published revision are sent;
  previews and partial tries are never sent by the client.
- `clear` uses the client's ClearType ids (0 NO PLAY … 10 MAX).
- Server checks: identity matches the revision, `ex_score ≤ max_ex_score`,
  `max_ex_score == notes * 2`, per-track totals add up, and the Arena client
  version/build is allowed (`client_not_allowed` otherwise). Keep the best
  score per player and revision, and count plays.

Response: `{"ok": true, "rank": 3}`.

### `ranking`

Request: `id`, `revision`. Response:

```json
{"ok": true, "scores": [{"player_name": "...", "ex_score": 1234, "max_ex_score": 2000,
  "clear": 5, "bad_poor": 12, "played_at": 1760000000000}]}
```

## Not part of this API

- Ordinary chart scores: a NONSTOP MIX play never reaches the ordinary chart
  endpoint (`forceNoIRSend`), and its record never enters the local score DB.
- Arena matches: NONSTOP MIX cannot be started while connected to Arena.
- Ratings, Dan or course records.
