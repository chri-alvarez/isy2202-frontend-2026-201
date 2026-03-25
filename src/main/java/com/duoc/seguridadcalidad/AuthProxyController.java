package com.duoc.seguridadcalidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class AuthProxyController {

    private static final Logger logger = LoggerFactory.getLogger(AuthProxyController.class);
    private final RestTemplate restTemplate;
    private final String loginUrl;
    private final String protectedExampleUrl;
    private final String recipesUrl;

    public AuthProxyController(RestTemplate restTemplate,
                               @Value("${backend.url:http://localhost:8081}") String backendUrl) {
        this.restTemplate = restTemplate;
        this.loginUrl = backendUrl + "/login";
        this.protectedExampleUrl = backendUrl + "/api/secure/profile";
        this.recipesUrl = backendUrl + "/recipes";
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
            logger.debug("Enviando POST a {}", loginUrl);
            ResponseEntity<String> response = restTemplate.exchange(loginUrl, HttpMethod.POST, requestEntity, String.class);
            logger.info("Login Response Status: {}", response.getStatusCode());
            logger.info("Login Response Body: {}", response.getBody());
            logger.info("====== LOGIN RESPONSE ======");
            return response;
        } catch (HttpStatusCodeException ex) {
            logger.error("Login Error Status: {}", ex.getStatusCode());
            logger.error("Login Error Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Login Exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body("Error: " + ex.getMessage());
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<String> profile(@RequestHeader(name = "Authorization", required = false) String authorization) {
        logger.info("====== PROFILE REQUEST ======");
        logger.info("URL: {}", protectedExampleUrl);
        
        if (authorization == null || authorization.isBlank()) {
            logger.warn("Profile request without Authorization header");
            return ResponseEntity.status(401).body("Authorization token missing");
        }
        
        logger.info("Authorization Header: {}", authorization.substring(0, Math.min(20, authorization.length())) + "...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            logger.debug("Enviando GET a {} con Authorization header", protectedExampleUrl);
            ResponseEntity<String> response = restTemplate.exchange(protectedExampleUrl, HttpMethod.GET, requestEntity, String.class);
            logger.info("Profile Response Status: {}", response.getStatusCode());
            logger.info("Profile Response Body: {}", response.getBody());
            logger.info("====== PROFILE RESPONSE ======");
            return response;
        } catch (HttpStatusCodeException ex) {
            logger.error("Profile Error Status: {}", ex.getStatusCode());
            logger.error("Profile Error Body: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Profile Exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body("Error: " + ex.getMessage());
        }
    }

    @GetMapping("/recipes")
    public ResponseEntity<String> getRecipes(@RequestHeader(name = "Authorization", required = false) String authorization) {
        logger.info("====== GET RECIPES REQUEST ======");

        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).body("Authorization token missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(recipesUrl, HttpMethod.GET, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            logger.error("Get Recipes Error: {}", ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Get Recipes Exception: {}", ex.getMessage());
            return ResponseEntity.status(500).body("Error: " + ex.getMessage());
        }
    }

    @PostMapping("/recipes")
    public ResponseEntity<String> createRecipe(@RequestHeader(name = "Authorization", required = false) String authorization,
                                               @RequestBody RecipeDto recipe) {
        logger.info("====== CREATE RECIPE REQUEST ======");

        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).body("Authorization token missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RecipeDto> requestEntity = new HttpEntity<>(recipe, headers);

        try {
            return restTemplate.exchange(recipesUrl, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            logger.error("Create Recipe Error: {}", ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error("Create Recipe Exception: {}", ex.getMessage());
            return ResponseEntity.status(500).body("Error: " + ex.getMessage());
        }
    }
}
