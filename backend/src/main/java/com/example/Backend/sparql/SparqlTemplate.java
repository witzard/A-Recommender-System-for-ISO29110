package com.example.Backend.sparql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads a SPARQL file from src/main/resources/sparql. Values are bound by FusekiService, never inserted here. */
public final class SparqlTemplate {

    private SparqlTemplate() {
    }

    public static String load(String name) {
        try (InputStream in = SparqlTemplate.class.getResourceAsStream("/sparql/" + name)) {
            if (in == null) {
                throw new IllegalStateException("SPARQL resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read SPARQL resource " + name, e);
        }
    }
}
