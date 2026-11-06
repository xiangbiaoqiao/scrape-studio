package com.scrapestudio.project;

import com.scrapestudio.common.ApiException;
import com.scrapestudio.project.dto.CodeResponse;
import com.scrapestudio.project.dto.CreateProjectRequest;
import com.scrapestudio.project.dto.FieldDto;
import com.scrapestudio.project.dto.FieldRequest;
import com.scrapestudio.project.dto.PickRequest;
import com.scrapestudio.project.dto.PreviewRequestBody;
import com.scrapestudio.project.dto.ProjectDetail;
import com.scrapestudio.project.dto.ProjectSummary;
import com.scrapestudio.project.dto.RenderRequest;
import com.scrapestudio.project.dto.UpdateProjectRequest;
import com.scrapestudio.common.PageResponse;
import com.scrapestudio.user.User;
import com.scrapestudio.worker.WorkerClient;
import com.scrapestudio.worker.dto.PickResponse;
import com.scrapestudio.worker.dto.PreviewRequest;
import com.scrapestudio.worker.dto.PreviewResponse;
import com.scrapestudio.worker.dto.RenderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CodeGenerator codeGenerator;
    private final WorkerClient workerClient;

    public ProjectController(
            ProjectService projectService,
            CodeGenerator codeGenerator,
            WorkerClient workerClient
    ) {
        this.projectService = projectService;
        this.codeGenerator = codeGenerator;
        this.workerClient = workerClient;
    }

    @GetMapping
    public PageResponse<ProjectSummary> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return projectService.list(user, query, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetail create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return projectService.create(user, request);
    }

    @GetMapping("/{id}")
    public ProjectDetail get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return projectService.get(user, id);
    }

    @PutMapping("/{id}")
    public ProjectDetail update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        projectService.delete(user, id);
    }

    @GetMapping("/{id}/fields")
    public List<FieldDto> listFields(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return projectService.listFields(user, id);
    }

    @PostMapping("/{id}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldDto addField(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody FieldRequest request
    ) {
        return projectService.addField(user, id, request);
    }

    @PutMapping("/{id}/fields/{fieldId}")
    public FieldDto updateField(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fieldId,
            @Valid @RequestBody FieldRequest request
    ) {
        return projectService.updateField(user, id, fieldId, request);
    }

    @DeleteMapping("/{id}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fieldId
    ) {
        projectService.deleteField(user, id, fieldId);
    }

    @PostMapping("/{id}/render")
    public RenderResponse render(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) RenderRequest request
    ) {
        ProjectDetail project = projectService.get(user, id);
        String url = request != null && request.url() != null && !request.url().isBlank()
                ? request.url().trim()
                : project.url();
        return workerClient.render(url);
    }

    @PostMapping("/{id}/pick")
    public PickResponse pick(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody PickRequest request
    ) {
        projectService.get(user, id);
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw ApiException.badRequest("sessionId is required");
        }
        return workerClient.pick(request.sessionId(), request.x(), request.y());
    }

    @PostMapping("/{id}/preview")
    public PreviewResponse preview(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) PreviewRequestBody request
    ) {
        ProjectRunSpec spec = projectService.loadRunSpec(user, id);

        String url = request != null && request.url() != null && !request.url().isBlank()
                ? request.url().trim()
                : spec.url();
        String itemSelector = request != null && request.itemSelector() != null
                ? request.itemSelector().trim()
                : spec.itemSelector();
        String itemSelectorType = request != null
                && request.itemSelectorType() != null
                && !request.itemSelectorType().isBlank()
                ? request.itemSelectorType()
                : spec.itemSelectorType().name();

        List<PreviewRequest.PreviewField> fields =
                request != null && request.fields() != null && !request.fields().isEmpty()
                        ? request.fields()
                        : spec.fields().stream()
                        .map(field -> new PreviewRequest.PreviewField(
                                field.name(),
                                field.selector(),
                                field.selectorType().name(),
                                field.attribute(),
                                field.multiple()
                        ))
                        .toList();

        int limit = request != null && request.limit() != null
                ? Math.min(Math.max(request.limit(), 1), 500)
                : 50;

        return workerClient.preview(new PreviewRequest(
                url,
                itemSelector,
                itemSelectorType,
                fields,
                limit
        ));
    }

    @PostMapping("/{id}/diagnose")
    public Map<String, Object> diagnose(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) RenderRequest request
    ) {
        ProjectDetail project = projectService.get(user, id);
        String url = request != null && request.url() != null && !request.url().isBlank()
                ? request.url().trim()
                : project.url();
        return workerClient.diagnose(url);
    }

    @GetMapping("/{id}/code")
    public CodeResponse code(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "python") String language
    ) {
        ProjectDetail project = projectService.get(user, id);
        try {
            return new CodeResponse(language, codeGenerator.generate(project, language));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(exception.getMessage());
        }
    }
}
