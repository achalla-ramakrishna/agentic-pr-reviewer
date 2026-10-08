package com.skyroute.auth;

import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A user who can log in. Passwords are stored only as BCrypt hashes. */
@Document("users")
public record AppUser(
        @Id String id,
        @Indexed(unique = true) String username,
        String passwordHash,
        Set<Role> roles,
        boolean enabled) {
}
