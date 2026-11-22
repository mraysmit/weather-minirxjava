package dev.mars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AppTest {
    @Test
    @Timeout(45)
    void mainRunsAndPrintsAllSevenRealExamples() throws InterruptedException {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream console = new PrintStream(bytes, true,
                StandardCharsets.UTF_8)) {
            System.setOut(console);
            try {
                App.main(new String[0]);
            } finally {
                System.setOut(original);
            }
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(7, output.lines()
                .filter(line -> line.startsWith("=== Example ")).count());
        for (int example = 1; example <= 7; example++) {
            assertTrue(output.contains("=== Example " + example + ":"));
        }
        for (String city : new String[] {"London", "Paris", "Berlin", "Madrid", "Rome"}) {
            assertTrue(output.contains(city));
        }
        assertTrue(output.contains("Collected 3/5 successful results"));
        assertTrue(output.contains("unfinished subtasks were cancelled"));
        assertTrue(output.contains("Failure type: IllegalStateException"));
    }
}
