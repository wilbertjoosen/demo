package com.example.reporting.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per USER_REGISTERED event, feeding the user-growth report's daily signup counts. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistration {

    private String userId;
    private String username;
    private String email;
    private Instant registeredAt;
}
