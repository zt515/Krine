package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.UtilEvalException;

class DragonSwitchStatement
        extends SimpleNode
        implements ParserConstants {

    public DragonSwitchStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        int numchild = jjtGetNumChildren();
        int child = 0;
        SimpleNode switchExp = ((SimpleNode) jjtGetChild(child++));
        Object switchVal = switchExp.eval(callstack, dragonBasicInterpreter);

		/*
            Note: this could be made clearer by adding an inner class for the
			cases and an object context for the child traversal.
		*/
        // first label
        DragonSwitchLabel label;
        Object node;
        ReturnControl returnControl = null;

        // get the first label
        if (child >= numchild)
            throw new EvalError("Empty switch statement.", this, callstack);
        label = ((DragonSwitchLabel) jjtGetChild(child++));

        // while more labels or blocks and haven't hit return control
        while (child < numchild && returnControl == null) {
            // if label is default or equals switchVal
            if (label.isDefault
                    || primitiveEquals(
                    switchVal, label.eval(callstack, dragonBasicInterpreter),
                    callstack, switchExp)
                    ) {
                // execute nodes, skipping labels, until a break or return
                while (child < numchild) {
                    node = jjtGetChild(child++);
                    if (node instanceof DragonSwitchLabel)
                        continue;
                    // eval it
                    Object value =
                            ((SimpleNode) node).eval(callstack, dragonBasicInterpreter);

                    // should check to disallow continue here?
                    if (value instanceof ReturnControl) {
                        returnControl = (ReturnControl) value;
                        break;
                    }
                }
            } else {
                // skip nodes until next label
                while (child < numchild) {
                    node = jjtGetChild(child++);
                    if (node instanceof DragonSwitchLabel) {
                        label = (DragonSwitchLabel) node;
                        break;
                    }
                }
            }
        }

        if (returnControl != null && returnControl.kind == RETURN)
            return returnControl;
        else
            return Primitive.VOID;
    }

    /**
     * Helper method for testing equals on two primitive or boxable objects.
     * yuck: factor this out into Primitive.java
     */
    private boolean primitiveEquals(
            Object switchVal, Object targetVal,
            CallStack callstack, SimpleNode switchExp)
            throws EvalError {
        if (switchVal instanceof Primitive || targetVal instanceof Primitive)
            try {
                // binaryOperation can return Primitive or wrapper type
                Object result = Primitive.binaryOperation(
                        switchVal, targetVal, ParserConstants.EQ);
                result = Primitive.unwrap(result);
                return result.equals(Boolean.TRUE);
            } catch (UtilEvalException e) {
                throw e.toEvalError(
                        "Switch value: " + switchExp.getText() + ": ",
                        this, callstack);
            }
        else
            return switchVal.equals(targetVal);
    }
}

