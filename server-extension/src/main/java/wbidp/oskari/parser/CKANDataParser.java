package wbidp.oskari.parser;

import java.util.ArrayList;

import fi.nls.oskari.domain.User;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

public class CKANDataParser {

    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);

    /**
     * Parses JSON user data from CKAN to Oskari Users.
     * 
     * @param JSONFromCKAN user data from CKAN as JSON.
     * @return an ArrayList of Oskari User objects.
     */
    public static ArrayList<User> parseJSONToUsers(String JSONFromCKAN) {
        ArrayList<User> users = new ArrayList<>();
        LOG.info("Parsing CKAN JSON data.");

        // TODO: Parse CKAN user data JSON to Oskari Users here
        // and add each created user to the ArrayList.
        //
        // Oskari user can be created like this:
        // User user = new User();
        // user.setScreenname("TT");
        // user.setFirstname("Testi");
        // user.setLastname("Teppo");
        // user.setEmail("");

        return users;
    }
}
