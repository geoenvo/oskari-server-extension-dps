package flyway.wbidp;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class V1_0_3__add_oskari_ckan_dataset_resource_log_table implements JdbcMigration {
    public void migrate(Connection connection) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("CREATE TABLE \"public\".\"oskari_ckan_dataset_resource_log\" (" +
                "\"id\" int4 NOT NULL PRIMARY KEY," +
                "\"last_modified\" timestamptz(6)," +
                "\"resource_uuid\" varchar(64) COLLATE \"pg_catalog\".\"default\");");
        statement.execute();
        statement.close();
    }
}
