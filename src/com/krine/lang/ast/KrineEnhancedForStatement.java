package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;
import com.krine.lang.utils.CollectionManager;

import java.util.Iterator;

/**
 * Implementation of the enhanced for(:) statement.
 * This statement uses Iterator to support iteration over a wide variety
 * of iterable types.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
class KrineEnhancedForStatement extends SimpleNode implements ParserConstants {
    String varName;

    KrineEnhancedForStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Class elementType = null;
        SimpleNode expression, statement = null;

        NameSpace enclosingNameSpace = callStack.top();
        SimpleNode firstNode = ((SimpleNode) jjtGetChild(0));
        int nodeCount = jjtGetNumChildren();

        if (firstNode instanceof KrineType) {
            elementType = ((KrineType) firstNode).getType(callStack, krineBasicInterpreter);
            expression = ((SimpleNode) jjtGetChild(1));
            if (nodeCount > 2)
                statement = ((SimpleNode) jjtGetChild(2));
        } else {
            expression = firstNode;
            if (nodeCount > 1)
                statement = ((SimpleNode) jjtGetChild(1));
        }

        BlockNameSpace eachNameSpace = new BlockNameSpace(enclosingNameSpace);
        callStack.swap(eachNameSpace);

        final Object iteratee = expression.eval(callStack, krineBasicInterpreter);

        if (iteratee == Primitive.NULL)
            throw new EvalError("The collection, array, map, iterator, or " +
                    "enumeration portion of a for statement cannot be null.",
                    this, callStack);

        CollectionManager cm = CollectionManager.getCollectionManager();
        if (!cm.isKrineIterable(iteratee))
            throw new EvalError("Can't iterate over type: "
                    + iteratee.getClass(), this, callStack);
        Iterator iterator = cm.getKrineIterator(iteratee);

        Object returnControl = Primitive.VOID;
        while (iterator.hasNext()) {
            try {
                Object value = iterator.next();
                if (value == null)
                    value = Primitive.NULL;
                if (elementType != null)
                    eachNameSpace.setTypedVariable(
                            varName/*name*/, elementType/*type*/,
                            value, new Modifiers()/*none*/);
                else
                    eachNameSpace.setVariable(varName, value, false);
            } catch (UtilEvalException e) {
                throw e.toEvalError(
                        "for loop iterator variable:" + varName, this, callStack);
            }

            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) // not empty statement
            {
                Object ret = statement.eval(callStack, krineBasicInterpreter);

                if (ret instanceof ReturnControl) {
                    switch (((ReturnControl) ret).kind) {
                        case RETURN:
                            returnControl = ret;
                            breakout = true;
                            break;

                        case CONTINUE:
                            break;

                        case BREAK:
                            breakout = true;
                            break;
                    }
                }
            }

            if (breakout)
                break;
        }

        callStack.swap(enclosingNameSpace);
        return returnControl;
    }
}
