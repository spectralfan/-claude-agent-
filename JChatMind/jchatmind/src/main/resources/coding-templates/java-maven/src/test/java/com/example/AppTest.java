package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {
    @Test
    void greet() {
        assertEquals("Hello, World", new App().greet("World"));
    }
}
