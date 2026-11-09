package com.scrapestudio.doctor;

import com.scrapestudio.worker.WorkerClient;
import com.scrapestudio.worker.dto.DiagnosisReport;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final WorkerClient workerClient;

    public DoctorController(WorkerClient workerClient) {
        this.workerClient = workerClient;
    }

    @PostMapping("/diagnose")
    public DiagnosisReport diagnose(@Valid @RequestBody DoctorRequest request) {
        return workerClient.diagnose(request.url());
    }
}
