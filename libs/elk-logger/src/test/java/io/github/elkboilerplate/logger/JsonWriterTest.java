package io.github.elkboilerplate.logger;

import io.github.elkboilerplate.logger.internal.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void escapesSpecialCharacters() {
        JsonWriter w = new JsonWriter(16);
        w.beginObject().field("m", "a\"b\\c\nd\te\u0001").endObject();
        assertEquals("{\"m\":\"a\\\"b\\\\c\\nd\\te\\u0001\"}", w.toString());
    }

    @Test
    void writesNestedStructures() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", 1);
        m.put("d", 2.5);
        m.put("b", true);
        m.put("l", Arrays.asList("x", null));
        m.put("nan", Double.NaN);
        m.put("turkce", "çğıöşü ÇĞİÖŞÜ");
        JsonWriter w = new JsonWriter(16);
        w.beginObject().name("f").value(m).field("z", 3).endObject();
        assertEquals("{\"f\":{\"n\":1,\"d\":2.5,\"b\":true,\"l\":[\"x\",null],\"nan\":\"NaN\",\"turkce\":\"çğıöşü ÇĞİÖŞÜ\"},\"z\":3}", w.toString());
    }
}
