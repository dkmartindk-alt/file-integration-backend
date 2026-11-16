# File Processing & Integration Backend

A robust, asynchronous Spring Boot backend designed to handle file-based integrations between a local file share and external systems. The solution ensures transactional consistency, idempotency, and fault tolerance using a message broker (RabbitMQ) and a relational database.

---

## 📋 Requirements

Files are placed on a file share. Each filename follows the pattern `<id>.zip`.

For each file, metadata must be retrieved from three external systems:

1. **Ret** – Retrieved using `<id>`
2. **Sag** – Retrieved using `<id>`
3. **Prio** – Uses `Sag.id` to determine a Low or High priority

Once metadata has been collected, the file and its associated metadata must be transferred to two target systems:

- **System 1** (Jira)
- **System 2** (Archive Store)

---

## 🏗️ Architecture & Design Principles

The project is built around a **"Simple Queue / Smart Database"** architecture:

1. **File System:** A Scanner monitors an input folder for new files. To avoid race conditions with files still being written, the file size is checked twice with a short interval — only files with a stable size are moved.
2. **Queue (RabbitMQ):** Acts solely as a lightweight buffer (carries only the filename as payload), keeping the broker free of heavy data and easy to scale.
3. **Database (State Store):** Serves as the Single Source of Truth. It tracks file state (`PROCESSING`, `COMPLETED`, `FAILED`), which external systems have received data (Jira, Archive Store), metadata from Ret/Sag/Prio, and the number of retry attempts.
4. **Idempotency:** If a Worker crashes mid-process and receives the message again, it checks the database and skips already-completed transfers (no duplicate data in external systems).

---

## 🔄 Processing Flow

```text
[ File Share /input ]
      │ (Scanner: cron + on startup)
      ▼
[ Stability Check (file size) ]
      │
      ├─► Stable?    ──► Move to /processing ──► Send filename to file.processing.queue
      ├─► Unstable?  ──► Skip (still being written)
      └─► Duplicate? ──► Move to /failed/filescanner

=== WORKER: Phase 1 – Caching ===
[ file.processing.queue ] ──► Create/fetch Case in DB (PROCESSING)
                                  │
                                  ▼
                          Fetch metadata via CaseDataFetcher:
                            • CaseMetaData (from Ret, keyed by <id>)
                            • CasePriority (from Prio, keyed by Sag.id → Low/High)
                            • CasePrivilege (from Sag, keyed by <id>)
                                  │
                          ├─► OK?   ──► Send caseId to file.transfer.queue (with priority)
                          └─► Fail? ──► Retry (max 5 attempts ──► FAILED → /failed/worker/caching)

=== WORKER: Phase 2 – Transfer ===
[ file.transfer.queue ] ──► Fetch Case from DB
                                  │
                                  ▼
                          Publish via CasePublisher:
                            • JiraService → System 1 (Jira)
                            • ArchiveStoreCaseService → System 2 (Archive Store)
                                  │
                          ├─► All OK? ──► Status = COMPLETED & Move file to /completed
                          └─► Fail?     ──► Retry (max 5 attempts ──► FAILED → /failed/worker/transfer)
```

---

## 🛠️ Technology Stack

* **Java 17+**
* **Spring Boot** (Spring Data JPA, Spring AMQP)
* **RabbitMQ** (Message Broker)
* **H2 / PostgreSQL / MySQL** (Database for state management)
* **Docker & Docker Compose** (For local infrastructure)

---

## 🚀 Getting Started (Local Development)

### 1. Start RabbitMQ via Docker

Run the following command to start RabbitMQ including the Management UI:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

* **Management UI:** [http://localhost:15672](http://localhost:15672) (Login: `guest` / `guest`)

### 2. Configuration

Each module has its own `application.properties` with the required settings:

* **Scanner** (`Scanner/src/main/resources/application.properties`) – Paths for input/processing/failed folders, RabbitMQ queue name, and cron expression for scanning.
* **Worker** (`Worker/src/main/resources/application.properties`) – Paths for processing/completed/failed folders, queue names for caching and transfer, thread counts per queue, database connection, and RabbitMQ retry policy.

> **Tip:** Make sure the folder paths in both modules point to the same physical directories (e.g. `C:/test/ib/processing`) so that Scanner and Worker operate on the same file share.

### 3. Start the Applications

Run each module separately from your IDE (e.g. IntelliJ IDEA) or via Maven:

```bash
# Terminal 1 – Scanner
cd Scanner && mvn spring-boot:run

# Terminal 2 – Worker
cd Worker && mvn spring-boot:run
```

> **Note:** The Scanner also performs a one-time scan on startup (`ApplicationReadyEvent`), in addition to the scheduled cron job.

---

## 📂 Project Structure

* `Scanner/` – Standalone Spring Boot application with scheduled jobs (`@Scheduled`) that scans the file share, moves stable files to `/processing`, and sends the filename to RabbitMQ. Has no database dependency.
* `Worker/` – Standalone Spring Boot application with two phases:
  * **Caching** (`@RabbitListener` on `file.processing.queue`) – Creates a Case record in the DB, fetches metadata from Ret, Sag, and Prio via `CaseDataFetcher` implementations, and forwards the caseId to the transfer queue with priority.
  * **Transfer** (`@RabbitListener` on `file.transfer.queue`) – Publishes data to System 1 (Jira) and System 2 (Archive Store) via `CasePublisher` implementations. Handles retries and moves files to `/completed` or `/failed/worker/transfer`.
  * `models/` – Entities (`Case`, `CaseMetaData`, `CasePriority`, `CasePrivilege`) and enums (`CaseTransferStatus`: `PROCESSING`, `COMPLETED`, `FAILED`).
  * `repositories/` – Spring Data JPA repositories for all entities.
  * `services/` – `CaseDataFetcher` and `CasePublisher` interfaces with concrete implementations (Jira, Archive Store).
