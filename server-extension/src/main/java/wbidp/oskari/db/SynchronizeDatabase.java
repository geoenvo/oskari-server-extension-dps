package wbidp.oskari.db;

import wbidp.oskari.jobs.SynchronizeUserDataJob;
import wbidp.oskari.parser.CKANDataParser;
import wbidp.oskari.parser.CKANOrganization;
import wbidp.oskari.parser.CKANUser;
import wbidp.oskari.util.DatabaseUserServiceCKAN;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.PropertyUtil;
import jj2000.j2k.codestream.HeaderInfo.QCC;
import fi.nls.oskari.service.UserService;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.domain.Role;
import fi.nls.oskari.domain.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.sql.PreparedStatement;
import java.util.ArrayList;

public class SynchronizeDatabase {

    private static final Logger LOG = LogFactory.getLogger(SynchronizeUserDataJob.class);

    private Connection connectToDatabase(String dbUrlProp, String dbUserProp, String dbPasswordProp) {
        Connection connection = null;

        // Get CKAN settings
        String dbUrl = PropertyUtil.get(dbUrlProp, "jdbc:postgresql://localhost:5432/testdb");
        String dbUsername = PropertyUtil.get(dbUserProp, "username");
        String dbPassword = PropertyUtil.get(dbPasswordProp, "password");

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

    public void synchronizeRolesFromCKAN() {
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        boolean truncateData = PropertyUtil.getOptional("ckan.integration.db.truncate", false);
        String CKANOrgsDumpFile = PropertyUtil.get("ckan.integration.ckanapi.dump.organizations", "/tmp/ckanorgsdump.jsonl");

        if (oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN organizations to Oskari groups.");
            return;
        }

        String CKANOrgsDump = CKANDataParser.readCKANDumpFile(CKANOrgsDumpFile);
        ArrayList<CKANOrganization> roles = CKANDataParser.parseJSONtoRoles(CKANOrgsDump);

        try {
            if (truncateData) {
                truncateData(oskariConnection, "oskari_roles");
                addRoles(roles);
            } else {
                addRoles(roles);
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }

        closeAllDbConnections(oskariConnection);
    }

    public void synchronizeUsersFromCKAN() {
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        boolean truncateData = PropertyUtil.getOptional("ckan.integration.db.truncate", false);
        String CKANUsersDumpFile = PropertyUtil.get("ckan.integration.ckanapi.dump.users", "/tmp/ckanusersdump.jsonl");

        if (oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        String CKANUsersDump = CKANDataParser.readCKANDumpFile(CKANUsersDumpFile);
        ArrayList<CKANUser> users = CKANDataParser.parseJSONToUsers(CKANUsersDump);

        try {
            if (truncateData) {
                truncateData(oskariConnection, "oskari_users");
                addUsers(users);
            } else {
                addUsers(users);
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }

        closeAllDbConnections(oskariConnection);
    }

    public void synchronizeLayersFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN layers to Oskari.");
            return;
        }

        closeAllDbConnections(oskariConnection);
    }

    private void addUsers(ArrayList<CKANUser> users) throws ServiceException {
        DatabaseUserServiceCKAN userService = new DatabaseUserServiceCKAN();

        users.forEach(user -> {
            try {
                String[] roles = {"2"};
                userService.createCKANUser(user, roles);
            } catch (ServiceException se) {
                LOG.error(se, "Error while adding user: " + user.getScreenname());
            }
        });
    }

    private void addRoles(ArrayList<CKANOrganization> roles) {
        DatabaseUserServiceCKAN userService = new DatabaseUserServiceCKAN();
        Set<CKANOrganization> roleSet = new HashSet<CKANOrganization>(roles);
        userService.storeCKANOrganizationsAsRoles(roleSet);
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
        LOG.info("Executing truncate..");
    }

    private void closeAllDbConnections(Connection... connections) {
        for (int i = 0; i < connections.length; ++i) {
            try {
                connections[i].close();
            } catch (SQLException e) {
                LOG.error(e + "Error closing database connection.");
            }
        }
    }
}
