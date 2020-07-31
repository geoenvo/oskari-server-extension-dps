package wbidp.oskari.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import fi.nls.oskari.domain.map.DataProvider;
import fi.nls.oskari.domain.map.OskariLayer;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.layer.DataProviderService;
import fi.nls.oskari.map.layer.DataProviderServiceMybatisImpl;
import fi.nls.oskari.service.OskariComponentManager;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.service.capabilities.CapabilitiesCacheService;
import fi.nls.oskari.service.capabilities.OskariLayerCapabilities;
import fi.nls.oskari.util.JSONHelper;
import fi.nls.oskari.wfs.GetGtWFSCapabilities;
import fi.nls.oskari.wms.GetGtWMSCapabilities;
import fi.nls.oskari.wmts.WMTSCapabilitiesParser;
import fi.nls.oskari.wmts.domain.WMTSCapabilities;
import wbidp.oskari.helpers.LayerHelper;
import wbidp.oskari.helpers.LayerJSONHelper;

public class CKANDataParser {

    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);

    private static final DataProviderService DATA_PROVIDER_SERVICE = new DataProviderServiceMybatisImpl();

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
        String CKANOrgUuid = (String) CKANOrgJSON.get("id");
        JSONArray CKANOrgUsersJSON = (JSONArray) CKANOrgJSON.get("users");
        org.setName(CKANOrgName);
        org.setUuid(CKANOrgUuid);
        Iterator it = CKANOrgUsersJSON.iterator();
        while (it.hasNext()) {
            JSONObject CKANUserJSON = (JSONObject) it.next();
            CKANUser user = getUserFromJSON(CKANUserJSON);
            org.addUser(user);
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

                        Iterator it = resources.iterator();
                        while (it.hasNext()) {
                            JSONObject resource = (JSONObject) it.next();
                            addLayersFromJSONResource(resource, isPrivateResource, connection);
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

    private static void addLayersFromJSONResource(JSONObject resource, boolean isPrivateResource, Connection connection) throws ServiceException {
        CapabilitiesCacheService capabilitiesService = OskariComponentManager
                .getComponentOfType(CapabilitiesCacheService.class);

        String url = (String) resource.get("url");
        url = url.contains("?") ? url.split("\\?")[0] : url;
        String format = (resource.get("format") != null) ? (String) resource.get("format") : "No format defined!";
        String user = (resource.get("username") != null) ? (String) resource.get("username") : "";
        String pw = (resource.get("password") != null) ? (String) resource.get("password") : "";
        String currentCrs = "EPSG:3067";

        switch ((format).toLowerCase()) {
            case "wms":
                addWMSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource);
                break;
            case "wmts":
                addWMTSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource);
                break;
            case "wfs":
                addWFSLayers(resource, connection, url, user, pw, currentCrs, isPrivateResource);
                break;
            case "esri rest":
                // TODO: Add support for Esri REST
                break;
            default:
                LOG.info(String.format("No match for data format (%s).", format));
        }
    }

    private static void addWMSLayers(JSONObject resource, Connection connection,
            CapabilitiesCacheService capabilitiesService, String url, String user, String pw, 
            String currentCrs, boolean isPrivateResource) throws ServiceException {
        // Get/Set WMS API version, defaults to 1.3.0
        // Supported WMS versions in Oskari: 1.1.1, 1.3.0
        String version = (resource.get("version") != null) ? (String) resource.get("version") : "1.3.0";

        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WMS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWMSCapabilities.getWMSCapabilities(capabilitiesService, url, user, pw, version,
                currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WMS, isPrivateResource, mainGroupName);
    }

    private static void addWMTSLayers(JSONObject resource, Connection connection,
            CapabilitiesCacheService capabilitiesService, String url, String user, String pw, 
            String currentCrs, boolean isPrivateResource) throws ServiceException {
        String version = (resource.get("version") != null) ? (String) resource.get("version") : "1.0.0";

        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";

        LOG.debug(String.format("Getting WMTS capabilities from %s (version %s)", url, version));
        OskariLayerCapabilities caps = capabilitiesService.getCapabilities(url, OskariLayer.TYPE_WMTS, version, user, pw);
        String capabilitiesXML = caps.getData();
        WMTSCapabilities wmtsCaps;
        try {
            wmtsCaps = WMTSCapabilitiesParser.parseCapabilities(capabilitiesXML);
            if (caps.getId() == null) {
                capabilitiesService.save(caps);
            }
            org.json.JSONObject resultJSON = WMTSCapabilitiesParser.asJSON(wmtsCaps, url, currentCrs);
            JSONHelper.putValue(resultJSON, "xml", capabilitiesXML);
            addLayers(connection, url, user, pw, currentCrs, resultJSON, OskariLayer.TYPE_WMTS, isPrivateResource, mainGroupName);
        } catch (IllegalArgumentException | XMLStreamException e) {
            LOG.error("Error while parsing WMTS capabilities. " + e);
        }
    }

    private static void addWFSLayers(JSONObject resource, Connection connection, String url, String user, String pw, 
            String currentCrs, boolean isPrivateResource) throws ServiceException {
        // Get/Set WFS API version, defaults to 1.1.0
        // Supported WFS versions in Oskari: 1.1.0, 2.0.0, 3.0.0
        String version = (resource.get("version") != null) ? (String) resource.get("version") : "1.1.0";
        
        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WFS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWFSCapabilities.getWFSCapabilities(url, user, pw, version, currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WFS, isPrivateResource, mainGroupName);
    }

    private static void addLayers(Connection connection, String url, String user, String pw, String currentCrs,
            org.json.JSONObject json, String layerType, boolean isPrivateResource, String mainGroupName) {
        try {
            mainGroupName = json.has("title") ? json.getString("title") : mainGroupName;
            org.json.JSONObject locale = LayerJSONHelper.getLocale(mainGroupName, mainGroupName, mainGroupName);
            DataProvider dp = DATA_PROVIDER_SERVICE.findByName(mainGroupName);
            if (dp == null) {
                dp = new DataProvider();
                dp.setLocale(locale);
                DATA_PROVIDER_SERVICE.insert(dp);
            }
            
            int groupId = LayerHelper.addMainGroup(mainGroupName, mainGroupName, mainGroupName);
            org.json.JSONArray layers = json.getJSONArray("layers");
            org.json.JSONArray layersToAdd = new org.json.JSONArray();
            
            // TODO: Define permissions JSON according to CKAN roles!
            org.json.JSONObject layerPermissions = LayerJSONHelper.getRolePermissionsJSON();
            
            // If resource is marked private in CKAN, only allow admins to see it!
            if (isPrivateResource) {
                layerPermissions = LayerJSONHelper.getAdminPermissionsJSON();
            }
            
            for (int i = 0; i < layers.length(); i++) {
                String layerName = layers.getJSONObject(i).getString("layerName");
                String layerTitle = layers.getJSONObject(i).getString("title");
                layersToAdd.put(LayerHelper.generateLayerJSON(layerType, url, layerName, mainGroupName,
                        LayerJSONHelper.getLocale(layerTitle, layerTitle, layerTitle), false, -1,
                        null, -1.0, -1.0, null, null, null, null, null, null, false, 0, currentCrs, LayerHelper.VERSION_WMS130,
                        user, pw, null, null, layerPermissions, LayerJSONHelper.getForceProxyAttributeJSON()));
            }
            
            int addedCount = LayerHelper.addLayers(layersToAdd, LayerHelper.getLayerGroups(groupId), true, connection);
            LOG.debug(String.format("Added %d layer(s) from %s to group %s.", addedCount, url, mainGroupName));
        } catch (JSONException e) {
            LOG.error("Unable to read layer data from json! " + e);
        }
    }
}
