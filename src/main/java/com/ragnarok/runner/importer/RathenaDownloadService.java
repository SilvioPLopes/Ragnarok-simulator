package com.ragnarok.runner.importer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Downloads YAML files from rAthena GitHub with retry and circuit breaker.
 *
 * Separated from RathenaImporter so that Resilience4j AOP can intercept calls
 * correctly (proxy-based interception requires cross-bean calls).
 *
 * Excluded from test profile — same as RathenaImporter.
 */
@Service
@Profile("!test")
public class RathenaDownloadService {

    private static final Logger log = LoggerFactory.getLogger(RathenaDownloadService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Downloads the YAML content from the given URL.
     *
     * Retries up to 3 times with exponential backoff (2s, 4s).
     * If all retries fail, the circuit breaker opens and falls back to returning null.
     *
     * @param url the raw GitHub URL
     * @return YAML content as String, or null if download fails after all retries
     */
    @Retry(name = "rathena", fallbackMethod = "downloadFalhou")
    @CircuitBreaker(name = "rathena")
    public String download(String url) {
        log.debug("Downloading: {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fallback invoked after all retries are exhausted.
     * Signature must match the decorated method with an extra Exception parameter.
     */
    private String downloadFalhou(String url, Exception e) {
        log.warn("rAthena unavailable after retries (url={}). Starting with existing data. Cause: {}",
                url, e.getMessage());
        return null;
    }
}
