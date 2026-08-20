
# URL Shortener

A backend-focused URL shortener built with **Java 21, Spring Boot 3, PostgreSQL, and Docker**. The project goes beyond storing `{shortCode -> URL}` in a basic map by implementing Base62 code generation, a custom LRU cache, token-bucket rate limiting, URL expiry, click analytics, and a small browser UI.

[![Live Demo](https://img.shields.io/badge/Live%20Demo-Open%20App-blue?style=for-the-badge&logo=render)](https://url-shortener-43x2.onrender.com/)

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-supported-blue?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-supported-2496ED?logo=docker)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## What the project does

The application provides three main flows:

1. **Create a short URL** from a long URL.
2. **Redirect** from the short code to the original URL.
3. **Inspect analytics** for a short code.

It also includes optional custom aliases and expiry dates, request rate limiting, an in-memory cache for redirect lookups, and scheduled cleanup of expired URLs.

## Features

- **Base62 short codes** generated from database IDs for auto-generated URLs
- **Custom aliases** with collision checking
- **O(1) LRU cache** implemented using `HashMap` + doubly linked list
- **Token-bucket rate limiter** per client IP
- **URL expiry** with `410 Gone` for expired links
- **Click analytics** including timestamp, referrer, client IP, and total click count
- **Scheduled cleanup** for expired mappings
- **Freed-code reuse** for previously released auto-generated codes
- **PostgreSQL** persistence
- **H2 profile** for local development/testing without PostgreSQL
- **Dockerfile** for containerized deployment
- Small static frontend for trying the application in a browser

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Web | Spring MVC / REST |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Local DB option | H2 |
| Build | Maven |
| Containerization | Docker |
| Frontend | HTML/CSS/JavaScript |

## Architecture

```text
                        Client / Browser
                              |
                 +------------+-------------+
                 |                          |
          POST /api/shorten          GET /{shortCode}
                 |                          |
                 +------------+-------------+
                              |
                    UrlShortenerController
                              |
              +---------------+----------------+
              |               |                |
      RateLimiterService  UrlShortenerService  AnalyticsService
              |               |                |
        TokenBucket      LRU Cache              |
                              |                |
                         PostgreSQL <-----------+
```

### Redirect path

The redirect endpoint is the hot path of the application:

```text
GET /{shortCode}
      |
      v
  LRU cache
      |
   hit | miss
      |    |
      |    v
      | PostgreSQL
      |    |
      +----+
           |
           v
   check expiry
           |
           v
   record click + analytics
           |
           v
      HTTP 302
```

The cache is **cache-aside**: the application checks the LRU cache first, queries PostgreSQL on a miss, and then inserts the result into the cache.

Click-count updates and click-event persistence currently happen **synchronously as part of the redirect request**. They are kept in separate service logic, but they are not an asynchronous queue-based analytics pipeline.

## How short codes are generated

For an auto-generated URL, the application first creates the database row so it receives an auto-increment ID. That ID is then encoded in Base62:

```text
Database ID  ->  Base62  ->  Short Code
     125     ->   "cb"   ->  /cb
```

Because the code comes from the database ID, two auto-generated URLs cannot receive the same generated code while using distinct IDs.

Custom aliases are different: because the user chooses the value, the application explicitly checks whether the alias already exists and also relies on the database uniqueness constraint.

## LRU Cache

The redirect cache is implemented manually using:

```text
HashMap<String, Node>
        +
Doubly linked list
```

This gives:

| Operation | Complexity |
|---|---:|
| Cache lookup | O(1) average |
| Insert/update | O(1) average |
| Move recently used item | O(1) |
| Evict least recently used item | O(1) |

The current cache is **in-memory and local to a single application instance**.

## Rate Limiting

Short URL creation is protected with a **token-bucket rate limiter**.

The default configuration is:

```text
Bucket capacity:       10 tokens
Refill rate:           10 tokens / minute
```

A request consumes one token. When no token is available, the API returns `429 Too Many Requests`.

The rate-limiter state is stored in a `ConcurrentHashMap` and is therefore also **local to the current application instance**.

## URL Expiry and Code Reuse

Each URL has an expiry time. The redirect path checks expiry and returns `410 Gone` for an expired link.

A scheduled cleanup job runs once per hour by default and removes expired mappings from the database.

For auto-generated codes, the cleanup process also places released codes into a small reuse pool. New auto-generated URLs try that pool before creating a new database row and Base62 code.

This is separate from database sequence behavior: deleting a database row does not normally cause PostgreSQL/H2 to reuse its old auto-increment ID.

## Analytics

For each redirect, the application records:

- short code / URL mapping
- click timestamp
- referrer
- client IP

The analytics endpoint returns total clicks and the most recent click information.

> **Privacy note:** client IP addresses are currently stored as received. A production system handling real users would need an explicit retention/privacy policy and possibly anonymization or hashing.

## REST API

### Create a short URL

```http
POST /api/shorten
Content-Type: application/json
```

Example request:

```json
{
  "originalUrl": "https://example.com/a/very/long/path",
  "customAlias": "example",
  "expiresInDays": 30
}
```

Response:

```json
{
  "shortCode": "example",
  "shortUrl": "http://localhost:8080/example",
  "originalUrl": "https://example.com/a/very/long/path",
  "expiresAt": "..."
}
```

### Redirect

```http
GET /{shortCode}
```

Returns:

```text
302 Found
Location: <original URL>
```

### Analytics

```http
GET /api/analytics/{shortCode}
```

Returns the short URL's metadata, total clicks, and recent click information.

## Running Locally

### Requirements

- Java 21
- Maven
- PostgreSQL, or use the H2 profile

### Option 1: H2 profile

The H2 profile is the easiest way to run the application locally without installing PostgreSQL.

```bash
git clone https://github.com/DragonC-der/URL-shortener
cd url-shortener
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Then open:

```text
http://localhost:8080
```

### Option 2: PostgreSQL

Create a database named `urlshortener`, then start the application:

```bash
mvn spring-boot:run
```

Default local values are:

```text
Host:     localhost
Port:     5432
Database: urlshortener
Username: postgres
Password: postgres
```

For a different environment, override them with:

```text
DATABASE_URL
DB_USERNAME
DB_PASSWORD
APP_BASE_URL
PORT
```

Do not commit real database credentials to GitHub.

## Docker

The repository includes a multi-stage Dockerfile.

Build the image:

```bash
docker build -t url-shortener .
```

Run it:

```bash
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/urlshortener \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e APP_BASE_URL=http://localhost:8080 \
  url-shortener
```

The application still requires a reachable PostgreSQL database when the PostgreSQL configuration is used.

## Project Structure

```text
url-shortener/
├── Dockerfile
├── .dockerignore
├── pom.xml
├── src/main/java/com/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── cache/
│   │   └── LruCache.java
│   ├── config/
│   │   └── CacheConfig.java
│   ├── controller/
│   │   └── UrlShortenerController.java
│   ├── dto/
│   │   ├── AnalyticsResponse.java
│   │   ├── ShortenRequest.java
│   │   └── ShortenResponse.java
│   ├── model/
│   │   ├── ClickEvent.java
│   │   ├── FreedCode.java
│   │   └── UrlMapping.java
│   ├── repository/
│   │   ├── ClickEventRepository.java
│   │   ├── FreedCodeRepository.java
│   │   └── UrlMappingRepository.java
│   ├── service/
│   │   ├── AnalyticsService.java
│   │   ├── ExpiryCleanupService.java
│   │   ├── RateLimiterService.java
│   │   └── UrlShortenerService.java
│   └── util/
│       ├── Base62Encoder.java
│       └── TokenBucket.java
└── src/main/resources/
    ├── application.yml
    ├── application-h2.yml
    └── static/
        └── index.html
```

## Limitations

This project is designed to demonstrate backend concepts in a compact implementation. It is **not a distributed production URL-shortening service** yet.

Important limitations:

- **LRU cache is per-instance and in memory.** Multiple application instances do not share cache state. Redis would be a natural next step.
- **Rate-limiter state is per-instance and in memory.** Multiple instances would each maintain their own buckets.
- **Rate-limiter buckets are not currently evicted**, so a very large number of distinct client IPs can increase memory usage.
- **Analytics writes are synchronous** on the redirect path. A real high-volume deployment could move click events to a message queue and process them asynchronously.
- **No authentication/authorization** is implemented; analytics are accessible by short code.
- **Client IPs are stored directly**, so privacy and retention requirements would need additional handling for real-world deployment.
- **JPA uses `ddl-auto: update`** for convenience. A production deployment would normally use database migrations such as Flyway or Liquibase.

## Possible Extensions

- Redis for distributed caching
- Redis/shared storage for distributed rate limiting
- Kafka/RabbitMQ for asynchronous analytics processing
- Authentication and per-user URL ownership
- Per-user analytics and access control
- Database migrations with Flyway/Liquibase
- Automated unit/integration tests
- QR-code generation
- Bulk URL creation API
- Metrics and observability with Micrometer/Prometheus

## What This Project Demonstrates

This project is mainly about practical backend engineering concepts:

- REST API design
- Spring Boot service/repository architecture
- JPA and relational persistence
- Base62 encoding
- Cache-aside caching
- LRU eviction
- Token-bucket rate limiting
- URL expiry and background cleanup
- Analytics/event recording
- Docker-based packaging
- Awareness of the limitations of in-memory state when moving to multiple instances

The implementation intentionally keeps the system small enough to understand while providing several useful system-design and backend interview topics to discuss.
