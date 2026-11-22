package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.CallExpr;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Expr;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.ForStmt;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IfExpr;
import chocopy.common.astnodes.IfStmt;
import chocopy.common.astnodes.IndexExpr;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.ListExpr;
import chocopy.common.astnodes.MemberExpr;
import chocopy.common.astnodes.MethodCallExpr;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.NoneLiteral;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.StringLiteral;
import chocopy.common.astnodes.UnaryExpr;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.WhileStmt;

import static chocopy.common.analysis.types.Type.BOOL_TYPE;
import static chocopy.common.analysis.types.Type.EMPTY_TYPE;
import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.NONE_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.STR_TYPE;

/**
 * Second pass of the semantic analysis: infers the type of every expression
 * and checks the typing rules of the ChocoPy reference (Chapter 5), using
 * the symbol tables and the class hierarchy built by
 * {@link DeclarationAnalyzer}.  It also checks the semantic rules about
 * assignments to inherited variables (rule 8) and returns at the top level
 * (rule 10).
 *
 * Error recovery follows Section 5.3.2 of the specification: an ill-typed
 * expression gets the most specific type that the conclusion of its typing
 * rule allows when the failing premise is dropped, and the message describes
 * the topmost failing premise.  The analyze methods return the inferred type
 * of expressions and null for all other nodes.
 */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** Name of the constructor method. */
    private static final String INIT_METHOD = "__init__";

    /** Collector for errors. */
    private final Errors errors;
    /** All classes of the program. */
    private final ClassHierarchy classes;
    /** The symbol table of the body of each function and method. */
    private final Map<FuncDef, SymbolTable<Type>> functionScopes;
    /** The symbol table of the region being checked. */
    private SymbolTable<Type> sym;
    /** Declared return type of the function being checked, or null at the
     *  top level of the program. */
    private ValueType expectedReturnType;

    /** A type checker for the program whose declarations were analyzed by
     *  DECLARATIONS, sending semantic errors to ERRORS0. */
    public TypeChecker(DeclarationAnalyzer declarations, Errors errors0) {
        sym = declarations.getGlobals();
        classes = declarations.getClassHierarchy();
        functionScopes = declarations.getFunctionScopes();
        errors = errors0;
    }

    /* ==================================================================
     * Declarations.
     * ================================================================== */

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        checkStatements(program.statements);
        return null;
    }

    /** [VAR-INIT]: the literal must be assignable to the declared type. */
    @Override
    public Type analyze(VarDef varDef) {
        ValueType declared = ValueType.annotationToValueType(varDef.var.type);
        Type value = varDef.value.dispatch(this);
        if (!classes.isAssignable(declared, value)) {
            err(varDef, "Expected type `%s`; got type `%s`", declared, value);
        }
        return null;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        for (Declaration member : classDef.declarations) {
            member.dispatch(this);
        }
        return null;
    }

    /** Check the body of a function or method in its own scope. */
    @Override
    public Type analyze(FuncDef funcDef) {
        SymbolTable<Type> enclosingScope = sym;
        ValueType enclosingReturnType = expectedReturnType;
        sym = functionScopes.get(funcDef);
        expectedReturnType =
            ValueType.annotationToValueType(funcDef.returnType);

        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        checkStatements(funcDef.statements);

        sym = enclosingScope;
        expectedReturnType = enclosingReturnType;
        return null;
    }

    /* ==================================================================
     * Statements.
     * ================================================================== */

    @Override
    public Type analyze(ExprStmt stmt) {
        stmt.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(IfStmt stmt) {
        checkCondition(stmt, stmt.condition);
        checkStatements(stmt.thenBody);
        checkStatements(stmt.elseBody);
        return null;
    }

    @Override
    public Type analyze(WhileStmt stmt) {
        checkCondition(stmt, stmt.condition);
        checkStatements(stmt.body);
        return null;
    }

    /** [FOR-STR] and [FOR-LIST]: iterate over a str or a list; each element
     *  must be assignable to the loop variable.  If the iterable has another
     *  type, the loop variable is not checked (as in the reference). */
    @Override
    public Type analyze(ForStmt stmt) {
        Type iterable = stmt.iterable.dispatch(this);
        Type elementType = null;
        if (iterable.isListType()) {
            elementType = iterable.elementType();
        } else if (STR_TYPE.equals(iterable)) {
            elementType = STR_TYPE;
        } else {
            err(stmt, "Cannot iterate over value of type `%s`", iterable);
        }

        if (elementType != null) {
            Type varType = checkAssignableVariable(stmt.identifier, stmt);
            if (!classes.isAssignable(varType, elementType)) {
                err(stmt, "Expected type `%s`; got type `%s`", varType,
                    elementType);
            }
        }
        checkStatements(stmt.body);
        return null;
    }

    /** [RETURN-e] and [RETURN-None].  A return outside of a function is a
     *  semantic error (rule 10). */
    @Override
    public Type analyze(ReturnStmt stmt) {
        Type value = stmt.value == null ? null : stmt.value.dispatch(this);
        if (expectedReturnType == null) {
            err(stmt, "Return statement cannot appear at the top level");
        } else if (value == null) {
            if (!classes.isAssignable(expectedReturnType, NONE_TYPE)) {
                err(stmt, "Expected type `%s`; got `None`",
                    expectedReturnType);
            }
        } else if (!classes.isAssignable(expectedReturnType, value)) {
            err(stmt, "Expected type `%s`; got type `%s`",
                expectedReturnType, value);
        }
        return null;
    }

    /** [VAR-ASSIGN-STMT], [ATTR-ASSIGN-STMT], [LIST-ASSIGN-STMT] and
     *  [MULTI-ASSIGN-STMT].  Errors about a target itself are attached to
     *  the target; a mismatch between the value and a target is reported
     *  once, for the leftmost such target, at the statement. */
    @Override
    public Type analyze(AssignStmt stmt) {
        Type value = stmt.value.dispatch(this);
        List<Type> targetTypes = new ArrayList<>();
        for (Expr target : stmt.targets) {
            targetTypes.add(checkAssignmentTarget(target));
        }

        for (Type targetType : targetTypes) {
            if (!classes.isAssignable(targetType, value)) {
                err(stmt, "Expected type `%s`; got type `%s`", targetType,
                    value);
                return null;
            }
        }
        if (stmt.targets.size() > 1 && value.isListType()
                && NONE_TYPE.equals(value.elementType())) {
            err(stmt, "Right-hand side of multiple assignment may not be %s",
                value);
        }
        return null;
    }

    /** Type check the assignment target TARGET and return the type of the
     *  location it denotes. */
    private Type checkAssignmentTarget(Expr target) {
        if (target instanceof Identifier) {
            return checkAssignableVariable((Identifier) target, target);
        }
        if (target instanceof IndexExpr) {
            return checkListElementTarget((IndexExpr) target);
        }
        return target.dispatch(this);   /* MemberExpr: an attribute. */
    }

    /** The target ID of an assignment or for loop must be a variable that
     *  is declared in the current scope (explicitly, or by a global or
     *  nonlocal declaration; rule 8).  If ID is no variable at all, the error
     *  is reported at NOTVARIABLESITE.  Return the variable's type. */
    private Type checkAssignableVariable(Identifier id, Node notVariableSite) {
        Type type = sym.get(id.name);
        if (type == null || !type.isValueType()) {
            err(notVariableSite, "Not a variable: %s", id.name);
            return id.setInferredType(OBJECT_TYPE);
        }
        if (!sym.declares(id.name)) {
            err(id, "Cannot assign to variable that is not explicitly "
                + "declared in this scope: %s", id.name);
        }
        return id.setInferredType(type);
    }

    /** The target LIST[INDEX] of an assignment is first checked like the
     *  expression LIST[INDEX]; if that reports no error, but LIST is a str
     *  (strings are immutable), the target is rejected as well. */
    private Type checkListElementTarget(IndexExpr target) {
        Type elementType = target.dispatch(this);
        Type listType = target.list.getInferredType();
        if (!listType.isListType()) {
            err(target, "`%s` is not a list type", listType);
        }
        return elementType;
    }

    /* ==================================================================
     * Literals and identifiers.
     * ================================================================== */

    @Override
    public Type analyze(IntegerLiteral literal) {
        return literal.setInferredType(INT_TYPE);
    }

    @Override
    public Type analyze(BooleanLiteral literal) {
        return literal.setInferredType(BOOL_TYPE);
    }

    @Override
    public Type analyze(StringLiteral literal) {
        return literal.setInferredType(STR_TYPE);
    }

    @Override
    public Type analyze(NoneLiteral literal) {
        return literal.setInferredType(NONE_TYPE);
    }

    /** [VAR-READ]: the identifier must name a variable. */
    @Override
    public Type analyze(Identifier id) {
        Type type = sym.get(id.name);
        if (type != null && type.isValueType()) {
            return id.setInferredType(type);
        }
        err(id, "Not a variable: %s", id.name);
        return id.setInferredType(OBJECT_TYPE);
    }

    /* ==================================================================
     * Operators.
     * ================================================================== */

    /** [NEGATE] and [NOT]. */
    @Override
    public Type analyze(UnaryExpr expr) {
        Type operand = expr.operand.dispatch(this);
        ValueType resultType = "-".equals(expr.operator) ? INT_TYPE
                                                         : BOOL_TYPE;
        if (!resultType.equals(operand)) {
            err(expr, "Cannot apply operator `%s` on type `%s`",
                expr.operator, operand);
        }
        return expr.setInferredType(resultType);
    }

    /** The typing rules of the binary operators: [ARITH], [INT-COMPARE],
     *  [BOOL-COMPARE], [STR-COMPARE], [STR-CONCAT], [LIST-CONCAT], [AND],
     *  [OR] and [IS]. */
    @Override
    public Type analyze(BinaryExpr expr) {
        Type left = expr.left.dispatch(this);
        Type right = expr.right.dispatch(this);
        boolean bothInt = INT_TYPE.equals(left) && INT_TYPE.equals(right);

        switch (expr.operator) {
        case "+":
            return expr.setInferredType(checkPlus(expr, left, right));
        case "-":
        case "*":
        case "//":
        case "%":
            return checkOperands(expr, bothInt, left, right, INT_TYPE);
        case "<":
        case "<=":
        case ">":
        case ">=":
            return checkOperands(expr, bothInt, left, right, BOOL_TYPE);
        case "==":
        case "!=":
            boolean comparable = bothInt
                || (left.equals(right) && (BOOL_TYPE.equals(left)
                                           || STR_TYPE.equals(left)));
            return checkOperands(expr, comparable, left, right, BOOL_TYPE);
        case "and":
        case "or":
            boolean bothBool = BOOL_TYPE.equals(left)
                && BOOL_TYPE.equals(right);
            return checkOperands(expr, bothBool, left, right, BOOL_TYPE);
        case "is":
            boolean bothObjects = !left.isSpecialType()
                && !right.isSpecialType();
            return checkOperands(expr, bothObjects, left, right, BOOL_TYPE);
        default:
            return expr.setInferredType(OBJECT_TYPE);
        }
    }

    /** [ARITH], [STR-CONCAT] and [LIST-CONCAT] for `+`.  If the operands do
     *  not match, the result is int if one operand is an int, and object
     *  otherwise (Section 5.3.2). */
    private Type checkPlus(BinaryExpr expr, Type left, Type right) {
        if (INT_TYPE.equals(left) && INT_TYPE.equals(right)) {
            return INT_TYPE;
        }
        if (STR_TYPE.equals(left) && STR_TYPE.equals(right)) {
            return STR_TYPE;
        }
        if (left.isListType() && right.isListType()) {
            return new ListValueType(classes.join(left.elementType(),
                                                  right.elementType()));
        }
        operatorError(expr, left, right);
        return INT_TYPE.equals(left) || INT_TYPE.equals(right) ? INT_TYPE
                                                               : OBJECT_TYPE;
    }

    /** Give EXPR the type RESULTTYPE, after reporting an error if the types
     *  LEFT and RIGHT of its operands are not VALID. */
    private Type checkOperands(BinaryExpr expr, boolean valid, Type left,
                               Type right, ValueType resultType) {
        if (!valid) {
            operatorError(expr, left, right);
        }
        return expr.setInferredType(resultType);
    }

    private void operatorError(BinaryExpr expr, Type left, Type right) {
        err(expr, "Cannot apply operator `%s` on types `%s` and `%s`",
            expr.operator, left, right);
    }

    /** [COND]: the type of a conditional expression is the join of its two
     *  branches. */
    @Override
    public Type analyze(IfExpr expr) {
        checkCondition(expr, expr.condition);
        Type thenType = expr.thenExpr.dispatch(this);
        Type elseType = expr.elseExpr.dispatch(this);
        return expr.setInferredType(classes.join((ValueType) thenType,
                                                 (ValueType) elseType));
    }

    /* ==================================================================
     * Lists.
     * ================================================================== */

    /** [NIL] and [LIST-DISPLAY]: [] has type <Empty>; otherwise the element
     *  type is the join of the types of all elements. */
    @Override
    public Type analyze(ListExpr expr) {
        ValueType elementType = null;
        for (Expr element : expr.elements) {
            ValueType type = (ValueType) element.dispatch(this);
            elementType = elementType == null ? type
                                              : classes.join(elementType, type);
        }
        if (elementType == null) {
            return expr.setInferredType(EMPTY_TYPE);
        }
        return expr.setInferredType(new ListValueType(elementType));
    }

    /** [LIST-SELECT] and [STR-SELECT]. */
    @Override
    public Type analyze(IndexExpr expr) {
        Type listType = expr.list.dispatch(this);
        Type indexType = expr.index.dispatch(this);
        if (listType.isListType()) {
            checkIndex(expr, indexType);
            return expr.setInferredType(listType.elementType());
        }
        if (STR_TYPE.equals(listType)) {
            checkIndex(expr, indexType);
            return expr.setInferredType(STR_TYPE);
        }
        err(expr, "Cannot index into type `%s`", listType);
        return expr.setInferredType(OBJECT_TYPE);
    }

    /** The index of EXPR, of type INDEXTYPE, must be an int. */
    private void checkIndex(IndexExpr expr, Type indexType) {
        if (!INT_TYPE.equals(indexType)) {
            err(expr, "Index is of non-integer type `%s`", indexType);
        }
    }

    /* ==================================================================
     * Objects: attributes and calls.
     * ================================================================== */

    /** [ATTR-READ]. */
    @Override
    public Type analyze(MemberExpr expr) {
        ClassInfo objectClass = checkMemberAccess(expr, expr.object);
        if (objectClass == null) {
            return expr.setInferredType(OBJECT_TYPE);
        }
        String name = expr.member.name;
        ValueType attrType = objectClass.getAttribute(name);
        if (attrType == null) {
            err(expr, "There is no attribute named `%s` in class `%s`", name,
                objectClass);
            return expr.setInferredType(OBJECT_TYPE);
        }
        return expr.setInferredType(attrType);
    }

    /** [INVOKE]: a method call; self is the object before the dot. */
    @Override
    public Type analyze(MethodCallExpr expr) {
        ClassInfo objectClass = checkMemberAccess(expr, expr.method.object);
        List<Type> argTypes = checkArguments(expr.args);
        if (objectClass == null) {
            return expr.setInferredType(OBJECT_TYPE);
        }
        String name = expr.method.member.name;
        FuncType method = objectClass.getMethod(name);
        if (method == null) {
            err(expr, "There is no method named `%s` in class `%s`", name,
                objectClass);
            return expr.setInferredType(OBJECT_TYPE);
        }
        expr.method.setInferredType(method);
        checkCall(expr, method, 1, argTypes);
        return expr.setInferredType(method.returnType);
    }

    /** [INVOKE] and [NEW]: a call of a function or a constructor. */
    @Override
    public Type analyze(CallExpr expr) {
        List<Type> argTypes = checkArguments(expr.args);
        Type callee = sym.get(expr.function.name);

        if (callee instanceof FuncType) {
            FuncType function = (FuncType) callee;
            expr.function.setInferredType(function);
            checkCall(expr, function, 0, argTypes);
            return expr.setInferredType(function.returnType);
        }
        if (callee instanceof ClassInfo) {
            ClassInfo classInfo = (ClassInfo) callee;
            checkCall(expr, classInfo.getMethod(INIT_METHOD), 1, argTypes);
            return expr.setInferredType(classInfo.getValueType());
        }
        err(expr, "Not a function or class: %s", expr.function.name);
        return expr.setInferredType(OBJECT_TYPE);
    }

    /** Type check OBJECT, the object of a member access EXPR, and return
     *  its class, or null (after reporting an error) if its type is not a
     *  class type. */
    private ClassInfo checkMemberAccess(Expr expr, Expr object) {
        Type objectType = object.dispatch(this);
        ClassInfo objectClass = classes.getClass(objectType);
        if (objectClass == null) {
            err(expr, "Cannot access member of non-class type `%s`",
                objectType);
        }
        return objectClass;
    }

    /** Type check the arguments ARGS of a call and return their types. */
    private List<Type> checkArguments(List<Expr> args) {
        List<Type> types = new ArrayList<>();
        for (Expr arg : args) {
            types.add(arg.dispatch(this));
        }
        return types;
    }

    /** Check that the arguments of type ARGTYPES of CALL match the
     *  parameters of FUNCTION, starting with its parameter FIRSTPARAM (1
     *  for methods and constructors, which receive self implicitly).  Only
     *  the first mismatch is reported; parameters are numbered from 0,
     *  including self. */
    private void checkCall(Expr call, FuncType function, int firstParam,
                           List<Type> argTypes) {
        int expected = function.parameters.size() - firstParam;
        if (argTypes.size() != expected) {
            err(call, "Expected %d arguments; got %d", expected,
                argTypes.size());
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            int param = firstParam + i;
            ValueType paramType = function.getParamType(param);
            if (!classes.isAssignable(paramType, argTypes.get(i))) {
                err(call, "Expected type `%s`; got type `%s` in parameter %d",
                    paramType, argTypes.get(i), param);
                return;
            }
        }
    }

    /* ==================================================================
     * Helpers.
     * ================================================================== */

    /** Type check STATEMENTS. */
    private void checkStatements(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            stmt.dispatch(this);
        }
    }

    /** The condition COND of NODE (an if, while or conditional expression)
     *  must be a bool. */
    private void checkCondition(Node node, Expr cond) {
        Type type = cond.dispatch(this);
        if (!BOOL_TYPE.equals(type)) {
            err(node, "Condition expression cannot be of type `%s`", type);
        }
    }

    /** Report a semantic error at NODE, with the message
     *  String.format(MESSAGE, ARGS).  Only the first error of a node is
     *  kept (see StudentAnalysis). */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }
}
