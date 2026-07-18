package jp.awabi2048.cccontent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SharedClockUsageSourceContractTest {
    @Test
    void featureCodeDoesNotCreateIndependentServerDateZones() throws Exception {
        Path sourceRoot = Path.of("src/main/kotlin/jp/awabi2048/cccontent");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".kt")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("ZoneId.systemDefault()"), () -> "共有時計を使用してください: " + file);
                assertFalse(source.contains("ZoneId.of(\"Asia/Tokyo\")"), () -> "共有時計を使用してください: " + file);
            }
        }
    }
}
