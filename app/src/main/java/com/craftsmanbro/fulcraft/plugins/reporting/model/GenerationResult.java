package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import java.util.Optional;

/** Feature-layer neutral generation result representation. */
public class GenerationResult {

  private boolean success;

  private String generatedTestCode;

  private String testClassName;

  private String errorMessage;

  private String errorCode;

  private Duration elapsedTime;

  private int tokenUsage;

  private int promptTokens;

  private int completionTokens;

  private int retryCount;

  private String llmModelUsed;

  private String rawLlmResponse;

  public static Builder success() {
    return new Builder(true);
  }

  public static Builder failure() {
    return new Builder(false);
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(final boolean success) {
    this.success = success;
  }

  public boolean isFailure() {
    return !success;
  }

  @JsonIgnore
  public Optional<String> getGeneratedTestCode() {
    return Optional.ofNullable(generatedTestCode);
  }

  @JsonGetter("generatedTestCode")
  public String getGeneratedTestCodeValue() {
    return generatedTestCode;
  }

  public void setGeneratedTestCode(final String generatedTestCode) {
    this.generatedTestCode = generatedTestCode;
  }

  @JsonIgnore
  public Optional<String> getTestClassName() {
    return Optional.ofNullable(testClassName);
  }

  @JsonGetter("testClassName")
  public String getTestClassNameValue() {
    return testClassName;
  }

  public void setTestClassName(final String testClassName) {
    this.testClassName = testClassName;
  }

  @JsonIgnore
  public Optional<String> getErrorMessage() {
    return Optional.ofNullable(errorMessage);
  }

  @JsonGetter("errorMessage")
  public String getErrorMessageValue() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @JsonIgnore
  public Optional<String> getErrorCode() {
    return Optional.ofNullable(errorCode);
  }

  @JsonGetter("errorCode")
  public String getErrorCodeValue() {
    return errorCode;
  }

  public void setErrorCode(final String errorCode) {
    this.errorCode = errorCode;
  }

  @JsonIgnore
  public Optional<Duration> getElapsedTime() {
    return Optional.ofNullable(elapsedTime);
  }

  public void setElapsedTime(final Duration elapsedTime) {
    this.elapsedTime = elapsedTime;
  }

  @JsonGetter("elapsedTimeMs")
  public Long getElapsedTimeMs() {
    return elapsedTime != null ? elapsedTime.toMillis() : null;
  }

  public void setElapsedTimeMs(final Long elapsedTimeMs) {
    this.elapsedTime = elapsedTimeMs != null ? Duration.ofMillis(elapsedTimeMs) : null;
  }

  public int getTokenUsage() {
    return tokenUsage;
  }

  public void setTokenUsage(final int tokenUsage) {
    this.tokenUsage = tokenUsage;
  }

  public int getPromptTokens() {
    return promptTokens;
  }

  public void setPromptTokens(final int promptTokens) {
    this.promptTokens = promptTokens;
  }

  public int getCompletionTokens() {
    return completionTokens;
  }

  public void setCompletionTokens(final int completionTokens) {
    this.completionTokens = completionTokens;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(final int retryCount) {
    this.retryCount = retryCount;
  }

  @JsonIgnore
  public Optional<String> getLlmModelUsed() {
    return Optional.ofNullable(llmModelUsed);
  }

  @JsonGetter("llmModelUsed")
  public String getLlmModelUsedValue() {
    return llmModelUsed;
  }

  public void setLlmModelUsed(final String llmModelUsed) {
    this.llmModelUsed = llmModelUsed;
  }

  @JsonIgnore
  public Optional<String> getRawLlmResponse() {
    return Optional.ofNullable(rawLlmResponse);
  }

  @JsonGetter("rawLlmResponse")
  public String getRawLlmResponseValue() {
    return rawLlmResponse;
  }

  public void setRawLlmResponse(final String rawLlmResponse) {
    this.rawLlmResponse = rawLlmResponse;
  }

  /** Builder for API compatibility with generation-side DTOs. */
  public static final class Builder {

    private final boolean success;

    private String generatedTestCode;

    private String testClassName;

    private String errorMessage;

    private String errorCode;

    private Duration elapsedTime;

    private int tokenUsage;

    private int promptTokens;

    private int completionTokens;

    private int retryCount;

    private String llmModelUsed;

    private String rawLlmResponse;

    private Builder(final boolean success) {
      this.success = success;
    }

    public Builder generatedTestCode(final String generatedTestCode) {
      this.generatedTestCode = generatedTestCode;
      return this;
    }

    public Builder testClassName(final String testClassName) {
      this.testClassName = testClassName;
      return this;
    }

    public Builder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder errorCode(final String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public Builder elapsedTime(final Duration elapsedTime) {
      this.elapsedTime = elapsedTime;
      return this;
    }

    public Builder tokenUsage(final int tokenUsage) {
      this.tokenUsage = tokenUsage;
      return this;
    }

    public Builder promptTokens(final int promptTokens) {
      this.promptTokens = promptTokens;
      return this;
    }

    public Builder completionTokens(final int completionTokens) {
      this.completionTokens = completionTokens;
      return this;
    }

    public Builder retryCount(final int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    public Builder llmModelUsed(final String llmModelUsed) {
      this.llmModelUsed = llmModelUsed;
      return this;
    }

    public Builder rawLlmResponse(final String rawLlmResponse) {
      this.rawLlmResponse = rawLlmResponse;
      return this;
    }

    public GenerationResult build() {
      final GenerationResult result = new GenerationResult();
      result.setSuccess(success);
      result.setGeneratedTestCode(generatedTestCode);
      result.setTestClassName(testClassName);
      result.setErrorMessage(errorMessage);
      result.setErrorCode(errorCode);
      result.setElapsedTime(elapsedTime);
      result.setTokenUsage(tokenUsage);
      result.setPromptTokens(promptTokens);
      result.setCompletionTokens(completionTokens);
      result.setRetryCount(retryCount);
      result.setLlmModelUsed(llmModelUsed);
      result.setRawLlmResponse(rawLlmResponse);
      return result;
    }
  }
}
