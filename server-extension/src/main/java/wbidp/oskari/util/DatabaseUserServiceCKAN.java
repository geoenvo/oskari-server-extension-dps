package wbidp.oskari.util;

import org.apache.commons.codec.digest.DigestUtils;

import fi.nls.oskari.domain.User;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.user.DatabaseUserService;
import fi.nls.oskari.user.MybatisUserService;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Modified version of Oskari DatabaseUserService.
 * 
 * Adds support for passwords that are stored directly from CKAN in it's
 * own format.
 */
public class DatabaseUserServiceCKAN extends DatabaseUserService {
    private MybatisUserService userService = new MybatisUserService();
    
    private static final Logger log = LogFactory.getLogger(DatabaseUserService.class);

    private static final int BCRYPT_PASSWORD_LENGTH = 60;

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

}