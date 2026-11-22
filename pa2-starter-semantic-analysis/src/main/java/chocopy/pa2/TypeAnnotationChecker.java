package chocopy.pa2;

import java.util.HashSet;
import java.util.Set;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.ClassType;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.ListType;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.TypedVar;
import chocopy.common.astnodes.VarDef;

import static chocopy.common.analysis.types.Type.NONE_TYPE;

/**
 * A pass that checks semantic rule 11: every class name used in a type
 * annotation (of a variable, attribute, parameter or return type) must be
 * a predefined class or a class defined somewhere in the program.  Forward
 * references to classes defined later are allowed.
 *
 * This pass is separate from {@link DeclarationAnalyzer} because, like in the
 * reference implementation, annotations are checked everywhere, even inside
 * declarations that the declaration analysis rejects as duplicates.
 */
public class TypeAnnotationChecker extends AbstractNodeAnalyzer<Void> {

    /** Receiver for semantic error messages. */
    private final Errors errors;
    /** Names of all classes that may appear in annotations. */
    private final Set<String> classNames = new HashSet<>();

    /** A checker sending its errors to ERRORS0. */
    public TypeAnnotationChecker(Errors errors0) {
        errors = errors0;
        for (String name : new String[] {"object", "int", "bool", "str"}) {
            classNames.add(name);
        }
        /* The implicit return type of a function without `->`. */
        classNames.add(NONE_TYPE.className());
    }

    @Override
    public Void analyze(Program program) {
        for (Declaration decl : program.declarations) {
            if (decl instanceof ClassDef) {
                classNames.add(decl.getIdentifier().name);
            }
        }
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        return null;
    }

    @Override
    public Void analyze(ClassDef classDef) {
        for (Declaration member : classDef.declarations) {
            member.dispatch(this);
        }
        return null;
    }

    @Override
    public Void analyze(FuncDef funcDef) {
        for (TypedVar param : funcDef.params) {
            param.type.dispatch(this);
        }
        funcDef.returnType.dispatch(this);
        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        return null;
    }

    @Override
    public Void analyze(VarDef varDef) {
        return varDef.var.type.dispatch(this);
    }

    @Override
    public Void analyze(ListType listType) {
        return listType.elementType.dispatch(this);
    }

    @Override
    public Void analyze(ClassType classType) {
        if (!classNames.contains(classType.className)) {
            errors.semError(classType, "Invalid type annotation; there is no "
                            + "class named: %s", classType.className);
        }
        return null;
    }
}
