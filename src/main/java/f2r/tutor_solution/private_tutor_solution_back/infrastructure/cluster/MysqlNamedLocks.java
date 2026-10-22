package f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Connection-scoped locks use dedicated physical sessions, never a pooled connection. */
@Component
public class MysqlNamedLocks {
  @FunctionalInterface
  public interface Connections { Connection open() throws SQLException; }
  private final Connections connections;

  @Autowired
  public MysqlNamedLocks(
      @Value("${spring.datasource.url:}") String url,
      @Value("${spring.datasource.username:}") String user,
      @Value("${spring.datasource.password:}") String password) {
    this(() -> {
      Properties properties = new Properties();
      properties.setProperty("user", user);
      properties.setProperty("password", password);
      properties.setProperty("connectTimeout", "3000");
      properties.setProperty("socketTimeout", "3000");
      return DriverManager.getConnection(url, properties);
    });
  }

  public MysqlNamedLocks(Connections connections) { this.connections = connections; }

  public Session tryAcquire(String purpose) throws SQLException {
    if (!purpose.matches("[a-z-]{1,24}")) throw new IllegalArgumentException("lock purpose");
    Connection connection = connections.open();
    try {
      connection.setNetworkTimeout(Runnable::run, 3000);
      // Names are scoped to the current database, not to each JVM or deployment color.
      String name;
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("SELECT CONCAT('f2r:', LEFT(SHA2(DATABASE(),256),16))")) {
        if (!result.next() || result.getString(1) == null) throw new SQLException("lock namespace unavailable");
        name = result.getString(1) + ":" + purpose;
      }
      if (query(connection, "SELECT GET_LOCK(?,0)", name) == 1) return new Session(connection, name);
      connection.close();
      return null;
    } catch (SQLException | RuntimeException failure) {
      try { connection.close(); } catch (SQLException ignored) { }
      throw failure;
    }
  }

  private static int query(Connection connection, String sql, String name) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(3);
      statement.setString(1, name);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? result.getInt(1) : 0;
      }
    }
  }

  public static final class Session implements AutoCloseable {
    private final Connection connection;
    private final String name;
    private boolean closed;
    private Session(Connection connection, String name) { this.connection = connection; this.name = name; }

    public synchronized boolean valid() {
      if (closed) return false;
      try {
        return query(connection, "SELECT IS_USED_LOCK(?) = CONNECTION_ID()", name) == 1;
      } catch (SQLException failure) { return false; }
    }

    @Override
    public synchronized void close() {
      if (closed) return;
      closed = true;
      try { query(connection, "SELECT RELEASE_LOCK(?)", name); } catch (SQLException ignored) { }
      // This is a physical DriverManager connection; even failed RELEASE cannot leak into a pool.
      try { connection.close(); } catch (SQLException ignored) { }
    }
  }
}
