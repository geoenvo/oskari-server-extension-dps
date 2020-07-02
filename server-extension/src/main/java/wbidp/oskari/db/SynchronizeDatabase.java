package wbidp.oskari.db;

import wbidp.oskari.jobs.SynchronizeUserDataJob;
import wbidp.oskari.parser.CKANDataParser;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.service.UserService;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.domain.Role;
import fi.nls.oskari.domain.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.sql.PreparedStatement;

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
    }

    public void synchronizeUsersFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        boolean truncateData = PropertyUtil.getOptional("ckan.integration.db.truncate", false);

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        try {
            if (truncateData) {
                truncateData(oskariConnection, "oskari_users");
                addUsers(CKANDataParser.parseJSONToUsers(""));
            } else {
                addUsers(CKANDataParser.parseJSONToUsers(""));
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }
    }

    public void synchronizeUsersFromCKAN(String userJSON) {
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        boolean truncateData = PropertyUtil.getOptional("ckan.integration.db.truncate", false);

        if (oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        try {
            if (truncateData) {
                truncateData(oskariConnection, "oskari_users");
                addUsers(CKANDataParser.parseJSONToUsers(userJSON));
            } else {
                addUsers(CKANDataParser.parseJSONToUsers(userJSON));
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }
    }

    public void synchronizeLayersFromCKAN() {
        Connection ckanConnection = connectToDatabase("ckan.integration.db.url", "ckan.integration.db.username", "ckan.integration.db.password");
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");

        if (ckanConnection == null || oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN layers to Oskari.");
            return;
        }
    }

    private void addUsers(ArrayList<User> users) throws ServiceException {
        UserService userService = UserService.getInstance();

        users.forEach(user -> {
            try {
                User retUser = userService.createUser(user);
                userService.setUserPassword(retUser.getScreenname(), String.format("changeme_%s", retUser.getScreenname()));
            } catch (ServiceException se) {
                LOG.error(se, "Error while adding user: " + user.getScreenname());
            }
        });
    }

    private void addUserToDb(String username, String firstName, String lastName, String email, String password) throws ServiceException {
        try {
            UserService userService = UserService.getInstance();
            User user = new User();
            user.setScreenname("TT");
            user.setFirstname("Testi");
            user.setLastname("Teppo");
            user.setEmail("");
            String[] roles = {"1"};
            User retUser = userService.createUser(user, roles);
            userService.setUserPassword(retUser.getScreenname(), String.format("changeme_%s", retUser.getScreenname()));
        } catch (ServiceException se) {
            LOG.error(se, "Unable to create user!");
        }
    }

    private void removeUserFromDb(String username) {
        try {
            UserService userService = UserService.getInstance();
            User userToDelete = userService.getUser(username);
            if (userToDelete != null) {
                userService.deleteUser(userToDelete.getId());
            } else {
                LOG.debug(String.format("Nothing deleted, username %s does not exist.", username));
            }
        } catch (ServiceException se) {
            LOG.error(se, "Error while removing user.");
        }
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
        LOG.info("Executing truncate..");
    }
}
