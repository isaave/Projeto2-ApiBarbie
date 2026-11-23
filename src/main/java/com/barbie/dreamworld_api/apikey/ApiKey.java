package com.barbie.dreamworld_api.apikey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade que representa uma chave de API.
 *
 * A coluna {@code keyValue} guarda apenas o HASH SHA-256 da chave. O valor
 * original existe somente no campo transitório {@code plainValue}, usado uma
 * única vez no momento da criação para devolver a chave ao cliente.
 *
 * Níveis de acesso:
 *   READ  → apenas GET
 *   WRITE → GET + escrita
 *   ADMIN → tudo, incluindo revogar outras chaves
 */
@Entity
@Table(name = "tb_api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String keyValue;

    @Transient
    private String plainValue;

    @Column(nullable = false, length = 100)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccessLevel accessLevel = AccessLevel.WRITE;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.plainValue = UUID.randomUUID().toString();
        this.keyValue = ApiKeyHasher.hash(this.plainValue);
        this.createdAt = LocalDateTime.now();
    }

    public enum AccessLevel {
        READ,
        WRITE,
        ADMIN
    }
}
