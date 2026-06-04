package com.example.configcenter.service;

import com.example.configcenter.model.dto.ValidationResult;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);
    private static final long TIMEOUT_SECONDS = 5;

    public ValidationResult execute(String scriptContent, String configKey, String configValue) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ValidationResult> future = executor.submit(() -> executeScript(scriptContent, configKey, configValue));
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

    private ValidationResult executeScript(String scriptContent, String configKey, String configValue) {
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            context.eval("js", scriptContent);

            Value validateFn = context.getBindings("js").getMember("validate");
            if (validateFn == null || !validateFn.canExecute()) {
                return new ValidationResult(false, "Script must define a 'validate(key, value)' function");
            }

            Value result = validateFn.execute(configKey, configValue);

            if (result == null || !result.hasMembers()) {
                return new ValidationResult(false, "Script validate() must return an object with 'valid' and 'message' properties");
            }

            Value validValue = result.getMember("valid");
            Value messageValue = result.getMember("message");

            boolean valid = validValue != null && validValue.asBoolean();
            String message = messageValue != null && !messageValue.isNull() ? messageValue.asString() : "";

            return new ValidationResult(valid, message);

        } catch (PolyglotException e) {
            log.error("Polyglot script error: {}", e.getMessage());
            return new ValidationResult(false, "Script error: " + e.getMessage());
        }
    }
}
