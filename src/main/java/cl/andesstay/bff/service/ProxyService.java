package cl.andesstay.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Set;

@Service
public class ProxyService {

    @Value("${andesstay.microservices.reservations-url}")
    private String reservationsUrl;

    @Value("${andesstay.microservices.catalog-url}")
    private String catalogUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Headers que pertenecen a la conexión HTTP en sí, no al contenido.
    // Si se reenvían tal cual, chocan con los que agrega el propio
    // servidor del BFF y generan respuestas corruptas (headers duplicados,
    // por ejemplo "Transfer-Encoding" repetido, que rompe el túnel de Cloudflare).
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "transfer-encoding", "connection", "content-length", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailer", "upgrade");

    public ResponseEntity<String> proxyReservations(String path, HttpMethod method, HttpServletRequest request) {
        String url = reservationsUrl + (path != null ? "/" + path : "");
        return forward(url, method, request);
    }

    public ResponseEntity<String> proxyCatalog(String path, HttpMethod method, HttpServletRequest request) {
        String url = catalogUrl + (path != null ? "/" + path : "");
        return forward(url, method, request);
    }

    // Arma la petición hacia el microservicio interno copiando los headers
    // del cliente (excepto host/content-length), reenvía el body tal cual,
    // y limpia los headers de la respuesta antes de devolverla al llamador.
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
        } catch (Exception ignored) {
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(stripHopByHopHeaders(response.getHeaders()))
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(stripHopByHopHeaders(e.getResponseHeaders()))
                    .body(e.getResponseBodyAsString());
        }
    }

    // Quita los headers hop-by-hop de una respuesta antes de reenviarla,
    // dejando solo los headers de contenido reales (Content-Type, etc.).
    private HttpHeaders stripHopByHopHeaders(HttpHeaders original) {
        HttpHeaders clean = new HttpHeaders();
        if (original != null) {
            original.forEach((name, values) -> {
                if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    clean.put(name, values);
                }
            });
        }
        return clean;
    }
}