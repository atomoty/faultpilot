package io.github.atomoty.faultpilot.server.controller;

import io.github.atomoty.faultpilot.core.model.DiagnosisReport;
import io.github.atomoty.faultpilot.core.model.DiagnosisRequest;
import io.github.atomoty.faultpilot.server.api.DiagnosisRequestDto;
import io.github.atomoty.faultpilot.server.repository.DiagnosisReportRepository;
import io.github.atomoty.faultpilot.server.service.DiagnosisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diagnoses")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final DiagnosisReportRepository repository;

    public DiagnosisController(DiagnosisService diagnosisService, DiagnosisReportRepository repository) {
        this.diagnosisService = diagnosisService;
        this.repository = repository;
    }

    @PostMapping
    public DiagnosisReport diagnose(@Valid @RequestBody DiagnosisRequestDto dto) {
        DiagnosisRequest request = new DiagnosisRequest(
                dto.projectId(), dto.environment(), dto.question(), dto.from(), dto.to());
        return diagnosisService.diagnose(request);
    }

    @GetMapping("/{diagnosisId}")
    public ResponseEntity<DiagnosisReport> get(@PathVariable String diagnosisId) {
        return repository.find(diagnosisId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
