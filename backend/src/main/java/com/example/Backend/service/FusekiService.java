package com.example.Backend.service;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FusekiService {
    
    @Value("${fuseki.query-endpoint}")
    private String queryEndpoint;

    public List<Map<String, String>> executeQuery(String sparql) {

        Query query = QueryFactory.create(sparql);

        List<Map<String, String>> results = new ArrayList<>();

        try (QueryExecution qexec =
                     QueryExecution.service(queryEndpoint, query)) {

            ResultSet resultSet = qexec.execSelect();

            while (resultSet.hasNext()) {

                QuerySolution solution = resultSet.next();

                Map<String, String> row = new LinkedHashMap<>();

                solution.varNames().forEachRemaining(var -> {
                    if (solution.get(var) != null) {
                        row.put(var, solution.get(var).toString());
                    }
                });

                results.add(row);
            }
        }

        return results;
    }
}