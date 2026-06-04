package com.example.configcenter.controller;

import com.example.configcenter.model.entity.TestAnalysisResult;
import com.example.configcenter.service.TestResultAnalyzer;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-analysis")
public class TestAnalysisController {

    private final TestResultAnalyzer analyzer;

    public TestAnalysisController(TestResultAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @PostMapping("/analyze")
    public List<TestAnalysisResult> analyzeRecent() {
        return analyzer.analyzeRecent();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return analyzer.getSummary();
    }

    @GetMapping("/category/{category}")
    public List<TestAnalysisResult> getByCategory(@PathVariable String category) {
        return analyzer.getByCategory(category);
    }

    @GetMapping("/test-log/{testLogId}")
    public List<TestAnalysisResult> getByTestLog(@PathVariable Long testLogId) {
        return analyzer.getByTestLog(testLogId);
    }
}
