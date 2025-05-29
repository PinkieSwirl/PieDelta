package eu.pieland.delta;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static eu.pieland.delta.DeltaTest.*;
import static java.nio.file.Files.createDirectories;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JavaApiTest {

    @TempDir
    private Path tmpdir;

    @ParameterizedTest
    @CsvSource(value = {"delta-diff-1.1.jar, delta-diff-1.1.3.jar"})
    void roundTripSuccessfully(String sourcePathString, String targetPathString) throws IOException {
        // setup
        Path source = createDirectories(tmpdir.resolve("source"));
        unpackZip(JavaApiTest.class, sourcePathString, source);
        Path target = createDirectories(tmpdir.resolve("target"));
        unpackZip(JavaApiTest.class, targetPathString, target);
        Map<Path, String> expected = toComparableMap(target);
        assertNotEquals(expected, toComparableMap(source));

        // act
        Path patch = Delta.create(source, target, tmpdir.resolve("patch.zip"));
        Delta.patch(inZip(patch), source);

        // assert
        assertEquals(expected, toComparableMap(source));
    }
}
