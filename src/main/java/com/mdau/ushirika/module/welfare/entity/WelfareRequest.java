package com.mdau.ushirika.module.welfare.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.welfare.enums.WelfareRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "welfare_requests",
    indexes = {
        @Index(name = "idx_wr_member_id",       columnList = "member_id"),
        @Index(name = "idx_wr_category_id",     columnList = "category_id"),
        @Index(name = "idx_wr_status",          columnList = "status"),
        @Index(name = "idx_wr_member_status",   columnList = "member_id, status"),
        @Index(name = "idx_wr_submitted_at",    columnList = "submitted_at"),
        @Index(name = "idx_wr_created_at",      columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelfareRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_wr_member"))
    private User member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_wr_category"))
    private WelfareCategory category;

    /** Public-facing tracking reference. */
    @Column(name = "reference_number", unique = true, nullable = false,
            updatable = false, length = 30)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WelfareRequestStatus status = WelfareRequestStatus.DRAFT;

    @Column(name = "amount_requested", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRequested;

    /** Welfare Fund is USD-only -- see WelfareService, which has no path that lets this be
     * set to anything else. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /** Description of the welfare need — visible to admins. */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Supporting document URLs (Cloudinary). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_urls", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> documentUrls = new ArrayList<>();

    /**
     * Anonymous rejection message — never reveals which admin rejected.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Internal notes visible only to SUPERADMIN. */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "welfareRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WelfareRequestApproval> approvals = new ArrayList<>();

    @OneToOne(mappedBy = "welfareRequest", cascade = CascadeType.ALL)
    private WelfareDisbursement disbursement;
}
