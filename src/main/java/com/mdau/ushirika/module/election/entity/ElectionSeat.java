package com.mdau.ushirika.module.election.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "election_seats",
    indexes = {
        @Index(name = "idx_eseat_election", columnList = "election_id"),
        @Index(name = "idx_eseat_sort",     columnList = "sort_order")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_eseat_election"))
    private Election election;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_winners", nullable = false)
    @Builder.Default
    private int maxWinners = 1;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /** Executive-committee seats require a manifesto statement, photo, and video to run. */
    @Column(name = "executive_tier", nullable = false)
    @Builder.Default
    private boolean executiveTier = false;

    @OneToMany(mappedBy = "seat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ElectionCandidacy> candidacies = new ArrayList<>();
}
