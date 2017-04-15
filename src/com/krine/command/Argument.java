package com.krine.command;

/**
 * @author kiva
 * @date 2017/4/16
 */
public class Argument {
    private static final String ARG_DEBUG = "g";
    private static final String ARG_END = "-";

    private boolean isDebug = false;
    private String[] rest;

    public Argument(String[] args) {
        parseArguments(args);
    }

    public boolean isDebug() {
        return isDebug;
    }

    public String[] getRest() {
        return rest;
    }

    private void parseArguments(String[] args) {
        boolean end = false;
        int parsedIndex;

        for (parsedIndex = 0; !end && parsedIndex < args.length; ++parsedIndex) {
            String arg = args[parsedIndex];
            if (arg.isEmpty() || arg.charAt(0) != '-') {
                continue;
            }

            switch (args[parsedIndex].substring(1)) {
                case ARG_DEBUG:
                    isDebug = true;
                    break;
                case "-":
                    end = true;
                    break;
            }
        }

        rest = new String[args.length - parsedIndex + 1];
        System.arraycopy(args, parsedIndex - 1, rest, 0, rest.length);
    }
}
