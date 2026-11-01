package com.example.Backend.service;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes SELECT queries against the Fuseki dataset.
 * Fuseki does NOT run SWRL: inferred knowledge is produced offline by the Pellet pipeline
 * (scripts/build_knowledge_base.py) and loaded into the named graph urn:iso29110:graph:inferred-swrl.
 */
@Service
public class FusekiService {

    @Value("${fuseki.query-endpoint}")
    private String queryEndpoint;

    /** Runs a query whose parameters are bound (IRIs / literals), never string-concatenated. */
    public List<Map<String, String>> select(String sparql, Map<String, String> iriBindings, Map<String, String> literalBindings) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(sparql);
        iriBindings.forEach(pss::setIri);
        literalBindings.forEach(pss::setLiteral);
        return execute(pss.asQuery());
    }

    public List<Map<String, String>> select(String sparql) {
        return select(sparql, Map.of(), Map.of());
    }

    private List<Map<String, String>> execute(Query query) {
        List<Map<String, String>> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.service(queryEndpoint, query)) {
            ResultSet resultSet = qexec.execSelect();
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();
                Map<String, String> row = new LinkedHashMap<>();
                solution.varNames().forEachRemaining(var -> {
                    RDFNode node = solution.get(var);
                    if (node != null) {
                        row.put(var, node.isLiteral() ? node.asLiteral().getLexicalForm()
                                : node.isURIResource() ? node.asResource().getURI() : node.toString());
                    }
                });
                results.add(row);
            }
        }
        return results;
    }
}
