package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;

/**
 * A specialized nameSpace	for Blocks (e.g. the body of a "for" statement).
 * The Block acts like a child nameSpace but only for typed variables
 * declared within it (block local scope) or untyped variables explicitly set
 * in it via setBlockVariable().  Otherwise variable assignment
 * (including untyped variable usage) acts like it is part of the containing
 * block.
 * <p>
 */
/*
    Note: This class essentially just delegates most of its methods to its
	parent.  The setVariable() indirection is very small.  We could probably
	fold this functionality back into the base NameSpace as a special case.
	But this has changed a few times so I'd like to leave this abstraction for
	now.
*/
class BlockNameSpace extends NameSpace {
    BlockNameSpace(NameSpace parent)
            throws EvalError {
        super(parent, parent.getName() + "/BlockNameSpace");
    }

    /**
     * Override the standard nameSpace behavior to make assignments
     * happen in our parent (enclosing) nameSpace, unless the variable has
     * already been assigned here via a typed declaration or through
     * the special setBlockVariable() (used for untyped args in try/catch).
     * <p>
     * i.e. only allow typed var declaration to happen in this nameSpace.
     * Typed vars are handled in the ordinary way local scope.  All untyped
     * assignments are delegated to the enclosing context.
     */
    /*
        Note: it may see like with the new 1.3 scoping this test could be
		removed, but it cannot.  When recurse is false we still need to set the
		variable in our parent, not here.
	*/
    public void setVariable(
            String name, Object value, boolean strictJava, boolean recurse)
            throws UtilEvalException {
        if (weHaveVar(name))
            // set the var here in the block nameSpace
            super.setVariable(name, value, strictJava, false);
        else
            // set the var in the enclosing (parent) nameSpace
            getParent().setVariable(name, value, strictJava, recurse);
    }

    /**
     * Set an untyped variable in the block nameSpace.
     * The BlockNameSpace would normally delegate this set to the parent.
     * Typed variables are naturally set locally.
     * This is used in try/catch block argument.
     */
    public void setBlockVariable(String name, Object value)
            throws UtilEvalException {
        super.setVariable(name, value, false/*strict?*/, false);
    }

    /**
     * We have the variable: either it was declared here with a type, giving
     * it block local scope or an untyped var was explicitly set here via
     * setBlockVariable().
     */
    private boolean weHaveVar(String name) {
        // super.variables.containsKey( name ) not any faster, I checked
        try {
            return super.getVariableImpl(name, false) != null;
        } catch (UtilEvalException e) {
            return false;
        }
    }

/**
 Get the actual BlockNameSpace 'this' reference.
 <p/>
 Normally a 'this' reference to a BlockNameSpace (e.g. if () { } )
 resolves to the parent nameSpace (e.g. the nameSpace containing the
 "if" statement).  However when code inside the BlockNameSpace needs to
 resolve things relative to 'this' we must use the actual block's 'this'
 reference.  Name.java is smart enough to handle this using
 getBlockThis().
 @see #getThis(KrineBasicInterpreter)
 This getBlockThis( KrineInterpreter declaringKrineBasicInterpreter )
 {
 return super.getThis( declaringInterpreter );
 }
 */

    //
    // Begin methods which simply delegate to our parent (enclosing scope)
    //

    /**
     This method recurse to find the nearest non-BlockNameSpace parent.

     public NameSpace getParent()
     {
     NameSpace parent = super.getParent();
     if ( parent instanceof BlockNameSpace )
     return parent.getParent();
     else
     return parent;
     }
     */
    /**
     * do we need this?
     */
    private NameSpace getNonBlockParent() {
        NameSpace parent = super.getParent();
        if (parent instanceof BlockNameSpace)
            return ((BlockNameSpace) parent).getNonBlockParent();
        else
            return parent;
    }

    /**
     * Get a 'this' reference is our parent's 'this' for the object closure.
     * e.g. Normally a 'this' reference to a BlockNameSpace (e.g. if () { } )
     * resolves to the parent nameSpace (e.g. the nameSpace containing the
     * "if" statement).
     */
    public This getThis(KrineBasicInterpreter declaringKrineBasicInterpreter) {
        return getNonBlockParent().getThis(declaringKrineBasicInterpreter);
    }

    /**
     * super is our parent's super
     */
    public This getSuper(KrineBasicInterpreter declaringKrineBasicInterpreter) {
        return getNonBlockParent().getSuper(declaringKrineBasicInterpreter);
    }

    /**
     * delegate import to our parent
     */
    public void importClass(String name) {
        getParent().importClass(name);
    }

    /**
     * delegate import to our parent
     */
    public void importPackage(String name) {
        getParent().importPackage(name);
    }

    public void setMethod(KrineMethod method)
            throws UtilEvalException {
        getParent().setMethod(method);
    }
}

