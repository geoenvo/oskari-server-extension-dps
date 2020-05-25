package wbidp.oskari.db;

import wbidp.oskari.jobs.SynchronizeUserDataJob;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.PropertyUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SynchronizeDatabase {

    private static final Logger LOG = LogFactory.getLogger(SynchronizeUserDataJob.class);

    private static Connection connectToCKANDatabase() {
        Connection connection = null;

        // Get CKAN settings
        String ckanDbUrl = PropertyUtil.get("ckan.integration.db.url", "jdbc:postgresql://localhost:5432/ckan");
        String ckanDbUsername = PropertyUtil.get("ckan.integration.db.username", "username");
        String ckanDbPassword = PropertyUtil.get("ckan.integration.db.password", "password");

        // Test connection
        try {
            connection = DriverManager.getConnection(ckanDbUrl, ckanDbUsername, ckanDbPassword);
            if (connection != null) {
                LOG.info("Connected to CKAN database.");
            }
        } catch (Exception e) {
            LOG.error("Unable to connect to CKAN database! " + e);
        }

        return connection;
    }

    public static void synchronizeGroupsFromCKAN() {
        Connection connection = connectToCKANDatabase();

        if (connection == null) {
            LOG.error("Unable to synchronize CKAN groups to Oskari.");
            return;
        }
    }

    public static void synchronizeUsersFromCKAN() {
        Connection connection = connectToCKANDatabase();

        if (connection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }
    }

    public static void synchronizeLayersFromCKAN() {
        Connection connection = connectToCKANDatabase();

        if (connection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }
    }

    private void addUsers(Connection connection) throws SQLException {

    }
    private void addLayers(Connection connection) throws SQLException {

    }

    private void truncateData(String tableName) throws SQLException {

    }
}
