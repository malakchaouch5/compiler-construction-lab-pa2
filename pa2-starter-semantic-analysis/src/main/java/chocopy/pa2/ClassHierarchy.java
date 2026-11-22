package chocopy.pa2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;

import static chocopy.common.analysis.types.Type.BOOL_TYPE;
import static chocopy.common.analysis.types.Type.EMPTY_TYPE;
import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.NONE_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.STR_TYPE;

/**
 * The inheritance tree of all classes of a program, predefined and
 * user-defined, together with the type relations of the ChocoPy reference
 * (Section 2.4 of the manual):
 *
 *   - {@link #isSubtype}:    the subtype relation  T1 <= T2,
 *   - {@link #isAssignable}: assignment compatibility  T1 <=a T2,
 *   - {@link #join}:         the least upper bound of two types.
 *
 * The tree is rooted at `object`.  As in the reference implementation, the
 * internal types `<None>` and `<Empty>` are treated as classes directly
 * below `object` that only inherit its `__init__` method.
 */
public class ClassHierarchy {

    /** All classes by name. */
    private final Map<String, ClassInfo> classes = new HashMap<>();

    /** The root class `object`. */
    private final ClassInfo objectClass;

    /** A hierarchy that contains only the predefined classes. */
    public ClassHierarchy() {
        objectClass = new ClassInfo(OBJECT_TYPE.className(), null);
        objectClass.addMethod("__init__", new FuncType(
            listOf(OBJECT_TYPE), NONE_TYPE));
        classes.put(objectClass.getName(), objectClass);

        for (ValueType special : new ValueType[] {INT_TYPE, BOOL_TYPE,
                                                  STR_TYPE}) {
            add(new ClassInfo(special.className(), objectClass));
        }
        /* Internal types; they are not visible as names in programs. */
        add(new ClassInfo(NONE_TYPE.className(), objectClass));
        add(new ClassInfo(EMPTY_TYPE.className(), objectClass));
    }

    /** Return the root class `object`. */
    public ClassInfo getObjectClass() {
        return objectClass;
    }

    /** Register the class INFO. */
    public void add(ClassInfo info) {
        classes.put(info.getName(), info);
    }

    /** Return the class named NAME, or null if there is none. */
    public ClassInfo getClass(String name) {
        return classes.get(name);
    }

    /** Return the class whose instances have type TYPE, or null if TYPE is
     *  not a class type (e.g. a list type). */
    public ClassInfo getClass(Type type) {
        if (type == null || type.isListType() || type.className() == null) {
            return null;
        }
        return classes.get(type.className());
    }

    /** Return true iff NAME is one of the classes int, bool and str, which
     *  can neither be extended nor hold the value None. */
    public static boolean isSpecialClass(String name) {
        return INT_TYPE.className().equals(name)
            || BOOL_TYPE.className().equals(name)
            || STR_TYPE.className().equals(name);
    }

    /* ------------------------------------------------------------------
     * Type relations.
     * ------------------------------------------------------------------ */

    /** Return true iff SUB <= SUPER (the subtype relation).  Every type is a
     *  subtype of object, and a class type is a subtype of its ancestors.
     *  List types are only subtypes of themselves and of object. */
    public boolean isSubtype(Type sub, Type sup) {
        if (sub == null || sup == null) {
            return false;
        }
        if (sub.equals(sup) || OBJECT_TYPE.equals(sup)) {
            return true;
        }
        ClassInfo subClass = getClass(sub);
        ClassInfo superClass = getClass(sup);
        return subClass != null && superClass != null
            && subClass.isSubclassOf(superClass);
    }

    /** Return true iff a value of type SOURCE may be assigned to a location
     *  of type TARGET, i.e. SOURCE <=a TARGET:
     *    1. SOURCE <= TARGET;
     *    2. SOURCE is <None> and TARGET is not int, bool or str;
     *    3. SOURCE is <Empty> and TARGET is a list type;
     *    4. SOURCE is [<None>] and TARGET is a list type [T] with
     *       <None> <=a T. */
    public boolean isAssignable(Type target, Type source) {
        if (isSubtype(source, target)) {
            return true;
        }
        if (NONE_TYPE.equals(source)) {
            return target != null && !target.isSpecialType();
        }
        if (EMPTY_TYPE.equals(source)) {
            return target != null && target.isListType();
        }
        if (source != null && source.isListType() && target != null
                && target.isListType()
                && NONE_TYPE.equals(source.elementType())) {
            return isAssignable(target.elementType(), NONE_TYPE);
        }
        return false;
    }

    /** Return the least upper bound of T1 and T2 with respect to <=a: the
     *  other type if one is assignable to the other, otherwise the closest
     *  common ancestor of two class types, otherwise object. */
    public ValueType join(ValueType t1, ValueType t2) {
        if (isAssignable(t2, t1)) {
            return t2;
        }
        if (isAssignable(t1, t2)) {
            return t1;
        }
        ClassInfo c1 = getClass(t1);
        ClassInfo c2 = getClass(t2);
        if (c1 == null || c2 == null) {
            return OBJECT_TYPE;
        }
        for (ClassInfo ancestor = c1; ancestor != null;
             ancestor = ancestor.getSuperClass()) {
            if (c2.isSubclassOf(ancestor)) {
                return ancestor.getValueType();
            }
        }
        return OBJECT_TYPE;
    }

    /** A modifiable list containing only TYPE. */
    private static List<ValueType> listOf(ValueType type) {
        List<ValueType> result = new ArrayList<>();
        result.add(type);
        return result;
    }
}
