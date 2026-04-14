package com.duoc.seguridadcalidad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthProxyControllerTest {

    @Mock
    private RestTemplate restTemplate;

    private AuthProxyController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthProxyController(restTemplate, "http://localhost:8081", false);
    }

    @Test
    void registerConRespuestaExitosaDebeRetornarSuccessTrue() {
        RegisterRequest request = new RegisterRequest("nuevo", "nuevo@correo.com", "123456");

        when(restTemplate.exchange(
                eq("http://localhost:8081/register"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"id\":1}"));

        ResponseEntity<String> response = controller.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("{\"success\":true}", response.getBody());
    }

    @Test
    void registerConErrorDelBackendDebePropagarStatusYBody() {
        RegisterRequest request = new RegisterRequest("nuevo", "nuevo@correo.com", "123456");

        when(restTemplate.exchange(
                eq("http://localhost:8081/register"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", "{\"error\":\"email duplicado\"}".getBytes(), null));

        ResponseEntity<String> response = controller.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("{\"error\":\"email duplicado\"}", response.getBody());
    }
}
