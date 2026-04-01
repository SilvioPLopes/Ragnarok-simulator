package com.ragnarok.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "age_verified")
    private boolean ageVerified = false;

    @Column(name = "cash_points")
    private Long cashPoints = 0L;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
