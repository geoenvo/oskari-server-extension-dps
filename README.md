# Oskari server-extension for WB IDP - Integrated Data Portal

This is a server-extension for WB IDP - Integrated Data Portal

## Modifying the initial application setup:
 
Initial content and configuration on the database is created with Flyway-scripts under:
 - server-extension/src/main/java/flyway/wbidp
 
To add necessary stuff to database you might need to run these manually to a fresh Oskari database
 - server-extension/src/main/resources/sql/

These migrations are run in version order and changing them will change the application that is created on an empty database.

Note that this line in oskari-ext.properties will be used to find the scripts (add wbidp) e.g.

    db.additional.modules=myplaces, userlayer, wbidp

Compile with:

    mvn clean install
    
Replace oskari-map.war under {jetty.home}/webapps/ with the one created under webapp-map/target 

## Settings related to CKAN Integration

IDP makes it possible to share/synchronize user credentials, groups (= organizations in CKAN) and spatial dataset resources between CKAN and Oskari.
This feature needs some preparing in CKAN and also some settings in Oskari for the synchronization to work. This section describes the needed steps and settings for the integration.

#### What is currently possible to synchronize?

 * Organizations (groups in Oskari)
    * Organizations are mapped as groups in Oskari
 * User accounts
    * Usernames, passwords and groups (organizations in CKAN) the user belongs to
    * IDP Oskari supports using CKAN hashed passwords
 * Spatial dataset resources
   * Supported API's: WMS, WMTS and WFS
   * Supported data formats: SHP and GeoTIFF (spatial data is first published to the local GeoServer)
   * Published layer rights are set according to the organizations in CKAN

#### Preparing CKAN for the integration

First, you need to setup a dump process for the data you want to synchronize to Oskari. For this to work, you need to dump CKAN user accounts, organization data and public datasets to the supported jsonl-format.

For more information on how to achieve this, please refer to https://docs.ckan.org/en/2.9/maintaining/database-management.html#database-management.

We recommend configuring a scheduled process on server side that is in sync with the scheduled Oskari task.

#### Preparing Oskari for the integration

To support using CKAN's hashed passwords with Oskari, you need to change the used UserService in **oskari-ext.properties** (replace the existing setting!):

    oskari.user.service=wbidp.oskari.util.DatabaseUserServiceCKAN

To automate the process of transferring data between CKAN and Oskari (users, groups and layers), you need to add e.g. these settings to **oskari-ext.properties**:

    ##################################
    # CKAN-Oskari integration setup
    ##################################
    
    oskari.scheduler.job.SynchronizeUserDataJob.cronLine=0 * * * * ?               # MANDATORY for group/user sync! Define the interval for user/organization sync.
    oskari.scheduler.job.SynchronizeLayerDataJob.cronLine=0 * * * * ?              # MANDATORY for spatial data sync! Define the interval for spatial resource sync.
    ckan.integration.ckanapi.dump.organizations=/tmp/ckanorgsdump.jsonl            # MANDATORY for group sync! Define the location for the user account dump file.
    ckan.integration.ckanapi.dump.users=/tmp/ckanusersdump.jsonl                   # MANDATORY for user sync! Define the location for the organization dump file.
    ckan.integration.ckanapi.dump.datasets=/tmp/ckandatasetsdump.jsonl             # MANDATORY for spatial data sync! Define the location for the spatial dataset dump file.
    ckan.integration.ckanapi.dump.datasets.secondary=/tmp/ckandatasetsdump2.jsonl  # OPTIONAL! Define the location for the secondary spatial dataset dump file. Can be used if e.g. public and private datasets are dumped to separate dump files.
    ckan.integration.ckanapi.sysadmin.apikey=SYSADMIN_API_KEY                      # OPTIONAL! Define a CKAN sysadmin API key if private datasets are used.
    
    ckan.integration.ckanapi.shp.resourceworkspaces=true                # OPTIONAL! Define (true/false) if workspaces in GeoServer are resource specific. Recommended option: true
    ckan.integration.ckanapi.shp.forceproxy=true                        # OPTIONAL! Define (true/false) if forceProxy-attribute will be set for SHP-based layers.
    ckan.integration.ckanapi.geotiff.forceproxy=true                    # OPTIONAL! Define (true/false) if forceProxy-attribute will be set for GeoTIFF-based layers.
    
    ckan.integration.db.url=jdbc:postgresql://localhost:5432/ckan       # NOT NEEDED! Reserved for possible future use.
    ckan.integration.db.username=oskari                                 # NOT NEEDED! Reserved for possible future use.
    ckan.integration.db.password=oskari19                               # NOT NEEDED! Reserved for possible future use.

## Settings related to Download Basket

To enable Download Basket functionality you need to add e.g. these settings to **oskari-ext.properties**:

    ##################################
    # Download bundle
    ##################################
    
    oskari.wfs.download.folder.name=/data/downloads
    oskari.wfs.download.normal.way.downloads=rectangle
    oskari.wfs.download.smtp.host=localhost
    oskari.wfs.download.smtp.port=25
    oskari.wfs.download.email.from=wbidp@wbidp.com
    oskari.wfs.download.email.subject=Download service
    oskari.wfs.download.email.header=
    
    oskari.wfs.download.email.message=The data you ordered can be downloaded at:\n
    oskari.wfs.download.email.footer={LINEBREAK}The download address is valid for 10 days.
    
    oskari.wfs.download.email.message.datadescription={LINEBREAK}Do not reply to this email.{LINEBREAK}{LINEBREAK}
    oskari.wfs.download.email.datadescription_link=
    oskari.wfs.download.link.url.prefix=https://localhost:8080/downloads/
    oskari.wfs.service.url=http://geo.stat.fi/geoserver/wfs
    oskari.wfs.error.message=Error in download service service, please try again later.
    oskari.wfs.download.error.report.support.email=wbidp@wbidp.com
    oskari.wfs.download.error.report.subject=Error in download service

## Settings related to Content Editor

To enable Content Editor functionality you need to add e.g. these settings to **oskari-ext.properties**:

    ##################################
    # Content editor bundle
    ##################################

    permission.types = EDIT_LAYER_CONTENT
    permission.EDIT_LAYER_CONTENT.name.fi=Edit layer
    permission.EDIT_LAYER_CONTENT.name.en=Edit layer
    permission.EDIT_LAYER_CONTENT.name.id=Lapisan edi

Also, you need to add the bundle to the dynamic bundles list and modify permissions e.g.

    # bundles that are added on runtime to view if user has one of configured role
    actionhandler.GetAppSetup.dynamic.bundles = admin-hierarchical-layerlist, admin-layereditor, admin-layerselector, admin-layerrights, admin-users, admin, content-editor
    actionhandler.GetAppSetup.dynamic.bundle.content-editor.roles = Admin

## License

This work is dual-licensed under MIT and [EUPL v1.1](https://joinup.ec.europa.eu/software/page/eupl/licence-eupl)
(any language version applies, English version is included in https://github.com/oskariorg/oskari-docs/blob/master/documents/LICENSE-EUPL.pdf).
You can choose between one of them if you use this work.

`SPDX-License-Identifier: MIT OR EUPL-1.1`

Copyright (c) 2014-present National Land Survey of Finland
