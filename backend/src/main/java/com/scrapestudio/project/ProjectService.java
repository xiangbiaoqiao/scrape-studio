package com.scrapestudio.project;

import com.scrapestudio.common.ApiException;
import com.scrapestudio.common.PageResponse;
import com.scrapestudio.project.dto.CreateProjectRequest;
import com.scrapestudio.project.dto.FieldDto;
import com.scrapestudio.project.dto.FieldRequest;
import com.scrapestudio.project.dto.ProjectDetail;
import com.scrapestudio.project.dto.ProjectSummary;
import com.scrapestudio.project.dto.UpdateProjectRequest;
import com.scrapestudio.run.Run;
import com.scrapestudio.run.RunRepository;
import com.scrapestudio.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final FieldRepository fieldRepository;
    private final RunRepository runRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            FieldRepository fieldRepository,
            RunRepository runRepository
    ) {
        this.projectRepository = projectRepository;
        this.fieldRepository = fieldRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummary> list(User owner, String query, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );

        Page<Project> projects = (query == null || query.isBlank())
                ? projectRepository.findByOwnerId(owner.getId(), pageable)
                : projectRepository.findByOwnerIdAndNameContainingIgnoreCase(
                        owner.getId(),
                        query.trim(),
                        pageable
                );

        return PageResponse.of(projects.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> recent(User owner, int limit) {
        Pageable pageable = PageRequest.of(
                0,
                Math.min(Math.max(limit, 1), 20),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        return projectRepository.findByOwnerId(owner.getId(), pageable)
                .map(this::toSummary)
                .getContent();
    }

    @Transactional
    public ProjectDetail create(User owner, CreateProjectRequest request) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName(request.name().trim());
        project.setUrl(normalizeUrl(request.url()));
        project.setDescription(trimToNull(request.description()));
        project = projectRepository.saveAndFlush(project);
        return toDetail(project, List.of());
    }

    @Transactional(readOnly = true)
    public ProjectDetail get(User owner, Long id) {
        Project project = requireProject(owner, id);
        List<Field> fields = fieldRepository.findByProjectIdOrderByOrderIndexAscIdAsc(id);
        return toDetail(project, fields);
    }

    @Transactional
    public ProjectDetail update(User owner, Long id, UpdateProjectRequest request) {
        Project project = requireProject(owner, id);

        project.setName(request.name().trim());
        project.setUrl(normalizeUrl(request.url()));
        project.setDescription(trimToNull(request.description()));

        if (request.itemSelector() != null) {
            project.setItemSelector(trimToNull(request.itemSelector()));
        }
        if (request.itemSelectorType() != null && !request.itemSelectorType().isBlank()) {
            project.setItemSelectorType(parseSelectorType(request.itemSelectorType()));
        }

        Integer oldInterval = project.getIntervalMinutes();
        if (request.intervalMinutes() != null) {
            project.setIntervalMinutes(request.intervalMinutes());
        }
        if (request.scheduleEnabled() != null) {
            project.setScheduleEnabled(request.scheduleEnabled());
        }

        if (!project.isScheduleEnabled()) {
            project.setNextRunAt(null);
        } else if (project.getNextRunAt() == null
                || request.intervalMinutes() != null
                && !request.intervalMinutes().equals(oldInterval)) {
            project.setNextRunAt(Instant.now().plus(project.getIntervalMinutes(), ChronoUnit.MINUTES));
        }

        project = projectRepository.saveAndFlush(project);
        List<Field> fields = fieldRepository.findByProjectIdOrderByOrderIndexAscIdAsc(id);
        return toDetail(project, fields);
    }

    @Transactional
    public void delete(User owner, Long id) {
        Project project = requireProject(owner, id);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public List<FieldDto> listFields(User owner, Long projectId) {
        requireProject(owner, projectId);
        return fieldRepository.findByProjectIdOrderByOrderIndexAscIdAsc(projectId)
                .stream()
                .map(FieldDto::from)
                .toList();
    }

    @Transactional
    public FieldDto addField(User owner, Long projectId, FieldRequest request) {
        Project project = requireProject(owner, projectId);
        Field field = new Field();
        field.setProject(project);
        field.setName(request.name().trim());
        field.setSelector(request.selector().trim());
        field.setSelectorType(parseSelectorType(request.selectorType()));
        field.setAttribute(normalizeAttribute(request.attribute()));
        field.setMultiple(request.multiple());
        field.setOrderIndex((int) fieldRepository.countByProjectId(projectId));

        field = fieldRepository.saveAndFlush(field);
        touch(project);
        return FieldDto.from(field);
    }

    @Transactional
    public FieldDto updateField(User owner, Long projectId, Long fieldId, FieldRequest request) {
        Project project = requireProject(owner, projectId);
        Field field = fieldRepository.findByIdAndProjectId(fieldId, projectId)
                .orElseThrow(() -> ApiException.notFound("Field not found"));

        field.setName(request.name().trim());
        field.setSelector(request.selector().trim());
        field.setSelectorType(parseSelectorType(request.selectorType()));
        field.setAttribute(normalizeAttribute(request.attribute()));
        field.setMultiple(request.multiple());

        field = fieldRepository.saveAndFlush(field);
        touch(project);
        return FieldDto.from(field);
    }

    @Transactional
    public void deleteField(User owner, Long projectId, Long fieldId) {
        Project project = requireProject(owner, projectId);
        Field field = fieldRepository.findByIdAndProjectId(fieldId, projectId)
                .orElseThrow(() -> ApiException.notFound("Field not found"));
        fieldRepository.delete(field);
        touch(project);
    }

    @Transactional(readOnly = true)
    public ProjectRunSpec loadRunSpec(User owner, Long projectId) {
        Project project = requireProject(owner, projectId);
        List<Field> fields = fieldRepository.findByProjectIdOrderByOrderIndexAscIdAsc(projectId);

        return new ProjectRunSpec(
                project.getId(),
                project.getName(),
                project.getUrl(),
                project.getItemSelector(),
                project.getItemSelectorType(),
                fields.stream()
                        .map(field -> new ProjectRunSpec.FieldSpec(
                                field.getName(),
                                field.getSelector(),
                                field.getSelectorType(),
                                field.getAttribute(),
                                field.isMultiple()
                        ))
                        .toList()
        );
    }

    public Project requireProject(User owner, Long id) {
        return projectRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> ApiException.notFound("Project not found"));
    }

    @Transactional(readOnly = true)
    public Project requireProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
    }

    private ProjectSummary toSummary(Project project) {
        long fieldCount = fieldRepository.countByProjectId(project.getId());
        long runCount = runRepository.countByProjectId(project.getId());
        String lastStatus = runRepository.findFirstByProjectIdOrderByStartedAtDesc(project.getId())
                .map(Run::getStatus)
                .map(Enum::name)
                .orElse(null);

        return new ProjectSummary(
                project.getId(),
                project.getName(),
                project.getUrl(),
                project.getDescription(),
                project.getItemSelector(),
                project.isScheduleEnabled(),
                project.getIntervalMinutes(),
                project.getLastRunAt(),
                project.getNextRunAt(),
                fieldCount,
                runCount,
                lastStatus,
                project.getUpdatedAt()
        );
    }

    private ProjectDetail toDetail(Project project, List<Field> fields) {
        return new ProjectDetail(
                project.getId(),
                project.getName(),
                project.getUrl(),
                project.getDescription(),
                project.getItemSelector(),
                project.getItemSelectorType().name(),
                project.isScheduleEnabled(),
                project.getIntervalMinutes(),
                project.getLastRunAt(),
                project.getNextRunAt(),
                fields.stream().map(FieldDto::from).toList(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private void touch(Project project) {
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("A URL is required");
        }

        String candidate = value.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw ApiException.badRequest("Only http:// and https:// URLs are supported");
            }
            if (uri.getHost() == null) {
                throw ApiException.badRequest("The URL must include a host");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("Invalid URL");
        }
    }

    private SelectorType parseSelectorType(String value) {
        if (value == null || value.isBlank()) {
            return SelectorType.CSS;
        }
        try {
            return SelectorType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("selectorType must be CSS or XPATH");
        }
    }

    private String normalizeAttribute(String value) {
        if (value == null || value.isBlank()) {
            return "text";
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
