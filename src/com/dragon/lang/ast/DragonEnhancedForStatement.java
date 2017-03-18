package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;
import com.dragon.lang.utils.CollectionManager;
import com.dragon.lang.UtilEvalException;

import java.util.Iterator;

/**
 * Implementation of the enhanced for(:) statement.
 * This statement uses Iterator to support iteration over a wide variety
 * of iterable types.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
class DragonEnhancedForStatement extends SimpleNode implements ParserConstants {
    String varName;

    DragonEnhancedForStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        Class elementType = null;
        SimpleNode expression, statement = null;

        NameSpace enclosingNameSpace = callstack.top();
        SimpleNode firstNode = ((SimpleNode) jjtGetChild(0));
        int nodeCount = jjtGetNumChildren();

        if (firstNode instanceof DragonType) {
            elementType = ((DragonType) firstNode).getType(callstack, dragonBasicInterpreter);
            expression = ((SimpleNode) jjtGetChild(1));
            if (nodeCount > 2)
                statement = ((SimpleNode) jjtGetChild(2));
        } else {
            expression = firstNode;
            if (nodeCount > 1)
                statement = ((SimpleNode) jjtGetChild(1));
        }

        BlockNameSpace eachNameSpace = new BlockNameSpace(enclosingNameSpace);
        callstack.swap(eachNameSpace);

        final Object iteratee = expression.eval(callstack, dragonBasicInterpreter);

        if (iteratee == Primitive.NULL)
            throw new EvalError("The collection, array, map, iterator, or " +
                    "enumeration portion of a for statement cannot be null.",
                    this, callstack);

        CollectionManager cm = CollectionManager.getCollectionManager();
        if (!cm.isDragonIterable(iteratee))
            throw new EvalError("Can't iterate over type: "
                    + iteratee.getClass(), this, callstack);
        Iterator iterator = cm.getDragonIterator(iteratee);

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
                        "for loop iterator variable:" + varName, this, callstack);
            }

            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) // not empty statement
            {
                Object ret = statement.eval(callstack, dragonBasicInterpreter);

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

        callstack.swap(enclosingNameSpace);
        return returnControl;
    }
}
