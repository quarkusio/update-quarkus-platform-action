package io.quarkus.automation.platform.update.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Singleton;

@Singleton
public class Processes {

    public int execute(List<String> commands) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(commands)
                .inheritIO()
                .start();
        return process.waitFor();
    }

    public int execute(List<String> commands, Path workingDirectory) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(commands)
                .directory(workingDirectory.toFile())
                .inheritIO()
                .start();
        return process.waitFor();
    }
}
