package wbidp.oskari.util;

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
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.wfs.GetGtWFSCapabilities;
import fi.nls.oskari.wms.GetGtWMSCapabilities;
import fi.nls.oskari.wmts.WMTSCapabilitiesParser;
import fi.nls.oskari.wmts.domain.WMTSCapabilities;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.simple.JSONObject;
import wbidp.oskari.helpers.FileHelper;
import wbidp.oskari.helpers.LayerHelper;
import wbidp.oskari.helpers.LayerJSONHelper;
import wbidp.oskari.parser.CKANDataParser;
import wbidp.oskari.parser.CKANOrganization;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CKANLayerDataHandler {
    private static final Logger LOG = LogFactory.getLogger(CKANDataParser.class);
    private static final DataProviderService DATA_PROVIDER_SERVICE = new DataProviderServiceMybatisImpl();

    private static String dataDestDir = "/tmp";
    private static String gsUrl  = PropertyUtil.get("geoserver.url", "http://localhost:8080/geoserver");
    private static ResponseHandler<String> responseHandler = LayerHelper.generateGeoServerResponseHandler();

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

        boolean updateNeeded = checkResourceStatus(resource, connection);

        if (updateNeeded) {
            String url = (String) resource.get("url");
            url = url.contains("?") ? url.split("\\?")[0] : url;
            String format = (resource.get("format") != null) ? (String) resource.get("format") : "No format defined!";
            String user = (resource.get("username") != null) ? (String) resource.get("username") : "";
            String pw = (resource.get("password") != null) ? (String) resource.get("password") : "";
            String currentCrs = "EPSG:3067";

            switch ((format).toLowerCase()) {
                case "wms":
                    addWMSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization, null, false);
                    break;
                case "wmts":
                    addWMTSLayers(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization, false);
                    break;
                case "wfs":
                    addWFSLayers(resource, connection, url, user, pw, currentCrs, isPrivateResource, organization, null, false);
                    break;
                case "esri rest":
                    // TODO: Add support for Esri REST
                    break;
                case "shp":
                    addShpFileAsLayer(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization);
                    break;
                case "tif":
                case "tiff":
                    addGeoTIFFAsLayer(resource, connection, capabilitiesService, url, user, pw, currentCrs, isPrivateResource, organization);
                    break;
                default:
                    LOG.info(String.format("No match for resource data format (%s).", format));
            }
        } else {
            LOG.info(String.format("Resource %s is up-to-date, skipping...", resource.get("name")));
        }
    }

    /**
     * Update the `wfs_url` and `wms_url` extra fields of the layer's associated
     * resource via the `resource_patch` API call in CKAN.
     * 
     * @param resource a CKAN resource as JSON object (each object of the resources array in /api/action/package_show CKAN API call).
     * @param wfsUrl the WMS URL of layer in GeoServer.
     * @param wmsUrl the WFS URL of layer in GeoServer.
     * @param apiKey the CKAN sysadmin API key to use for the API call.
     */
    private static void updateUrlToCKAN(JSONObject resource, String wfsUrl, String wmsUrl, String apiKey) {
        try {
            String uuid = (String) resource.get("id");
            String url = (String) resource.get("url");
            // get CKAN domain from resource URL
            URL resourceUrl = new URL(url);
            String protocol = resourceUrl.getProtocol();
            String authority = resourceUrl.getAuthority();
            String resourcePatchEndpoint = "api/action/resource_patch";
            String apiUrl = String.format("%s://%s/%s", protocol, authority, resourcePatchEndpoint);
            LOG.info("Start Update URL to CKAN");
            LOG.info(String.format("CKAN Resource UUID: %s", uuid));
            LOG.info(String.format("CKAN Resource URL: %s", url));
            LOG.info(String.format("CKAN API URL: %s", apiUrl));
            LOG.info(String.format("GeoServer WFS URL: %s", wfsUrl));
            LOG.info(String.format("GeoServer WMS URL: %s", wmsUrl));
            // example request with curl
            // curl -X POST http://ckan.url/api/action/resource_patch -H "Authorization: the-sysadmin-api-key" -d '{"id": "the-resource-file-uuid", "wms_url": "http://the.wms.url", "wfs_url": "http://the.wfs.url"}'
            // only make API call API key is provided and if WFS URL or WMS URL is set
            if (apiKey != null && (wfsUrl != null || wmsUrl != null)) {
                CloseableHttpClient httpclient = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost(apiUrl);
                JSONObject json = new JSONObject();
                json.put("id", uuid);
                if (wfsUrl != null) {
                    json.put("wfs_url", wfsUrl);
                }
                if (wmsUrl != null) {
                    json.put("wms_url", wmsUrl);
                }
                StringEntity params = new StringEntity(json.toString());
                httpPost.setEntity(params);
                httpPost.setHeader("content-type", "application/json");
                httpPost.setHeader("Authorization", apiKey);
                LOG.info(String.format("Update URL to CKAN (request: %s) ", httpPost.getRequestLine()));
                httpclient.execute(httpPost);
            } else {
                LOG.info("Skip updating URL to CKAN due to the following reasons:");
                if (apiKey == null) {
                    LOG.info("CKAN API Key not provided.");
                }
                if (wfsUrl == null) {
                    LOG.info("GeoServer WFS URL not provided.");
                }
                if (wmsUrl == null) {
                    LOG.info("GeoServer WMS URL not provided.");
                }
            }
            LOG.info("End Update URL to CKAN");
        } catch (Exception e) {
            LOG.error("Error when updating URL to CKAN! " + e);
        }
    }

    private static boolean checkResourceStatus(JSONObject resource, Connection connection) {
        boolean updateNeeded = false;
        String uuid = (String) resource.get("id");
        String lastModified = (resource.get("last_modified") != null) ? (String) resource.get("last_modified") : (String) resource.get("created");
        updateNeeded = resourceNeedsUpdate(connection, uuid, lastModified);

        return updateNeeded;
    }

    private static boolean resourceNeedsUpdate(Connection connection, String uuid, String lastModified) {
        LocalDateTime lastModifiedDate = LocalDateTime.parse(lastModified, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));
        LocalDateTime currentModifiedDate = null;

        String sql = "SELECT * FROM oskari_ckan_dataset_resource_log WHERE resource_uuid = '" + uuid + "';";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            LOG.debug("Executing:", ps.toString());
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                currentModifiedDate = rs.getTimestamp("last_modified").toLocalDateTime();
            }
        } catch (SQLException ex) {
            LOG.error("Error while checking resource." + ex.toString());
            return false;
        }

        if (currentModifiedDate != null && (currentModifiedDate.isEqual(lastModifiedDate) || currentModifiedDate.isAfter(lastModifiedDate))) {
            return false;
        }

        return true;
    }

    private static void addOrUpdateResourceInfo(JSONObject resource, Connection connection) {
        String uuid = (String) resource.get("id");
        String lastModified = (resource.get("last_modified") != null) ? (String) resource.get("last_modified") : (String) resource.get("created");

        String sql = "INSERT INTO \"oskari_ckan_dataset_resource_log\" (last_modified, resource_uuid)\n" +
                "VALUES\n" +
                "('" + lastModified + "', '" + uuid + "')\n" +
                "ON CONFLICT (resource_uuid) DO UPDATE SET last_modified = '" + lastModified + "';";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            LOG.debug("Executing:", ps.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.error("Error while updating Oskari CKAN resource log." + ex.toString());
        }
    }

    private static void addWMSLayers(JSONObject resource, Connection connection,
                                     CapabilitiesCacheService capabilitiesService, String url, String user, String pw,
                                     String currentCrs, boolean isPrivateResource, CKANOrganization organization, String overrideName, boolean addForceProxy) throws ServiceException {
        // Get/Set WMS API version, defaults to 1.3.0
        // Supported WMS versions in Oskari: 1.1.1, 1.3.0
        String version = "1.3.0";
        if ((resource.get("version") != null) && !((String) resource.get("version")).isEmpty()) {
            version = (String) resource.get("version");
        }

        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WMS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWMSCapabilities.getWMSCapabilities(capabilitiesService, url, user, pw, version,
                currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WMS, isPrivateResource, mainGroupName, organization, overrideName, addForceProxy);
        addOrUpdateResourceInfo(resource, connection);
    }

    private static void addWMTSLayers(JSONObject resource, Connection connection,
                                      CapabilitiesCacheService capabilitiesService, String url, String user, String pw,
                                      String currentCrs, boolean isPrivateResource, CKANOrganization organization, boolean addForceProxy) throws ServiceException {
        String version = "1.0.0";
        if ((resource.get("version") != null) && !((String) resource.get("version")).isEmpty()) {
            version = (String) resource.get("version");
        }

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
            addLayers(connection, url, user, pw, currentCrs, resultJSON, OskariLayer.TYPE_WMTS, isPrivateResource, mainGroupName, organization, null, addForceProxy);
            addOrUpdateResourceInfo(resource, connection);
        } catch (IllegalArgumentException | XMLStreamException e) {
            LOG.error("Error while parsing WMTS capabilities. " + e);
        }
    }

    private static void addWFSLayers(JSONObject resource, Connection connection, String url, String user, String pw,
                                     String currentCrs, boolean isPrivateResource, CKANOrganization organization, String overrideName, boolean addForceProxy) throws ServiceException {
        // Get/Set WFS API version, defaults to 1.1.0
        // Supported WFS versions in Oskari: 1.1.0, 2.0.0, 3.0.0
        String version = "1.1.0";
        if ((resource.get("version") != null) && !((String) resource.get("version")).isEmpty()) {
            version = (String) resource.get("version");
        }

        String mainGroupName = (resource.get("name") != null) ? (String) resource.get("name") : "Misc Layers";
        LOG.debug(String.format("Getting WFS capabilities from %s (version %s)", url, version));
        org.json.JSONObject json = GetGtWFSCapabilities.getWFSCapabilities(url, user, pw, version, currentCrs);
        addLayers(connection, url, user, pw, currentCrs, json, OskariLayer.TYPE_WFS, isPrivateResource, mainGroupName, organization, overrideName, addForceProxy);
        addOrUpdateResourceInfo(resource, connection);
    }

    private static void addLayers(Connection connection, String url, String user, String pw, String currentCrs,
                                  org.json.JSONObject json, String layerType, boolean isPrivateResource, String mainGroupName,
                                  CKANOrganization organization, String overrideName, boolean addForceProxy) {
        try {
            if (json.has("title") && !json.getString("title").isEmpty()) {
                mainGroupName = json.getString("title");
            }
            String forcedSRS = PropertyUtil.get("ckan.integration.ckanapi.layer.forcedsrs", "EPSG:3857");
            boolean useDefaultLocales = PropertyUtil.getOptional("ckan.integration.ckanapi.layer.defaultlocales", false);
            org.json.JSONObject locale = LayerJSONHelper.getIDPLocale(mainGroupName, mainGroupName);
            if (useDefaultLocales) {
                locale = LayerJSONHelper.getDefaultLocale(mainGroupName, mainGroupName, mainGroupName);
            }

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
                layerPermissions = LayerJSONHelper.getRolePermissionsJSONForPrivateResource(organization.getName());
            }

            org.json.JSONObject layerAttributes = new org.json.JSONObject();
            layerAttributes = LayerJSONHelper.getForcedSRSJSON(forcedSRS.split(","));

            if (addForceProxy) {
                LayerJSONHelper.addForceProxySetting(layerAttributes);
            }

            for (int i = 0; i < layers.length(); i++) {
                String layerName = layers.getJSONObject(i).getString("layerName");
                String layerTitle = layers.getJSONObject(i).getString("title");
                if (overrideName != null) {
                    layerTitle = overrideName;
                }

                org.json.JSONObject layerLocale = LayerJSONHelper.getIDPLocale(layerTitle, layerTitle);
                if (useDefaultLocales) {
                    layerLocale = LayerJSONHelper.getDefaultLocale(layerTitle, layerTitle, layerTitle);
                }

                layersToAdd.put(LayerHelper.generateLayerJSON(layerType, url, layerName, mainGroupName,
                        layerLocale, false, -1,
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
        boolean publishWFS = (resource.get("publish_wfs") != null && !((String)resource.get("publish_wfs")).equals("")) ? Boolean.valueOf((String)resource.get("publish_wfs")) : true;
        boolean removeSpacesFromShpName = PropertyUtil.getOptional("ckan.integration.ckanapi.shp.removespaces", false);
        boolean createResourceWorkspaces = PropertyUtil.getOptional("ckan.integration.ckanapi.shp.resourceworkspaces", true);
        boolean addForceProxy = PropertyUtil.getOptional("ckan.integration.ckanapi.shp.forceproxy", false);
        String sysAdminAPIKey = PropertyUtil.get("ckan.integration.ckanapi.sysadmin.apikey", null);

        String workspaceName = organization.getName().replaceAll("[^a-zA-Z0-9]+", "_");
        String storeName = "shp_store";
        if (resource.get("name") != null) {
            storeName = ((String) resource.get("name")).replaceAll("[^a-zA-Z0-9]+", "_");
        }

        if (createResourceWorkspaces) {
            workspaceName = String.format("%s_%s", workspaceName, storeName);
        }

        try {
            createGeoServerWorkspace(workspaceName, responseHandler, gsUrl);

            LOG.info(String.format("Getting shp file from: %s", url));
            String filename = url.substring(url.lastIndexOf("/") + 1);
            String dataFilePath = String.format("%s/%s", dataDestDir, filename);
            //InputStream in = new URL(url).openStream();

            URL fileUrl = new URL(url);
            HttpURLConnection URLConnection = (HttpURLConnection)fileUrl.openConnection();
            if (isPrivateResource && sysAdminAPIKey != null) {
                URLConnection.setRequestProperty("X-CKAN-API-Key", sysAdminAPIKey);
            }
            InputStream in = URLConnection.getInputStream();

            Files.copy(in, Paths.get(dataFilePath), StandardCopyOption.REPLACE_EXISTING);
            File shpFileZip = new File(dataFilePath);
            String resourceName = FileHelper.getFileNameFromZip(dataFilePath, "shp");

            if (removeSpacesFromShpName) {
                shpFileZip = FileHelper.renameFilesInZip(dataFilePath, new File(String.format("%s/%s", dataDestDir,
                filename.substring(0, filename.lastIndexOf('.')))), " ", "_");
            }

            deleteGeoServerStore("datastore", workspaceName, storeName, responseHandler, gsUrl);
            uploadShpFileToGeoServer(workspaceName, storeName, responseHandler, gsUrl, shpFileZip);
            uploadSldToGeoServer(gsUrl, new File(dataFilePath), workspaceName, FileHelper.getFileNameFromZip(dataFilePath, "shp"));

            String wmsUrl = String.format("%s/%s/wms", gsUrl, workspaceName);
            resource.put("name", String.format("%s (local data)", organization.getTitle()));
            if (publishWFS) {
                LOG.debug("Publishing uploaded shp also as WFS layer.");
                String wfsUrl = String.format("%s/%s/wfs", gsUrl, workspaceName);
                addWFSLayers(resource, connection, wfsUrl, user, pw, currentCrs, isPrivateResource, organization, resourceName, false);
                updateUrlToCKAN(resource, wfsUrl, null, sysAdminAPIKey);
            }
            addWMSLayers(resource, connection, capabilitiesService, wmsUrl, user, pw, currentCrs, isPrivateResource,
                    organization, resourceName, addForceProxy);
            updateUrlToCKAN(resource, null, wmsUrl, sysAdminAPIKey);
            addOrUpdateResourceInfo(resource, connection);
        } catch (Exception e) {
            LOG.error("Error while adding shapefile! " + e);
        }
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

    private static void uploadSldToGeoServer(String gsUrl, File styleFile, String workspaceName, String shpName) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpPost httpPost = new HttpPost(String.format("%s/rest/styles", gsUrl));
        httpPost.setEntity(new FileEntity(styleFile, ContentType.create("application/zip")));
        httpPost.setHeader("Authorization", authHeaderValue);

        // Make sure the shp (layer) name is encoded correctly to use in url.
        // NOTE! For some unknown reason, GeoServer seems to require double encoding spaces
        // (and probably other special characters) in layer names. That is why the shpName
        // is encoded twice here.
        // TODO: If possible, try to find a different solution for this at some point!
        shpName = URIUtil.encodeQuery(URIUtil.encodeQuery(shpName));

        LOG.info(String.format("Uploading style to GeoServer (request: %s) ", httpPost.getRequestLine()));

        HttpResponse response = client.execute(httpPost);

        String status = response.getStatusLine().toString();
        LOG.info(String.format("Got response from GeoServer: %s", status));
        String location = response.getFirstHeader("Location") != null ? response.getFirstHeader("Location").getValue() : null;
        String styleName = null;
        if (location != null) {
            styleName = URIUtil.decode(location.substring(location.lastIndexOf("/") + 1, location.length()));
        } else if (status.contains("403")) {
            styleName = StringUtils.substringBetween(status, "Style ", " already");
        }

        if (styleName != null && shpName != null) {
            HttpPut httpPut = new HttpPut(String.format("%s/rest/layers/%s:%s", gsUrl, workspaceName, shpName));
            httpPut.setEntity(new StringEntity(String.format("<layer><defaultStyle><name>%s</name></defaultStyle></layer>", styleName), ContentType.create("text/xml")));
            httpPut.setHeader("Authorization", authHeaderValue);
            LOG.info(String.format("Setting style (%s) for layer (%s:%s).", styleName, workspaceName, shpName));
            response = client.execute(httpPut);
            LOG.info(String.format("Got response from GeoServer: %s", response.getStatusLine().toString()));
        }
    }

    private static void addGeoTIFFAsLayer(JSONObject resource, Connection connection, CapabilitiesCacheService capabilitiesService,
                                          String url, String user, String pw, String currentCrs, boolean isPrivateResource,
                                          CKANOrganization organization) throws ServiceException {
        boolean createResourceWorkspaces = PropertyUtil.getOptional("ckan.integration.ckanapi.geotiff.resourceworkspaces", false);
        boolean addForceProxy = PropertyUtil.getOptional("ckan.integration.ckanapi.geotiff.forceproxy", false);
        String sysAdminAPIKey = PropertyUtil.get("ckan.integration.ckanapi.sysadmin.apikey", null);

        String workspaceName = organization.getName().replaceAll("[^a-zA-Z0-9]+", "_");
        String storeName = "geotiff_store";
        if (resource.get("name") != null) {
            storeName = ((String) resource.get("name")).replaceAll("[^a-zA-Z0-9]+", "_");
        }
        String resourceName = null;

        if (createResourceWorkspaces) {
            workspaceName = String.format("%s_%s", workspaceName, storeName);
        }

        try {
            createGeoServerWorkspace(workspaceName, responseHandler, gsUrl);

            LOG.info(String.format("Getting GeoTIFF file from: %s", url));
            String filename = url.substring(url.lastIndexOf("/") + 1);
            String dataFilePath = String.format("%s/%s", dataDestDir, filename);
            //InputStream in = new URL(url).openStream();

            URL fileUrl = new URL(url);
            HttpURLConnection URLConnection = (HttpURLConnection)fileUrl.openConnection();
            if (isPrivateResource && sysAdminAPIKey != null) {
                URLConnection.setRequestProperty("X-CKAN-API-Key", sysAdminAPIKey);
            }
            InputStream in = URLConnection.getInputStream();

            Files.copy(in, Paths.get(dataFilePath), StandardCopyOption.REPLACE_EXISTING);
            File tiffFile = new File(dataFilePath);

            deleteGeoServerStore("coveragestore", workspaceName, storeName, responseHandler, gsUrl);
            uploadGeoTIFFToGeoServer(workspaceName, storeName, responseHandler, gsUrl, tiffFile);

            String wmsUrl = String.format("%s/%s/ows?service=WMS", gsUrl, workspaceName);
            resource.put("name", String.format("%s (local data)", organization.getTitle()));
            addWMSLayers(resource, connection, capabilitiesService, wmsUrl, user, pw, currentCrs, isPrivateResource,
                    organization, resourceName, addForceProxy);
            addOrUpdateResourceInfo(resource, connection);
        } catch (Exception e) {
            LOG.error("Error while adding GeoTIFF! " + e);
        }
    }

    private static void uploadGeoTIFFToGeoServer(String workspaceName, String storeName, ResponseHandler<String> responseHandler,
                                                 String gsUrl, File tiffFile) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpPut httpPut = new HttpPut(String.format("%s/rest/workspaces/%s/coveragestores/%s/file.geotiff", gsUrl, workspaceName, storeName));
        httpPut.setEntity(new FileEntity(tiffFile, ContentType.create("image/tiff")));
        httpPut.setHeader("Authorization", authHeaderValue);

        LOG.info(String.format("Uploading GeoTIFF to GeoServer (request: %s) ", httpPut.getRequestLine()));

        String responseBody = httpclient.execute(httpPut, responseHandler);
        LOG.info(String.format("Got response from GeoServer: %s", responseBody.toString()));
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

    private static void deleteGeoServerWorkspace(String workspaceName, ResponseHandler<String> responseHandler,
                                                 String gsUrl) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpDelete httpDelete = new HttpDelete(String.format("%s/rest/workspaces/%s?recurse=true", gsUrl, workspaceName));
        httpDelete.setHeader("Authorization", authHeaderValue);

        LOG.info(String.format("Deleting GeoServer workspace (request: %s) ", httpDelete.getRequestLine()));

        String responseBody = httpclient.execute(httpDelete, responseHandler);
        LOG.info(String.format("Got response from GeoServer: %s", responseBody.toString()));
    }

    private static void deleteGeoServerStore(String storeType, String workspaceName, String storeName, ResponseHandler<String> responseHandler,
                                                 String gsUrl) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authHeaderValue = LayerHelper.generateGeoServerAuthHeader();
        HttpDelete httpDelete = new HttpDelete(String.format("%s/rest/workspaces/%s/%ss/%s?recurse=true", gsUrl, workspaceName, storeType, storeName));
        httpDelete.setHeader("Authorization", authHeaderValue);

        LOG.info(String.format("Deleting GeoServer %s (request: %s) ", storeType, httpDelete.getRequestLine()));

        String responseBody = httpclient.execute(httpDelete, responseHandler);
        LOG.info(String.format("Got response from GeoServer: %s", responseBody.toString()));
    }
}