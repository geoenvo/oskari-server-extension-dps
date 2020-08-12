package wbidp.oskari.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;

import javax.xml.stream.XMLStreamException;

import fi.nls.oskari.geoserver.GeoserverPopulator;
import fi.nls.oskari.map.myplaces.service.GeoServerProxyService;
import fi.nls.oskari.util.PropertyUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.simple.JSONObject;

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
import wbidp.oskari.helpers.FileHelper;
import wbidp.oskari.helpers.LayerHelper;
import wbidp.oskari.helpers.LayerJSONHelper;
import wbidp.oskari.parser.CKANDataParser;
import wbidp.oskari.parser.CKANOrganization;

public class CKANLayerDataHandler {
    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);
    private static final DataProviderService DATA_PROVIDER_SERVICE = new DataProviderServiceMybatisImpl();

    /**
     * Parses the given CKAN resource JSON, gets capabilities from the supported APIs
     * and adds all layers to Oskari.
     * 
     * @param resource CKAN data resource as JSON.
     * @param isPrivateResource indicates if the resource is private.
     * @param connection the database connection to Oskari.
     * @throws ServiceException
     */
    public static void addLayersFromCKANJSONResource(JSONObject resource, boolean isPrivateResource, Connection connection,
                                                     CKANOrganization organization) throws ServiceException {
        CapabilitiesCacheService capabilitiesService = OskariComponentManager.getComponentOfType(CapabilitiesCacheService.class);

        String url = (String) resource.get("url");
        url = url.contains("?") ? url.split("\\?")[0] : url;
        String format = (resource.get("format") != null) ? (String) resource.get("format") : "No format defined!";
        String user = (resource.get("username") != null) ? (String) resource.get("username") : "";
        String pw = (resource.get("password") != null) ? (String) resource.get("password") : "";
        String currentCrs = "EPSG:3067";

        switch ((format).toLowerCase()) {
            case "wms":
                addWMSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization);
                break;
            case "wmts":
                addWMTSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization);
                break;
            case "wfs":
                addWFSLayers(resource, connection, url, user, pw, currentCrs, isPrivateResource, organization);
                break;
            case "esri rest":
                // TODO: Add support for Esri REST
                break;
            case "shp":
                addShpFileAsLayer(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization);
                break;
            case "tiff":
                // TODO: Add support for GeoTIFF
                break;
            default:
                LOG.info(String.format("No match for data format (%s).", format));
        }
    }

    private static void addWMSLayers(JSONObject resource, Connection connection,
                                     CapabilitiesCacheService capabilitiesService, String url, String user, String pw,
                                     String currentCrs, boolean isPrivateResource, CKANOrganization organization) throws ServiceException {
        // Get/Set WMS API version, defaults to 1.3.0
        // Supported WMS versions in Oskari: 1.1.1, 1.3.0
        String version = (resource.get("version") != null) ? (String) resource.get("version") : "1.3.0";

        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WMS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWMSCapabilities.getWMSCapabilities(capabilitiesService, url, user, pw, version,
                currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WMS, isPrivateResource, mainGroupName, organization);
    }

    private static void addWMTSLayers(JSONObject resource, Connection connection,
                                      CapabilitiesCacheService capabilitiesService, String url, String user, String pw,
                                      String currentCrs, boolean isPrivateResource, CKANOrganization organization) throws ServiceException {
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
            addLayers(connection, url, user, pw, currentCrs, resultJSON, OskariLayer.TYPE_WMTS, isPrivateResource, mainGroupName, organization);
        } catch (IllegalArgumentException | XMLStreamException e) {
            LOG.error("Error while parsing WMTS capabilities. " + e);
        }
    }

    private static void addWFSLayers(JSONObject resource, Connection connection, String url, String user, String pw,
                                     String currentCrs, boolean isPrivateResource, CKANOrganization organization) throws ServiceException {
        // Get/Set WFS API version, defaults to 1.1.0
        // Supported WFS versions in Oskari: 1.1.0, 2.0.0, 3.0.0
        String version = (resource.get("version") != null) ? (String) resource.get("version") : "1.1.0";
        
        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WFS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWFSCapabilities.getWFSCapabilities(url, user, pw, version, currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WFS, isPrivateResource, mainGroupName, organization);
    }

    private static void addLayers(Connection connection, String url, String user, String pw, String currentCrs,
                                  org.json.JSONObject json, String layerType, boolean isPrivateResource, String mainGroupName,
                                  CKANOrganization organization) {
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
            org.json.JSONObject layerPermissions = LayerJSONHelper.getRolePermissionsJSONForRoleAndAdmin(organization.getName());
            // If resource is marked private in CKAN, only allow admins to see it!
            if (isPrivateResource) {
                layerPermissions = LayerJSONHelper.getAdminPermissionsJSON();
            }
            // TODO: Define layer attributes here, if needed
            org.json.JSONObject layerAttributes = new org.json.JSONObject();
            for (int i = 0; i < layers.length(); i++) {
                String layerName = layers.getJSONObject(i).getString("layerName");
                String layerTitle = layers.getJSONObject(i).getString("title");
                layersToAdd.put(LayerHelper.generateLayerJSON(layerType, url, layerName, mainGroupName,
                        LayerJSONHelper.getLocale(layerTitle, layerTitle, layerTitle), false, -1,
                        null, -1.0, -1.0, null, null, null, null, null, null, false, 0, currentCrs, LayerHelper.VERSION_WMS130,
                        user, pw, null, null, layerPermissions, layerAttributes));
            }
            int addedCount = LayerHelper.addLayers(layersToAdd, LayerHelper.getLayerGroups(groupId), true, connection);
            LOG.debug(String.format("Added %d layer(s) from %s to group %s.", addedCount, url, mainGroupName));
        } catch (JSONException e) {
            LOG.error("Unable to read layer data from json! " + e);
        }
    }

    private static void addShpFileAsLayer(JSONObject resource, Connection connection, CapabilitiesCacheService capabilitiesService,
                                          String url, String user, String pw, String currentCrs, boolean isPrivateResource,
                                          CKANOrganization organization) throws ServiceException {
        String dataDestDir = "/tmp";
        String gsUrl  = PropertyUtil.get("geoserver.url", "http://localhost:8080/geoserver");
        boolean publishWFS = PropertyUtil.getOptional("ckan.integration.shp.publish.wfs", true);
        ResponseHandler<String> responseHandler = LayerHelper.generateGeoServerResponseHandler();

        String workspaceName = organization.getName().replaceAll("[^a-zA-Z0-9]+", "_");
        String storeName = "shp_store";
        if (resource.get("name") != null) {
            storeName = ((String) resource.get("name")).replaceAll("[^a-zA-Z0-9]+", "_");
        }

        try {
            createGeoServerWorkspace(workspaceName, responseHandler, gsUrl);

            LOG.info(String.format("Getting shp file from: %s", url));
            String filename = url.substring(url.lastIndexOf("/") + 1);
            String dataPath = String.format("%s/%s", dataDestDir, filename);
            InputStream in = new URL(url).openStream();
            Files.copy(in, Paths.get(dataPath), StandardCopyOption.REPLACE_EXISTING);
            File shpFileZip = new File(dataPath);
            FileHelper.unzipArchive(dataDestDir, new File(dataPath));

            uploadShpFileToGeoServer(workspaceName, storeName, responseHandler, gsUrl, shpFileZip);

            String wmsUrl = String.format("%s/%s/wms", gsUrl, workspaceName);
            resource.put("name", String.format("%s (local shp data)", organization.getTitle()));
            addWMSLayers(resource, connection, capabilitiesService, wmsUrl, user, pw, currentCrs, isPrivateResource, organization);
            if (publishWFS) {
                String wfsUrl = String.format("%s/%s/wfs", gsUrl, workspaceName);
                addWFSLayers(resource, connection, wfsUrl, user, pw, currentCrs, isPrivateResource, organization);
            }
        } catch (Exception e) {
            LOG.error("Error while adding shapefile! " + e);
        }
    }

    private static void createGeoServerWorkspace(String workspaceName, ResponseHandler<String> responseHandler,
                                                 String gsUrl) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpPost httpPost = new HttpPost(String.format("%s/rest/workspaces", gsUrl));
        httpPost.setEntity(new StringEntity(String.format("<workspace><name>%s</name></workspace>", workspaceName), ContentType.create("text/xml")));
        httpPost.setHeader("Authorization", authHeaderValue);

        LOG.info(String.format("Creating GeoServer workspace (request: %s) ", httpPost.getRequestLine()));

        String responseBody = httpclient.execute(httpPost, responseHandler);
        LOG.info(String.format("Got response from GeoServer: %s", responseBody.toString()));
    }

    private static void uploadShpFileToGeoServer(String workspaceName, String storeName, ResponseHandler<String> responseHandler,
                                                 String gsUrl, File shpFileZip) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpPut httpPut = new HttpPut(String.format("%s/rest/workspaces/%s/datastores/%s/file.shp", gsUrl, workspaceName, storeName));
        httpPut.setEntity(new FileEntity(shpFileZip, ContentType.create("application/zip")));
        httpPut.setHeader("Authorization", authHeaderValue);

        LOG.info(String.format("Uploading shp to GeoServer (request: %s) ", httpPut.getRequestLine()));

        String responseBody = httpclient.execute(httpPut, responseHandler);
        LOG.info(String.format("Got response from GeoServer: %s", responseBody.toString()));
    }
}