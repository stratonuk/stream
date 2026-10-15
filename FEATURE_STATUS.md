# Feature status

Legend: **Done** / **Partial** / **Not yet**

| Area | Status | Notes |
|------|--------|-------|
| Xtream login + catalogue sync | Done | Generic Xtream + M3U fallback from Strata foundation |
| Live TV playback | Done | Media3 HLS / TS |
| Movies / series playback | Done | Resume + continue watching |
| Monochrome design system | Done | Matches approved reference palette |
| Expandable sidebar nav | Done | Home, Live TV, Movies, TV Series, My List, Search, Profiles, Settings |
| UK-first category engine | Done | Multi-tag BBC/ITV/Sky/Sports/…; International hidden by default |
| Custom player controls | Done | Netflix-style overlay from foundation; restyled monochrome |
| EPG now/next | Done | XMLTV where provider supplies it — never invented |
| TMDB enrichment | Done | Requires optional API key; attribution in About |
| My List | Partial | Watchlist + favourite channels wired; polish ongoing |
| Local profiles | Partial | Active profile id persisted in DataStore; full per-profile DB scoping pending |
| Safe in-place APK updates | Done | Permanent applicationId + release cert; Room destructive fallback removed |
| Backup & restore | Done | Settings export/import of prefs, CW, favourites, history |
| Encrypted credentials | Done | Xtream password in EncryptedSharedPreferences (Keystore) |
| Category rename/reorder UI | Partial | Engine ready; settings UI subset |
| Skip intro / recap | Partial | Only when accurate timestamps exist |
| Physical Fire TV QA | Not yet | Emulator / host build verified; device pending user install |
| Alexa voice | Not yet | Not claimed |

Do not treat Gap Specifications/ docs as shipped features — many are design notes.
