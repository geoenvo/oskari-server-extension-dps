package wbidp.oskari.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import fi.nls.oskari.domain.User;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.util.PropertyUtil;
import wbidp.oskari.helpers.LayerHelper;
import wbidp.oskari.jobs.SynchronizeUserDataJob;
import wbidp.oskari.parser.CKANDataParser;
import wbidp.oskari.parser.CKANOrganization;
import wbidp.oskari.parser.CKANUser;
import wbidp.oskari.util.DatabaseUserServiceCKAN;

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

    /**
     * Read organizations from the defined CKAN JSON file and synchronize
     * them with Oskari roles.
     */
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
                truncateData(oskariConnection, "oskari_role_oskari_user");
                addRoles(roles);
            } else {
                addRoles(roles);
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }

        closeAllDbConnections(oskariConnection);
    }

    /**
     * Read users from the defined CKAN JSON file and synchronize
     * them with Oskari users.
     */
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

    /**
     * Read users and organizations from the defined CKAN JSON file and synchronize
     * them with Oskari users and roles.
     */
    public void synchronizeUsersWithRolesFromCKAN() {
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        boolean truncateData = PropertyUtil.getOptional("ckan.integration.db.truncate", false);
        String CKANOrgsDumpFile = PropertyUtil.get("ckan.integration.ckanapi.dump.organizations", "/tmp/ckanorgsdump.jsonl");
        String CKANUsersDumpFile = PropertyUtil.get("ckan.integration.ckanapi.dump.users", "/tmp/ckanusersdump.jsonl");

        if (oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        String CKANOrgsDump = CKANDataParser.readCKANDumpFile(CKANOrgsDumpFile);
        ArrayList<CKANOrganization> roles = CKANDataParser.parseJSONtoRoles(CKANOrgsDump);
        String CKANUsersDump = CKANDataParser.readCKANDumpFile(CKANUsersDumpFile);
        ArrayList<CKANUser> users = CKANDataParser.parseJSONToUsers(CKANUsersDump);
        ArrayList<CKANOrganization> systemRoles = new ArrayList<>();

        try {
            if (truncateData) {
                truncateData(oskariConnection, "oskari_roles");
                truncateData(oskariConnection, "oskari_role_oskari_user");
                systemRoles = addRoles(roles);
                truncateData(oskariConnection, "oskari_users");
                addUsers(users, roles);
            } else {
                systemRoles = addRoles(roles);
                addUsers(users, roles);
            }
        } catch (Exception e) {
            LOG.error("Unable to synchronize users! " + e);
        }

        closeAllDbConnections(oskariConnection);
    }

    /**
     * Read layer API data from the defined CKAN JSON file, read API capabilities 
     * and add all layers from supported API's to Oskari.
     */
    public void synchronizeLayersFromCKAN() {
        Connection oskariConnection = connectToDatabase("db.url", "db.username", "db.password");
        
        if (oskariConnection == null) {
            LOG.error("Unable to synchronize CKAN users to Oskari.");
            return;
        }

        LayerHelper.emptyLayerCapabilitiesCache(oskariConnection);
        String CKANDatasetsDumpFile = PropertyUtil.get("ckan.integration.ckanapi.dump.datasets", "/tmp/ckandatasetsdump.jsonl");
        String CKANDatasetsDumpFile2 = PropertyUtil.get("ckan.integration.ckanapi.dump.datasets.secondary", null);
        readAndParseDump(oskariConnection, CKANDatasetsDumpFile);

        if (CKANDatasetsDumpFile2 != null) {
            readAndParseDump(oskariConnection, CKANDatasetsDumpFile2);
        }

        closeAllDbConnections(oskariConnection);
    }

    private void readAndParseDump(Connection oskariConnection, String CKANLayersDumpFile) {
        String CKANLayersDump = CKANDataParser.readCKANDumpFile(CKANLayersDumpFile);
        CKANDataParser.parseJSONAndAddLayers(CKANLayersDump, oskariConnection);
    }

    private void addUsers(ArrayList<CKANUser> users) throws ServiceException {
        DatabaseUserServiceCKAN userService = new DatabaseUserServiceCKAN();

        users.forEach(user -> {
            try {
                String[] roles = {"2"};
                User existingUser = userService.getUser(user.getScreenname());
                if (existingUser == null) {
                    userService.createCKANUser(user, roles);
                } else {
                    user.setId(existingUser.getId());
                    userService.modifyUserwithRoles(user, roles);
                }
            } catch (ServiceException se) {
                LOG.error(se, "Error while adding user: " + user.getScreenname());
            }
        });
    }

    private void addUsers(ArrayList<CKANUser> users, ArrayList<CKANOrganization> organizations) throws ServiceException {
        DatabaseUserServiceCKAN userService = new DatabaseUserServiceCKAN();

        users.forEach(user -> {
            try {
                ArrayList<String> userRoles = new ArrayList<>();
                organizations.forEach(organization -> {
                    for (User orgUser : organization.getUsers()) {
                        if (orgUser.getScreenname().equals(user.getScreenname())) {
                            userRoles.add(String.valueOf(userService.getRoleByName(organization.getName()).getId()));
                        }
                     }
                });
                // If the user is marked sysadmin in CKAN, grant Admin-role in Oskari also.
                // Otherwise, grant the User-role.
                if (user.isCKANSysAdmin()) {
                    userRoles.add(String.valueOf(userService.getRoleByName("Admin").getId()));
                } else {
                    userRoles.add(String.valueOf(userService.getRoleByName("User").getId()));
                }
                String[] roles = new String[userRoles.size()];
                roles = userRoles.toArray(roles);
                User existingUser = userService.getUser(user.getScreenname());
                if (existingUser == null) {
                    LOG.debug("USER ROLES: " + Arrays.toString(roles) + " " + user.getScreenname());
                    userService.createCKANUser(user, roles);
                } else {
                    LOG.debug("USER ROLES: " + Arrays.toString(roles) + " " + user.getScreenname());
                    user.setId(existingUser.getId());
                    userService.updateUserPassword(user.getScreenname(), user.getCKANPasswordHash());
                    userService.modifyUserwithRoles(user, roles);
                }
            } catch (ServiceException se) {
                LOG.error(se, "Error while adding user: " + user.getScreenname());
            }
        });
        deleteObsoleteUsers(users, userService);
    }

    private void deleteObsoleteUsers(ArrayList<CKANUser> users, DatabaseUserServiceCKAN userService) throws ServiceException {
        for(User user : userService.getUsers()) {
            boolean removeUser = true;
            for(User userInCKAN : users) {
                if (userInCKAN.getScreenname().equals(user.getScreenname())) {
                    removeUser = false;
                    break;
                }
            }
            if (removeUser) {
                userService.deleteUser(user.getId());
            }
        }
    }

    private ArrayList<CKANOrganization> addRoles(ArrayList<CKANOrganization> roles) {
        DatabaseUserServiceCKAN userService = new DatabaseUserServiceCKAN();
        Set<CKANOrganization> roleSet = new HashSet<CKANOrganization>(roles);
        ArrayList<CKANOrganization> systemRoles = new ArrayList<>();
        systemRoles = new ArrayList<>(userService.storeCKANOrganizationsAsRoles(roleSet));
        return systemRoles;
    }

    private void truncateData(Connection connection, String tableName) throws SQLException {
        String sql = "TRUNCATE TABLE " + tableName + " CASCADE;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
