package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.UtilEvalException;

import java.util.*;


/**
 * A nameSpace which maintains an external map of values held in variables in
 * its scope.  This mechanism provides a standard collections based interface
 * to the nameSpace as well as a convenient way to export and view values of
 * the nameSpace without the ordinary Krine wrappers.
 * </p>
 * <p>
 * Variables are maintained internally in the normal fashion to support
 * meta-information (such as variable type and visibility modifiers), but
 * exported and imported in a synchronized way.  Variables are exported each
 * time they are written by Krine.  Imported variables from the map appear
 * in the Krine nameSpace as untyped variables with no modifiers and
 * shadow any previously defined variables in the scope.
 * <p/>
 * <p>
 * Note: this class is inherently dependent on Java 1.2, however it is not
 * used directly by the lang as other than type NameSpace, so no dependency is
 * introduced.
 */
/*
    Implementation notes:  krine methods are not currently exported to the
	external nameSpace.  All that would be required to add this is to override
	setMethod() and provide a friendlier view than vector (currently used) for
	overloaded forms (perhaps a map by method SignatureKey).
*/
public class ExternalNameSpace extends NameSpace {
    private Map<String, Object> externalMap;

    public ExternalNameSpace() {
        this(null, "External Map Namespace", null);
    }

    /**
     */
    public ExternalNameSpace(NameSpace parent, String name, Map<String, Object> externalMap) {
        super(parent, name);

        if (externalMap == null)
            externalMap = new HashMap<>();

        this.externalMap = externalMap;

    }

    /**
     * Get the map view of this nameSpace.
     */
    public Map<String, Object> getMap() {
        return externalMap;
    }

    /**
     * Set the external Map which to which this nameSpace synchronizes.
     * The previous external map is detached from this nameSpace.  Previous
     * map values are retained in the external map, but are removed from the
     * Krine nameSpace.
     */
    public void setMap(Map<String, Object> map) {
        // Detach any existing nameSpace to preserve it, then clear this
        // nameSpace and set the new one
        this.externalMap = null;
        clear();
        this.externalMap = map;
    }

    /**
     */
    void setVariable(
            String name, Object value, boolean strictJava, boolean recurse)
            throws UtilEvalException {
        super.setVariable(name, value, strictJava, recurse);
        putExternalMap(name, value);
    }

    /**
     */
    public void unsetVariable(String name) {
        super.unsetVariable(name);
        externalMap.remove(name);
    }

    /**
     */
    public String[] getVariableNames() {
        // union of the names in the internal nameSpace and external map
        Set<String> nameSet = new HashSet<>();
        String[] nsNames = super.getVariableNames();
        nameSet.addAll(Arrays.asList(nsNames));
        nameSet.addAll(externalMap.keySet());
        return nameSet.toArray(new String[0]);
    }

    /**
     */
    /*
        Notes: This implementation of getVariableImpl handles the following
		cases:
		1) var in map not in local scope - var was added through map
		2) var in map and in local scope - var was added through nameSpace
		3) var not in map but in local scope - var was removed via map
		4) var not in map and not in local scope - non-existent var
	*/
    protected Variable getVariableImpl(String name, boolean recurse)
            throws UtilEvalException {
        // check the external map for the variable name
        Object value = externalMap.get(name);

        if (value == null && externalMap.containsKey(name))
            value = Primitive.NULL;

        Variable var;
        if (value == null) {
            // The var is not in external map and it should therefore not be
            // found in local scope (it may have been removed via the map).
            // Clear it prophylactically.
            super.unsetVariable(name);

            // Search parent for var if applicable.
            var = super.getVariableImpl(name, recurse);
        } else {
            // Var in external map may be found in local scope with type and
            // modifier info.
            Variable localVar = super.getVariableImpl(name, false);

            // If not in local scope then it was added via the external map,
            // we'll wrap it and pass it along.  Else we'll use the local
            // version.
            if (localVar == null)
                var = new Variable(name, (Class) null, value, null);
            else
                var = localVar;
        }

        return var;
    }

    /**
     */
	/*
		Note: the meaning of getDeclaredVariables() is not entirely clear, but
		the name (and current usage in class generation support) suggests that
		untyped variables should not be included.  Therefore we do not
		currently have to add the external names here.
	*/
    public Variable[] getDeclaredVariables() {
        return super.getDeclaredVariables();
    }

    /**
     */
    public void setTypedVariable(
            String name, Class type, Object value, Modifiers modifiers)
            throws UtilEvalException {
        super.setTypedVariable(name, type, value, modifiers);
        putExternalMap(name, value);
    }

    /*
        Note: we could override this method to allow krine methods to appear in
        the external map.
    */
    public void setMethod(KrineMethod method)
            throws UtilEvalException {
        super.setMethod(method);
    }

    /*
        Note: kind of far-fetched, but... we could override this method to
        allow krine methods to be inserted into this nameSpace via the map.
    */
    public KrineMethod getMethod(
            String name, Class[] sig, boolean declaredOnly)
            throws UtilEvalException {
        return super.getMethod(name, sig, declaredOnly);
    }


    /*
        Note: this method should be overridden to add the names from the
        external map, as is done in getVariableNames();
    */
    protected void getAllNamesAux(List<String> list) {
        super.getAllNamesAux(list);
    }

    /**
     * Clear all variables, methods, and imports from this nameSpace and clear
     * all values from the external map (via Map clear()).
     */
    public void clear() {
        super.clear();
        externalMap.clear();
    }

    /**
     * Place an unwrapped value in the external map.
     * Krine primitive types are represented by their object wrappers, so
     * it is not possible to differentiate between wrapper types and primitive
     * types via the external Map.
     */
    protected void putExternalMap(String name, Object value) {
        if (value instanceof Variable)
            try {
                value = unwrapVariable((Variable) value);
            } catch (UtilEvalException ute) {
                // There should be no case for this.  unwrapVariable throws
                // UtilEvalException in some cases where it holds an LeftValue or array
                // arrayIndex.
                throw new InterpreterException("unexpected UtilEvalException");
            }

        if (value instanceof Primitive)
            value = Primitive.unwrap(value);

        externalMap.put(name, value);
    }
}

