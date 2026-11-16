package com.logplatform.ingestion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchRequest(@NotEmpty @Size(max = 500) List<@Valid LogEventRequest> events) {}
