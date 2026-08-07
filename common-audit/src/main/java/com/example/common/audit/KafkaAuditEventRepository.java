package com.example.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces Boot's default in-memory AuditEventRepository: every AuditEvent (both auto-captured REST
 * calls, see RestCallAuditAspect, and Spring Security's own login success/failure events, which Boot
 * auto-publishes through this same interface once it's on the classpath) is published to Kafka
 * instead of held in memory. Querying happens via audit-service's Elasticsearch-backed API, not
 * through this interface, so find() is intentionally a no-op — this bean is write-only by design.
 *
 * <p>Store-and-forward on publish failure: {@code kafkaTemplate.send()} is async, so a broker outage
 * surfaces on the returned future, not as a synchronous exception — a plain try/catch around send()
 * would never see it. Failed records go into a small bounded in-memory queue instead of being
 * dropped, and a periodic task drains it once the broker is reachable again. In-memory only (not
 * disk-backed) — a service restart during an outage still loses whatever's queued, which is an
 * accepted tradeoff for this audit trail, not a data path that needs stronger durability.
 */
@Component
@Slf4j
public class KafkaAuditEventRepository implements AuditEventRepository {

    private static final int MAX_PENDING = 1000;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String serviceName;
    private final Queue<AuditRecord> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSize = new AtomicInteger(0);

    public KafkaAuditEventRepository(KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${spring.application.name}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceName = serviceName;
    }

    @Override
    public void add(AuditEvent event) {
        AuditRecord record = AuditRecord.from(serviceName, event);
        try {
            kafkaTemplate.send(AuditTopics.AUDIT_EVENTS, record).whenComplete((result, ex) -> {
                if (ex != null) {
                    enqueue(record);
                }
            });
        } catch (Exception e) {
            // send() itself can throw synchronously (e.g. TimeoutException while blocked in the
            // producer waiting on topic metadata, bounded by spring.kafka.producer.properties.max.block.ms)
            // — the whenComplete() callback above only covers failures reported via the async future.
            enqueue(record);
        }
    }

    private void enqueue(AuditRecord record) {
        if (pendingSize.get() >= MAX_PENDING) {
            log.warn("Audit event queue full ({} pending) — dropping oldest to make room", MAX_PENDING);
            if (pending.poll() != null) {
                pendingSize.decrementAndGet();
            }
        }
        pending.offer(record);
        int size = pendingSize.incrementAndGet();
        log.warn("Kafka unreachable, audit event queued for retry ({} pending)", size);
    }

    /** Drains oldest-first; stops at the first failure each tick rather than thrashing through the whole queue. */
    @Scheduled(fixedDelay = 10_000)
    void drainPending() {
        if (pending.isEmpty()) {
            return;
        }
        int drained = 0;
        AuditRecord record;
        while ((record = pending.peek()) != null) {
            try {
                kafkaTemplate.send(AuditTopics.AUDIT_EVENTS, record).get(3, TimeUnit.SECONDS);
                pending.poll();
                pendingSize.decrementAndGet();
                drained++;
            } catch (Exception e) {
                log.warn("Kafka still unreachable, {} audit event(s) remain queued: {}", pendingSize.get(), e.toString());
                break;
            }
        }
        if (drained > 0) {
            log.info("Flushed {} queued audit event(s) to Kafka, {} remain", drained, pendingSize.get());
        }
    }

    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        return List.of();
    }
}
