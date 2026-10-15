import Board.AppVersion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.jar.JarFile;

/** Build metadata, isolated version loading, and the graphical application's safe CLI entry. */
public final class VersionTest {
    private static int checks;
    private static byte[] versionClass;

    private VersionTest() {}

    public static void main(String[] arguments) throws Exception {
        Path project = Path.of(System.getProperty("quoridor.test.project", ".")).toAbsolutePath();
        String version =
                Files.readString(project.resolve("VERSION"), StandardCharsets.UTF_8).trim();
        Path jar =
                Path.of(
                        System.getProperty(
                                "quoridor.test.jar",
                                project.resolve("out/Quoridor.jar").toString()));
        check(
                version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
                "Repository version has three numeric components");
        equal(AppVersion.current(), version, "Application API uses VERSION");
        try (InputStream resource =
                AppVersion.class.getResourceAsStream("/app-version.properties")) {
            check(resource != null, "Version resource is available on the production classpath");
            equal(
                    readProperties(resource).getProperty("version"),
                    version,
                    "Production resource uses VERSION");
        }
        try (InputStream resource =
                AppVersion.class.getResourceAsStream("/Board/AppVersion.class")) {
            check(resource != null, "Version class is available for isolated loading");
            versionClass = resource.readAllBytes();
        }
        isolatedMetadataValidation(version);
        packagedMetadata(project, jar, version);
        headlessVersionCommand(jar, version);
        System.out.println("Version tests passed (" + checks + " checks).");
    }

    private static void isolatedMetadataValidation(String version) throws Exception {
        equal(
                isolatedVersion((String) null, null),
                "development",
                "IDE without generated metadata has a clear fallback");
        for (String valid : List.of("0.0.0", "1.2.3", "255.255.65535", version)) {
            equal(
                    isolatedVersion("version=" + valid + "\n", null),
                    valid,
                    "Unpackaged version resource is accepted");
            equal(
                    isolatedVersion("version=" + valid + "\n", valid),
                    valid,
                    "Matching resource and JAR manifest are accepted");
        }
        for (String invalid :
                List.of(
                        "",
                        "2",
                        "2.0",
                        "2.0.0.0",
                        "v2.0.0",
                        "2.0.0-beta",
                        "-1.0.0",
                        "01.0.0",
                        "1.00.0",
                        "1.0.00",
                        "256.0.0",
                        "0.256.0",
                        "0.0.65536",
                        "999999999999.0.0",
                        "2.0.0 ")) {
            rejectsMetadata(
                    () ->
                            new ByteArrayInputStream(
                                    ("version=" + invalid + "\n").getBytes(StandardCharsets.UTF_8)),
                    null,
                    "Invalid version is rejected: " + invalid);
        }
        rejectsMetadata(
                () -> new ByteArrayInputStream("other=2.0.0\n".getBytes(StandardCharsets.UTF_8)),
                null,
                "Missing version property is rejected");
        rejectsMetadata(
                () ->
                        new ByteArrayInputStream(
                                "version=\\uNOTHEX\n".getBytes(StandardCharsets.UTF_8)),
                null,
                "Malformed properties are rejected");
        rejectsMetadata(
                () ->
                        new ByteArrayInputStream(
                                ("version=" + version + "\n").getBytes(StandardCharsets.UTF_8)),
                version.equals("1.0.0") ? "2.0.0" : "1.0.0",
                "Resource and manifest disagreement is rejected");
        rejectsMetadata(
                () ->
                        new InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("Simulated unreadable build metadata");
                            }
                        },
                null,
                "Unreadable resource is rejected");
    }

    private static void packagedMetadata(Path project, Path jarPath, String version)
            throws IOException {
        check(Files.isRegularFile(jarPath), "A built application JAR exists");
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            check(jar.getManifest() != null, "Application JAR has a manifest");
            var attributes = jar.getManifest().getMainAttributes();
            equal(attributes.getValue("Main-Class"), "Main", "JAR entry point is Main");
            equal(
                    attributes.getValue("Implementation-Version"),
                    version,
                    "JAR manifest uses VERSION");
            equal(
                    attributes.getValue("Implementation-Title"),
                    "Quoridor",
                    "JAR identifies the application");
            var resource = jar.getJarEntry("app-version.properties");
            check(resource != null, "Version resource is packaged");
            try (InputStream source = jar.getInputStream(resource)) {
                equal(
                        readProperties(source).getProperty("version"),
                        version,
                        "Packaged resource uses VERSION");
            }
            for (String notice : List.of("LICENSE", "AUTHORS.md")) {
                var entry = jar.getJarEntry("META-INF/" + notice);
                check(entry != null, notice + " is included in the JAR");
                try (InputStream source = jar.getInputStream(entry)) {
                    equal(
                            new String(source.readAllBytes(), StandardCharsets.UTF_8),
                            Files.readString(project.resolve(notice), StandardCharsets.UTF_8),
                            notice + " content is preserved");
                }
            }
        }
    }

    private static void headlessVersionCommand(Path jar, String version) throws Exception {
        String executable =
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process =
                new ProcessBuilder(
                                java.toString(),
                                "-Djava.awt.headless=true",
                                "-jar",
                                jar.toString(),
                                "--version")
                        .redirectErrorStream(true)
                        .start();
        try {
            check(
                    process.waitFor(10, TimeUnit.SECONDS),
                    "Version CLI exits without opening a graphical window");
            equal(process.exitValue(), 0, "Version CLI exits successfully");
            equal(
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .strip(),
                    "Quoridor " + version,
                    "Version CLI reports exactly the built version");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    private static Properties readProperties(InputStream source) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(source, StandardCharsets.UTF_8));
        return properties;
    }

    private static String isolatedVersion(String properties, String manifest) throws Exception {
        return isolatedVersion(
                () ->
                        properties == null
                                ? null
                                : new ByteArrayInputStream(
                                        properties.getBytes(StandardCharsets.UTF_8)),
                manifest);
    }

    private static String isolatedVersion(Supplier<InputStream> source, String manifest)
            throws Exception {
        ClassLoader loader =
                new ClassLoader(null) {
                    {
                        definePackage("Board", null, null, null, null, manifest, null, null);
                    }

                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        if (!name.equals("Board.AppVersion")) {
                            throw new ClassNotFoundException(name);
                        }
                        return defineClass(name, versionClass, 0, versionClass.length);
                    }

                    @Override
                    public InputStream getResourceAsStream(String name) {
                        return name.equals("app-version.properties") ? source.get() : null;
                    }
                };
        return (String) loader.loadClass("Board.AppVersion").getMethod("current").invoke(null);
    }

    private static void rejectsMetadata(
            Supplier<InputStream> source, String manifest, String message) throws Exception {
        try {
            isolatedVersion(source, manifest);
            throw new AssertionError(message);
        } catch (InvocationTargetException | ExceptionInInitializerError exception) {
            Throwable failure = exception;
            while ((failure instanceof InvocationTargetException
                            || failure instanceof ExceptionInInitializerError)
                    && failure.getCause() != null) {
                failure = failure.getCause();
            }
            check(failure instanceof IllegalStateException, message);
        }
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                java.util.Objects.equals(actual, expected),
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
