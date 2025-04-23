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

public class Num_someCollection {

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
    LocatedProp subject = LocatedProp.fromJSON(new JSONObject("{\"loc\":{\"result\":{\"external\":false,\"depth\":3,\"start\":0,\"end\":0,\"type\":\"ast.Num\"},\"steps\":[{\"type\":\"child\",\"value\":0},{\"type\":\"child\",\"value\":0},{\"type\":\"child\",\"value\":1}]},\"prop\":{\"name\":\"someCollection\"}}"));
    // Establish reference value on a fresh AST
    EvaluatedValue reference = PropEvaluation.evaluateProp(cfg.reparse(), subject);
    AstInfo info = cfg.reparse();
    // Apply intermediate steps
    PropEvaluation.evaluateProp(info, LocatedProp.fromJSON(new JSONObject("{\"loc\":{\"result\":{\"external\":false,\"depth\":3,\"start\":0,\"end\":0,\"type\":\"ast.Num\"},\"steps\":[{\"type\":\"child\",\"value\":0},{\"type\":\"child\",\"value\":0},{\"type\":\"child\",\"value\":1}]},\"prop\":{\"name\":\"modifyingCollection\"}}")));
    // Re-evaluate on mutated AST
    EvaluatedValue reeval = PropEvaluation.evaluateProp(info, subject);
    assertEquals("Failed Test for Num_someCollection", reference, reeval);
  }

}
