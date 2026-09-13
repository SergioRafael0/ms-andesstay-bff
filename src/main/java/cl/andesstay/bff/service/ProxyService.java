package cl.andesstay.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;

@Service
public class ProxyService {

    @Value("${andesstay.microservices.reservations-url}")
    private String reservationsUrl;

    @Value("${andesstay.microservices.catalog-url}")
    private String catalogUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<String> proxyReservations(String path, HttpMethod method, HttpServletRequest request) {
        String url = reservationsUrl + (path != null ? "/" + path : "");
        return forward(url, method, request);
    }

    public ResponseEntity<String> proxyCatalog(String path, HttpMethod method, HttpServletRequest request) {
        String url = catalogUrl + (path != null ? "/" + path : "");
        return forward(url, method, request);
    }

    private ResponseEntity<String> forward(String url, HttpMethod method, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                headers.add(name, request.getHeader(name));
            }
        }

        byte[] body = null;
        try {
            if (request.getInputStream() != null) {
                body = request.getInputStream().readAllBytes();
            }
        } catch (Exception ignored) {}

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.exchange(url, method, entity, String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(e.getResponseBodyAsString());
        }
    }
}
