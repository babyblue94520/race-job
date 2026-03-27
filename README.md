# Race Job

A lightweight and extensible Java scheduling library designed for distributed environments.  
By separating **job definition**, **job management**, and **job execution**, *race-job* ensures that in a multi-instance cluster **each job runs exactly once** using **RDB optimistic locking**.

If you implement `RaceJobEventBus`, the system automatically synchronizes job changes across nodes, enabling a fully **distributed scheduler**.

---

## 📌 Features

- **Cluster-safe job execution** — only one instance runs each job using RDB optimistic locking.
- **Clear separation of job management and execution**.
- **Extendable Event Service** for distributed message sync.
- **Cron scheduling, dependent jobs, dynamic job updates**.
- **Global Kill Switch** (`execution-enabled`) to pause execution engine on specific nodes.
- **No Quartz required** — simpler, safer, and easier to maintain.

---

## 📘 Architecture Overview

```mermaid
flowchart TD

%% === Job Manage Service ===
    subgraph JMS[Job Manage Service]
        JMS_Op[Create / Update / Delete Job]
    end

%% === Database ===
    DB[(Job Table)]

%% === Job Handle Service ===
subgraph JHS[Job Handle Service]
JHS_Register[Register Handlers by group and name]
JHS_Reload[Periodically reload jobs from DB]
JHS_Compete[Compete for job execution using optimistic lock]
JHS_Execute[Execute handler]
end

%% === Flows ===
JMS_Op -->|Write / Update / Delete| DB

JHS_Register --> JHS_Reload
JHS_Reload -->|Load jobs| DB

JHS_Reload --> JHS_Compete
JHS_Compete -->|Lock acquired| JHS_Execute
JHS_Compete -->|Lock failed| JHS_Reload

JHS_Execute -->|Update next_run_time / status| DB

```

## 🧩 Requirements

* Spring Boot 2+
* Java 11+

## 🚀 Quick Start
1. Add Dependency

    ```xml
    
    <dependency>
        <groupId>io.github.babyblue94520</groupId>
        <artifactId>race-job</artifactId>
        <version>1.0.0-RELEASE</version>
    </dependency>
    ```

2. Configuration (application.yml)

    ```yaml
    race-job:
      instance: raceJobScheduler
      reload-interval: 60000
      thread-count: 20 # default processors * 2
      check-wait-time: 1000 # Wait time to check if a job is being processed
      update-active-interval: 60000 # The running job periodically updates its last active timestamp
      execution-enabled: true # If false, the scheduler engine will NOT start
      abort-on-error: true # If true, aborts the task on exception
    ```

3. Enable Race Job

    ```java
    
    @EnableRaceJob
    @Configuration
    public class RaceJobConfig {
    
    }
    ```

---

## 🛠 Job Management

### Create or Update a Job

*   **Create**: If the job (defined by group and name) does not exist, it is inserted into the database.
*   **Update**: If the job exists, it will only be updated if the provided `version` is **greater** than the current version in the database.
*   **Important**: The `enabled` state cannot be modified through the `add()` method. Use `enable()` or `disable()` instead.

```java
@Autowired
private RaceJobScheduler scheduler;

// Create a job
scheduler.add(RaceJob.builder()
        .group("group").name("name").key("key")
        .cron("* * * * * ?")
        .version(1)
        .build());

// Update a job (requires version increment)
scheduler.add(RaceJob.builder()
        .group("group").name("name").key("key")
        .version(2)
        .cron("0 0 * * * ?")
        .build());

// Control job status
scheduler.enable(new RaceJobKey("group", "name"));
scheduler.disable(new RaceJobKey("group", "name"));
```


🏃 Register Job Handler

Trigger handler by job key

```java
@Component
public class RaceJobHandlerRegister implements InitializingBean {

  @Autowired
  private RaceJobScheduler scheduler;
  
  @Override
  public void afterPropertiesSet() throws Exception {
      String key = "key";
      scheduler.registerHandler(key, (job) -> {
          // Do something
      });
  }
}
```
### 🔗 Creating Dependent Jobs

Jobs can be triggered after another job finishes.

```java
@EnableRaceJob
public class RaceJobConfig {

    @Autowired
    private RaceJobScheduler scheduler;

    @Override
    public void afterPropertiesSet() throws Exception {
        scheduler.add(RaceJob.builder()
                .group("after-group")
                .name("after-name")
                .dependsKey("key")
                .build());
    }
}
```

### 🌐 Enable Distributed Mode with Event Service

Implementing `RaceJobEventBus` allows job changes to be broadcast across all nodes, enabling full distributed synchronization.

### Event Type

* CHANGE 

    When a job is updated, notify other nodes to reload immediately

* COMPLETE

    When a job completes, notify other nodes to execute dependent jobs

* EXECUTE

    When manually executing a job, notify other nodes to execute the job

Example Implementation

**Example**

```java
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
@Service
public class RaceJobEventBusImpl implements RaceJobEventBus {

    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    @Override
    public void send(String body) {
        executor.submit(() -> {
            listeners.forEach(consumer -> consumer.accept(body));
        });
    }

    @Override
    public void listen(Consumer<String> listener) {
        listeners.add(listener);
    }
}
```

```mermaid
flowchart TD

%% === Job Event Service ===
    subgraph EventService[RaceJobEventBus]
        ChangeEvent[CHANGE Event]
        ExecuteEvent[EXECUTE Event]
        CompleteEvent[COMPLETE Event]
    end

%% === Nodes ===
    subgraph Node1[Node 1]
        N1_Reload[Reload Job from DB]
        N1_Compete[Try RDB Optimistic Lock]
        N1_Execute[Execute Job if Lock Acquired]
    end

    subgraph Node2[Node 2]
        N2_Reload[Reload Job from DB]
        N2_Compete[Try RDB Optimistic Lock]
        N2_Execute[Execute Job if Lock Acquired]
    end

%% === Flows ===
ChangeEvent -->|Job Updated| N1_Reload
ChangeEvent -->|Job Updated| N2_Reload

ExecuteEvent -->|Manual Trigger| N1_Compete
ExecuteEvent -->|Manual Trigger| N2_Compete

CompleteEvent -->|Dependent Jobs| N1_Compete
CompleteEvent -->|Dependent Jobs| N2_Compete

N1_Reload --> N1_Compete
N2_Reload --> N2_Compete

N1_Compete -->|Lock acquired| N1_Execute
N1_Compete -->|Lock failed| N1_Reload

N2_Compete -->|Lock acquired| N2_Execute
N2_Compete -->|Lock failed| N2_Reload

N1_Execute --> CompleteEvent
N2_Execute --> CompleteEvent
```

## 💾 Database Schema

### MySQL

```sql
CREATE TABLE IF NOT EXISTS `race_job`
(
    `instance`              varchar(100)    NOT NULL DEFAULT '',
    `group`                 varchar(100)    NOT NULL DEFAULT '',
    `name`                  varchar(100)    NOT NULL DEFAULT '',
    `key`                   varchar(100)    NOT NULL DEFAULT '',
    `version`               int             NOT NULL DEFAULT 1,
    `timezone`              varchar(10)     NOT NULL DEFAULT '',
    `description`           varchar(200)    NOT NULL DEFAULT '',
    `cron`                  varchar(200)    NOT NULL DEFAULT '',
    `depends_key`           varchar(100)    NOT NULL DEFAULT '',
    `prev_time`             bigint(13)      NOT NULL DEFAULT 0,
    `next_time`             bigint(13)      NOT NULL DEFAULT 0,
    `enabled`               tinyint(1)      NOT NULL DEFAULT 1,
    `state`                 int(1)          NOT NULL DEFAULT 0,
    `start_time`            bigint(13)      NOT NULL DEFAULT 0,
    `end_time`              bigint(13)      NOT NULL DEFAULT 0,
    `last_active_time`      bigint(13)      NOT NULL DEFAULT 0,
    `data`                  text            NULL,
    PRIMARY KEY (`instance`, `group`, `name`) USING BTREE
) ENGINE = InnoDB ROW_FORMAT = Dynamic;

```
