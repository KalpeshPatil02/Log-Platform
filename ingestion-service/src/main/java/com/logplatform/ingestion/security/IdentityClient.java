package com.logplatform.ingestion.security;

import com.logplatform.ingestion.config.IngestionProperties;
import com.logplatform.ingestion.exception.IdentityUnavailableException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Service-to-service call to identity-service /internal/api-keys/validate. */
@Component
public class IdentityClient {
    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    private final RestTemplate restTemplate;
    private final IngestionProperties props;

    public IdentityClient(RestTemplate identityRestTemplate, IngestionProperties props) {
        this.restTemplate = identityRestTemplate;
        this.props = props;
    }

    public ValidationResult validate(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", props.internalToken());
        try {
            ResponseEntity<ValidationResult> res = restTemplate.exchange(
                    props.identityUrl() + "/internal/api-keys/validate", HttpMethod.POST,
                    new HttpEntity<>(Map.of("apiKey", apiKey), headers), ValidationResult.class);
            if (res.getBody() == null) {
                throw new IdentityUnavailableException("Empty response from identity-service", null);
            }
            return res.getBody();
        } catch (RestClientException e) {
            // includes 401 from a wrong internal token: that is OUR misconfiguration, not a bad customer key
            log.error("identity-service call failed: {}", e.getMessage());
            throw new IdentityUnavailableException("Cannot validate API key right now", e);
        }
    }

    public record ValidationResult(boolean valid, String reason, String tenantId, String apiKeyId) {}
}
