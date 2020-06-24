package wbidp.oskari.db;

import wbidp.oskari.jobs.SynchronizeUserDataJob;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.PropertyUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;

public class SynchronizeDatabase {

    private static final Logger LOG = LogFactory.getLogger(SynchronizeUserDataJob.class);

    private Connection connectToDatabase(String dbUrlProp, String dbUserProp, String dbPasswordProp) {
        Connection connection = null;

        // Get CKAN settings
        String dbUrl = PropertyUtil.get(dbUrlProp, "jdbc:postgresql://localhost:5432/testdb");
        String dbUsername = PropertyUtil.get(dbUrlProp, "username");
        String dbPassword = PropertyUtil.get(dbUrlProp, "password");

        // Test connection
        try {
            connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            if (connection != null) {
                LOG.info("Connected to database.");
            }
        } catch (Exception e) {
            LOG.error("Unable to connect to database! " + e);
        }

        return connection;
    }

    public void synchronizeGroupsFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN groups to Oskari.");
            return;
        }

        try {
            truncateData(oskariConnection, "oskari_roles");
            truncateData(oskariConnection, "oskari_role_oskari_user");
        } catch (Exception e) {
            LOG.error("Unable to truncate table(s)! " + e);
        }

        // TODO: Add new groups (roles in Oskari) from CKAN
    }

    public void synchronizeUsersFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        try {
            truncateData(oskariConnection, "oskari_users");
        } catch (Exception e) {
            LOG.error("Unable to truncate table! " + e);
        }

        // TODO: Add new users from CKAN
        // TODO: Add users to roles in Oskari
    }

    public void synchronizeLayersFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN layers to Oskari.");
            return;
        }
    }

    private void addUsers(Connection connection) throws SQLException {
        // TODO: Read users from CKAN and add to Oskari DB
    }

    private void addRoles(Connection connection) throws SQLException {
        // TODO: Read groups from CKAN and add to Oskari DB as roles
    }

    private void mapUsersToRole(Connection connection) throws SQLException {
        // TODO: Map user to a role in Oskari DB oskari_role_oskari_user
    }

    private void addLayers(Connection connection) throws SQLException {
        // TODO: Read layer APIs from CKAN and add to Oskari DB
    }

    private void truncateData(Connection connection, String tableName) throws SQLException {
        String sql = "TRUNCATE TABLE ? CASCADE;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);

            LOG.debug("Executing:", ps.toString());
            int i = ps.executeUpdate();
            LOG.debug("Truncate result:", i);
        }
    }
}
