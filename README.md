<div align="center">

# URL Shortener

**A URL shortener built with Spring Boot** — going past the "hash a string, store in a map" version most portfolio implementations stop at: custom Base62 encoding, a hand-built LRU cache, token-bucket rate limiting, and click analytics, backed by PostgreSQL and ready to deploy on Render.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-ready-blue?logo=postgresql)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

</div>

---

## Table of Contents

- [Why this isn't the tutorial version](#why-this-isnt-the-tutorial-version)
- [Features](#features)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Algorithms Reference](#algorithms-reference-have-these-ready-to-explain)
- [REST API Reference](#rest-api-reference)
- [Deploying to Render](#deploying-to-render)
- [Known Limitations](#known-limitations)
- [Possible Extensions](#possible-extensions)
- [Project Structure](#project-structure)

## ⚠️ A note on testing before you build this

This was written in a sandbox without access to Maven Central, so **it has not been compiled or run in that environment**. Every file follows standard Spring Boot 3 conventions and was reviewed carefully. If `mvn spring-boot:run` throws an error, paste it back — it gets fixed fast.

## Why this isn't the tutorial version

The "todo-list-tier" URL shortener is: generate a random string, store `{code: url}` in a hash map, redirect on lookup. That doesn't demonstrate anything an interviewer hasn't seen fifty times this month. This version is built around four decisions specifically chosen to give you something real to defend in an interview:

1. **Base62 encoding is derived from the DB's own auto-increment ID**, not a random string — meaning collisions are structurally impossible for auto-generated codes, not just "unlikely." The only place a real collision check exists is for user-supplied custom aliases, which is exactly where the code actually needs one.
2. **The LRU cache is hand-built** (`HashMap` + doubly linked list, O(1) get/put), not `LinkedHashMap.removeEldestEntry()`. The code comments are upfront that the 5-line stdlib version is the right call in production — this version exists so the mechanics are visible and explainable.
3. **Rate limiting uses a token bucket**, not a fixed-window counter — which matters because fixed windows allow a 2x burst right at a window boundary; token buckets don't have that failure mode.
4. **Click analytics are a separate service from URL creation/resolution** — Single-Responsibility applied from the start, not retrofitted.

## Features

- 🔗 Custom Base62 short codes derived from auto-increment DB IDs — collision-free by construction
- ⚡ Hand-built O(1) LRU cache in front of every redirect (cache-aside pattern)
- 🚦 Token-bucket rate limiting per client IP
- 📊 Click analytics — total clicks, timestamps, referrers, per short code
- ⏳ Optional expiry per URL, with custom aliases and collision checking
- 🐘 PostgreSQL-backed, Render-deployment-ready out of the box

## Live page link
[Click here](https://url-shortener-43x2.onrender.com/)


(Wait a bit or visit again after some time of clicking on the link)
Service Cold start needed a bit time :)

## Quick Start

Requires Java 21, Maven, and either PostgreSQL running locally or the H2 profile for quick testing.

```bash
git clone <your-repo-url>
cd url-shortener

# Fastest path - no Postgres install needed:
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Then open **http://localhost:8080** for the demo frontend, or hit the API directly:
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path"}'
```

**With PostgreSQL** (matches the Render deployment target):
```bash
# create a local Postgres DB named urlshortener first, then:
mvn spring-boot:run
```
Connection settings default to `localhost:5432` with `postgres`/`postgres` — override via `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables, exactly how Render injects them in production.

## Architecture

```
Client
  │
  ├── POST /api/shorten          (rate-limited per IP, token bucket)
  ├── GET  /{shortCode}          (redirect - the hot path)
  └── GET  /api/analytics/{code} (click history)
  ▼
UrlShortenerController
  │
  ├── RateLimiterService   → TokenBucket per client IP
  ├── UrlShortenerService  → Base62Encoder, LruCache (cache-aside), UrlMappingRepository
  └── AnalyticsService     → ClickEventRepository
  ▼
PostgreSQL (Render-hosted)
  ├── url_mappings (indexed on shortCode)
  └── click_events (indexed on url_mapping_id)
```

### The redirect path, traced end to end

1. Request hits `GET /{shortCode}`.
2. `UrlShortenerService.resolveOriginalUrl()` checks the in-memory `LruCache` first — **cache-aside pattern**: the application code manages the cache explicitly (check cache, fall back to DB, populate cache on miss), rather than a transparent look-through cache.
3. On a cache hit, the DB is never touched — this is the entire point of putting a cache in front of the redirect path, since redirects are overwhelmingly the highest-traffic endpoint of any URL shortener.
4. On a miss, `UrlMappingRepository.findByShortCode()` hits Postgres, using the index on `shortCode` — an indexed lookup instead of a table scan is what keeps this fast even with millions of rows.
5. Expiry is checked before returning; expired links return `410 Gone` instead of redirecting.
6. A `ClickEvent` row is recorded as a side effect of the redirect — analytics never blocks the actual redirect.

<details>
<summary><strong>A routing gotcha worth knowing (and mentioning in an interview)</strong></summary>

The redirect endpoint is `@GetMapping("/{shortCode:^[^.]*$}")`, not just `@GetMapping("/{shortCode}")`. Without the regex constraint, this catch-all path variable matches *any* single path segment — including `/index.html` — and Spring routes annotated `@Controller` mappings with higher priority than static resource serving. That means the redirect handler would swallow requests for the frontend's own `index.html`, `favicon.ico`, and any other static asset at the root path, before Spring ever gets a chance to serve the actual file. Since short codes are always plain alphanumeric (never containing a dot) but static assets always have a file extension, excluding anything with a `.` in the path pattern lets those requests fall through to static resource serving instead.
</details>

## Algorithms Reference (have these ready to explain)

### Base62 encoding
Converts a numeric DB ID into a short alphanumeric string using positional-numeral conversion — the same concept as converting a number to binary or hex, just base 62 (`0-9`, `a-z`, `A-Z`) instead of base 2 or 16. ID `125` encodes to `"cb"` — division-and-remainder, reversed at the end. Decoding reverses the process. See `Base62Encoder.java`.

> **Why the first several short codes look like plain numbers:** the Base62 alphabet starts with the digits `0-9`, and DB auto-increment IDs start at 1 — so any ID under 10 encodes to itself (`encode(5) → "5"`). This is identical behavior on H2 and PostgreSQL; it's not a database difference, just small-number coincidence. Codes start including letters once the ID passes 9 (`encode(10) → "a"`, `encode(125) → "cb"`).

### LRU Cache
A `HashMap<K, Node>` for O(1) key lookup, plus a doubly linked list for O(1) reordering — moving a just-accessed node to the front, and evicting from the back when the cache is full. See `LruCache.java` for the full walkthrough in comments, including the honest caveat about lock contention under high concurrency.

### Token Bucket rate limiting
Each client IP gets a bucket that starts full and refills continuously over time (not in discrete steps). A request consumes one token; if the bucket is empty, the request is rejected with `429 Too Many Requests`. This allows short bursts up to the bucket's capacity while still enforcing a steady-state rate — unlike a fixed-window counter, which can allow double the intended rate right at a window boundary. See `TokenBucket.java`.

## REST API Reference

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/shorten` | `{ originalUrl, customAlias?, expiresInDays? }` | `{ shortCode, shortUrl, originalUrl, expiresAt }` |
| GET | `/{shortCode}` | — | `302 Found` redirect to the original URL |
| GET | `/api/analytics/{shortCode}` | — | `{ shortCode, originalUrl, totalClicks, createdAt, expiresAt, recentClicks[] }` |

Rate limit on `/api/shorten`: 10 requests per client IP, refilling at 10/minute (configurable in `application.yml`).

## Deploying to Render

1. Push this repo to GitHub.
2. Create a **PostgreSQL** instance on Render — copy its internal `DATABASE_URL`.
3. Create a **Web Service** pointing at this repo. Build command: `mvn clean package -DskipTests`. Start command: `java -jar target/url-shortener-1.0.0.jar`.
4. Set environment variables on the web service: `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` (from the Postgres instance), and `APP_BASE_URL` to your Render-assigned public URL (needed so generated short URLs point at the real deployed domain, not `localhost`).

## Known Limitations

Named directly rather than left for someone else to discover:

- **Cache is per-instance, in-memory.** Scale to multiple server instances and each one has its own cache with no shared state — a cache hit on instance A doesn't help instance B. Redis would be the standard fix for a shared cache layer.
- **Rate limiter state is also per-instance and unbounded in memory** — the `ConcurrentHashMap` of IP → bucket grows forever and doesn't survive a restart. Fine for a single-instance portfolio deployment, a real gap at scale.
- **Click IPs are stored raw**, not hashed or anonymized — a production system handling real user data would need to address this for privacy compliance.
- **No auth** — anyone can shorten a URL or view any short code's analytics, and there's no concept of ownership or accounts. Kept deliberately out of scope to keep the project focused on the caching/scaling story.

## Possible Extensions

- Redis-backed cache and rate limiter, to make both work correctly across multiple instances
- Auth + per-user URL ownership and private analytics
- QR code generation per short URL
- Bulk shorten API for programmatic use

## Project Structure

```
url-shortener/
├── pom.xml
├── src/main/java/com/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── cache/
│   │   └── LruCache.java              # hand-built O(1) LRU cache
│   ├── config/
│   │   └── CacheConfig.java            # wires LruCache as a Spring bean
│   ├── model/
│   │   ├── UrlMapping.java
│   │   └── ClickEvent.java
│   ├── repository/
│   │   ├── UrlMappingRepository.java
│   │   └── ClickEventRepository.java
│   ├── dto/
│   │   ├── ShortenRequest.java
│   │   ├── ShortenResponse.java
│   │   └── AnalyticsResponse.java
│   ├── util/
│   │   ├── Base62Encoder.java
│   │   └── TokenBucket.java
│   ├── service/
│   │   ├── UrlShortenerService.java     # core create/resolve logic
│   │   ├── AnalyticsService.java         # separate from the above - SRP
│   │   └── RateLimiterService.java
│   └── controller/
│       └── UrlShortenerController.java
├── src/main/resources/
│   ├── application.yml                   # Postgres via env vars (Render-ready)
│   ├── application-h2.yml                 # local dev fallback profile
│   └── static/index.html                    # demo frontend
└── README.md
```

---

<div align="center">

Built to demonstrate caching, indexing, and rate limiting — not just "hash a string and redirect."

</div>
