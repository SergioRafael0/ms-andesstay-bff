package cl.andesstay.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;

@Service
public class ProxyService {

    @Value("${andesstay.microservices.reservations-url}")
    private String reservationsUrl;

    @Value("${andesstay.microservices.catalog-url}")
    private String catalogUrl;

    // RestTemplate con timeouts: sin ellos, un microservicio caído deja la
    // petición colgada indefinidamente.
    private final RestTemplate restTemplate;

    public ProxyService() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

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
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("connection") && !name.equalsIgnoreCase("upgrade") && !name.equalsIgnoreCase("expect") && !name.equalsIgnoreCase("origin") && !name.toLowerCase().startsWith("access-control-request-") && !name.equalsIgnoreCase("content-length")) {
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
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // Error 5xx del microservicio interno: se traduce a 502 para el cliente
            // en lugar de dejar que la excepción escape como error 500 sin control.
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"El microservicio interno devolvió un error\",\"detail\":\""
                            + e.getStatusCode().value() + "\"}");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Sin conexión / timeout hacia el microservicio interno.
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"Microservicio interno no disponible\"}");
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