package flyway.wbidp;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class V1_0_3__add_oskari_ckan_dataset_resource_log_table implements JdbcMigration {
    public void migrate(Connection connection) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("CREATE SEQUENCE \"public\".\"oskari_ckan_dataset_resource_log_id_seq\" \n" +
                "INCREMENT 1\n" +
                "MINVALUE  1\n" +
                "MAXVALUE 9223372036854775807\n" +
                "START 1\n" +
                "CACHE 1;");
        statement.execute();
        statement.close();

        PreparedStatement statement2 = connection.prepareStatement("CREATE TABLE \"public\".\"oskari_ckan_dataset_resource_log\" (" +
                "\"id\" int8 NOT NULL DEFAULT nextval('oskari_ckan_dataset_resource_log_id_seq'::regclass)," +
                "\"last_modified\" timestamptz(6)," +
                "\"resource_uuid\" uuid," +
                "CONSTRAINT \"oskari_ckan_dataset_resource_log_pkey\" PRIMARY KEY (\"id\")," +
                "CONSTRAINT \"oskari_ckan_dataset_resource_log_uuid_key\" UNIQUE (\"resource_uuid\"));");
        statement2.execute();
        statement2.close();
    }
}
