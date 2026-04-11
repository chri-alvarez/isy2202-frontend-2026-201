package com.duoc.seguridadcalidad;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class AuthProxyController {

    private static final Logger logger = LoggerFactory.getLogger(AuthProxyController.class);
    private static final String COOKIE_NAME = "jwt_token";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\"(?:token|accessToken|jwt|bearer|authToken)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;
    private final String loginUrl;
    private final String registerUrl;
    private final String protectedExampleUrl;
    private final String recipesUrl;
    private final boolean secureCookie;

    public AuthProxyController(RestTemplate restTemplate,
                               @Value("${backend.url:http://localhost:8081}") String backendUrl,
                               @Value("${app.cookie.secure:true}") boolean secureCookie) {
        this.restTemplate = restTemplate;
        this.loginUrl = backendUrl + "/login";
        this.registerUrl = backendUrl + "/register";
        this.protectedExampleUrl = backendUrl + "/api/secure/profile";
        this.recipesUrl = backendUrl + "/recipes";
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest loginRequest) {
        logger.info("====== LOGIN REQUEST ======");
        logger.info("URL: {}", loginUrl);
        logger.info("Body: username={}", loginRequest.getUsername());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> requestEntity = new HttpEntity<>(loginRequest, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(loginUrl, HttpMethod.POST, requestEntity, String.class);
            logger.info("Login Response Status: {}", response.getStatusCode());

            String rawToken = extractToken(response.getBody());
            if (rawToken == null) {
                logger.error("No se pudo extraer el token JWT de la respuesta del backend");
                return ResponseEntity.status(500).body("{\"error\":\"Token not found in backend response\"}");
            }

            // Almacenar únicamente el valor del token (sin prefijo "Bearer ")
            String tokenValue = rawToken.startsWith("Bearer ") ? rawToken.substring(7) : rawToken;

            ResponseCookie jwtCookie = ResponseCookie.from(COOKIE_NAME, tokenValue)
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Strict")
                    .path("/")
                    .build();

            logger.info("JWT cookie establecida para el usuario: {}", loginRequest.getUsername());
            logger.info("====== LOGIN RESPONSE ======");
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .body("{\"success\":true}");

        } catch (HttpStatusCodeException ex) {
            logger.error("Login Error Status: {}", ex.getStatusCode());
            logger.error("Login Error Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Login Exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest registerRequest) {
        logger.info("====== REGISTER REQUEST ======");
        logger.info("URL: {}", registerUrl);
        logger.info("Body: username={}, email={}", registerRequest.getUsername(), registerRequest.getEmail());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegisterRequest> requestEntity = new HttpEntity<>(registerRequest, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(registerUrl, HttpMethod.POST, requestEntity, String.class);
            logger.info("Register Response Status: {}", response.getStatusCode());
            logger.info("====== REGISTER RESPONSE ======");
            return ResponseEntity.status(response.getStatusCode()).body("{\"success\":true}");
        } catch (HttpStatusCodeException ex) {
            logger.error("Register Error Status: {}", ex.getStatusCode());
            logger.error("Register Error Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Register Exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    @GetMapping("/session")
    public ResponseEntity<String> session(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        if (token != null && !token.isBlank()) {
            return ResponseEntity.ok("{\"authenticated\":true}");
        }
        return ResponseEntity.status(401).body("{\"authenticated\":false}");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        ResponseCookie clearCookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        logger.info("JWT cookie eliminada (logout)");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body("{\"success\":true}");
    }

    @GetMapping("/profile")
    public ResponseEntity<String> profile(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        logger.info("====== PROFILE REQUEST ======");
        logger.info("URL: {}", protectedExampleUrl);

        if (token == null || token.isBlank()) {
            logger.warn("Profile request sin cookie JWT");
            return ResponseEntity.status(401).body("No autenticado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(protectedExampleUrl, HttpMethod.GET, requestEntity, String.class);
            logger.info("Profile Response Status: {}", response.getStatusCode());
            logger.info("====== PROFILE RESPONSE ======");
            return response;
        } catch (HttpStatusCodeException ex) {
            logger.error("Profile Error Status: {}", ex.getStatusCode());
            logger.error("Profile Error Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Profile Exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    @GetMapping("/recipes")
    public ResponseEntity<String> getRecipes(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        logger.info("====== GET RECIPES REQUEST ======");

        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body("No autenticado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(recipesUrl, HttpMethod.GET, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            logger.error("Get Recipes Error: {}", ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Get Recipes Exception: {}", ex.getMessage());
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    @PostMapping("/recipes")
    public ResponseEntity<String> createRecipe(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                               @RequestBody RecipeDto recipe) {
        logger.info("====== CREATE RECIPE REQUEST ======");

        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body("No autenticado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RecipeDto> requestEntity = new HttpEntity<>(recipe, headers);

        try {
            return restTemplate.exchange(recipesUrl, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            logger.error("Create Recipe Error: {}", ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Create Recipe Exception: {}", ex.getMessage());
            return ResponseEntity.status(500).body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * Extrae el valor del token JWT desde el cuerpo de la respuesta del backend.
     * Soporta respuestas JSON con campos "token", "accessToken", "jwt", "bearer" o "authToken",
     * y también tokens crudos (plain text).
     */
    private String extractToken(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        // Buscar un campo de token conocido en la respuesta JSON
        Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Si no hay JSON o no se encontró el campo, tratar el cuerpo completo como token crudo
        String trimmed = responseBody.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
