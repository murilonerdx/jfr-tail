package io.jfrtail.agent;

import io.jfrtail.agent.api.JfrTailMonitor;

import java.lang.instrument.Instrumentation;

public class JfrTailAgent {
    private static int tcpPort = 7099;
    private static int webPort = 8080;

    public static void premain(String args, Instrumentation inst) {
        start(args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(args);
    }

    private static void start(String args) {
        parseArgs(args);
        try {
            System.out.println("[JfrTailAgent] Initializing Monitor...");
            JfrTailMonitor.getInstance().start(webPort, tcpPort);
        } catch (Exception e) {
            System.err.println("[JfrTailAgent] Failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseArgs(String args) {
        if (args != null && !args.isEmpty()) {
            for (String arg : args.split(";")) {
                String[] parts = arg.split("=");
                if (parts.length == 2) {
                    if ("port".equals(parts[0])) {
                        tcpPort = Integer.parseInt(parts[1]);
                    } else if ("webPort".equals(parts[0])) {
                        webPort = Integer.parseInt(parts[1]);
                    }
                }
            }
        }
    }
}
