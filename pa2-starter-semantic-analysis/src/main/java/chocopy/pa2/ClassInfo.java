package chocopy.pa2;

import java.util.LinkedHashMap;
import java.util.Map;

import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;

/**
 * Semantic information about a class: its super-class and the attributes and
 * methods it has, including the inherited ones.
 *
 * ClassInfo objects are stored in the global symbol table under the class
 * name, so that a lookup of a name can tell a class apart from a variable
 * (a {@link ValueType}) or a function (a {@link FuncType}).  They never
 * appear in the JSON output.
 */
public class ClassInfo extends Type {

    /** Name of the class. */
    private final String name;
    /** Super-class, or null for `object`. */
    private final ClassInfo superClass;
    /** Attribute types, inherited ones first, in declaration order. */
    private final Map<String, ValueType> attributes = new LinkedHashMap<>();
    /** Method signatures, inherited ones first, in declaration order.  The
     *  first parameter of each signature is the class that defines it. */
    private final Map<String, FuncType> methods = new LinkedHashMap<>();

    /** A class named NAME that extends SUPERCLASS (null for none) and
     *  inherits all its attributes and methods. */
    public ClassInfo(String name, ClassInfo superClass) {
        this.name = name;
        this.superClass = superClass;
        if (superClass != null) {
            attributes.putAll(superClass.attributes);
            methods.putAll(superClass.methods);
        }
    }

    public String getName() {
        return name;
    }

    public ClassInfo getSuperClass() {
        return superClass;
    }

    /** The value type of the instances of this class. */
    public ClassValueType getValueType() {
        return new ClassValueType(name);
    }

    /** Return the type of attribute NAME, or null if there is none. */
    public ValueType getAttribute(String attrName) {
        return attributes.get(attrName);
    }

    /** Return the signature of method NAME, or null if there is none. */
    public FuncType getMethod(String methodName) {
        return methods.get(methodName);
    }

    /** Return true iff this class has an attribute or a method NAME. */
    public boolean hasMember(String memberName) {
        return attributes.containsKey(memberName)
            || methods.containsKey(memberName);
    }

    /** Add (or override) the attribute NAME of type TYPE. */
    public void addAttribute(String attrName, ValueType type) {
        attributes.put(attrName, type);
    }

    /** Add (or override) the method NAME with signature TYPE. */
    public void addMethod(String methodName, FuncType type) {
        methods.put(methodName, type);
    }

    /** Return true iff this class is OTHER or a (transitive) subclass of
     *  OTHER. */
    public boolean isSubclassOf(ClassInfo other) {
        for (ClassInfo c = this; c != null; c = c.superClass) {
            if (c == other) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String className() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
