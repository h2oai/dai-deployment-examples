package ai.h2o.mojos.db;

import com.typesafe.config.ConfigFactory;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.readers.MojoPipelineReaderBackendFactory;

import static org.junit.Assert.*;
import static ai.h2o.mojos.db.Utils.f;
import static ai.h2o.mojos.db.Utils.join;

public class WorkerTest {
  private static SQLCommandConfig cfgSingle;
  private static SQLCommandConfig cfgMulti;
  private static MojoPipeline model;
  private BlockingQueue<String> queue;
  private Args args;

  static final String[] IRIS_DATA = new String[] {
     "6.7,2.5,5.8,1.8",
     "7.2,3.6,6.1,2.5",
     "6.5,3.2,5.1,2.0",
     "6.4,2.7,5.3,1.9",
     "6.8,3.0,5.5,2.1",
  };

  @BeforeClass
  public static void beforeAll() throws Exception {
    cfgSingle = new SQLCommandConfig(ConfigFactory.load("worker_test_update_single").getConfig("mojo-db-scoring-app"));
    cfgMulti = new SQLCommandConfig(ConfigFactory.load("worker_test_update_multi").getConfig("mojo-db-scoring-app"));
    model = MojoPipeline.loadFrom(
        MojoPipelineReaderBackendFactory.createReaderBackend(
            ClassLoader.getSystemResourceAsStream("pipeline.mojo")));
    fillH2();
  }

  @Before
  public void before() throws Exception {
    queue = new ArrayBlockingQueue<>(IRIS_DATA.length+1);
    for (int i = 0; i < IRIS_DATA.length; i++) {
      queue.put(f("%d,%s", i, IRIS_DATA[i]));
    }
    queue.put(Worker.POISON_PILL);
    args = new Args();
    args.stats = true;
  }

  @Test
  public void testUpdateSingle() throws Exception {
    Worker w = new Worker(queue, model, cfgSingle, args);
    w.run();

    List<double[]> actPred = getPredictions(cfgSingle, new String[] {"prediction"}, "iris_table_single");
    assertTrue(actPred.stream().allMatch(p -> p[0] > 0));
  }

  @Test
  public void testUpdateMulti() throws Exception {
    Worker w = new Worker(queue, model, cfgMulti, args);
    w.run();

    List<double[]> actPred = getPredictions(cfgMulti,
                                            new String[] {"species_Iris_setosa", "species_Iris_versicolor", "species_Iris_virginica"},
                                            "iris_table_multi");
    assertTrue(actPred.stream().allMatch(p -> p[0] > 0 && p[1] > 0 && p[2] > 0));
  }

  static List<double[]> getPredictions(SQLCommandConfig cfg, String[] selectCol, String table) throws SQLException {
    try(Connection c = DriverManager.getConnection(cfg.dbConnectionString, cfg.dbUser, cfg.dbPassword)) {
      try (Statement st = c.createStatement()) {
        ResultSet rs = st.executeQuery(f("select %s from %s", join(selectCol, ","), table));
        List<double[]> result = new LinkedList<>();
        while (rs.next()) {
          double[] res = new double[selectCol.length];
          for (int i = 0; i < selectCol.length; i++) res[i] = rs.getDouble(selectCol[i]);
          result.add(res);
        }
        return result;
      }
    }
  }

  static void fillH2() throws SQLException {
    // Fill H2
    try(Connection c = DriverManager.getConnection(cfgSingle.dbConnectionString, cfgSingle.dbUser, cfgSingle.dbPassword)) {
      try (Statement st = c.createStatement()) {
        // Simple input data
        st.execute("drop table if exists iris_table_single");
        st.execute("drop table if exists iris_table_multi");

        st.execute("create table iris_table_single "
                   + "(id int primary key, sepal_len double, sepal_wid double, petal_len double, petal_wid double, "
                   + "prediction double)");
        st.execute("create table iris_table_multi "
                   + "(id int primary key, sepal_len double, sepal_wid double, petal_len double, petal_wid double, "
                   + "species_Iris_setosa double, species_Iris_versicolor double, species_Iris_virginica double)");

        for (String t : new String[] {"iris_table_single", "iris_table_multi"}) {
          for (int i = 0; i < IRIS_DATA.length; i++) {
            st.execute(
                f(
                  "insert into %s (id,sepal_len,sepal_wid,petal_len,petal_wid) values(%d,%s)",
                  t,
                  i,
                  IRIS_DATA[i]
                )
            );
          }
        }
      }
    }
  }
}