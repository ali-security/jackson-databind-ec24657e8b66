package com.fasterxml.jackson.databind.introspect;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.databind.*;

/**
 * Tests covering the [databind#5398] follow-up: when a property's setter is
 * {@code @JsonIgnore}d (and the getter carries {@code @JsonProperty} that
 * keeps the property non-ignored as a whole), the inferred field mutator
 * retained via {@code MapperFeature.INFER_PROPERTY_MUTATORS} must NOT be
 * used to bypass the ignored setter. The property should remain
 * serialization-only (read-only).
 */
public class JsonIgnoreSetterFieldBypass5398Test extends BaseMapTest
{
    static class RenamedGetterIgnoredSetter {
        private String prop;

        @JsonProperty("renamedProp")
        public String getProp() {
            return prop;
        }

        @JsonIgnore
        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    static class StandardGetterIgnoredSetter {
        private String prop;

        @JsonProperty
        public String getProp() {
            return prop;
        }

        @JsonIgnore
        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    // Sibling shape: READ_ONLY access on getter rather than @JsonIgnore on setter.
    static class ReadOnlyRenamedGetter {
        private String prop;

        @JsonProperty(value = "renamedProp", access = JsonProperty.Access.READ_ONLY)
        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    // READ_ONLY getter renamed onto the name of another, writable property:
    // fix for [databind#5967] must not strip fields of that other property
    static class ReadOnlyRenameCollision {
        private int a;

        @JsonProperty(value = "b", access = JsonProperty.Access.READ_ONLY)
        public int getA() {
            return a;
        }

        @JsonProperty
        public int b;
    }

    // Annotation on (non-visible) field must still be merged into getter
    // for serialization, even if setter is ignored
    static class AnnotatedFieldIgnoredSetter {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String prop;

        @JsonProperty
        public String getProp() {
            return prop;
        }

        @JsonIgnore
        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    // Same shape as `StandardGetterIgnoredSetter`, but with annotations
    // applied via mix-in
    static class PlainBean {
        private String prop;

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    static abstract class PlainBeanMixIn {
        @JsonProperty
        public abstract String getProp();

        @JsonIgnore
        public abstract void setProp(String prop);
    }

    // 2.x defaults FAIL_ON_UNKNOWN_PROPERTIES to true; disable so the "not written"
    // outcome (rather than an exception) is what we assert on read paths.
    private final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public void testRenamedGetterStillSerializes() throws Exception
    {
        RenamedGetterIgnoredSetter bean = new RenamedGetterIgnoredSetter();
        bean.setProp("someValue");
        assertEquals("{\"renamedProp\":\"someValue\"}",
                MAPPER.writeValueAsString(bean));
    }

    public void testStandardGetterStillSerializes() throws Exception
    {
        StandardGetterIgnoredSetter bean = new StandardGetterIgnoredSetter();
        bean.setProp("someValue");
        assertEquals("{\"prop\":\"someValue\"}",
                MAPPER.writeValueAsString(bean));
    }

    // The renamed property must be read-only: @JsonIgnore on the setter
    // blocks the write, and the inferred field mutator must not bypass it.
    public void testRenamedIgnoredSetterDoesNotBypassViaField() throws Exception
    {
        RenamedGetterIgnoredSetter result = MAPPER.readValue(
                "{\"renamedProp\":\"someValue\"}",
                RenamedGetterIgnoredSetter.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    public void testStandardIgnoredSetterDoesNotBypassViaField() throws Exception
    {
        StandardGetterIgnoredSetter result = MAPPER.readValue(
                "{\"prop\":\"someValue\"}",
                StandardGetterIgnoredSetter.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    // With default settings (FAIL_ON_UNKNOWN_PROPERTIES enabled) property with
    // ignored setter is to be silently skipped, not written via field
    public void testStandardIgnoredSetterWithDefaultMapper() throws Exception
    {
        StandardGetterIgnoredSetter result = new ObjectMapper().readValue(
                "{\"prop\":\"someValue\"}",
                StandardGetterIgnoredSetter.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    // Updating an existing instance must not overwrite value via field either
    public void testIgnoredSetterDoesNotBypassViaFieldWhenUpdating() throws Exception
    {
        StandardGetterIgnoredSetter bean = new StandardGetterIgnoredSetter();
        bean.setProp("original");
        StandardGetterIgnoredSetter result = MAPPER.readerForUpdating(bean)
                .readValue("{\"prop\":\"someValue\"}");
        assertSame(bean, result);
        assertEquals("original", result.getProp());
    }

    // Same via mix-in annotations
    public void testMixInIgnoredSetterDoesNotBypassViaField() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper()
                .addMixIn(PlainBean.class, PlainBeanMixIn.class)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        PlainBean bean = new PlainBean();
        bean.setProp("someValue");
        assertEquals("{\"prop\":\"someValue\"}", mapper.writeValueAsString(bean));

        PlainBean result = mapper.readValue("{\"prop\":\"someValue\"}",
                PlainBean.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    // Control: feeding the implicit field name (rather than the renamed
    // public name) also does not write the field.
    public void testImplicitFieldNameDoesNotWriteWhenSetterIgnored() throws Exception
    {
        RenamedGetterIgnoredSetter result = MAPPER.readerFor(RenamedGetterIgnoredSetter.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue("{\"prop\":\"someValue\"}");
        assertNotNull(result);
        assertNull(result.getProp());
    }

    // Bypass must also stay closed when INFER_PROPERTY_MUTATORS is disabled,
    // i.e. the fix is not just an artifact of inference being on.
    public void testInferMutatorsDisabledStillBlocksWrite() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper()
                .disable(MapperFeature.INFER_PROPERTY_MUTATORS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        RenamedGetterIgnoredSetter result = mapper.readValue(
                "{\"renamedProp\":\"someValue\"}",
                RenamedGetterIgnoredSetter.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    // Sibling: @JsonProperty(access = READ_ONLY) on the renamed getter must
    // produce the same outcome -- serialize under the new name, not write
    // back via the inferred field mutator.
    public void testReadOnlyRenamedGetterIsSerializeOnly() throws Exception
    {
        ReadOnlyRenamedGetter bean = new ReadOnlyRenamedGetter();
        bean.setProp("someValue");
        assertEquals("{\"renamedProp\":\"someValue\"}",
                MAPPER.writeValueAsString(bean));

        ReadOnlyRenamedGetter result = MAPPER.readValue(
                "{\"renamedProp\":\"someValue\"}",
                ReadOnlyRenamedGetter.class);
        assertNotNull(result);
        assertNull(result.getProp());
    }

    public void testReadOnlyRenameCollisionKeepsOtherPropertyWritable() throws Exception
    {
        ReadOnlyRenameCollision result = MAPPER.readValue("{\"b\":5}",
                ReadOnlyRenameCollision.class);
        assertEquals(5, result.b);
    }

    // Fix for [databind#5967] must not drop field annotations on serialization
    public void testFieldAnnotationsRetainedForSerialization() throws Exception
    {
        AnnotatedFieldIgnoredSetter bean = new AnnotatedFieldIgnoredSetter();
        bean.setProp("");
        assertEquals("{}", MAPPER.writeValueAsString(bean));
        bean.setProp("x");
        assertEquals("{\"prop\":\"x\"}", MAPPER.writeValueAsString(bean));

        AnnotatedFieldIgnoredSetter result = MAPPER.readValue("{\"prop\":\"x\"}",
                AnnotatedFieldIgnoredSetter.class);
        assertNull(result.getProp());
    }
}
