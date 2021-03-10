package wbidp.oskari.helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class LayerJSONHelper {

    public static JSONObject getDefaultLocale(final String fi, final String en, final String sv) throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject fiJSON = new JSONObject();
        fiJSON.put("name", fi);
        JSONObject enJSON = new JSONObject();
        enJSON.put("name", en);
        JSONObject svJSON = new JSONObject();
        svJSON.put("name", sv);
        json.put("fi", fiJSON);
        json.put("en", enJSON);
        json.put("sv", svJSON);
        return json;
    }

    public static JSONObject getIDPLocale(final String in, final String en) throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject inJSON = new JSONObject();
        inJSON.put("name", in);
        JSONObject enJSON = new JSONObject();
        enJSON.put("name", en);
        json.put("in", inJSON);
        json.put("en", enJSON);
        return json;
    }

    public static JSONObject getAttributesJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("forceProxy", true);
        json.put("geometry", "GEOM");
        return json;
    }

    public static JSONObject getForceProxyAttributeJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("forceProxy", true);
        return json;
    }

    public static JSONObject getForcedSRSJSON() throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray forcedSRS = new JSONArray();
        forcedSRS.put("EPSG:3857");
        json.put("forcedSRS", forcedSRS);
        return json;
    }

    public static JSONObject getForcedSRSJSON(String[] crsArray) throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray forcedSRS = new JSONArray();
        Arrays.stream(crsArray).forEach(crs -> forcedSRS.put(crs));
        json.put("forcedSRS", forcedSRS);
        return json;
    }

    public static JSONObject addForceProxySetting(JSONObject json) throws JSONException {
        json.put("forceProxy", true);
        return json;
    }

    public static JSONObject getRolePermissionsJSONForRoleAndAdmin(String roleName) throws JSONException {
        JSONObject json = new JSONObject();

        JSONArray adminRights = new JSONArray();
        adminRights.put("PUBLISH");
        adminRights.put("VIEW_LAYER");
        adminRights.put("VIEW_PUBLISHED");
        adminRights.put("EDIT_LAYER_CONTENT");
        adminRights.put("DOWNLOAD");
        json.put("Admin", adminRights);

        JSONArray roleRights = new JSONArray();
        roleRights.put("PUBLISH");
        roleRights.put("VIEW_LAYER");
        roleRights.put("VIEW_PUBLISHED");
        roleRights.put("DOWNLOAD");
        json.put(roleName, roleRights);

        JSONArray guestRights = new JSONArray();
        guestRights.put("VIEW_LAYER");
        guestRights.put("VIEW_PUBLISHED");
        json.put("Guest", guestRights);

        return json;
    }

    public static JSONObject getRolePermissionsJSONForPrivateResource(String roleName) throws JSONException {
        JSONObject json = new JSONObject();

        JSONArray adminRights = new JSONArray();
        adminRights.put("PUBLISH");
        adminRights.put("VIEW_LAYER");
        adminRights.put("VIEW_PUBLISHED");
        adminRights.put("EDIT_LAYER_CONTENT");
        adminRights.put("DOWNLOAD");
        json.put("Admin", adminRights);

        JSONArray roleRights = new JSONArray();
        roleRights.put("PUBLISH");
        roleRights.put("VIEW_LAYER");
        roleRights.put("VIEW_PUBLISHED");
        roleRights.put("DOWNLOAD");
        json.put(roleName, roleRights);

        return json;
    }

    public static JSONObject getRolePermissionsJSON() throws JSONException {
        JSONObject json = new JSONObject();

        JSONArray adminRights = new JSONArray();
        adminRights.put("PUBLISH");
        adminRights.put("VIEW_LAYER");
        adminRights.put("VIEW_PUBLISHED");
        adminRights.put("EDIT_LAYER_CONTENT");
        adminRights.put("DOWNLOAD");
        json.put("Admin", adminRights);

        JSONArray userRights = new JSONArray();
        userRights.put("PUBLISH");
        userRights.put("VIEW_LAYER");
        userRights.put("VIEW_PUBLISHED");
        userRights.put("DOWNLOAD");
        json.put("User", userRights);

        JSONArray guestRights = new JSONArray();
        guestRights.put("VIEW_LAYER");
        guestRights.put("VIEW_PUBLISHED");
        json.put("Guest", guestRights);

        return json;
    }

    public static JSONObject getAdminPermissionsJSON() throws JSONException {
        JSONObject json = new JSONObject();

        JSONArray adminRights = new JSONArray();
        adminRights.put("PUBLISH");
        adminRights.put("VIEW_LAYER");
        adminRights.put("VIEW_PUBLISHED");
        adminRights.put("EDIT_LAYER_CONTENT");
        adminRights.put("DOWNLOAD");
        json.put("Admin", adminRights);

        return json;
    }

    public static JSONObject getCroppingLayersAttributesJSON(final String uniqueColumn) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("forceProxy", true);
        json.put("cropping", true);
        json.put("geometryColumn", "STRING");
        json.put("unique", uniqueColumn);
        json.put("geometry", "GEOM");

        return json;
    }
}