package wbidp.oskari.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import wbidp.oskari.util.CKANLayerDataHandler;

public class CKANDataParser {

    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);

    /**
     * Read content of CKAN dump file.
     * 
     * @param CKANDumpFilePath path to CKAN dump file.
     * @return String content of CKAN dump file.
     */
    public static String readCKANDumpFile(String CKANDumpFilePath) {
        String CKANDump = "";

        try {
            CKANDump = new String(Files.readAllBytes(Paths.get(CKANDumpFilePath)));
        } catch (IOException e) {
            // e.printStackTrace();
            LOG.error("Unable to read CKAN dump file! " + e);
        }

        return CKANDump;
    }

    /**
     * Get an Oskari CKANUser from a CKAN User JSONObject
     * 
     * @param CKANUserJSON a CKAN User JSONObject.
     * @return an Oskari CKANUser object.
     */
    public static CKANUser getUserFromJSON(JSONObject CKANUserJSON) {
        CKANUser user = new CKANUser();

        String CKANUserName = (String) CKANUserJSON.get("name");
        String CKANUserUuid = (String) CKANUserJSON.get("id");
        String CKANUserEmail = (String) CKANUserJSON.get("email");
        String CKANUserPasswordHash = (String) CKANUserJSON.get("password_hash");
        String CKANUserFullname = (String) CKANUserJSON.get("fullname");
        boolean CKANSysAdmin = (CKANUserJSON.get("sysadmin") != null) ? (boolean) CKANUserJSON.get("sysadmin") : false;
        user.setCKANSysAdmin(CKANSysAdmin);
        user.setScreenname(CKANUserName);
        //user.setUuid(CKANUserUuid);
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
            // if CKAN user full name is not set, use CKAN username as Oskari user first
            // name and blank last name
            user.setFirstname(user.getScreenname());
            user.setLastname("");
        }
        user.setCKANPasswordHash(CKANUserPasswordHash);

        return user;
    }

    /**
     * Get an Oskari Role from a CKAN Organization JSONObject.
     * 
     * @param CKANOrgJSON a CKAN Organization JSONObject.
     * @return a CKANOrganization object.
     */
    public static CKANOrganization getRoleFromJSON(JSONObject CKANOrgJSON) {
        CKANOrganization org = new CKANOrganization();

        String CKANOrgName = (String) CKANOrgJSON.get("name");
        String CKANDisplayName = (String) CKANOrgJSON.get("display_name");
        String CKANTitle = (String) CKANOrgJSON.get("title");
        String CKANOrgUuid = (String) CKANOrgJSON.get("id");
        org.setName(CKANOrgName);
        org.setUuid(CKANOrgUuid);
        org.setDisplayName(CKANDisplayName);
        org.setTitle(CKANTitle);

        if (CKANOrgJSON.get("users") != null) {
            JSONArray CKANOrgUsersJSON = (JSONArray) CKANOrgJSON.get("users");
            LOG.debug("Parsing users for organization from json: " + CKANOrgUsersJSON.toJSONString());
            Iterator it = CKANOrgUsersJSON.iterator();
            while (it.hasNext()) {
                JSONObject CKANUserJSON = (JSONObject) it.next();
                CKANUser user = getUserFromJSON(CKANUserJSON);
                boolean added = org.addUser(user);
                if (added) {
                    LOG.debug("Added user to organization: " + user.getScreenname() + " " + org.getUsers().size());
                }
            }
        }

        return org;
    }

    /**
     * Parses JSON user data from CKAN to Oskari Users.
     * 
     * @param JSONFromCKAN user data from CKAN as JSON.
     * @return an ArrayList of Oskari CKANUser objects.
     */
    public static ArrayList<CKANUser> parseJSONToUsers(String JSONFromCKAN) {
        ArrayList<CKANUser> users = new ArrayList<>();
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
                        CKANUser user = getUserFromJSON(CKANUserJSON);
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
                        CKANOrganization org = getRoleFromJSON(CKANOrgJSON);
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

    /**
     * Parse JSON Layer API data from CKAN and add the WMS/WMTS/WFS/Esri layers to
     * Oskari.
     * 
     * @param JSONFromCKAN Layer API data from CKAN as JSON.
     * @return an ArrayList of OskariLayer objects.
     */
    public static void parseJSONAndAddLayers(String JSONFromCKAN, Connection connection) {
        String CKANLayerJSONStr;
        JSONObject CKANLayerJSON = new JSONObject();
        JSONParser parser = new JSONParser();

        LOG.info("Parsing CKAN Layer API JSONL data.");

        BufferedReader reader = new BufferedReader(new StringReader(JSONFromCKAN));
        try {
            while ((CKANLayerJSONStr = reader.readLine()) != null) {
                if ((CKANLayerJSONStr != null) && !(CKANLayerJSONStr.isEmpty())) {
                    try {
                        CKANLayerJSON = (JSONObject) parser.parse(CKANLayerJSONStr);
                        JSONArray resources = (JSONArray) CKANLayerJSON.get("resources");
                        boolean isPrivateResource = (boolean) CKANLayerJSON.get("private");
                        CKANOrganization organization = getRoleFromJSON((JSONObject) CKANLayerJSON.get("organization"));

                        Iterator it = resources.iterator();
                        while (it.hasNext()) {
                            JSONObject resource = (JSONObject) it.next();
                            CKANLayerDataHandler.addLayersFromCKANJSONResource(resource, isPrivateResource, connection, organization);
                        }
                    } catch (Exception e) {
                        LOG.error("Unable to parse CKAN Layer API JSON! " + e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to read CKAN Layer API JSON String! " + e);
        }
    }
}
