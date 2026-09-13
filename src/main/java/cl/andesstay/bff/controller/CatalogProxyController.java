package cl.andesstay.bff.controller;

import cl.andesstay.bff.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogProxyController {

    private final ProxyService proxyService;

    public CatalogProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) String body
    ) {
        String path = extractPath(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return proxyService.proxyCatalog(path, method, request);
    }

    private String extractPath(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/catalog/";
        if (fullPath.startsWith(prefix)) {
            String sub = fullPath.substring(prefix.length());
            return sub.isEmpty() ? null : sub;
        }
        return null;
    }
}
