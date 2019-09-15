/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ai.h2o.mojos.db;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import ai.h2o.mojos.runtime.MojoPipeline;
import picocli.CommandLine;

import static ai.h2o.mojos.db.Utils.createConnection;
import static ai.h2o.mojos.db.Utils.f;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "dbscorer")
public class MojoDbScorer extends Args implements Callable<Integer> {

  static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MojoDbScorer.class);

  public static void main(String[] args) {
    int exitCode = new CommandLine(new MojoDbScorer()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    if (verbose) {
      LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
      ctx.getRootLogger().setLevel(Level.DEBUG);
    }
    // Switch on verbose if necessary
    SQLCommandConfig cfg = new SQLCommandConfig.Builder().loadFrom(configFile).build();

    if (cfg.dbPasswordPrompt) {
      System.out.print(f("Please enter JDBC password for user %s:", cfg.dbUser));
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(System.in));
      cfg.dbPassword = reader.readLine();
    }

    if (!cfg.modelFile.exists()) {
      System.out.println("Cannot locate model zip file " + cfg.modelFile);
      return -1;
    }
    MojoPipeline model = MojoPipeline.loadFrom(cfg.modelFile.getAbsolutePath());
    LOGGER.debug("Using model file: file={}", cfg.modelFile);

    if (capacity <= 0) {
      capacity = (int) Math.round((numWorkers + (numWorkers * 0.75)));
    }
    LOGGER.debug("TQ: capacity={}, numWorkers={}", capacity, numWorkers);

    if (inspect) {
      dumpInspect(cfg.modelFile, model);
      return 0;
    }

    // Create a set of worker threads
    BlockingQueue<String> queue = new ArrayBlockingQueue<String>(capacity);
    Thread[] workers = new Thread[numWorkers];
    for (int i = 0; i < workers.length; i++) {
      (workers[i] = new Thread(new Worker(queue, model, cfg, this), "DB-Scorer-Worker-"+i)).start();
    }

    try (Connection connection = createConnection(cfg, LOGGER)) {
      try (Statement statement = connection.createStatement()) {

        ResultSet resultSet = statement.executeQuery(cfg.sqlSelectStatement);
        ResultSetMetaData rsmd = resultSet.getMetaData();
        int columnsNumber = rsmd.getColumnCount();
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Reading ResultSet: sqlSelect={}, colNames",
                       cfg.sqlSelectStatement,
                       IntStream.range(0, columnsNumber)
                           .mapToObj(i -> getColName(rsmd, i)).toArray());
        }

        // for CSV output write header
        if (cfg.sqlWriteFormat == SQLCommandConfig.ExportFormat.CSV) {
          System.out.print(cfg.sqlKey + ",");
          for (int i = 0; i < model.getOutputMeta().size(); i++) {
            System.out.print(model.getOutputMeta().getColumnName(i));
            if (model.getOutputMeta().size() > i + 1) {
              System.out.print(",");
            }
          }
          System.out.println();
        }

        String resultSetRow = "";
        int rowsSelected = 0;

        if (wait) {
          System.out.println("Model Ready... press Enter to start ");
          Scanner scanner = new Scanner(System.in);
          scanner.nextLine();
          System.out.println("Running....");
        }
        while (resultSet.next()) {
          for (int i = 1; i <= columnsNumber; i++) {
            if (i > 1) {
              resultSetRow =
                  resultSetRow + "" + cfg.sqlFieldSeparator + resultSet.getString(i);
            } else {
              resultSetRow = resultSet.getString(i);
            }
          }
          queue.put(resultSetRow);
          rowsSelected++;
        }

        if (stats) {
            LOGGER.info("Total selected rows: {}", rowsSelected);
        }
      }
    }

    // Add special end-of-stream markers to terminate the workers
    for (int i = 0; i < workers.length; i++) {
      queue.put(Worker.POISON_PILL);
    }
    // Wait for all to finish
    for (int i = 0; i < workers.length; i++) {
      workers[i].join();
    }

    return 0;
  }

  void dumpInspect(File modelFile, MojoPipeline model) {
    System.out.println("Details of Model: " + modelFile.getAbsolutePath());
    System.out.println("UUID: " + model.getUuid());
    System.out.println("Input Features");
    String select = "";
    for (int i = 0; i < model.getInputMeta().size(); i++) {
      System.out.println(
          i + " =  Name: " + model.getInputMeta().getColumnName(i) + " Type: " + model
              .getInputMeta().getColumnType(i));
      if (select.length() > 1) {
        select = select + ", " + model.getInputMeta().getColumnName(i);
      } else {
        select = model.getInputMeta().getColumnName(i);
      }
    }
    System.out.println("Output Features");
    for (int i = 0; i < model.getOutputMeta().size(); i++) {
      System.out.println(
          i + " = Name: " + model.getOutputMeta().getColumnName(i) + " Type: " + model
              .getOutputMeta().getColumnType(i));
    }

    System.out.println("Suggested configuration for properties file:");
    System.out.println("\nselect <add-table-index>, " + select + " from <add-table-name>");
    System.out.println("\nupdate <add-table-name> set where <add-table-index>=");

    System.out.println(
        "\nChange the values in <> above and manually test before using them in the program.");

    long memfree = Runtime.getRuntime().freeMemory();
    long
        memorySize =
        ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
            .getTotalPhysicalMemorySize();
    System.out.println("\nThe System has " + Math.round(memorySize / 1073741824)
                       + "GB available physically. This program is using " + Math
                           .round(memfree / 1073741824)
                       + "GB Consider adjusting -Xms and -Xmx to no more than " + Math
                           .round((memorySize / 1073741824) * 0.75) + "GB");
    System.out
        .println("The System has " + Runtime.getRuntime().availableProcessors() + " Processors.");
  }

  private String getColName(ResultSetMetaData rsmd, int index) {
    try {
      return rsmd.getColumnName(index);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}

class Args {

  @Option(names = {"-C", "--config"}, description = "Configuration file.", required = true)
  File configFile;

  @Option(names = {"-v", "--verbose"}, description = "Verbose output.")
  boolean verbose = false;

  @Option(names = {"-w", "--wait"}, description = "Wait.")
  boolean wait = false;

  @Option(names = {"-s", "--stats"}, description = "Print statistics.")
  boolean stats = false;

  @Option(names = {"-i", "--inspect"}, description = "Inspect.")
  boolean inspect = false;

  @Option(names = {"-c", "--capacity"}, description = "Capacity.")
  int capacity = -1;

  @Option(names = {"-n", "--num_workers"}, description = "Number of workers.")
  int numWorkers = Runtime.getRuntime().availableProcessors();

  @Option(names = {"-e", "--data_errors"}, description = "Print errors in data.")
  boolean logDataErrors = false;

}
