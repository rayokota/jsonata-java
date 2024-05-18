package com.dashjoin.jsonata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.dashjoin.jsonata.Jsonata.jsonata;
import com.dashjoin.jsonata.Jsonata.Frame;
import com.dashjoin.jsonata.json.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.dashjoin.jsonata.Jsonata.NULL_VALUE;

@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonataTest {

    boolean testExpr(String expr, Object data, Map<String,Object> bindings,
        Object expected, String code) {
        boolean success = true;
        try {

        if (debug) System.out.println("Expr="+expr+" Expected="+expected+" ErrorCode="+code);
        if (debug) System.out.println(data);

        Frame bindingFrame = null;
        if (bindings!=null) {
            // If we have bindings, create a binding env with the settings
            bindingFrame = new Frame(null);
            for (Entry<String,Object> e : bindings.entrySet()) {
                bindingFrame.bind(e.getKey(), e.getValue());
            }
        }

        Jsonata jsonata = jsonata(expr);
        if (bindingFrame!=null)
            bindingFrame.setRuntimeBounds(debug ? 500000L : 1000L, 303);
        Object result = jsonata.evaluate(data, bindingFrame);
        if (code!=null)
            success = false;
        
        if (expected!=null && !expected.equals(result)) {
            // if ((""+expected).equals(""+result))
            //     System.out.println("Value equals failed, stringified equals = true. Result = "+result);
            // else
                success = false;
        }
    
        if (expected==null && result!=null)
            success = false;

        if (debug && success) System.out.println("--Result = "+result);

        if (!success) {
            System.out.println("--Expr="+expr+" Expected="+expected+" ErrorCode="+code);
            System.out.println("--Data="+data);
            System.out.println("--Result = "+result+" Class="+(result!=null ? result.getClass():null));
            System.out.println("--Expect = "+expected+" ExpectedError="+code);
            System.out.println("WRONG RESULT");
        }

        //assertEquals("Must be equal", expected, ""+result);
        } catch (Throwable t) {
            if (code==null) {
            System.out.println("--Expr="+expr+" Expected="+expected+" ErrorCode="+code);
            System.out.println("--Data="+data);

                if (t instanceof JException) {
                    JException je = (JException)t;
                    System.out.println("--Exception     = "+je.getError()+"  --> "+je);
                } else
                    System.out.println("--Exception     = "+t);

                System.out.println("--ExpectedError = "+code+" Expected="+expected);
                System.out.println("WRONG RESULT (exception)");
                success = false;
            }
            if (!success) t.printStackTrace(System.out);
            if (debug && success) System.out.println("--Exception = "+t);
            //if (true) System.exit(-1);
        }
        return success;
    }

    static ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
    ObjectMapper getObjectMapper() {
        return om;
    }

    Object toJson(String jsonStr) throws JsonMappingException, JsonProcessingException {
        //ObjectMapper om = getObjectMapper();
        //Object json = om.readValue(jsonStr, Object.class);
        Object json = Json.parseJson(jsonStr);
        return json;
    }

    Object readJson(String name) throws StreamReadException, DatabindException, IOException {
        //ObjectMapper om = getObjectMapper();
        //Object json = om.readValue(new java.io.FileReader(name, Charset.forName("UTF-8")), Object.class);

        Object json = Json.parseJson(new java.io.FileReader(name, Charset.forName("UTF-8")));
        return json;
    }

    @Test
    public void testSimple() {
        testExpr("42", null, null, 42,null);
        testExpr("(3*(4-2)+1.01e2)/-2", null, null, -53.5,null);
    }

    @Test
    public void testPath() throws Exception {
        Object data = readJson("jsonata/test/test-suite/datasets/dataset0.json");
        System.out.println(data);
        testExpr("foo.bar", data, null, 42,null);
    }

    static class TestDef {
        String expr;
        String dataset;
        Object bindings;
        Object result;
    }

    int testFiles = 0;
    int testCases = 0;

    public void runCase(String name) throws Exception {
      if (!runTestSuite(name))
          throw new Exception();
    }

    public void runSubCase(String name, int subNr) throws Exception {
        List cases = (List)readJson(name);
        if (!runTestCase(name+"_"+subNr, (Map<String, Object>) cases.get(subNr)))
            throw new Exception();
    }
    
    boolean runTestSuite(String name) throws Exception {

        //System.out.println("Running test "+name);
        testFiles++;

        boolean success = true;

        Object testCase = readJson(name);
        if (testCase instanceof List) {
            // some cases contain a list of test cases
            // loop over the case definitions
            for (Object testDef : ((List)testCase)) {
                System.out.println("Running sub-test");
                success &= runTestCase(name, (Map<String, Object>) testDef);
            }
        } else {
            success &= runTestCase(name, (Map<String, Object>) testCase);
        }
        return success;
    }

    void replaceNulls(Object o) {
      if (o instanceof List) {
        int index = 0;
        for (Object i : ((List) o)) {
          if (i == null)
            ((List) o).set(index, Jsonata.NULL_VALUE);
          else
            replaceNulls(i);
          index++;
        }
      }
      if (o instanceof Map) {
        for (Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
          if (e.getValue() == null)
            e.setValue(Jsonata.NULL_VALUE);
          else
            replaceNulls(e.getValue());
        }
      }
    }
    
    public static class TestOverride {
        public String name;
        public Boolean ignoreError;
        public Object alternateResult;
        public String alternateCode;
        public String reason;
    }

    public static class TestOverrides {
        public TestOverride[] override;
    }

    static TestOverrides testOverrides;

    static TestOverrides getTestOverrides() {
        if (testOverrides!=null)
            return testOverrides;

        try {
            testOverrides = new ObjectMapper().readValue(
                new File("test/test-overrides.json"), TestOverrides.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return testOverrides;
    }

    TestOverride getOverrideForTest(String name) {
        if (ignoreOverrides) return null;

        TestOverrides tos = getTestOverrides();
        for (TestOverride to : tos.override) {
            if (name.indexOf(to.name)>=0)
                return to;
        }
        return null;
    }

    boolean runTestCase(String name, Map<String, Object> testDef) throws Exception {

        testCases++;
        if (debug) System.out.println("\nRunning test "+name);

        String expr = (String)testDef.get("expr");

        if (expr==null) {
            String exprFile = (String)testDef.get("expr-file");
            String fileName = name.substring(0, name.lastIndexOf("/")) + "/" + exprFile;
            expr = IOUtils.toString(new FileInputStream(fileName));
        }

        String dataset = (String)testDef.get("dataset");
        Map<String,Object> bindings = (Map)testDef.get("bindings");
        Object result = testDef.get("result");
        
        // if (result == null)
        //   if (testDef.containsKey("result"))
        //     result = Jsonata.NULL_VALUE;

        //replaceNulls(result);

        String code = (String)testDef.get("code");
        
        if (testDef.get("error") instanceof Map)
          code = (String) ((Map)testDef.get("error")).get("code");

        //System.out.println(""+bindings);

        Object data = testDef.get("data");
        if (data==null && dataset!=null)
            data = readJson("jsonata/test/test-suite/datasets/"+dataset+".json");

        TestOverride to = getOverrideForTest(name);
        if (to!=null) {
            System.out.println("OVERRIDE used : "+to.name+" for "+name+" reason="+to.reason);
            if (to.alternateResult!=null) {
                result = to.alternateResult;
            }
            if (to.alternateCode!=null) {
                code = to.alternateCode;
            }
        }
        boolean res;
        if (debug && expr.equals("(  $inf := function(){$inf()};  $inf())")) {
            System.err.println("DEBUG MODE: skipping infinity test: "+expr);
            res = true;
        }
        else
            res = testExpr(expr, data, bindings, result, code);

        if (to!=null) {
            // There is an override/alternate result for this defined...
            if (res==false && to.ignoreError!=null && to.ignoreError) {
                System.out.println("Test "+name+" failed, but override allows failure");
                res = true;
            }
        }

        return res;
    }

    String groupDir = "jsonata/test/test-suite/groups/";

    boolean runTestGroup(String group) throws Exception {
        
        File dir = new File(groupDir, group);
        System.out.println("Run group "+dir);
        File[] files = dir.listFiles();
        Arrays.sort(files);
        boolean success = true;
        int count = 0, good = 0;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".json")) {
                boolean res = runTestSuite(groupDir+group+"/"+name);
                success &= res;

                count++;
                if (res)
                    good++;
            }
        }
        int successPercentage = 100*good/count;
        System.out.println("Success: "+good+" / "+count+" = "+(100*good/count)+"%");
        assertEquals(count, good, successPercentage+"% succeeded");
        //assertEquals("100% test runs must succeed", 100, successPercentage);
        return success;
    }

    boolean debug = java.lang.management.ManagementFactory.getRuntimeMXBean().
        getInputArguments().toString().contains("-Xrunjdwp:transport");

    boolean ignoreOverrides = false;

    // For local dev: @Test
    public void testSuite() throws Exception {
        //runTestSuite("jsonata/test/test-suite/groups/boolean-expresssions/test.jsonx");
        //runTestSuite("jsonata/test/test-suite/groups/boolean-expresssions/case017.json");
        //runTestSuite("jsonata/test/test-suite/groups/fields/case000.json");
        //runTestGroup("fields");
        //runTestGroup("comments");
        //runTestGroup("comparison-operators");
        //runTestGroup("boolean-expresssions");
        //runTestGroup("array-constructor");
        //runTestGroup("transform");
        //runTestGroup("function-substring");
        //runTestGroup("wildcards");
        //runTestSuite("jsonata/test/test-suite/groups/function-substring/case012.json");
        //runTestSuite("jsonata/test/test-suite/groups/transform/case030.json");
        //runTestSuite("jsonata/test/test-suite/groups/array-constructor/case006.json");
        // Filter:
        //runTestSuite("jsonata/test/test-suite/groups/array-constructor/case017.json");
        String s = "jsonata/test/test-suite/groups/wildcards/case003.json";
        s = "jsonata/test/test-suite/groups/flattening/large.json";
        s = "jsonata/test/test-suite/groups/function-sum/case006.json";
        s = "jsonata/test/test-suite/groups/function-substring/case016.json";
        s = "jsonata/test/test-suite/groups/null/case001.json";
        s = "jsonata/test/test-suite/groups/context/case003.json";
        s = "jsonata/test/test-suite/groups/object-constructor/case008.json";
        runTestSuite(s);
        //String g = "function-applications"; // partly
        //String g = "higher-order-functions"; // works!
        //String g = "hof-map";
        //String g = "joins";
        //String g = "function-join"; // looks good
        //String g = "descendent-operator"; // nearly
        //String g = "object-constructor";
        //String g = "flattening";
        //String g = "parent-operator";
        //String g = "function-substring"; // nearly - unicode encoding issues
        //String g = "function-substringBefore"; // works!
        //String g = "function-substringAfter"; // works!
        //String g = "function-sum"; // works! rounding error delta
        //String g = "function-max"; // nearly - [-1,-5] second unary wrong!!!
        //String g = "function-average"; // nearly - [-1,-5] second unary wrong!!!
        //String g = "function-pad"; // nearly - unicode
        //String g = "function-trim"; // works!
        //String g = "function-contains"; // works NO regexp
        //String g = "function-join"; // works NO regexp
        //runTestGroup(g);

        //runAllTestGroups();
    }

    void runAllTestGroups() throws Exception {
        File dir = new File(groupDir);
        File[] groups = dir.listFiles();
        Arrays.sort(groups);
        for (File g : groups) {
            String name = g.getName();
            System.out.println("@Test");
            System.out.println("public void runTestGroup_"+name.replaceAll("-","_")+"() {");
            System.out.println("\trunTestGroup(\""+name+"\");");
            System.out.println("}");
            //runTestGroup(name);
        }

        System.out.println("Total test files="+testFiles+" cases="+testCases);
    }
}
