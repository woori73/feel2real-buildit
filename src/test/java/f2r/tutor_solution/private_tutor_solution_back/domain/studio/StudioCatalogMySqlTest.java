package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The same contract on a new disposable CI schema; never connects to a production host. */
@EnabledIfEnvironmentVariable(named="F2R_STUDIO_MYSQL_TEST",matches="true")
class StudioCatalogMySqlTest extends StudioCatalogTest {
  private String schema;
  @Override protected DataSource database() {
    schema="f2r_catalog_"+UUID.randomUUID().toString().replace("-","");
    try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3318/mysql","root","isolated_test_root");var statement=connection.createStatement()) {
      statement.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    } catch(java.sql.SQLException e) { throw new IllegalStateException(e); }
    return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3318/"+schema,"root","isolated_test_root");
  }
  @Override protected void cleanup() {
    if(schema==null||!schema.matches("f2r_catalog_[a-f0-9]{32}"))return;
    try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3318/mysql","root","isolated_test_root");var statement=connection.createStatement()) {
      statement.execute("DROP DATABASE "+schema);
    } catch(java.sql.SQLException e) { throw new IllegalStateException(e); }
  }
}
