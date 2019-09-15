package ai.h2o.mojos.db;

import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;

import static ai.h2o.mojos.db.Utils.createConnection;
import static ai.h2o.mojos.db.Utils.isEmpty;

public class Worker implements Runnable {

  static final String POISON_PILL = "__No_More_Work__";

  static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Worker.class);

  private final MojoPipeline model;
  private final BlockingQueue<String> q;
  private final SQLCommandConfig cmdConfig;
  private final Args args;
  
  private int numRowsScored = 0;
  private int numRowsRead = 0;
  private int numRowError = 0;

  Worker(BlockingQueue<String> q, MojoPipeline model, SQLCommandConfig cmdConfig, Args args) {
    this.q = q;
    this.model = model;
    this.cmdConfig = cmdConfig;
    this.args = args;
  }

  public void run() {
    MojoFrameBuilder frameBuilder = model.getInputFrameBuilder();
    MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();
    MojoFrame iframe, oframe;

    String[] features = model.getInputMeta().getColumnNames();

    StopWatch timer = new StopWatch();
    try (Connection connection = createConnection(cmdConfig, LOGGER)) {
      try (Statement statement = createStatement(connection)) {
        while (true) {
          String row;
          // Take the row to score from the queue
          try(StopWatch.Lap lapTakeRow = timer.startLap("Get row from queue")) {
            row = q.take();
            numRowsRead++;
          } catch (InterruptedException ex) {
            // Somebody signalled interupt, finish the work
            break;
          }
          LOGGER.debug("Processing row: row={}", row);
          LOGGER.debug("Target update column: sqlPredictionCol={}", cmdConfig.sqlPredictionCol);

          // Terminate if the end-of-stream marker was retrieved
          if (row.equals(POISON_PILL)) {
            numRowsRead--;
            if (args.stats) {
              LOGGER.info("Rows statistics: read={}, scored={}, errors={}, queue length={}",
                          numRowsRead, numRowsScored, numRowError, q.size());
            }
            break;
          }

          // Fill row builder
          String rowId;
          try(StopWatch.Lap lap = timer.startLap("Row parsing")) {
            String[] fileline = row.split(cmdConfig.sqlFieldSeparator);
            rowId = fileline[0];
            
            if (!parseRow(row, fileline, features, rowBuilder))
              continue;
          }

          // Make a prediction
          try(StopWatch.Lap lap = timer.startLap("MOJO prediction")) {
            frameBuilder = model.getInputFrameBuilder();
            frameBuilder.addRow(rowBuilder);
            iframe = frameBuilder.toMojoFrame();
            oframe = model.transform(iframe);
          }

          String[] prediction = null;
          // Single prediction
          String result = row + " ";
          // Multi prediction
          String resultM = "";

          try (StopWatch.Lap lap = timer.startLap("Preparing output statement")) {
            if (!isEmpty(cmdConfig.sqlPredictionCol)) {
              prediction = oframe.getColumn(cmdConfig.sqlSavePrediction).getDataAsStrings();
              result = prediction[0];
            } else {
              for (int r = 0; r < oframe.getNcols(); r++) {
                if (cmdConfig.sqlWriteFormat != SQLCommandConfig.ExportFormat.CSV) {
                  // if not writing CSV do not format the data
                  resultM = resultM + "" + normalizeColumnName(oframe.getColumnName(r)) + "=";
                }
                prediction = oframe.getColumn(r).getDataAsStrings();
                for (int a = 0; a < prediction.length; a++) {
                  if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.CSV) {
                    resultM = resultM + prediction[a];
                  } else {
                    resultM = resultM + "'" + prediction[a] + "'";
                  }
                  if (r < oframe.getNcols() - 1) {
                    resultM = resultM + ",";
                  }
                }
              }
            }
          }
          
          LOGGER.debug("Update single field: field={}, result={}", cmdConfig.sqlSavePrediction, result);
          LOGGER.debug("Update multiple fields: result={}", resultM);

          String sqlUpdateStm = cmdConfig.sqlWriteStatement
              .replaceAll("@KEY@", cmdConfig.sqlKey)
              .replaceAll("@ROWID@","'" + rowId + "'");

          try (StopWatch.Lap lap = timer.startLap("Preparing output statement #2")) {

            if (isEmpty(cmdConfig.sqlPredictionCol)) {
              if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.UPDATE) {
                sqlUpdateStm = sqlUpdateStm
                    .replaceFirst("@RESULT@", resultM);
              } else if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.INSERT) {
                resultM = resultM.replaceAll("\\\"|'|", "");
                String[] resultdata = resultM.split("=| |,");
                String colnames = "";
                String colvalues = "";
                if (cmdConfig.sqlKey.length() != 0) {
                  colnames = "\"" + cmdConfig.sqlKey + "\"";
                  colvalues = "'" + rowId + "'";
                }
                for (int i = 0; i < resultdata.length; i = i + 2) {
                  if (colnames.length() > 1) {
                    colnames = colnames + ",\"" + resultdata[i] + "\"";
                  } else {
                    colnames = "\"" + resultdata[i] + "\"";
                  }
                  if (colvalues.length() > 0) {
                    colvalues = colvalues + ",'" + resultdata[i + 1] + "'";
                  } else {
                    colvalues = "'" + resultdata[i + 1] + "'";
                  }
                }
                sqlUpdateStm = sqlUpdateStm
                    .replaceFirst("@COLNAMES@", colnames)
                    .replaceFirst("@COLVALUES@", colvalues);
              } else if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.CSV) {
                sqlUpdateStm = rowId + "," + resultM;
              }
            } else {
              if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.UPDATE) {
                sqlUpdateStm =
                    sqlUpdateStm
                        .replaceFirst( "@PREDICTION_COL@", cmdConfig.sqlPredictionCol)
                        .replaceFirst("@PREDICTION@", "'" + result + "'");
              } else if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.INSERT) {
                sqlUpdateStm =
                    sqlUpdateStm
                        .replaceFirst("@PREDICTION@", result);
              }
              if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.CSV) {
                // save just the value for the CSV updates
                sqlUpdateStm = rowId + "," + result;
              }
              LOGGER.debug("Prepared result: row: {}, result {}", row, result);
            }
          }

          LOGGER.debug("Updating record: {}", sqlUpdateStm);

          try(StopWatch.Lap lap = timer.startLap("Updating DB with prediction")) {
            if (cmdConfig.sqlWriteFormat == SQLCommandConfig.ExportFormat.CSV) {
              System.out.println(sqlUpdateStm);
            } else {
              try {
                statement.executeUpdate(sqlUpdateStm);
              } catch (SQLException ex) {
                LOGGER.error("Cannot execute update statement", ex);
              }
            }
          }

          LOGGER.debug("Timing: {}", timer.toString());
          numRowsScored++;
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Cannot create DB connection!", e);
    }
  }

  private Statement createStatement(Connection conn) throws SQLException {
    return conn != null ? conn.createStatement() : null;
  }

  private boolean parseRow(String row,
                           String[] fileline,
                           String[] features,
                           MojoRowBuilder rowBuilder) {
    assert rowBuilder.size() == features.length : "RowBuilder does not match number of input features!";

    if (LOGGER.isDebugEnabled()) { // protect expensive debug statement
      LOGGER.debug("Row split: {}",
                   Arrays.toString(
                       IntStream.range(0, fileline.length).mapToObj(i -> i + "=" + fileline[i]).toArray()
                   ));
    }
    
    if (fileline.length != features.length + 1) {
      numRowError++;
      LOGGER.debug("Bad row: wrong number of features! row={}, len={}, expected features={}",
                   row, fileline.length, features.length);
      return false;
    }
    int fieldErrors = 0;
    for (int f = 0; f < rowBuilder.size(); f++) {
      if (fileline.length <= (f + 1)) {
        // parsing can be very strange.... for some records
        rowBuilder.setValue(features[f], "");
      } else {
        if (fileline[1 + f].toLowerCase().equals("null")) {
          rowBuilder.setValue(features[f], "");
        } else {
          try {
            rowBuilder.setValue(features[f], fileline[1 + f]);
          } catch (Exception ex) {
            numRowError++;
            fieldErrors++;
            LOGGER.debug("Bad row field! feature={}, value={}", f, fileline[1+f]);
            continue;
          }
        }

      }
    }
    return fieldErrors == 0;
  }

  static String normalizeColumnName(String name) {
    return name.replace('.', '_').replace('-', '_');
  }
}
    



    
    
  