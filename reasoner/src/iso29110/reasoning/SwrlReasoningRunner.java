package iso29110.reasoning;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Runs the SWRL rules stored in the ontology with the Pellet reasoner and writes every
 * entailed fact about named individuals that is NOT already asserted (materialisation).
 *
 * This is the component that performs actual SWRL execution for the prototype.
 * Fuseki does not execute SWRL; the triples written here are loaded into a separate
 * named graph so asserted and inferred knowledge remain distinguishable.
 *
 * Usage:
 *   java -cp "lib/*:build" iso29110.reasoning.SwrlReasoningRunner \
 *        --in ontology.ttl --in data.ttl [--exclude-rule Label]... [--no-rules] \
 *        --out-inferred out.nt --report report.json
 */
public final class SwrlReasoningRunner {

    private static final String ISO_NS = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#";

    public static void main(String[] args) throws Exception {
        List<String> inputs = new ArrayList<>();
        Set<String> excluded = new TreeSet<>();
        boolean noRules = false;
        String outInferred = null;
        String report = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--in": inputs.add(args[++i]); break;
                case "--exclude-rule": excluded.add(args[++i]); break;
                case "--no-rules": noRules = true; break;
                case "--out-inferred": outInferred = args[++i]; break;
                case "--report": report = args[++i]; break;
                default: throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (inputs.isEmpty() || outInferred == null) {
            throw new IllegalArgumentException("--in and --out-inferred are required");
        }

        long start = System.currentTimeMillis();
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        // 1. Load every input document and merge all axioms into one working ontology.
        Set<OWLAxiom> axioms = new HashSet<>();
        Map<String, String> inputHashes = new LinkedHashMap<>();
        for (String in : inputs) {
            OWLOntology o = manager.loadOntologyFromOntologyDocument(new File(in));
            axioms.addAll(o.getAxioms());
            inputHashes.put(in, sha256(Paths.get(in)));
            manager.removeOntology(o);
        }

        // 2. Select the rules to execute (all rules unless excluded for ablation runs).
        List<Map<String, Object>> ruleInfo = new ArrayList<>();
        List<OWLAxiom> toRemove = new ArrayList<>();
        for (OWLAxiom ax : axioms) {
            if (ax instanceof SWRLRule) {
                SWRLRule rule = (SWRLRule) ax;
                String label = ruleLabel(rule);
                boolean enabled = !noRules && !excluded.contains(label);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("label", label);
                r.put("enabled", enabled);
                r.put("bodyAtoms", rule.getBody().size());
                r.put("headAtoms", rule.getHead().size());
                ruleInfo.add(r);
                if (!enabled) toRemove.add(ax);
            }
        }
        axioms.removeAll(toRemove);
        ruleInfo.sort(Comparator.comparing(m -> String.valueOf(m.get("label"))));

        OWLOntology working = manager.createOntology(axioms, IRI.create("urn:iso29110:working"));

        // 3. Reason with Pellet (SWRL rules are DL-safe rules evaluated over named individuals).
        PelletReasoner reasoner = PelletReasonerFactory.getInstance().createReasoner(working);
        boolean consistent = reasoner.isConsistent();
        if (!consistent) {
            writeReport(report, inputHashes, ruleInfo, false, 0, 0, 0, System.currentTimeMillis() - start, excluded, noRules);
            System.err.println("ONTOLOGY IS INCONSISTENT - no inferences written.");
            System.exit(2);
        }
        reasoner.precomputeInferences();

        // 4. Collect entailed facts about named individuals in the ISO namespace that are not asserted.
        List<String> lines = new ArrayList<>();
        int typeCount = 0, objCount = 0, dataCount = 0;
        Set<OWLClass> classes = new TreeSet<>(working.getClassesInSignature());
        Set<OWLObjectProperty> objProps = new TreeSet<>(working.getObjectPropertiesInSignature());
        Set<OWLDataProperty> dataProps = new TreeSet<>(working.getDataPropertiesInSignature());
        // RDF 1.1: a plain literal and an xsd:string literal with the same lexical form are the same value.
        // OWL API 3 keeps them apart, so asserted data values are compared by (subject, property, lexical form, datatype*).
        Set<String> assertedData = new HashSet<>();
        for (OWLDataPropertyAssertionAxiom a : working.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION)) {
            if (a.getSubject().isNamed() && !a.getProperty().isAnonymous()) {
                assertedData.add(dataKey(a.getSubject().asOWLNamedIndividual(), a.getProperty().asOWLDataProperty(), a.getObject()));
            }
        }
        for (OWLNamedIndividual ind : new TreeSet<>(working.getIndividualsInSignature())) {
            if (!ind.getIRI().toString().startsWith(ISO_NS)) continue;
            for (OWLClass c : reasoner.getTypes(ind, false).getFlattened()) {
                if (c.isOWLThing() || !c.getIRI().toString().startsWith(ISO_NS)) continue;
                if (working.containsAxiom(df.getOWLClassAssertionAxiom(c, ind))) continue;
                lines.add(nt(ind.getIRI()) + " " + nt(OWLRDFVocabulary.RDF_TYPE.getIRI()) + " " + nt(c.getIRI()) + " .");
                typeCount++;
            }
            for (OWLObjectProperty p : objProps) {
                if (!p.getIRI().toString().startsWith(ISO_NS)) continue;
                for (OWLNamedIndividual v : reasoner.getObjectPropertyValues(ind, p).getFlattened()) {
                    if (working.containsAxiom(df.getOWLObjectPropertyAssertionAxiom(p, ind, v))) continue;
                    lines.add(nt(ind.getIRI()) + " " + nt(p.getIRI()) + " " + nt(v.getIRI()) + " .");
                    objCount++;
                }
            }
            for (OWLDataProperty p : dataProps) {
                if (!p.getIRI().toString().startsWith(ISO_NS)) continue;
                for (OWLLiteral v : reasoner.getDataPropertyValues(ind, p)) {
                    if (assertedData.contains(dataKey(ind, p, v))) continue;
                    lines.add(nt(ind.getIRI()) + " " + nt(p.getIRI()) + " " + ntLiteral(v) + " .");
                    dataCount++;
                }
            }
        }
        Collections.sort(lines);
        Files.write(Paths.get(outInferred), lines, StandardCharsets.UTF_8);
        reasoner.dispose();

        long ms = System.currentTimeMillis() - start;
        writeReport(report, inputHashes, ruleInfo, true, typeCount, objCount, dataCount, ms, excluded, noRules);
        System.out.printf("consistent=true rulesEnabled=%d inferred: types=%d objectProps=%d dataProps=%d (%d ms)%n",
                ruleInfo.stream().filter(m -> Boolean.TRUE.equals(m.get("enabled"))).count(), typeCount, objCount, dataCount, ms);
    }

    /** Rule identifier = the rdfs:label Protégé stores on the rule. */
    static String ruleLabel(SWRLRule rule) {
        for (OWLAnnotation a : rule.getAnnotations()) {
            if (a.getProperty().isLabel() && a.getValue() instanceof OWLLiteral) {
                return ((OWLLiteral) a.getValue()).getLiteral();
            }
        }
        return "UNLABELLED:" + Integer.toHexString(rule.toString().hashCode());
    }

    static String dataKey(OWLNamedIndividual i, OWLDataProperty p, OWLLiteral v) {
        String dt = v.isRDFPlainLiteral() || v.getDatatype().isString() ? "string" : v.getDatatype().getIRI().toString();
        return i.getIRI() + "|" + p.getIRI() + "|" + v.getLiteral() + "|" + dt + "|" + (v.hasLang() ? v.getLang() : "");
    }

    static String nt(IRI iri) { return "<" + iri.toString() + ">"; }

    static String ntLiteral(OWLLiteral l) {
        String esc = l.getLiteral().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        if (l.hasLang()) return "\"" + esc + "\"@" + l.getLang();
        return "\"" + esc + "\"^^<" + l.getDatatype().getIRI() + ">";
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(Files.readAllBytes(p));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static void writeReport(String path, Map<String, String> inputs, List<Map<String, Object>> rules, boolean consistent,
                            int types, int objs, int datas, long ms, Set<String> excluded, boolean noRules) throws IOException {
        if (path == null) return;
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"reasoner\": \"Pellet 2.3.1 via OWL API 3.4.3\",\n");
        sb.append("  \"runAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"consistent\": ").append(consistent).append(",\n");
        sb.append("  \"noRules\": ").append(noRules).append(",\n");
        sb.append("  \"excludedRules\": ").append(jsonList(new ArrayList<>(excluded))).append(",\n");
        sb.append("  \"elapsedMs\": ").append(ms).append(",\n");
        sb.append("  \"inferred\": {\"types\": ").append(types).append(", \"objectPropertyValues\": ").append(objs)
          .append(", \"dataPropertyValues\": ").append(datas).append("},\n");
        sb.append("  \"inputs\": {");
        int i = 0;
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            sb.append(i++ == 0 ? "\n" : ",\n").append("    \"").append(json(e.getKey())).append("\": \"").append(e.getValue()).append("\"");
        }
        sb.append("\n  },\n  \"rules\": [");
        i = 0;
        for (Map<String, Object> r : rules) {
            sb.append(i++ == 0 ? "\n" : ",\n").append("    {\"label\": \"").append(json(String.valueOf(r.get("label"))))
              .append("\", \"enabled\": ").append(r.get("enabled")).append(", \"bodyAtoms\": ").append(r.get("bodyAtoms"))
              .append(", \"headAtoms\": ").append(r.get("headAtoms")).append("}");
        }
        sb.append("\n  ]\n}\n");
        Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String jsonList(List<String> xs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) sb.append(i == 0 ? "" : ", ").append("\"").append(json(xs.get(i))).append("\"");
        return sb.append("]").toString();
    }

    static String json(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
