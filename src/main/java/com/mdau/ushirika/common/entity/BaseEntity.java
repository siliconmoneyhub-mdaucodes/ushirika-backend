package com.mdau.ushirika.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 150, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    /**
     * Optimistic locking — Hibernate compares this on every UPDATE.
     * Prevents lost updates when two transactions modify the same row concurrently.
     * OptimisticLockException is thrown if versions don't match.
     *
     * Must stay null (not defaulted to 0L) here -- Spring Data JPA's isNew() check for
     * @Version entities is "is version null?", not "is id null?". A non-null default made
     * every brand-new entity look already-persisted, so save() routed through merge()
     * instead of persist() and returned a different managed instance than the one callers
     * kept a reference to. Invisible almost everywhere, but broke same-transaction cascades
     * like Election -> ElectionSeat where the stale reference gets used as a not-null FK.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
