package com.krine.lang.ast;

import com.krine.lang.utils.StringUtil;

/**
 * This exception is thrown when parse errors are encountered.
 * You can explicitly create objects of this exception type by
 * calling the method generateParseException in the generated
 * parser.
 * <p>
 * You can modify this class to customize your error reporting
 * mechanisms so long as you retain the public fields.
 */

// Begin Krine Modification - public, extend EvalError
public final class ParseException extends EvalError {
// End Krine Modification - public, extend EvalError

    // Begin Krine Modification - sourceFile

    private String sourceFile = "<unknown>";

    /**
     * Used to add source file info to exception
     */
    public void setErrorSourceFile(String file) {
        this.sourceFile = file;
    }

    public String getErrorSourceFile() {
        return sourceFile;
    }

    // End Krine Modification - sourceFile

    /**
     * This constructor is used by the method "generateParseException"
     * in the generated parser.  Calling this constructor generates
     * a new object of this type with the fields "currentToken",
     * "expectedTokenSequences", and "tokenImage" set.  The boolean
     * flag "specialConstructor" is also set to true to indicate that
     * this constructor was used to create this object.
     * This constructor calls its super class with the empty string
     * to force the "toString" method of parent class "Throwable" to
     * print the error message in the form:
     * ParseException: <result of getMessage>
     */
    public ParseException(Token currentTokenVal,
                          int[][] expectedTokenSequencesVal,
                          String[] tokenImageVal
    ) {
        // Begin Krine Modification - constructor
        this();
        // End Krine Modification - constructor
        specialConstructor = true;
        currentToken = currentTokenVal;
        expectedTokenSequences = expectedTokenSequencesVal;
        tokenImage = tokenImageVal;
    }

    /**
     * The following constructors are for use by you for whatever
     * purpose you can think of.  Constructing the exception in this
     * manner makes the exception behave in the normal way - i.e., as
     * documented in the class "Throwable".  The fields "errorToken",
     * "expectedTokenSequences", and "tokenImage" do not contain
     * relevant information.  The JavaCC generated code does not use
     * these constructors.
     */

    public ParseException() {
        // Begin Krine Modification - constructor
        this("");
        // End Krine Modification - constructor
        specialConstructor = false;
    }

    public ParseException(String message) {
        // Begin Krine Modification - super constructor args
        // null node, null callStack, ParseException knows where the error is.
        super(message, null, null);
        // End Krine Modification - super constructor args
        specialConstructor = false;
    }

    public ParseException(String message, Throwable cause) {
        // Begin Krine Modification - super constructor args
        // null node, null callStack, ParseException knows where the error is.
        super(message, null, null, cause);
        // End Krine Modification - super constructor args
        specialConstructor = false;
    }

    /**
     * This variable determines which constructor was used to create
     * this object and thereby affects the semantics of the
     * "getMessage" method (see below).
     */
    protected boolean specialConstructor;

    /**
     * This is the last token that has been consumed successfully.  If
     * this object has been created due to a parse error, the token
     * following this token will (therefore) be the first error token.
     */
    public Token currentToken;

    /**
     * Each entry in this array is an array of integers.  Each array
     * of integers represents a sequence of tokens (by their ordinal
     * values) that is expected at this point of the parse.
     */
    public int[][] expectedTokenSequences;

    /**
     * This is a reference to the "tokenImage" array of the generated
     * parser within which the parse error occurred.  This array is
     * defined in the generated ...Constants interface.
     */
    public String[] tokenImage;

    // Begin Krine Modification - moved body to overloaded getMessage()
    public String getMessage() {
        return getMessage(false);
    }
    // End Krine Modification - moved body to overloaded getMessage()

    /**
     * This method has the standard behavior when this object has been
     * created using the standard constructors.  Otherwise, it uses
     * "currentToken" and "expectedTokenSequences" to generate a parse
     * error message and returns it.  If this object has been created
     * due to a parse error, and you do not catch it (it gets thrown
     * from the parser), then this method is called during the printing
     * of the final stack trace, and hence the correct error message
     * gets displayed.
     */
    // Begin Krine Modification - added debug param
    public String getMessage(boolean debug) {
        // End Krine Modification - added debug param
        if (!specialConstructor) {
            return super.getRawMessage();
        }
        String expected = "";
        int maxSize = 0;
        for (int[] expectedTokenSequence : expectedTokenSequences) {
            if (maxSize < expectedTokenSequence.length) {
                maxSize = expectedTokenSequence.length;
            }
            for (int j = 0; j < expectedTokenSequence.length; j++) {
                expected += tokenImage[expectedTokenSequence[j]] + " ";
            }
            if (expectedTokenSequence[expectedTokenSequence.length - 1] != 0) {
                expected += "...";
            }
            expected += eol + "    ";
        }
        // Begin Krine Modification - added sourceFile info
        String result = "In file: " + sourceFile + " got \"";
        // End Krine Modification - added sourceFile info
        Token tok = currentToken.next;
        for (int i = 0; i < maxSize; i++) {
            if (i != 0) result += " ";
            if (tok.kind == 0) {
                result += tokenImage[0];
                break;
            }
            result += StringUtil.addEscapes(tok.image);
            tok = tok.next;
        }
        result += "\" at line " + currentToken.next.beginLine + ", column " + currentToken.next.beginColumn + "." + eol;

        // Begin Krine Modification - made conditional on debug
        if (debug) {
            if (expectedTokenSequences.length == 1) {
                result += "Was expecting:" + eol + "    ";
            } else {
                result += "Was expecting one of:" + eol + "    ";
            }

            result += expected;
        }
        // End Krine Modification - made conditional on debug

        return result;
    }

    /**
     * The end of line string for this machine.
     */
    protected String eol = System.getProperty("line.separator", "\n");

    // Begin Krine Modification - override error methods and toString

    public int getErrorLineNumber() {
        return currentToken.next.beginLine;
    }

    public String getErrorText() {
        // copied from generated getMessage()
        int maxSize = 0;
        for (int[] expectedTokenSequence : expectedTokenSequences) {
            if (maxSize < expectedTokenSequence.length)
                maxSize = expectedTokenSequence.length;
        }

        String result = "";
        Token tok = currentToken.next;
        for (int i = 0; i < maxSize; i++) {
            if (i != 0) result += " ";
            if (tok.kind == 0) {
                result += tokenImage[0];
                break;
            }
            result += StringUtil.addEscapes(tok.image);
            tok = tok.next;
        }

        return result;
    }

    // End Krine Modification - override error methods and toString

}
