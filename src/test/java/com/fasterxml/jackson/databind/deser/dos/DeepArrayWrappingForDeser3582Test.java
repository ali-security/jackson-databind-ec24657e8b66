package com.fasterxml.jackson.databind.deser.dos;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class DeepArrayWrappingForDeser3582Test extends BaseMapTest
{
    // 23-Aug-2022, tatu: Before fix, failed with 5000
    private final static int TOO_DEEP_NESTING = 9999;
    private final static int NOT_TOO_DEEP_NESTING = 9;

    private final ObjectMapper MAPPER = newObjectMapper()
            .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);

    public void testArrayWrapping() throws Exception
    {
        final String doc = _nestedDoc(TOO_DEEP_NESTING, "[ ", "] ", "{}");
        try {
            MAPPER.readValue(doc, Point.class);
            fail("Should not pass");
        } catch (MismatchedInputException e) {
            verifyException(e, "Cannot deserialize");
            verifyException(e, "nested Array");
        }
    }

    public void testArrayWrappingPass() throws Exception
    {
        final String doc = _nestedDoc(NOT_TOO_DEEP_NESTING, "[ ", "] ", "{}");
        try {
            MAPPER.readValue(doc, Point.class);
        } catch (MismatchedInputException e) {
            fail("Should not throw exception");
        }
    }

    private String _nestedDoc(int nesting, String open, String close, String content) {
        StringBuilder sb = new StringBuilder(nesting * (open.length() + close.length()));
        for (int i = 0; i < nesting; ++i) {
            sb.append(open);
            if ((i & 31) == 0) {
                sb.append("\n");
            }
        }
        sb.append("\n").append(content).append("\n");
        for (int i = 0; i < nesting; ++i) {
            sb.append(close);
            if ((i & 31) == 0) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}
