package com.example.configcenter.repository;

import com.example.configcenter.model.entity.TestAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestAnalysisResultRepository extends JpaRepository<TestAnalysisResult, Long> {

    List<TestAnalysisResult> findByTestLogId(Long testLogId);

    List<TestAnalysisResult> findByProblemCategoryOrderByCreatedAtDesc(String category);

    List<TestAnalysisResult> findTop50ByOrderByCreatedAtDesc();
}
