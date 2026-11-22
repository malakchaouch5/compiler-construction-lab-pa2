import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.Program;
import chocopy.pa2.StudentAnalysis;
import chocopy.reference.RefAnalysis;
import chocopy.reference.RefParser;

/**
 * Differential tester for PA2: runs the reference semantic analysis and ours
 * on every ChocoPy file given on the command line (files or directories) and
 * compares the results.
 *
 *  - Program without semantic errors in the reference: the complete typed AST
 *    (all inferred types and locations) must be equal.
 *  - Program with errors: every reference error (message and start location)
 *    must also be reported by us (the grading rule); additional errors of
 *    ours are listed as EXTRA.
 *
 * Programs with syntax errors are skipped.  Usage (Windows: use `;`):
 *   javac -cp "chocopy-ref.jar:target/assignment.jar" -d tools/difftest/out \
 *         tools/difftest/DiffHarness.java
 *   java -cp "chocopy-ref.jar:target/assignment.jar:tools/difftest/out" \
 *         DiffHarness <file-or-directory>...
 */
public class DiffHarness {

    private int validEqual = 0;
    private int passed = 0;
    private int failed = 0;
    private int withExtraErrors = 0;
    private int syntaxErrors = 0;

    public static void main(String[] args) throws Exception {
        DiffHarness harness = new DiffHarness();
        for (Path file : pythonFiles(args)) {
            harness.compare(file);
        }
        harness.printSummary();
    }

    /** All .py files named by ARGS, directories searched recursively. */
    private static List<Path> pythonFiles(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String arg : args) {
            try (Stream<Path> walk = Files.walk(Paths.get(arg))) {
                walk.filter(f -> f.toString().endsWith(".py")).sorted()
                    .forEach(files::add);
            }
        }
        return files;
    }

    /** Compare both analyses on the program in FILE. */
    private void compare(Path file) throws Exception {
        String source = new String(Files.readAllBytes(file), "UTF-8");
        Program parsed = RefParser.process(source, false);
        if (parsed.hasErrors()) {
            syntaxErrors++;
            return;
        }
        String ast = parsed.toJSON();
        JsonNode expected = Node.readTree(
            RefAnalysis.process((Program) Node.fromJSON(ast)).toJSON());
        JsonNode actual;
        try {
            actual = Node.readTree(StudentAnalysis.process(
                (Program) Node.fromJSON(ast), false).toJSON());
        } catch (RuntimeException | AssertionError crash) {
            failed++;
            System.out.println("CRASH   " + file + ": " + crash);
            return;
        }

        Set<String> expectedErrors = errors(expected);
        Set<String> actualErrors = errors(actual);
        if (expectedErrors.isEmpty()) {
            if (expected.equals(actual)) {
                passed++;
                validEqual++;
            } else {
                failed++;
                System.out.println("DIFF    " + file + " " + actualErrors);
            }
            return;
        }
        Set<String> missing = new TreeSet<>(expectedErrors);
        missing.removeAll(actualErrors);
        Set<String> extra = new TreeSet<>(actualErrors);
        extra.removeAll(expectedErrors);
        if (!missing.isEmpty()) {
            failed++;
            System.out.println("MISSING " + file + " " + missing);
        } else {
            passed++;
        }
        if (!extra.isEmpty()) {
            withExtraErrors++;
            System.out.println("EXTRA   " + file + " " + extra);
        }
    }

    /** The errors of PROGRAM as "line:column message". */
    private static Set<String> errors(JsonNode program) {
        Set<String> result = new TreeSet<>();
        for (JsonNode error : program.get("errors").get("errors")) {
            JsonNode location = error.get("location");
            result.add(location.get(0) + ":" + location.get(1) + " "
                       + error.get("message").asText());
        }
        return result;
    }

    private void printSummary() {
        System.out.printf("Passed: %d (valid programs with equal typed AST: "
                          + "%d), failed: %d, with extra errors: %d, skipped "
                          + "(syntax errors): %d%n", passed, validEqual,
                          failed, withExtraErrors, syntaxErrors);
    }
}
