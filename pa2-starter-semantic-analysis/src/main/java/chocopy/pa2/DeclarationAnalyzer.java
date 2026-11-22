package chocopy.pa2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.ClassType;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.GlobalDecl;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.NonLocalDecl;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.TypeAnnotation;
import chocopy.common.astnodes.TypedVar;
import chocopy.common.astnodes.VarDef;

import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.NONE_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.STR_TYPE;

/**
 * First pass of the semantic analysis: analyzes all declarations.
 *
 * It builds
 *   - the global symbol table (variables, functions and classes),
 *   - the class hierarchy with the attributes and methods of every class,
 *   - one symbol table for the body of every function and method,
 * and checks the semantic rules 1-9 of the PA2 specification (duplicate and
 * shadowing names, global/nonlocal declarations, super-classes, attribute and
 * method overriding, method self parameters and missing returns).  Rule 11
 * (type annotations) is checked by {@link TypeAnnotationChecker}.
 *
 * The dispatch methods for declarations return the Type that the declared
 * name is bound to (a ValueType, FuncType or ClassInfo), or null if the
 * declaration is invalid and must not be entered into the symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {

    /** Receiver for semantic error messages. */
    private final Errors errors;
    /** Global symbol table. */
    private final SymbolTable<Type> globals = new SymbolTable<>();
    /** Symbol table of the declarative region being analyzed. */
    private SymbolTable<Type> sym = globals;
    /** All classes of the program. */
    private final ClassHierarchy classes = new ClassHierarchy();
    /** Names of all classes, including those defined later in the program
     *  (the shadowing rule also applies to them). */
    private final Set<String> classNames = new HashSet<>();
    /** The symbol table of the body of each function and method. */
    private final Map<FuncDef, SymbolTable<Type>> functionScopes =
        new IdentityHashMap<>();
    /** For each function scope, the names declared there with `global`. */
    private final Map<SymbolTable<Type>, Set<String>> globalNames =
        new IdentityHashMap<>();
    /** Methods that re-declare a name of their class.  Like the reference
     *  implementation, we do not analyze their bodies. */
    private final Set<FuncDef> duplicateMethods =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
    }

    public SymbolTable<Type> getGlobals() {
        return globals;
    }

    public ClassHierarchy getClassHierarchy() {
        return classes;
    }

    public Map<FuncDef, SymbolTable<Type>> getFunctionScopes() {
        return functionScopes;
    }

    /* ==================================================================
     * Program: global declarations, then all function and method bodies.
     * ================================================================== */

    @Override
    public Type analyze(Program program) {
        declarePredefinedNames();
        for (Declaration decl : program.declarations) {
            if (decl instanceof ClassDef) {
                classNames.add(decl.getIdentifier().name);
            }
        }

        /* Step 1: bind every global name, in program order.  Classes are
         * processed here as a whole, so that a super-class must have been
         * defined earlier in the program. */
        for (Declaration decl : program.declarations) {
            Identifier id = decl.getIdentifier();
            Type type = decl.dispatch(this);
            if (type == null) {
                continue;
            }
            if (globals.declares(id.name)) {
                duplicateError(id);
            } else {
                globals.put(id.name, type);
                if (type instanceof ClassInfo) {
                    classes.add((ClassInfo) type);
                }
            }
        }

        /* Step 2: the bodies of functions and methods.  Every global name is
         * known at this point, so bodies may refer to later declarations. */
        for (Declaration decl : program.declarations) {
            if (decl instanceof FuncDef) {
                analyzeBody((FuncDef) decl, globals);
            } else if (decl instanceof ClassDef) {
                for (Declaration member : ((ClassDef) decl).declarations) {
                    if (!(member instanceof FuncDef)) {
                        continue;
                    }
                    FuncDef method = (FuncDef) member;
                    if (duplicateMethods.contains(method)) {
                        checkNestedReturnPaths(method);
                    } else {
                        analyzeBody(method, globals);
                    }
                }
            }
        }
        return null;
    }

    /** Enter the predefined classes and functions into the global table. */
    private void declarePredefinedNames() {
        for (String name : new String[] {"object", "int", "bool", "str"}) {
            globals.put(name, classes.getClass(name));
            classNames.add(name);
        }
        globals.put("print", new FuncType(listOf(OBJECT_TYPE), NONE_TYPE));
        globals.put("input", new FuncType(new ArrayList<>(), STR_TYPE));
        globals.put("len", new FuncType(listOf(OBJECT_TYPE), INT_TYPE));
    }

    /* ==================================================================
     * Declarations.
     * ================================================================== */

    @Override
    public Type analyze(VarDef varDef) {
        return ValueType.annotationToValueType(varDef.var.type);
    }

    /** Return the signature of a function or method.  Like the reference
     *  implementation, it leaves out parameters that are rejected because
     *  they shadow a class name or repeat an earlier parameter (this only
     *  matters for the error messages of method overriding). */
    @Override
    public Type analyze(FuncDef funcDef) {
        List<ValueType> paramTypes = new ArrayList<>();
        Set<String> paramNames = new HashSet<>();
        for (TypedVar param : funcDef.params) {
            String name = param.identifier.name;
            if (!classNames.contains(name) && paramNames.add(name)) {
                paramTypes.add(ValueType.annotationToValueType(param.type));
            }
        }
        return new FuncType(paramTypes,
            ValueType.annotationToValueType(funcDef.returnType));
    }

    /** A `global x` must name a variable of the global scope (rule 3). */
    @Override
    public Type analyze(GlobalDecl decl) {
        String name = decl.variable.name;
        Type type = globals.declares(name) ? globals.get(name) : null;
        if (type == null || !type.isValueType()) {
            err(decl.variable, "Not a global variable: %s", name);
            return null;
        }
        return type;
    }

    /** A `nonlocal x` must name a variable of a function (rule 3).  As in
     *  the reference implementation, the nearest declaration of x, starting
     *  with the current scope, decides: it must be a variable that is
     *  neither global nor declared `global`.  If x is declared in the
     *  current scope itself, the caller then reports a duplicate. */
    @Override
    public Type analyze(NonLocalDecl decl) {
        String name = decl.variable.name;
        for (SymbolTable<Type> scope = sym; scope != globals;
             scope = scope.getParent()) {
            if (scope.declares(name)) {
                Type type = scope.get(name);
                if (type.isValueType()
                        && !globalNames.get(scope).contains(name)) {
                    return type;
                }
                break;
            }
        }
        err(decl.variable, "Not a nonlocal variable: %s", name);
        return null;
    }

    /** Check a class definition (rules 1, 4-7) and return its ClassInfo.
     *
     *  As in the reference implementation, only members that pass all
     *  checks are added to the class, and a member is a duplicate only if an
     *  earlier member of the same name was added.  The bodies of duplicate
     *  methods are not analyzed. */
    @Override
    public Type analyze(ClassDef classDef) {
        String className = classDef.name.name;
        ClassInfo superClass = resolveSuperClass(classDef.superClass);
        ClassInfo info = new ClassInfo(className, superClass);
        Set<String> addedMembers = new HashSet<>();

        for (Declaration member : classDef.declarations) {
            Identifier id = member.getIdentifier();
            if (addedMembers.contains(id.name)) {
                duplicateError(id);
                if (member instanceof FuncDef) {
                    duplicateMethods.add((FuncDef) member);
                }
            } else if (member instanceof VarDef
                       && isValidAttribute(info, id)) {
                info.addAttribute(id.name, (ValueType) member.dispatch(this));
                addedMembers.add(id.name);
            } else if (member instanceof FuncDef
                       && isValidMethod(info, (FuncDef) member)) {
                info.addMethod(id.name, (FuncType) member.dispatch(this));
                addedMembers.add(id.name);
            }
        }
        return info;
    }

    /** Return the class named by the super-class identifier ID.  It must be
     *  a class defined earlier in the program that is not int, bool or str
     *  (rule 4); on error, recover with object. */
    private ClassInfo resolveSuperClass(Identifier id) {
        Type type = globals.get(id.name);
        if (type == null) {
            err(id, "Super-class not defined: %s", id.name);
        } else if (!(type instanceof ClassInfo)) {
            err(id, "Super-class must be a class: %s", id.name);
        } else if (ClassHierarchy.isSpecialClass(id.name)) {
            err(id, "Cannot extend special class: %s", id.name);
        } else {
            return (ClassInfo) type;
        }
        return classes.getObjectClass();
    }

    /** An attribute ID of class INFO may not re-define an inherited
     *  attribute or method (rule 5).  Return true iff ID is valid. */
    private boolean isValidAttribute(ClassInfo info, Identifier id) {
        if (info.getSuperClass().hasMember(id.name)) {
            err(id, "Cannot re-define attribute: %s", id.name);
            return false;
        }
        return true;
    }

    /** METHOD of class INFO may not re-define an inherited attribute
     *  (rule 5), it may override an inherited method only with the same
     *  signature apart from the first parameter (rule 7), and its first
     *  parameter must have the type of INFO (rule 6).  The checks are made
     *  in the order of the reference implementation.  Return true iff METHOD
     *  is valid. */
    private boolean isValidMethod(ClassInfo info, FuncDef method) {
        Identifier id = method.name;
        ClassInfo superClass = info.getSuperClass();
        FuncType inherited = superClass.getMethod(id.name);
        FuncType signature = (FuncType) method.dispatch(this);

        if (superClass.getAttribute(id.name) != null) {
            err(id, "Cannot re-define attribute: %s", id.name);
        } else if (inherited != null && !sameSignature(inherited, signature)) {
            err(id, "Method overridden with different type signature: %s",
                id.name);
        } else if (!hasSelfParameter(method, info)) {
            err(id, "First parameter of the following method must be of "
                + "the enclosing class: %s", id.name);
        } else {
            return true;
        }
        return false;
    }

    /** Return true iff the first parameter of METHOD has the type of the
     *  enclosing class INFO. */
    private static boolean hasSelfParameter(FuncDef method, ClassInfo info) {
        if (method.params.isEmpty()) {
            return false;
        }
        TypeAnnotation selfType = method.params.get(0).type;
        return selfType instanceof ClassType
            && ((ClassType) selfType).className.equals(info.getName());
    }

    /** Return true iff the method signatures F1 and F2 have the same
     *  return type, the same number of parameters and the same parameter
     *  types, ignoring the first parameter (self). */
    private static boolean sameSignature(FuncType f1, FuncType f2) {
        if (f1.parameters.size() != f2.parameters.size()
                || !f1.returnType.equals(f2.returnType)) {
            return false;
        }
        for (int i = 1; i < f1.parameters.size(); i++) {
            if (!f1.getParamType(i).equals(f2.getParamType(i))) {
                return false;
            }
        }
        return true;
    }

    /* ==================================================================
     * Function and method bodies.
     * ================================================================== */

    /** Build the symbol table of the body of FUNC, a function or method
     *  nested in the region PARENT, and analyze nested functions. */
    private void analyzeBody(FuncDef func, SymbolTable<Type> parent) {
        SymbolTable<Type> local = new SymbolTable<>(parent);
        functionScopes.put(func, local);
        globalNames.put(local, new HashSet<>());
        SymbolTable<Type> enclosing = sym;
        sym = local;

        for (TypedVar param : func.params) {
            declareLocal(param.identifier,
                         ValueType.annotationToValueType(param.type));
        }
        for (Declaration decl : func.declarations) {
            Identifier id = decl.getIdentifier();
            Type type = decl.dispatch(this);
            if (decl instanceof GlobalDecl || decl instanceof NonLocalDecl) {
                /* Refers to an outer variable, so it may reuse a class
                 * name; null if the outer variable does not exist. */
                if (type != null && declareInScope(id, type)
                        && decl instanceof GlobalDecl) {
                    globalNames.get(sym).add(id.name);
                }
            } else {
                declareLocal(id, type);
            }
        }
        for (Declaration decl : func.declarations) {
            if (decl instanceof FuncDef) {
                analyzeBody((FuncDef) decl, local);
            }
        }

        sym = enclosing;
        checkReturnPaths(func);
    }

    /** Bind ID, a parameter, local variable or nested function, to TYPE in
     *  the current function scope.  The name may not shadow a class name
     *  (rule 2) and may not be declared twice in the same scope (rule 1). */
    private void declareLocal(Identifier id, Type type) {
        if (classNames.contains(id.name)) {
            err(id, "Cannot shadow class name: %s", id.name);
        } else {
            declareInScope(id, type);
        }
    }

    /** Bind ID to TYPE in the current scope, unless ID is already declared
     *  there (rule 1).  Return true iff ID was bound. */
    private boolean declareInScope(Identifier id, Type type) {
        if (sym.declares(id.name)) {
            duplicateError(id);
            return false;
        }
        sym.put(id.name, type);
        return true;
    }

    /** The reference implementation checks rule 9 for the functions nested
     *  in a duplicate method FUNC (and nothing else in its body). */
    private void checkNestedReturnPaths(FuncDef func) {
        for (Declaration decl : func.declarations) {
            if (decl instanceof FuncDef) {
                checkNestedReturnPaths((FuncDef) decl);
                checkReturnPaths((FuncDef) decl);
            }
        }
    }

    /** A function whose return type is int, bool or str must return a
     *  value on every path through its body (rule 9). */
    private void checkReturnPaths(FuncDef func) {
        ValueType returnType = ValueType.annotationToValueType(func.returnType);
        if (returnType.isSpecialType()
                && !ReturnPaths.alwaysReturns(func.statements)) {
            err(func.name, "All paths in this function/method must have a "
                + "return statement: %s", func.name.name);
        }
    }

    /* ==================================================================
     * Helpers.
     * ================================================================== */

    /** Report that ID is declared twice in the same scope (rule 1). */
    private void duplicateError(Identifier id) {
        err(id, "Duplicate declaration of identifier in same scope: %s",
            id.name);
    }

    /** Report a semantic error at NODE, with the message
     *  String.format(MESSAGE, ARGS). */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    /** A modifiable list containing only TYPE. */
    private static List<ValueType> listOf(ValueType type) {
        List<ValueType> result = new ArrayList<>();
        result.add(type);
        return result;
    }
}
