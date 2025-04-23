package tragdor;

import tragdor.config.UserConfig;
import tragdor.EvaluatedValue;
import tragdor.PropEvaluation;
import tragdor.LocatedProp;
import codeprober.AstInfo;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class Program_literalValues {

  @Test
  public void run() throws IOException {
    final java.io.ByteArrayOutputStream configLoader = new java.io.ByteArrayOutputStream();
    final byte[] buf = new byte[512];
    int readBytes;
    try (java.io.InputStream src = getClass().getResourceAsStream("config.json")) {
      while ((readBytes = src.read(buf)) != -1) { configLoader.write(buf, 0, readBytes); }
    }
    final byte[] configBytes = configLoader.toByteArray();
    UserConfig cfg = new UserConfig(new JSONObject(new String(configBytes, 0, configBytes.length, java.nio.charset.StandardCharsets.UTF_8)));
    LocatedProp subject = LocatedProp.fromJSON(new JSONObject("{\"loc\":{\"result\":{\"external\":false,\"depth\":0,\"start\":0,\"end\":0,\"type\":\"ast.Program\"},\"steps\":[]},\"prop\":{\"name\":\"literalValues\"}}"));
    // Establish reference value on a fresh AST
    EvaluatedValue reference = PropEvaluation.evaluateProp(cfg.reparse(), subject);
    // Repeatedly re-invoke to detect flakyness
    for (int repeat = 0; repeat < 16; ++repeat) {
      EvaluatedValue reeval = PropEvaluation.evaluateProp(cfg.reparse(), subject);
      assertEquals("Failed test for Program_literalValues", reference, reeval);
    }
    // It passed 16 iterations. This is unfortunately no guarantee of non-flakyness,
    // but it is very likely to be OK. Increase '16' to get higher confidence.
  }

}
