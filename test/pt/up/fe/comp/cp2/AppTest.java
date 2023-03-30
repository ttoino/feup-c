package pt.up.fe.comp.cp2;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsIo;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class AppTest {

    @Test
    public void testHelloWorld() {
        var code = SpecsIo.getResource("pt/up/fe/comp/cp2/apps/HelloWorld.jmm");
        var jasminResult = TestUtils.backend(code, Collections.emptyMap());
        var result = TestUtils.runJasmin(jasminResult.getJasminCode(),Collections.emptyMap());
        assertEquals("Hello, World!",result);
    }

    @Test
    public void testSimple() {
        var code = SpecsIo.getResource("pt/up/fe/comp/cp2/apps/Simple.jmm");
        var jasminResult = TestUtils.backend(code, Collections.emptyMap());
        var result = TestUtils.runJasmin(jasminResult.getJasminCode(),Collections.emptyMap());
        assertEquals("30",result);
    }

}
