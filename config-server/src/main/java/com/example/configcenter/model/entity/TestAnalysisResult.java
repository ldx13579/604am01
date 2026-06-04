package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_analysis_result")
public class TestAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_log_id", nullable = false)
    private Long testLogId;

    @Column(name = "problem_category", nullable = false, length = 50)
    private String problemCategory;

    @Column(name = "root_cause", length = 500)
    private String rootCause;

    @Column(name = "suggestion", length = 1000)
    private String suggestion;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "matched_pattern", length = 200)
    private String matchedPattern;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTestLogId() { return testLogId; }
    public void setTestLogId(Long testLogId) { this.testLogId = testLogId; }

    public String getProblemCategory() { return problemCategory; }
    public void setProblemCategory(String problemCategory) { this.problemCategory = problemCategory; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getMatchedPattern() { return matchedPattern; }
    public void setMatchedPattern(String matchedPattern) { this.matchedPattern = matchedPattern; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
