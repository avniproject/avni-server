package org.avni.server.framework.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.avni.server.util.ObjectMapperSingleton;
import org.junit.Test;
import org.owasp.encoder.Encode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JsonEncoderTest {
    private SampleObject parseJson(String json) throws JsonProcessingException {
        return ObjectMapperSingleton.getObjectMapper().readValue(json, SampleObject.class);
    }

    @Test
    public void encodeNothing() throws JsonProcessingException {
        SampleObject sampleObject = new SampleObject(1, "abc", Arrays.asList(new SampleObject2(2, "xyz")), new SampleObject2(3, "hsd"));
        SampleObject encoded = parseJson(JsonEncoder.encode(sampleObject).toString());
        assertEquals(sampleObject.a, encoded.a);
        assertEquals(sampleObject.b, encoded.b);
        assertEquals(sampleObject.twos.size(), encoded.twos.size());
        assertEquals(sampleObject.twos.get(0).x, encoded.twos.get(0).x);
        assertEquals(sampleObject.twos.get(0).y, encoded.twos.get(0).y);
        assertEquals(sampleObject.sampleObject2.x, encoded.sampleObject2.x);
        assertEquals(sampleObject.sampleObject2.y, encoded.sampleObject2.y);
    }

    @Test
    public void encode() throws JsonProcessingException {
        String htmlString = "<alert>abc</alert>";
        SampleObject sampleObject = new SampleObject(1, htmlString, Arrays.asList(new SampleObject2(2, htmlString)), new SampleObject2(3, htmlString));
        SampleObject encoded = parseJson(JsonEncoder.encode(sampleObject).toString());
        assertEquals(sampleObject.a, encoded.a);
        assertEquals(Encode.forHtml(sampleObject.b), encoded.b);
        assertEquals(sampleObject.twos.size(), encoded.twos.size());
        assertEquals(sampleObject.twos.get(0).x, encoded.twos.get(0).x);
        assertEquals(Encode.forHtml(sampleObject.twos.get(0).y), encoded.twos.get(0).y);
        assertEquals(sampleObject.sampleObject2.x, encoded.sampleObject2.x);
        assertEquals(Encode.forHtml(sampleObject.sampleObject2.y), encoded.sampleObject2.y);
    }

    public static class SampleObject {
        private int a;
        private String b;
        private List<SampleObject2> twos;
        private SampleObject2 sampleObject2;

        public SampleObject() {
        }

        public SampleObject(int a, String b, List<SampleObject2> twos, SampleObject2 sampleObject2) {
            this.a = a;
            this.b = b;
            this.twos = twos;
            this.sampleObject2 = sampleObject2;
        }

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public List<SampleObject2> getTwos() {
            return twos;
        }

        public void setTwos(List<SampleObject2> twos) {
            this.twos = twos;
        }

        public SampleObject2 getSampleObject2() {
            return sampleObject2;
        }

        public void setSampleObject2(SampleObject2 sampleObject2) {
            this.sampleObject2 = sampleObject2;
        }
    }

    public static class SampleObject2 {
        private int x;
        private String y;

        public SampleObject2() {
        }

        public SampleObject2(int x, String y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public String getY() {
            return y;
        }

        public void setY(String y) {
            this.y = y;
        }
    }
}
