package wbidp.oskari.util;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;

import fi.nls.oskari.domain.User;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.user.DatabaseUserService;
import fi.nls.oskari.user.MybatisRoleService;
import fi.nls.oskari.user.MybatisUserService;
import wbidp.oskari.parser.CKANOrganization;
import wbidp.oskari.parser.CKANUser;
import fi.nls.oskari.domain.Role;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Modified version of Oskari DatabaseUserService.
 * 
 * Adds support for passwords that are stored directly from CKAN in it's
 * own format.
 */
public class DatabaseUserServiceCKAN extends DatabaseUserService {
    private MybatisRoleService roleService = new MybatisRoleService();
    private MybatisUserService userService = new MybatisUserService();
    
    private static final Logger log = LogFactory.getLogger(DatabaseUserService.class);

    private static final int BCRYPT_PASSWORD_LENGTH = 60;
    private static final String ERR_USER_MISSING = "User was null";

    @Override
    public User login(final String user, final String pass) throws ServiceException {
        try {
            final String expectedHashedPassword = userService.getPassword(user);
            if (expectedHashedPassword == null) {
                return null;
            }
            
            final String username;
            if (expectedHashedPassword.startsWith("MD5:")) {
                final String hashedPass = "MD5:" + DigestUtils.md5Hex(pass);
                username = userService.login(user, hashedPass);
                log.debug("Tried to login user with:", user, "/", pass, "-> ", hashedPass, "- Got username:", username);
                if (username == null) {
                    return null;
                }
            }
            else if (expectedHashedPassword.length() == BCRYPT_PASSWORD_LENGTH) {
                log.debug("Tried to login user:", user, "/", pass, " with BCrypt password");
                if (!BCrypt.checkpw(pass, expectedHashedPassword)) {
                    return null;
                }
                username = user;
            }
            else if (expectedHashedPassword.startsWith("$pbkdf2-sha512")) {
                log.debug("Tried to login user:", user, "/", pass, " with CKAN password");
                if (!CKANPasswordHandler.validatePassword(pass, expectedHashedPassword)) {    
                    return null;
                }
                username = user;
            }
            else {
                log.error("Unknown password hash format for user ", user);
                return null;
            }
            
           	return getUser(username);
        }
        catch (Exception ex) {
            throw new ServiceException("Unable to handle login", ex);
        }
    }

    public User createCKANUser(CKANUser user, String[] roleIds) throws ServiceException {
        log.info("createCKANUser #######################");
        if(user == null) {
            throw new ServiceException(ERR_USER_MISSING);
        }
        if(user.getUuid() == null || user.getUuid().isEmpty()) {
            user.setUuid(generateUuid());
        }
        Long id = userService.addUser(user);
        
        for(String roleId : roleIds){
        	log.debug("roleId: " + roleId + " userId: " + id);
            roleService.linkRoleToUser(Long.valueOf(roleId), id);
        }    

        setUserPassword(user.getScreenname(), user.getCKANPasswordHash());

        return userService.find(id);
    }

    @Override
    public void setUserPassword(String username, String password) throws ServiceException {
        if (password.startsWith("$pbkdf2-sha512")) {
            userService.setPassword(username, password);
        } else {
            String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
            userService.setPassword(username, hashed);
        }
    }

    @Override
    public void updateUserPassword(String username, String password) throws ServiceException {
        if (password.startsWith("$pbkdf2-sha512")) {
            userService.updatePassword(username, password);
        } else {
            String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
            userService.updatePassword(username, hashed);
        }
    }

    public Set<CKANOrganization> storeCKANOrganizationsAsRoles(final Set<CKANOrganization> userRoles) {
        Set<CKANOrganization> updatedRoles = new HashSet<>();
        try {
            updatedRoles = ensureRolesInDB(userRoles);
        } catch (ServiceException e) {
            log.error(e + "Error storing CKAN organizations as roles to db.");
        }
        return updatedRoles;
    }

    private Set<CKANOrganization> ensureRolesInDB(final Set<CKANOrganization> userRoles) throws ServiceException {
        // Remove roles that do not exist as organizations in CKAN anymore
        removeDeletedCKANRoles(userRoles);
        final Role[] systemRoles = getRoles();
        final Set<Role> rolesToInsert = new HashSet<Role>(userRoles.size());
        for(Role userRole : userRoles) {
            boolean found = false;
            for(Role role : systemRoles) {
                if(role.getName().equals(userRole.getName())) {
                    // assign ID from role with same name in db
                    userRole.setId(role.getId());
                    found = true;
                }
            }
            if(!found) {
                Role dbRole = insertRole(userRole.getName());
                userRole.setId(dbRole.getId());
            }
        }
        return userRoles;
    }

    private void removeDeletedCKANRoles(Set<CKANOrganization> userRoles) throws ServiceException {
        Role[] systemRoles = getRoles();
        for(Role role : systemRoles) {
            boolean removeRole = true;
            if (!role.getName().equals("Admin") && !role.getName().equals("User") && !role.getName().equals("Guest")) {
                for(Role userRole : userRoles) {
                    if (userRole.getName().equals(role.getName())) {
                        removeRole = false;
                        break;
                    }
                }
                if (removeRole) {
                    roleService.delete(role.getId());
                }
            }
        }
    }
}