package com.example.configcenter.service;

import com.example.configcenter.model.entity.ConfigChangeTestLog;
import com.example.configcenter.model.entity.TestAnalysisResult;
import com.example.configcenter.repository.ConfigChangeTestLogRepository;
import com.example.configcenter.repository.TestAnalysisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TestResultAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TestResultAnalyzer.class);

    private final TestAnalysisResultRepository analysisRepo;
    private final ConfigChangeTestLogRepository testLogRepo;
    private final List<AnalysisPattern> patterns;

    public TestResultAnalyzer(TestAnalysisResultRepository analysisRepo,
                              ConfigChangeTestLogRepository testLogRepo) {
        this.analysisRepo = analysisRepo;
        this.testLogRepo = testLogRepo;
        this.patterns = initPatterns();
    }

    public TestAnalysisResult analyze(ConfigChangeTestLog testLog) {
        if ("PASS".equals(testLog.getTestResult())) return null;

        String error = testLog.getErrorMessage() != null ? testLog.getErrorMessage() : "";

        for (AnalysisPattern ap : patterns) {
            if (ap.pattern.matcher(error).find()) {
                TestAnalysisResult result = new TestAnalysisResult();
                result.setTestLogId(testLog.getId());
                result.setProblemCategory(ap.category);
                result.setRootCause(ap.rootCause);
                result.setSuggestion(ap.suggestion);
                result.setConfidence(ap.confidence);
                result.setMatchedPattern(ap.patternName);
                return analysisRepo.save(result);
            }
        }

        TestAnalysisResult result = new TestAnalysisResult();
        result.setTestLogId(testLog.getId());
        result.setProblemCategory("UNKNOWN");
        result.setRootCause("未匹配已知模式，可能为新型问题");
        result.setSuggestion("请人工审查错误日志并考虑添加新的分析规则");
        result.setConfidence(0.2);
        result.setMatchedPattern("NONE");
        return analysisRepo.save(result);
    }

    public List<TestAnalysisResult> analyzeRecent() {
        List<ConfigChangeTestLog> failures = testLogRepo.findTop20ByTestResultOrderByCreatedAtDesc("FAIL");
        List<ConfigChangeTestLog> errors = testLogRepo.findTop20ByTestResultOrderByCreatedAtDesc("ERROR");

        List<TestAnalysisResult> results = new ArrayList<>();
        Set<Long> analyzed = new HashSet<>();

        for (ConfigChangeTestLog tl : failures) {
            if (!analysisRepo.findByTestLogId(tl.getId()).isEmpty()) continue;
            if (analyzed.add(tl.getId())) {
                TestAnalysisResult r = analyze(tl);
                if (r != null) results.add(r);
            }
        }
        for (ConfigChangeTestLog tl : errors) {
            if (!analysisRepo.findByTestLogId(tl.getId()).isEmpty()) continue;
            if (analyzed.add(tl.getId())) {
                TestAnalysisResult r = analyze(tl);
                if (r != null) results.add(r);
            }
        }

        return results;
    }

    public Map<String, Object> getSummary() {
        List<TestAnalysisResult> recent = analysisRepo.findTop50ByOrderByCreatedAtDesc();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (TestAnalysisResult r : recent) {
            categoryCounts.merge(r.getProblemCategory(), 1L, Long::sum);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAnalyzed", recent.size());
        summary.put("categoryDistribution", categoryCounts);
        summary.put("topCategory", categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("NONE"));
        return summary;
    }

    public List<TestAnalysisResult> getByCategory(String category) {
        return analysisRepo.findByProblemCategoryOrderByCreatedAtDesc(category);
    }

    public List<TestAnalysisResult> getByTestLog(Long testLogId) {
        return analysisRepo.findByTestLogId(testLogId);
    }

    private List<AnalysisPattern> initPatterns() {
        List<AnalysisPattern> p = new ArrayList<>();

        p.add(new AnalysisPattern(
                "VALIDATION_FORMAT",
                Pattern.compile("(?i)(format|格式|pattern|正则|invalid.*(format|格式))"),
                "VALIDATION_ERROR",
                "配置值格式不符合校验脚本中定义的正则表达式或格式规则",
                "检查配置值是否符合要求的格式（如JSON、IP地址、URL等），参考校验脚本中的规则定义",
                0.85));

        p.add(new AnalysisPattern(
                "VALIDATION_RANGE",
                Pattern.compile("(?i)(range|范围|overflow|underflow|超出|too\\s*(large|small|big)|越界)"),
                "OVERFLOW_ERROR",
                "配置值超出了允许的数值范围或长度限制",
                "确认数值型配置的取值范围，检查字符串长度是否超过限制",
                0.80));

        p.add(new AnalysisPattern(
                "VALIDATION_REQUIRED",
                Pattern.compile("(?i)(required|必填|not\\s*(null|empty|blank)|cannot\\s+be\\s+(null|empty))"),
                "VALIDATION_ERROR",
                "配置值为空但校验规则要求非空",
                "确保配置值不为空，检查是否有意外的空白字符",
                0.90));

        p.add(new AnalysisPattern(
                "ENCODING_ISSUE",
                Pattern.compile("(?i)(encod|编码|charset|utf|gbk|乱码|malformed)"),
                "ENCODING_ERROR",
                "配置值包含不兼容的字符编码，可能存在非UTF-8字符",
                "确保配置值使用UTF-8编码，移除不可见的特殊字符或BOM标记",
                0.75));

        p.add(new AnalysisPattern(
                "JSON_PARSE",
                Pattern.compile("(?i)(json|parse|syntax.*error|unexpected.*token|JSON\\.parse)"),
                "FORMAT_ERROR",
                "配置值不是合法的JSON格式",
                "使用JSON格式化工具验证配置值，检查是否有缺失的引号、括号或逗号",
                0.90));

        p.add(new AnalysisPattern(
                "SCRIPT_TIMEOUT",
                Pattern.compile("(?i)(timeout|超时|timed?\\s*out|execution.*time)"),
                "TIMEOUT_ERROR",
                "校验脚本执行超时，可能包含无限循环或处理大量数据",
                "简化校验逻辑或增加超时阈值，检查脚本是否有无限循环",
                0.85));

        p.add(new AnalysisPattern(
                "SCRIPT_ERROR",
                Pattern.compile("(?i)(script.*error|nashorn|graalvm|javascript|ReferenceError|TypeError)"),
                "SCRIPT_ERROR",
                "校验脚本自身存在语法或运行时错误",
                "检查校验脚本的语法正确性，确保引用的变量和函数已定义",
                0.80));

        p.add(new AnalysisPattern(
                "SECURITY_VIOLATION",
                Pattern.compile("(?i)(security|安全|blocked|forbidden|dangerous|injection|xss|sql)"),
                "SECURITY_ERROR",
                "配置值包含被安全规则拦截的危险内容",
                "移除配置值中的脚本标签、SQL关键词或其他可能触发安全规则的内容",
                0.90));

        p.add(new AnalysisPattern(
                "DEPENDENCY_CONFLICT",
                Pattern.compile("(?i)(conflict|冲突|depends|依赖|reference|引用.*不存在)"),
                "DEPENDENCY_ERROR",
                "配置项存在依赖关系冲突，引用了不存在的其他配置",
                "检查配置间的引用关系，确保依赖的配置项已存在且值有效",
                0.70));

        p.add(new AnalysisPattern(
                "CONNECTION_ISSUE",
                Pattern.compile("(?i)(connection|connect|连接|network|网络|refused|unreachable)"),
                "INFRASTRUCTURE_ERROR",
                "测试过程中遇到网络或连接问题",
                "检查网络连通性和目标服务可用性，可能是临时性问题",
                0.65));

        return p;
    }

    private record AnalysisPattern(
            String patternName,
            Pattern pattern,
            String category,
            String rootCause,
            String suggestion,
            double confidence
    ) {}
}
