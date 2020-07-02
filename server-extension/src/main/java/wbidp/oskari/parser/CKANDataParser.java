package wbidp.oskari.parser;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;

import fi.nls.oskari.domain.User;
import fi.nls.oskari.domain.Role;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class CKANDataParser {

    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);

    /**
     * Read content of CKAN dump file.
     * 
     * @param CKANDumpfilePath path to CKAN dump file.
     * @return String content of CKAN dump file.
     */
    public static String readCKANDumpFile(String CKANDumpFilePath) {
        String CKANDump = "";

        try {
            CKANDump = new String(Files.readAllBytes(Paths.get(CKANDumpFilePath)));
        } catch (IOException e) {
            //e.printStackTrace();
            LOG.error("Unable to read CKAN dump file! " + e);
        }

        return CKANDump;
    }

    /**
     * Get an Oskari User from a CKAN User JSONObject
     * 
     * @param CKANUserJSON a CKAN User JSONObject.
     * @return an Oskari User object.
     */
    public static User getUserFromJSON(JSONObject CKANUserJSON) {
        User user = new User();

        String CKANUserName = (String) CKANUserJSON.get("name");
        String CKANUserUuid = (String) CKANUserJSON.get("id");
        String CKANUserEmail = (String) CKANUserJSON.get("email");
        //String CKANUserPasswordHash = (String) CKANUserJSON.get("password_hash");
        String CKANUserFullname = (String) CKANUserJSON.get("fullname");
        user.setScreenname(CKANUserName);
        user.setUuid(CKANUserUuid);
        if ((CKANUserEmail != null) && !(CKANUserEmail.isEmpty())) {
            user.setEmail(CKANUserEmail);
        }
        if ((CKANUserFullname != null) && !(CKANUserFullname.isEmpty())) {
            String[] names = CKANUserFullname.split(" ");
            if (names.length > 1) {
                user.setFirstname(names[0]);
                user.setLastname(names[names.length - 1]);
            } else {
                user.setFirstname(names[0]);
                user.setLastname("");
            }
        } else {
            // if CKAN user full name is not set, use CKAN username as Oskari user first name and blank last name
            user.setFirstname(user.getScreenname());
            user.setLastname("");
        }

        return user;
    }

    /**
     * Parses JSON user data from CKAN to Oskari Users.
     * 
     * @param JSONFromCKAN user data from CKAN as JSON.
     * @return an ArrayList of Oskari User objects.
     */
    public static ArrayList<User> parseJSONToUsers(String JSONFromCKAN) {
        ArrayList<User> users = new ArrayList<>();
        String CKANUserJSONStr;
        JSONObject CKANUserJSON = new JSONObject();
        JSONParser parser = new JSONParser();

        LOG.info("Parsing CKAN User JSONL data.");

        // read each line in JSONL String and map to a JSON object
        BufferedReader reader = new BufferedReader(new StringReader(JSONFromCKAN));
        try {
            while ((CKANUserJSONStr = reader.readLine()) != null) {
                if ((CKANUserJSONStr != null) && !(CKANUserJSONStr.isEmpty())) {
                    // System.out.println(CKANUserJSONStr);
                    try {
                        CKANUserJSON = (JSONObject) parser.parse(CKANUserJSONStr);
                        User user = getUserFromJSON(CKANUserJSON);
                        // System.out.println(user.getScreenname());
                        // System.out.println(user.getEmail());
                        // System.out.println(user.getFullName());
                        users.add(user);
                    } catch (ParseException e) {
                        LOG.error("Unable to parse CKAN User JSON! " + e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to read CKAN User JSON String! " + e);
        }

        return users;
    }

    /**
     * Parse JSON Organization data from CKAN to Oskari roles.
     * 
     * @param JSONFromCKAN Organization data from CKAN as JSON.
     * @return an ArrayList of CKANOrganization objects.
     */
    public static ArrayList<CKANOrganization> parseJSONtoRoles(String JSONFromCKAN) {
        ArrayList<CKANOrganization> roles = new ArrayList<>();
        String CKANOrgJSONStr;
        JSONObject CKANOrgJSON = new JSONObject();
        JSONParser parser = new JSONParser();

        LOG.info("Parsing CKAN Organization JSONL data.");

        // read each line in JSONL String and map to a JSON object
        BufferedReader reader = new BufferedReader(new StringReader(JSONFromCKAN));
        try {
            while ((CKANOrgJSONStr = reader.readLine()) != null) {
                if ((CKANOrgJSONStr != null) && !(CKANOrgJSONStr.isEmpty())) {
                    // System.out.println(CKANOrgJSONStr);
                    try {
                        CKANOrgJSON = (JSONObject) parser.parse(CKANOrgJSONStr);
                        String CKANOrgName = (String) CKANOrgJSON.get("name");
                        String CKANOrgUuid = (String) CKANOrgJSON.get("id");
                        JSONArray CKANOrgUsersJSON = (JSONArray) CKANOrgJSON.get("users");
                        CKANOrganization org = new CKANOrganization();
                        org.setName(CKANOrgName);
                        org.setUuid(CKANOrgUuid);
                        Iterator it = CKANOrgUsersJSON.iterator();
                        while (it.hasNext()) {
                            JSONObject CKANUserJSON = (JSONObject) it.next();
                            User user = getUserFromJSON(CKANUserJSON);
                            // System.out.println(user.getScreenname());
                            // System.out.println(user.getEmail());
                            // System.out.println(user.getFullName());
                            org.addUser(user);
                        }
                        roles.add(org);
                    } catch (ParseException e) {
                        LOG.error("Unable to parse CKAN Organization JSON! " + e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to read CKAN Organization JSON String! " + e);
        }

        return roles;
    }
}
