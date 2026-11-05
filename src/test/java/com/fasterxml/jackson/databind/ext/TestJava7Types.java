package com.fasterxml.jackson.databind.ext;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * @since 2.7
 */
public class TestJava7Types extends BaseMapTest
{
    public void testPathRoundtrip() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();

        Path input = Paths.get("/tmp", "foo.txt");

        String json = mapper.writeValueAsString(input);
        assertNotNull(json);

        Path p = mapper.readValue(json, Path.class);
        assertNotNull(p);
        
        assertEquals(input.toUri(), p.toUri());
        assertEquals(input, p);
    }

    // [databind#]: Only accept "file" scheme or no scheme; reject jar:, http:, s3:, etc.
    public void testRejectNonFileSchemes() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();

        // file: scheme should work
        Path p = mapper.readValue("\"file:///tmp/foo.txt\"", Path.class);
        assertNotNull(p);

        // Bare path (no scheme) should work
        p = mapper.readValue("\"/tmp/foo.txt\"", Path.class);
        assertNotNull(p);

        // Non-file schemes should fail
        _verifyRejectScheme(mapper, "jar:http://example.com/foo.jar!/path");
        _verifyRejectScheme(mapper, "http://example.com/path");
        _verifyRejectScheme(mapper, "s3://bucket/key");
        _verifyRejectScheme(mapper, "custom://something");

        // and message should list schemes that are allowed
        try {
            mapper.readValue(quote("s3://bucket/key"), Path.class);
            fail("Should not pass");
        } catch (Exception e) {
            verifyException(e, "scheme 's3' not allowed");
            verifyException(e, "allowed: [\"file\"]");
        }
    }

    // [databind#]: scheme matching case-insensitive, as by `Paths.get(URI)` itself
    public void testAllowedSchemeCaseInsensitive() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();

        for (String input : new String[] {
                "FILE:///tmp/foo.txt", "FiLe:///tmp/foo.txt"
        }) {
            Path p = mapper.readValue(quote(input), Path.class);
            assertNotNull(p);
            assertEquals(Paths.get("/tmp", "foo.txt").toAbsolutePath(), p.toAbsolutePath());
        }
    }

    // [databind#]: allowed schemes may be overridden
    public void testCustomAllowedSchemes() throws Exception
    {
        // Case-insensitive both ways: allow-list entry in upper case, input in lower
        ObjectMapper mapper = _mapperWithSchemes(Arrays.asList("FILE"));
        Path p = mapper.readValue(quote("file:///tmp/foo.txt"), Path.class);
        assertNotNull(p);
        assertEquals(Paths.get("/tmp", "foo.txt").toAbsolutePath(), p.toAbsolutePath());

        // But if "file" not included, it gets rejected like any other scheme
        mapper = _mapperWithSchemes(Arrays.asList("jar", "jrt"));
        try {
            mapper.readValue(quote("file:///tmp/foo.txt"), Path.class);
            fail("Should have thrown for scheme in: file:///tmp/foo.txt");
        } catch (Exception e) {
            verifyException(e, "scheme 'file' not allowed");
            verifyException(e, "allowed: [\"jar\", \"jrt\"]");
        }

        // Scheme-less values, however, are always accepted
        assertNotNull(mapper.readValue(quote("/tmp/foo.txt"), Path.class));

        // and empty allow-list is legal, if unhelpful
        _verifyRejectScheme(_mapperWithSchemes(Collections.<String>emptyList()),
                "file:///tmp/foo.txt");
    }

    // [databind#6129]: providers only visible via Thread context class loader must
    // not be reached for schemes that are not allowed
    public void testDisallowedSchemeViaContextClassLoader() throws Exception
    {
        final String input = CustomSchemeFsProvider.SCHEME + ":///foo.txt";
        final ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
        // Registration is not at root of test classpath, so provider is NOT one of
        // installed ones (that is, not found by `Paths.get(URI)`) unless added like so:
        final URL serviceRoot = getClass().getResource("/tcclfs/");
        assertNotNull(serviceRoot);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[] { serviceRoot }, origLoader));

        try {
            // Not allowed by default, and must be rejected without provider lookup
            _verifyRejectScheme(new ObjectMapper(), input);
        } finally {
            Thread.currentThread().setContextClassLoader(origLoader);
        }
    }

    // [databind#6129]: same check must apply with polymorphic handling (default typing)
    public void testRejectNonFileSchemesPolymorphic() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping(DefaultTyping.NON_FINAL);
        Path input = Paths.get("/tmp", "foo.txt");
        String json = mapper.writeValueAsString(new Object[] { input });
        final String uriStr = quote(input.toUri().toString());
        assertTrue(json.indexOf(uriStr) >= 0);

        for (String scheme : new String[] {
                "jar:http://example.com/foo.jar!/path", "http://example.com/path"
        }) {
            try {
                mapper.readValue(json.replace(uriStr, quote(scheme)), Object[].class);
                fail("Should have thrown for scheme in: " + scheme);
            } catch (Exception e) {
                verifyException(e, "not allowed for Path deserialization");
            }
        }
    }

    // [databind#6129]: non-"file" schemes are usable if (and only if) explicitly allowed
    public void testAllowedJarScheme() throws Exception
    {
        // Note: must write actual zip first; file system created on missing file
        // could not be found by URI later on
        File zip = File.createTempFile("jackson-6129", ".zip");
        zip.deleteOnExit();
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zip));
        try {
            zout.putNextEntry(new ZipEntry("foo.txt"));
            zout.write("foo".getBytes("UTF-8"));
            zout.closeEntry();
        } finally {
            zout.close();
        }
        final URI zipUri = URI.create("jar:" + zip.toURI());
        final String input = zipUri + "!/foo.txt";
        FileSystem zipfs = FileSystems.newFileSystem(zipUri, Collections.<String, Object>emptyMap());
        try {
            // Not allowed by default, even if file system exists
            _verifyRejectScheme(new ObjectMapper(), input);

            // but accepted if allowed
            ObjectMapper mapper = _mapperWithSchemes(Arrays.asList("file", "jar"));
            Path p = mapper.readValue(quote(input), Path.class);
            assertEquals(zipfs.getPath("/foo.txt"), p);
        } finally {
            zipfs.close();
        }
    }

    public void testNullAllowedSchemes() throws Exception
    {
        try {
            new NioPathDeserializer(null);
            fail("Should not pass");
        } catch (IllegalArgumentException e) {
            verifyException(e, "`allowedSchemes` must not be null");
        }
    }

    private ObjectMapper _mapperWithSchemes(Collection<String> allowedSchemes) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Path.class, new NioPathDeserializer(allowedSchemes));
        return new ObjectMapper().registerModule(module);
    }

    private void _verifyRejectScheme(ObjectMapper mapper, String input) {
        try {
            mapper.readValue(quote(input), Path.class);
            fail("Should have thrown for scheme in: " + input);
        } catch (Exception e) {
            // expected - handleWeirdStringValue will throw by default
            verifyException(e, "not allowed for Path deserialization");
        }
    }

    // [databind#1688]:
    public void testPolymorphicPath() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping(DefaultTyping.NON_FINAL);
        Path input = Paths.get("/tmp", "foo.txt");

        String json = mapper.writeValueAsString(new Object[] { input });

        Object[] obs = mapper.readValue(json, Object[].class);
        assertEquals(1, obs.length);
        Object ob = obs[0];
        assertTrue(ob instanceof Path);

        assertEquals(input.toString(), ob.toString());
    }
}
