package com.scrapestudio.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Project> findByOwnerIdAndNameContainingIgnoreCase(Long ownerId, String name, Pageable pageable);

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

    List<Project> findByScheduleEnabledTrueAndNextRunAtBefore(Instant now);

    long countByOwnerId(Long ownerId);
}
