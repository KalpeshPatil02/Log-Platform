package com.logplatform.ingestion.dto;

import java.util.List;

/** accepted = newly queued; duplicates = skipped; generatedEventIds = IDs assigned by this service. */
public record IngestResponse(int accepted, int duplicates, int generatedEventIds, List<String> eventIds) {}
