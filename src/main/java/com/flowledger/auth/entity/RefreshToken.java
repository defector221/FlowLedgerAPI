package com.flowledger.auth.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="refresh_tokens") @Getter @Setter @NoArgsConstructor
public class RefreshToken { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false) private UUID userId; @Column(nullable=false,unique=true) private String tokenHash; @Column(nullable=false) private Instant expiresAt; private boolean revoked; private UUID replacedBy; @Column(nullable=false,updatable=false) private Instant createdAt; @PrePersist void pre(){createdAt=Instant.now();} }
