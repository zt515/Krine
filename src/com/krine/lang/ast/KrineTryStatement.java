package com.krine.lang.ast;

import com.krine.lang.InterpreterException;
import com.krine.lang.KrineBasicInterpreter;
import com.krine.lang.UtilEvalException;
import com.krine.lang.utils.CallStack;

import java.util.ArrayList;
import java.util.List;


class KrineTryStatement extends SimpleNode {
    KrineTryStatement(int id) {
        super(id);
    }

    public Object eval(CallStack callStack, KrineBasicInterpreter krineBasicInterpreter)
            throws EvalError {
        waitForDebugger();

        KrineBlock tryBlock = ((KrineBlock) jjtGetChild(0));

        List<krineFormalParameter> catchParams = new ArrayList<>();
        List<KrineBlock> catchBlocks = new ArrayList<>();

        int numChildren = jjtGetNumChildren();
        Node node = null;
        int i = 1;
        while ((i < numChildren) && ((node = jjtGetChild(i++)) instanceof krineFormalParameter)) {
            catchParams.add((krineFormalParameter) node);
            catchBlocks.add((KrineBlock) jjtGetChild(i++));
            node = null;
        }
        // finally block
        KrineBlock finallyBlock = null;
        if (node != null)
            finallyBlock = (KrineBlock) node;

// Why both of these?

        KrineTargetException target = null;
        Throwable thrown = null;
        Object ret = null;

		/*
            Evaluate the contents of the try { } block and catch any resulting
			TargetErrors generated by the script.
			We save the callStack depth and if an exception is thrown we pop
			back to that depth before continuing.  The exception short circuited
			any intervening method context pops.

			Note: we the stack info... what do we do with it?  append
			to exception message?
		*/
        int callStackDepth = callStack.depth();
        try {
            ret = tryBlock.eval(callStack, krineBasicInterpreter);
        } catch (KrineTargetException e) {
            target = e;
            String stackInfo = "Krine Stack: ";
            while (callStack.depth() > callStackDepth)
                stackInfo += "\t" + callStack.pop() + "\n";
        }

        // unwrap the target error
        if (target != null)
            thrown = target.getTarget();


        // If we have an exception, find a catch
        try {
            if (thrown != null) {
                int n = catchParams.size();
                for (i = 0; i < n; i++) {
                    // Get catch block
                    krineFormalParameter fp = catchParams.get(i);

                    // Should cache this subject to classloader change message
                    // Evaluation of the formal parameter simply resolves its
                    // type via the specified nameSpace.. it doesn't modify the
                    // nameSpace.
                    fp.eval(callStack, krineBasicInterpreter);

                    if (fp.type == null && krineBasicInterpreter.isStrictJava())
                        throw new EvalError(
                                "(Strict Java) Untyped catch block", this, callStack);

                    // If the param is typed check assignability
                    if (fp.type != null)
                        try {
                            thrown = (Throwable) Types.castObject(
                                    thrown/*rsh*/, fp.type/*lhsType*/, Types.ASSIGNMENT);
                        } catch (UtilEvalException e) {
                            /*
                                Catch the mismatch and continue to try the next
								Note: this is inefficient, should have an
								isAssignableFrom() that doesn't throw
								// TODO: we do now have a way to test assignment
								// 	in castObject(), use it?
							*/
                            continue;
                        }

                    // Found match, execute catch block
                    KrineBlock cb = catchBlocks.get(i);

                    // Prepare to execute the block.
                    // We must create a new BlockNameSpace to hold the catch
                    // parameter and swap it on the stack after initializing it.

                    NameSpace enclosingNameSpace = callStack.top();
                    BlockNameSpace cbNameSpace =
                            new BlockNameSpace(enclosingNameSpace);

                    try {
                        if (fp.type == krineFormalParameter.UNTYPED)
                            // set an untyped variable directly in the block
                            cbNameSpace.setBlockVariable(fp.name, thrown);
                        else {
                            // set a typed variable (directly in the block)
                            Modifiers modifiers = new Modifiers();
                            cbNameSpace.setTypedVariable(
                                    fp.name, fp.type, thrown, modifiers);
                        }
                    } catch (UtilEvalException e) {
                        throw new InterpreterException(
                                "Unable to set var in catch block nameSpace.");
                    }

                    // put cbNameSpace on the top of the stack
                    callStack.swap(cbNameSpace);
                    try {
                        ret = cb.eval(callStack, krineBasicInterpreter);
                    } finally {
                        // put it back
                        callStack.swap(enclosingNameSpace);
                    }

                    target = null;  // handled target
                    break;
                }
            }
        } finally {
            // evaluate finally block
            if (finallyBlock != null) {
                final Object result = finallyBlock.eval(callStack, krineBasicInterpreter);
                if (result instanceof ReturnControl) {
                    //noinspection ReturnInsideFinallyBlock
                    return result;
                }
            }
        }
        if (target != null) {
            // exception fell through, throw it upward...
            throw target;
        }
        // no exception return
        return ((ret instanceof ReturnControl)) ? ret : Primitive.VOID;
    }
}
