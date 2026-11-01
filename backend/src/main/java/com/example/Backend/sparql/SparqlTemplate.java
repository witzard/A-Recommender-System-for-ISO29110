package com.example.Backend.sparql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a SPARQL file from src/main/resources/sparql and keeps "#BEGIN name ... #END" blocks only for
 * parameters that were supplied ("#BEGIN a+b" = only when all of a and b were supplied). The same files and the same rule are used by
 * evaluation/run_cq_evaluation.py, so the evaluation measures exactly what the application runs.
 * Values are NEVER inserted here: they are bound afterwards with ParameterizedSparqlString.
 */
public final class SparqlTemplate {

    private static final Pattern BEGIN = Pattern.compile("^\\s*#BEGIN\\s+([\\w+]+)");
    private static final Pattern END = Pattern.compile("^\\s*#END");

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

    public static String render(String template, Set<String> suppliedParameters) {
        StringBuilder out = new StringBuilder();
        boolean keep = true;
        for (String line : template.split("\\R")) {
            Matcher b = BEGIN.matcher(line);
            if (b.find()) {
                keep = suppliedParameters.containsAll(java.util.Arrays.asList(b.group(1).split("\\+")));
                continue;
            }
            if (END.matcher(line).find()) {
                keep = true;
                continue;
            }
            if (keep) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}
