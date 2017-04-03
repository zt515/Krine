package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

class KrineSwitchStatement
        extends SimpleNode
        implements ParserConstants {

    public KrineSwitchStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        int numChildren = jjtGetNumChildren();
        int child = 0;
        SimpleNode switchExp = ((SimpleNode) jjtGetChild(child++));
        Object switchVal = switchExp.eval(callStack, krineBasicInterpreter);

		/*
            Note: this could be made clearer by adding an inner class for the
			cases and an object context for the child traversal.
		*/
        // first label
        KrineSwitchLabel label;
        Object node;
        ReturnControl returnControl = null;

        // get the first label
        if (child >= numChildren)
            throw new EvalError("Empty switch statement.", this, callStack);
        label = ((KrineSwitchLabel) jjtGetChild(child++));

        // while more labels or blocks and haven't hit return control
        while (child < numChildren && returnControl == null) {
            // if label is default or equals switchVal
            if (label.isDefault
                    || primitiveEquals(
                    switchVal, label.eval(callStack, krineBasicInterpreter),
                    callStack, switchExp)
                    ) {
                // execute nodes, skipping labels, until a break or return
                while (child < numChildren) {
                    node = jjtGetChild(child++);
                    if (node instanceof KrineSwitchLabel)
                        continue;
                    // eval it
                    Object value =
                            ((SimpleNode) node).eval(callStack, krineBasicInterpreter);

                    // should check to disallow continue here?
                    if (value instanceof ReturnControl) {
                        returnControl = (ReturnControl) value;
                        break;
                    }
                }
            } else {
                // skip nodes until next label
                while (child < numChildren) {
                    node = jjtGetChild(child++);
                    if (node instanceof KrineSwitchLabel) {
                        label = (KrineSwitchLabel) node;
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
            CallStack callStack, SimpleNode switchExp)
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
                        this, callStack);
            }
        else
            return switchVal.equals(targetVal);
    }
}

