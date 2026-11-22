package chocopy.pa2;

import chocopy.common.astnodes.Program;

/** Top-level class for performing semantic analysis. */
public class StudentAnalysis {

    /** Perform semantic analysis on PROGRAM, adding error messages and
     *  type annotations. Provide debugging output iff DEBUG. Returns modified
     *  tree.
     *
     *  The analysis makes three passes over the AST:
     *    1. DeclarationAnalyzer builds the symbol tables and the class
     *       hierarchy and checks the declaration rules;
     *    2. TypeAnnotationChecker checks that annotations name classes;
     *    3. TypeChecker infers the type of every expression and checks the
     *       typing rules.
     *  As in the reference implementation, type checking only runs if the
     *  declarations are free of errors, because the symbol tables of an
     *  erroneous program are incomplete. */
    public static Program process(Program program, boolean debug) {
        if (program.hasErrors()) {
            return program;
        }
        /* At most one error message per AST node (Section 5.3.2). */
        program.errors.suppressMultipleErrors();

        DeclarationAnalyzer declarationAnalyzer =
            new DeclarationAnalyzer(program.errors);
        program.dispatch(declarationAnalyzer);
        program.dispatch(new TypeAnnotationChecker(program.errors));

        if (!program.hasErrors()) {
            TypeChecker typeChecker =
                new TypeChecker(declarationAnalyzer, program.errors);
            program.dispatch(typeChecker);
        }

        if (debug) {
            System.err.printf("Semantic analysis found %d error(s).%n",
                              program.getErrorList().size());
        }
        return program;
    }
}
