package com.example.configcenter.service;

import com.example.configcenter.model.dto.ValidationResult;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Component
public class ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);
    private static final long TIMEOUT_SECONDS = 5;
    private static final int MAX_SCRIPT_LENGTH = 64 * 1024;
    private static final int MAX_OUTPUT_BYTES = 4096;

    private static final Set<String> ALLOWED_JS_GLOBALS = Set.of(
            "JSON", "Math", "parseInt", "parseFloat", "isNaN", "isFinite",
            "String", "Number", "Boolean", "Array", "Object", "RegExp", "Date",
            "Map", "Set", "Error", "TypeError", "RangeError", "undefined", "NaN", "Infinity"
    );

    private final SecurityRuleService securityRuleService;

    public ScriptExecutor(SecurityRuleService securityRuleService) {
        this.securityRuleService = securityRuleService;
    }

    public ValidationResult execute(String scriptContent, String configKey, String configValue) {
        ValidationResult preCheck = preValidateScript(scriptContent);
        if (preCheck != null) {
            return preCheck;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "script-executor");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<ValidationResult> future = executor.submit(
                    () -> executeScript(scriptContent, configKey, configValue));
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Script execution timed out after {} seconds", TIMEOUT_SECONDS);
            return new ValidationResult(false, "Script execution timed out after " + TIMEOUT_SECONDS + " seconds");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Script execution failed: {}", cause.getMessage());
            return new ValidationResult(false, "Script execution error: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ValidationResult(false, "Script execution interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    private ValidationResult preValidateScript(String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            return new ValidationResult(false, "Script content cannot be empty");
        }

        if (scriptContent.length() > MAX_SCRIPT_LENGTH) {
            return new ValidationResult(false,
                    "Script exceeds maximum allowed size of " + (MAX_SCRIPT_LENGTH / 1024) + "KB");
        }

        Set<String> blockedKeywords = securityRuleService.getBlockedKeywords();
        for (String keyword : blockedKeywords) {
            if (scriptContent.contains(keyword)) {
                return new ValidationResult(false,
                        "Script contains blocked keyword: " + keyword + ". Only pure data validation logic is allowed.");
            }
        }

        Pattern suspiciousPattern = securityRuleService.getSuspiciousPattern();
        if (suspiciousPattern.matcher(scriptContent).find()) {
            return new ValidationResult(false,
                    "Script contains suspicious patterns that are not allowed in validation scripts");
        }

        return null;
    }

    private ValidationResult executeScript(String scriptContent, String configKey, String configValue) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(MAX_OUTPUT_BYTES);

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowIO(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(false)
                .out(outputStream)
                .err(outputStream)
                .option("engine.WarnInterpreterOnly", "false")
                .option("js.ecmascript-version", "2021")
                .build()) {

            Value bindings = context.getBindings("js");
            removeUnsafeGlobals(bindings);

            context.eval("js", scriptContent);

            Value validateFn = bindings.getMember("validate");
            if (validateFn == null || !validateFn.canExecute()) {
                return new ValidationResult(false, "Script must define a 'validate(key, value)' function");
            }

            Value result = validateFn.execute(configKey, configValue);

            if (result == null || !result.hasMembers()) {
                return new ValidationResult(false,
                        "validate() must return an object with 'valid' (boolean) and 'message' (string) properties");
            }

            Value validValue = result.getMember("valid");
            Value messageValue = result.getMember("message");

            boolean valid = validValue != null && validValue.isBoolean() && validValue.asBoolean();
            String message = messageValue != null && !messageValue.isNull() ? messageValue.asString() : "";

            return new ValidationResult(valid, message);

        } catch (PolyglotException e) {
            if (e.isResourceExhausted()) {
                return new ValidationResult(false, "Script exceeded resource limits");
            }
            log.error("Polyglot script error: {}", e.getMessage());
            return new ValidationResult(false, "Script error: " + e.getMessage());
        }
    }

    private void removeUnsafeGlobals(Value bindings) {
        try {
            bindings.removeMember("load");
            bindings.removeMember("loadWithNewGlobal");
            bindings.removeMember("exit");
            bindings.removeMember("quit");
            bindings.removeMember("print");
            bindings.removeMember("printErr");
            bindings.removeMember("read");
            bindings.removeMember("readFully");
            bindings.removeMember("readline");
        } catch (Exception ignored) {
        }
    }
}
