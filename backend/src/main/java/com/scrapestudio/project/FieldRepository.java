package com.scrapestudio.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldRepository extends JpaRepository<Field, Long> {

    List<Field> findByProjectIdOrderByOrderIndexAscIdAsc(Long projectId);

    Optional<Field> findByIdAndProjectId(Long id, Long projectId);

    long countByProjectId(Long projectId);
}
