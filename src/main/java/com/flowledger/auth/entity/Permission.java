package com.flowledger.auth.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="permissions") @Getter @Setter @NoArgsConstructor
public class Permission { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; @Column(nullable=false) private String module; private String description; @Column(nullable=false,updatable=false) private Instant createdAt; @PrePersist void pre(){createdAt=Instant.now();} }
