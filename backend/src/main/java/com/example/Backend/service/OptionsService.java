package com.example.Backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OptionsService {

    private static final String ISO_PREFIX =
            "PREFIX iso: <http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#>\n";

    private final FusekiService fusekiService;

    public OptionsService(FusekiService fusekiService) {
        this.fusekiService = fusekiService;
    }

    public Map<String, Object> getOptions() {
        Map<String, Object> options = new LinkedHashMap<>();

        options.put("processes", fusekiService.executeQuery(ISO_PREFIX + """
            SELECT DISTINCT ?value ?label
            WHERE {
                ?process a iso:Process .
                BIND(REPLACE(STR(?process), "^.*#", "") AS ?value)
                BIND(
                    IF(?value = "PM",
                       "Project Management (PM)",
                       IF(?value = "SI",
                          "Software Implementation (SI)",
                          ?value))
                    AS ?label
                )
                FILTER(?value IN ("PM", "SI"))
            }
            ORDER BY ?value
            """));

        options.put("roles", fusekiService.executeQuery(ISO_PREFIX + """
            SELECT DISTINCT ?value ?label
            WHERE {
                ?role a iso:Role ;
                      iso:hasRoleName ?roleName .

                BIND(STR(?roleName) AS ?value)
                BIND(STR(?roleName) AS ?label)
            }
            ORDER BY LCASE(?label)
            """));

        options.put("workProducts", fusekiService.executeQuery(ISO_PREFIX + """
            SELECT DISTINCT ?value
            WHERE {
                {
                    ?entity iso:hasOutputWorkProductName ?name .
                }
                UNION
                {
                    ?entity iso:hasInputWorkProductName ?name .
                }
                BIND(STR(?name) AS ?value)
                FILTER(STRLEN(STR(?name)) > 0)
            }
            ORDER BY LCASE(?value)
            """));

        options.put("situations", List.of(
                Map.of(
                        "value", "NOT_APPROVED",
                        "label", "Not approved / requires approval"
                ),
                Map.of(
                        "value", "APPROVED",
                        "label", "Already approved"
                )
        ));

        return options;
    }
}
