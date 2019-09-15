package ai.h2o.mojos.db;

import org.junit.Test;

import static org.junit.Assert.*;

public class SQLCommandConfigTest {

  @Test
  public void testLoad() {
    SQLCommandConfig cfg = new SQLCommandConfig.Builder().build();
    assertEquals("SQLCommandConfig{modelFile='pipeline.mojo', sqlConnectionString='', sqlUser='postgres', sqlPassword='', sqlPrompt='false', sqlSelectStatement='', sqlWriteStatement='', sqlWriteFormat=CSV, sqlKey='id', sqlPredictionCol='', sqlSavePrediction=0, sqlFieldSeparator=','}",
                 cfg.toString());
  }
}