package com.project.distributed_codegen.workspace_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_publications")
@Getter
@Setter
public class ProjectPublication {
    @Id
    private Long projectId;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "project_id")
    private Project project;
    @Column(nullable = false, unique = true)
    private UUID slug;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, columnDefinition = "text")
    private String snapshot;
    @Column(nullable = false)
    private Instant publishedAt;
}
