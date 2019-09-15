package ai.h2o.mojos.db;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class Utils {

  public static final boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  public static String f(String s, Object ...args) {
    return String.format(s, args);
  }

  public static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().startsWith("windows");
  }

  public static String decodeBase64(String s) {
    return isEmpty(s) ? s : new String(Base64.getDecoder().decode(s));
  }

  public static Connection createConnection(SQLCommandConfig cmdConfig, Logger logger) throws SQLException {
    Connection connection = null;
    if (cmdConfig.sqlWriteFormat != SQLCommandConfig.ExportFormat.CSV) {
      if (!cmdConfig.hasCredentials()) {
        logger.debug("Connection string without seperate SQLUser and SQLPassword details.");
        connection = DriverManager.getConnection(cmdConfig.dbConnectionString);
      } else {
        if (isWindows()) {
          logger.debug("Connection string using Windows connection string.");
          connection =
              DriverManager.getConnection(
                  f("%s;user=%s;password=%s",
                    cmdConfig.dbConnectionString,
                    cmdConfig.dbUser,
                    cmdConfig.dbPassword
                  )
              );
        } else {
          logger.debug("Connection string used separate SQL parameters.");
          connection =
              DriverManager.getConnection(
                  cmdConfig.dbConnectionString,
                  cmdConfig.dbUser,
                  cmdConfig.dbPassword
              );
        }
      }
      logger.debug("Connected to database.");
    }
    return connection;
  }

  public static String join(String[] ss, String sep) {
    StringBuilder sb = new StringBuilder();
    for (String item : ss) {
      if (sb.length() > 0) sb.append(sep);
      sb.append(item);
    }
    return sb.toString();
  }
}
