package com.example.user;

import com.example.common.model.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private String id;

    private String keycloakId;
    private String username;
    private String email;

    @Setter
    private String displayName;

    @Setter
    private Address shippingAddress;

    @Setter
    private String nationalId;

    @Setter
    private String phone;

    /** Anything beyond the named fields above — extensibility without another schema change. */
    @Setter
    private Map<String, String> customAttributes;

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

    public User(String keycloakId, String username, String email, String displayName) {
        this.keycloakId = keycloakId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
    }

    /** Admin-created placeholder profile — see UserService#createUser for the known limitation. */
    public User(String keycloakId, String username, String email, String displayName, Address shippingAddress) {
        this(keycloakId, username, email, displayName);
        this.shippingAddress = shippingAddress;
    }

    void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
