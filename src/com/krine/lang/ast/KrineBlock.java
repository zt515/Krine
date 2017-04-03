package com.krine.lang.ast;

import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.utils.CallStack;

class KrineBlock extends SimpleNode {
    public boolean isSynchronized = false;

    KrineBlock(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        return eval(callStack, krineBasicInterpreter, false);
    }

    /**
     * @param overrideNamespace if set to true the block will be executed
     *                          in the current namespace (not a subordinate one).
     *                          <p>
     *                          If true *no* new BlockNamespace will be swapped onto the stack and
     *                          the eval will happen in the current
     *                          top namespace.  This is used by KrineMethod, TryStatement, etc.
     *                          which must initialize the block first and also for those that perform
     *                          multiple passes in the same block.
     */
    public Object eval(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter,
            boolean overrideNamespace)
            throws EvalError {
        waitForDebugger();

        Object syncValue = null;
        if (isSynchronized) {
            // First node is the expression on which to sync
            SimpleNode exp = ((SimpleNode) jjtGetChild(0));
            syncValue = exp.eval(callStack, krineBasicInterpreter);
        }

        Object ret;
        if (isSynchronized) // Do the actual synchronization
            synchronized (syncValue) {
                ret = evalBlock(
                        callStack, krineBasicInterpreter, overrideNamespace, null/*filter*/);
            }
        else
            ret = evalBlock(
                    callStack, krineBasicInterpreter, overrideNamespace, null/*filter*/);

        return ret;
    }

    Object evalBlock(
            CallStack callStack, KrineBasicInterpreter krineBasicInterpreter,
            boolean overrideNamespace, NodeFilter nodeFilter)
            throws EvalError {
        Object ret = Primitive.VOID;
        NameSpace enclosingNameSpace = null;
        if (!overrideNamespace) {
            enclosingNameSpace = callStack.top();
            BlockNameSpace bodyNameSpace =
                    new BlockNameSpace(enclosingNameSpace);

            callStack.swap(bodyNameSpace);
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

                if (node instanceof KrineClassDeclaration)
                    node.eval(callStack, krineBasicInterpreter);
            }
            for (int i = startChild; i < numChildren; i++) {
                SimpleNode node = ((SimpleNode) jjtGetChild(i));
                if (node instanceof KrineClassDeclaration)
                    continue;

                // filter nodes
                if (nodeFilter != null && !nodeFilter.isVisible(node))
                    continue;

                ret = node.eval(callStack, krineBasicInterpreter);

                // statement or embedded block evaluated a return statement
                if (ret instanceof ReturnControl)
                    break;
            }
        } finally {
            // make sure we put the namespace back when we leave.
            if (!overrideNamespace)
                callStack.swap(enclosingNameSpace);
        }
        return ret;
    }

    public interface NodeFilter {
        boolean isVisible(SimpleNode node);
    }

}

