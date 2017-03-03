package com.dragon.lang.ast;

import com.dragon.lang.DragonBasicInterpreter;
import com.dragon.lang.utils.CallStack;

class DragonBlock extends SimpleNode {
    public boolean isSynchronized = false;

    DragonBlock(int id) {
        super(id);
    }

    public Object eval(CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter)
            throws EvalError {
        return eval(callstack, dragonBasicInterpreter, false);
    }

    /**
     * @param overrideNamespace if set to true the block will be executed
     *                          in the current namespace (not a subordinate one).
     *                          <p>
     *                          If true *no* new BlockNamespace will be swapped onto the stack and
     *                          the eval will happen in the current
     *                          top namespace.  This is used by DragonMethod, TryStatement, etc.
     *                          which must intialize the block first and also for those that perform
     *                          multiple passes in the same block.
     */
    public Object eval(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter,
            boolean overrideNamespace)
            throws EvalError {
        Object syncValue = null;
        if (isSynchronized) {
            // First node is the expression on which to sync
            SimpleNode exp = ((SimpleNode) jjtGetChild(0));
            syncValue = exp.eval(callstack, dragonBasicInterpreter);
        }

        Object ret;
        if (isSynchronized) // Do the actual synchronization
            synchronized (syncValue) {
                ret = evalBlock(
                        callstack, dragonBasicInterpreter, overrideNamespace, null/*filter*/);
            }
        else
            ret = evalBlock(
                    callstack, dragonBasicInterpreter, overrideNamespace, null/*filter*/);

        return ret;
    }

    Object evalBlock(
            CallStack callstack, DragonBasicInterpreter dragonBasicInterpreter,
            boolean overrideNamespace, NodeFilter nodeFilter)
            throws EvalError {
        Object ret = Primitive.VOID;
        NameSpace enclosingNameSpace = null;
        if (!overrideNamespace) {
            enclosingNameSpace = callstack.top();
            BlockNameSpace bodyNameSpace =
                    new BlockNameSpace(enclosingNameSpace);

            callstack.swap(bodyNameSpace);
        }

        int startChild = isSynchronized ? 1 : 0;
        int numChildren = jjtGetNumChildren();

        try {
            /*
                Evaluate block in two passes:
				First do class declarations then do everything else.
			*/
            for (int i = startChild; i < numChildren; i++) {
                SimpleNode node = ((SimpleNode) jjtGetChild(i));

                if (nodeFilter != null && !nodeFilter.isVisible(node))
                    continue;

                if (node instanceof DragonClassDeclaration)
                    node.eval(callstack, dragonBasicInterpreter);
            }
            for (int i = startChild; i < numChildren; i++) {
                SimpleNode node = ((SimpleNode) jjtGetChild(i));
                if (node instanceof DragonClassDeclaration)
                    continue;

                // filter nodes
                if (nodeFilter != null && !nodeFilter.isVisible(node))
                    continue;

                ret = node.eval(callstack, dragonBasicInterpreter);

                // statement or embedded block evaluated a return statement
                if (ret instanceof ReturnControl)
                    break;
            }
        } finally {
            // make sure we put the namespace back when we leave.
            if (!overrideNamespace)
                callstack.swap(enclosingNameSpace);
        }
        return ret;
    }

    public interface NodeFilter {
        boolean isVisible(SimpleNode node);
    }

}

