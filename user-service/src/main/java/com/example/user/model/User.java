package com.example.user.model;

import com.example.common.model.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/** findByNationalIdAndDeletedFalse(nationalId). */
@CompoundIndex(def = "{'nationalId': 1, 'deleted': 1}")
@Document(collection = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private String id;

    /** Identity lives in Keycloak, not here — see KeycloakAdminClient. findByKeycloakId(keycloakId). */
    @Indexed
    private String keycloakId;

    @Setter
    private Address shippingAddress;

    @Setter
    private String nationalId;

    @Setter
    private String phone;

    /** Anything beyond the named fields above — extensibility without another schema change. */
    @Setter
    private Map<String, String> customAttributes;

    @Indexed
    private boolean deleted = false;
    private Instant deletedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    public User(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public User(String keycloakId, Address shippingAddress) {
        this(keycloakId);
        this.shippingAddress = shippingAddress;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
