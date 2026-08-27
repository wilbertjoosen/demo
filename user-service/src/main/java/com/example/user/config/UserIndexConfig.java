package com.example.user.config;

import com.example.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject.Type;
import org.springframework.stereotype.Component;

/**
 * user-service does not enable {@code spring.data.mongodb.auto-index-creation}, so the one index
 * the app actually depends on is created explicitly here, once, on startup.
 * <p>
 * Partial + unique on {@code nationalId}: this is the real guard for national-ID uniqueness — the
 * pre-check in {@code UserServiceImpl.assertNationalIdAvailable} is racy on its own and only exists
 * for a friendlier error. The partial filter scopes it to:
 * <ul>
 *     <li>string values only — a profile with no national ID stores no such field, and those must
 *         not collide with each other;</li>
 *     <li>non-deleted profiles — a soft-deleted account should not keep a real person from later
 *         registering their own ID.</li>
 * </ul>
 * If existing data already violates this, {@code ensureIndex} fails on startup — deliberately loud:
 * the duplicates need cleaning before the constraint can hold.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserIndexConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        String name = mongoTemplate.indexOps(User.class).ensureIndex(
                new Index()
                        .named("uniq_nationalId_active")
                        .on("nationalId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("nationalId").type(Type.stringType())
                                        .and("deleted").is(false))));

        log.info("Ensured Mongo index '{}' on users.nationalId (unique, partial)", name);
    }
}
