package com.scrapestudio.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

    @EntityGraph(attributePaths = "project")
    Page<Run> findByProjectIdOrderByStartedAtDesc(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "project")
    Page<Run> findByProjectOwnerIdOrderByStartedAtDesc(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "project")
    Page<Run> findByProjectOwnerIdAndStatusOrderByStartedAtDesc(
            Long ownerId,
            Run.RunStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "project")
    Page<Run> findByProjectOwnerIdAndProjectIdOrderByStartedAtDesc(
            Long ownerId,
            Long projectId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "project")
    Page<Run> findByProjectOwnerIdAndProjectIdAndStatusOrderByStartedAtDesc(
            Long ownerId,
            Long projectId,
            Run.RunStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "project")
    List<Run> findTop8ByProjectOwnerIdOrderByStartedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = "project")
    List<Run> findByProjectOwnerIdAndStartedAtAfter(Long ownerId, Instant after);

    @EntityGraph(attributePaths = "project")
    Optional<Run> findByIdAndProjectOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = "project")
    @Query("select r from Run r where r.id = :id")
    Optional<Run> findWithProjectById(@Param("id") Long id);

    Optional<Run> findFirstByProjectIdOrderByStartedAtDesc(Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectOwnerId(Long ownerId);

    long countByProjectOwnerIdAndStatus(Long ownerId, Run.RunStatus status);

    long countByProjectOwnerIdAndStartedAtAfter(Long ownerId, Instant after);

    @Query("select coalesce(sum(r.itemCount), 0) from Run r where r.project.owner.id = :ownerId")
    long sumItemCountByOwnerId(@Param("ownerId") Long ownerId);
}
