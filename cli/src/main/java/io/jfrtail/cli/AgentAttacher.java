package io.jfrtail.cli;

import com.sun.tools.attach.VirtualMachine;
import java.io.File;

public class AgentAttacher {
    public static void attach(String pid, String agentJarPath, String options) throws Exception {
        File agentFile = new File(agentJarPath);
        if (!agentFile.exists()) {
            throw new IllegalArgumentException("Agent JAR not found: " + agentJarPath);
        }

        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentFile.getAbsolutePath(), options);
        } finally {
            vm.detach();
        }
    }
}
