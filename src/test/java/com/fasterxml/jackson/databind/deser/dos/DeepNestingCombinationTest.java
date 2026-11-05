package com.fasterxml.jackson.databind.deser.dos;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.annotation.JsonRootName;

// [databind#3397], wrt JsonNode
@JsonRootName("rootNode")
public class DeepNestingCombinationTest extends BaseMapTest
{
    public String a;
    public int b;
    public DeepNestingCombinationTest c;
    private final static int TOO_DEEP_NESTING = 15;
    private final static int NOT_TOO_DEEP_NESTING = 8;
    private final String docTemplate = "%s{\"a\":\1.5, \"b\":%s5%s, \"c\":%s{\"a\":\"as\", \"b\":2, \"c\":null}%s}%s";
    private final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);

    public void testObjectWithArray() throws Exception
    {
        String doc = fillTemplate(NOT_TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING);
        DeepNestingCombinationTest n = MAPPER.readValue(doc, DeepNestingCombinationTest.class);
        assertEquals(n.a, "asd");
        assertEquals(n.b, 5);
        assertEquals(n.c.a, "as");
        assertEquals(n.c.b, 2);
    }

    public void testObjectWithArrayFail() throws Exception
    {
      try {
        String doc = fillTemplate(TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING);
        DeepNestingCombinationTest n = MAPPER.readValue(doc, DeepNestingCombinationTest.class);
        fail("Should not pass");
      } catch (Exception e) {
            verifyException(e, "Cannot deserialize");
            verifyException(e, "nested Arrays");
      }
    }

    public void testObjectWithPrimitiveFail() throws Exception
    {
      try {
        String doc = fillTemplate(NOT_TOO_DEEP_NESTING, TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING);
        DeepNestingCombinationTest n = MAPPER.readValue(doc, DeepNestingCombinationTest.class);
        fail("Should not pass");
      } catch (Exception e) {
            verifyException(e, "Cannot deserialize");
            verifyException(e, "nested Arrays");
      }
    }

    public void testObjectWithInObjectFail() throws Exception
    {
      try {
        String doc = fillTemplate(NOT_TOO_DEEP_NESTING, NOT_TOO_DEEP_NESTING, TOO_DEEP_NESTING);
        DeepNestingCombinationTest n = MAPPER.readValue(doc, DeepNestingCombinationTest.class);
        fail("Should not pass");
      } catch (Exception e) {
            verifyException(e, "Cannot deserialize");
            verifyException(e, "nested Arrays");
      }
    }

    private static String fillTemplate(int root, int primitive, int object) {
        return String.format("%s{\"a\":\"asd\", \"b\":%s5%s, \"c\":%s{\"a\":\"as\", \"b\":2, \"c\":null}%s}%s",
            repeat(root, "["),
            repeat(primitive, "["),
            repeat(primitive, "]"), repeat(object, "["),
            repeat(object, "]"), repeat(root, "]"));
    }

    private static String repeat(int n, String s) {
        StringBuilder sb = new StringBuilder(n * s.length());
        for (int i = 0; i < n; ++i) { sb.append(s); }
        return sb.toString();
    }
}