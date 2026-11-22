package chocopy.pa2;

import java.util.List;

import chocopy.common.astnodes.IfStmt;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;

/**
 * Decides whether a statement list returns on every path (semantic rule 9).
 *
 * A list returns on every path if one of its statements is a `return`, or an
 * `if` whose then-branch and else-branch both return on every path (an
 * `elif` is an `if` inside the else-branch).  Loops never count, because
 * their body may be executed zero times.
 */
final class ReturnPaths {

    private ReturnPaths() { }

    /** Return true iff executing STATEMENTS always ends in a return. */
    static boolean alwaysReturns(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            if (stmt instanceof ReturnStmt) {
                return true;
            }
            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (alwaysReturns(ifStmt.thenBody)
                        && alwaysReturns(ifStmt.elseBody)) {
                    return true;
                }
            }
        }
        return false;
    }
}
