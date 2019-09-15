package ai.h2o.mojos.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.util.stream.Stream;

import static ai.h2o.mojos.db.Utils.decodeBase64;

public class SQLCommandConfig {

  enum ExportFormat {
    CSV,
    UPDATE,
    INSERT;

    public static ExportFormat from(String statement) {
      String lstm = statement.toLowerCase().trim();
      return lstm.toLowerCase().startsWith("update")
             ? UPDATE
             : (
                 statement.toLowerCase().startsWith("insert")
                 ? INSERT
                 : CSV
             );
    }

  }
  SQLCommandConfig(Config conf) {
    modelFile = new File(conf.getString("model.file"));
    dbConnectionString = conf.getString("db.connection");
    dbUser = conf.getString("db.user");
    dbPassword = decodeBase64(conf.getString("db.password"));
    dbPasswordPrompt = conf.getBoolean("db.prompt");
    sqlKey = conf.getString("sql.key");
    sqlPredictionCol = conf.getString("sql.predictionCol");
    sqlSelectStatement = conf.getString("sql.select");
    sqlWriteStatement = conf.getString("sql.write");
    sqlWriteFormat = ExportFormat.from(sqlWriteStatement);
    sqlSavePrediction = conf.getInt("sql.savePrediction");
    sqlFieldSeparator = conf.getString("sql.separator");
  }

  final File modelFile;

  final String dbConnectionString;
  final String dbUser;
  String dbPassword;
  final Boolean dbPasswordPrompt;
  
  final String sqlSelectStatement;
  final String sqlWriteStatement;
  final ExportFormat sqlWriteFormat;
  final String sqlKey;
  final String sqlPredictionCol;
  int sqlSavePrediction;
  final String sqlFieldSeparator;
  public boolean hasCredentials() {
    return !Stream.of(dbUser, dbPassword).allMatch(Utils::isEmpty);
  }

  public static class Builder {

    SQLCommandConfig build() {
      Config conf = ConfigFactory.load().getConfig("mojo-db-scoring-app");
      return new SQLCommandConfig(conf);
    }
    Builder loadFrom(File f) {
      System.setProperty("config.file", f.getAbsolutePath());
      return this;
    }

  }
  
  @Override
  public String toString() {
    return "SQLCommandConfig{" +
           "modelFile='" + modelFile + '\'' +
           ", sqlConnectionString='" + dbConnectionString + '\'' +
           ", sqlUser='" + dbUser + '\'' +
           ", sqlPassword='" + dbPassword + '\'' +
           ", sqlPrompt='" + dbPasswordPrompt + '\'' +
           ", sqlSelectStatement='" + sqlSelectStatement + '\'' +
           ", sqlWriteStatement='" + sqlWriteStatement + '\'' +
           ", sqlWriteFormat=" + sqlWriteFormat +
           ", sqlKey='" + sqlKey + '\'' +
           ", sqlPredictionCol='" + sqlPredictionCol + '\'' +
           ", sqlSavePrediction=" + sqlSavePrediction +
           ", sqlFieldSeparator='" + sqlFieldSeparator + '\'' +
           '}';
  }
}
