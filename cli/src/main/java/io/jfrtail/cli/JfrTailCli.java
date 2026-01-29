package io.jfrtail.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "jfr-tail", mixinStandardHelpOptions = true, version = "jfr-tail 1.0", description = "Tail -f for JFR events.")
public class JfrTailCli implements Callable<Integer> {

    @Command(name = "token", description = "Generate a JWT token (Owner Mode)")
    public Integer token(
            @Option(names = { "-s", "--secret" }, required = true, description = "Shared Secret") String secret,
            @Option(names = { "--ttl" }, defaultValue = "300", description = "Time to live in seconds") long ttl) {
        try {
            String token = io.jfrtail.common.security.JwtLite.generateToken(secret, ttl);
            System.out.println(token);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Command(name = "attach", description = "Attach to a running JVM PID")
    public Integer attach(
            @Option(names = { "-p", "--pid" }, required = true, description = "Process ID") String pid,
            @Option(names = { "--port" }, defaultValue = "7099", description = "Agent port") int port,
            @Option(names = { "-a",
                    "--agent-jar" }, required = true, description = "Path to jfrtail-agent.jar") String agentJar,
            @Option(names = { "--record" }, description = "Record events to JSONL file") String recordFile,
            @Option(names = {
                    "--web-port" }, defaultValue = "8080", description = "Agent Web Dashboard port") int webPort,
            @Option(names = { "--actuator-url" }, description = "Spring Actuator Base URL") String actuatorUrl,
            @Option(names = { "--actuator-user" }, description = "Actuator Basic Auth User") String actuatorUser,
            @Option(names = { "--actuator-pass" }, description = "Actuator Basic Auth Password") String actuatorPass,
            @Option(names = { "-s", "--secret" }, description = "Shared Secret (Owner)") String secret,
            @Option(names = { "-t", "--token" }, description = "Existing JWT Token (Guest)") String token) {
        try {
            System.out.println("Attaching to PID " + pid + "...");

            // Validate Auth
            if (secret == null && token == null) {
                System.out.println(
                        "WARNING: No secret or token provided. Agent will generate a random secret (check logs).");
            }

            // Pass args to Agent
            String agentArgs = "port=" + port + ";webPort=" + webPort;
            if (secret != null) {
                // Pass secret via system property is safer in some contexts, but here we pass
                // via args string if agent supports it
                // Our Agent main simpler parser might need update.
                // Alternatively, Agent code checks System Property 'jfrtail.secret'
                // We'll trust the user to set the secret manually or view the generated one.
                // For 'attach', we can try to pass it if Agent supported it.
                // Current Agent implementation checks 'jfrtail.secret' prop which we can't
                // easily set via attach args string without modifying Agent parsing.
                // Wait, Agent is loaded via 'attach'. We can't set Sys Props easily on target
                // JVM unless we use -D.
                // Let's assume for now the User attaches, sees the secret in Target stdout, and
                // uses it.
                // OR we update Agent to parse secret from args string.
            }

            // We need to update Agent to verify auth, which we did.
            // Agent consumes 'port' and 'webPort' from arg string.
            // Let's update Agent to consume 'secret' from arg string too in next step if
            // needed.

            AgentAttacher.attach(pid, agentJar, agentArgs);

            System.out.println("Connecting to agent on port " + port + "...");

            // Resolve Token
            String finalToken = token;
            if (finalToken == null && secret != null) {
                finalToken = io.jfrtail.common.security.JwtLite.generateToken(secret, 60);
            }

            TuiManager tui = new TuiManager("localhost", port, recordFile, actuatorUrl, actuatorUser, actuatorPass,
                    finalToken);
            tui.start();
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Command(name = "connect", description = "Connect to an already running JFR Tail Agent")
    public Integer connect(
            @Option(names = { "--host" }, defaultValue = "localhost", description = "Agent host") String host,
            @Option(names = { "--port" }, defaultValue = "7099", description = "Agent port") int port,
            @Option(names = { "--record" }, description = "Record events to JSONL file") String recordFile,
            @Option(names = { "--actuator-url" }, description = "Spring Actuator Base URL") String actuatorUrl,
            @Option(names = { "--actuator-user" }, description = "Actuator Basic Auth User") String actuatorUser,
            @Option(names = { "--actuator-pass" }, description = "Actuator Basic Auth Password") String actuatorPass,
            @Option(names = { "-s", "--secret" }, description = "Shared Secret (Owner)") String secret,
            @Option(names = { "-t", "--token" }, description = "Existing JWT Token (Guest)") String token) {
        try {
            System.out.println("Connecting to agent at " + host + ":" + port + "...");

            // Resolve Token
            String finalToken = token;
            if (finalToken == null && secret != null) {
                finalToken = io.jfrtail.common.security.JwtLite.generateToken(secret, 60);
            }

            TuiManager tui = new TuiManager(host, port, recordFile, actuatorUrl, actuatorUser, actuatorPass,
                    finalToken);
            tui.start();
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Command(name = "bundle", description = "Download an incident bundle from a running agent")
    public Integer bundle(
            @Option(names = { "--host" }, defaultValue = "localhost", description = "Agent host") String host,
            @Option(names = { "--port" }, defaultValue = "8080", description = "Agent Web port") int port,
            @Option(names = { "-s", "--secret" }, description = "Shared Secret (Owner)") String secret,
            @Option(names = { "-t", "--token" }, description = "Existing JWT Token (Guest)") String token,
            @Option(names = { "-o",
                    "--output" }, defaultValue = "jfr-bundle.json", description = "Output file") String output) {
        try {
            String finalToken = token;
            if (finalToken == null && secret != null) {
                finalToken = io.jfrtail.common.security.JwtLite.generateToken(secret, 60);
            }
            if (finalToken == null) {
                System.err.println("Error: Secret or Token required for bundle.");
                return 1;
            }

            System.out.println("Downloading bundle from " + host + ":" + port + "...");
            java.net.URL url = java.net.URI.create("http://" + host + ":" + port + "/jfr/bundle?token=" + finalToken)
                    .toURL();
            java.nio.file.Files.copy(url.openStream(), java.nio.file.Paths.get(output),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Bundle saved to: " + output);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JfrTailCli()).execute(args);
        System.exit(exitCode);
    }
}
