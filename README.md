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

To automate the process of transferring data between CKAN and Oskari (users, groups and layers), you need to add e.g. these settings to **oskari-ext.properties**:

    ##################################
    # CKAN-Oskari integration setup
    ##################################
    
    oskari.scheduler.job.SynchronizeUserDataJob.cronLine=0 * * * * ?
    oskari.scheduler.job.SynchronizeLayerDataJob.cronLine=0 * * * * ?
    ckan.integration.db.url=jdbc:postgresql://localhost:5432/ckan
    ckan.integration.db.username=oskari
    ckan.integration.db.password=oskari19

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
    permission.EDIT_LAYER_CONTENT.name.en=Edit layer

Also, you need to add the bundle to the dynamic bundles list and modify permissions e.g.

    # bundles that are added on runtime to view if user has one of configured role
    actionhandler.GetAppSetup.dynamic.bundles = admin-hierarchical-layerlist, admin-layereditor, admin-layerselector, admin-layerrights, admin-users, admin
    actionhandler.GetAppSetup.dynamic.bundle.content-editor.roles = Admin

## License

This work is dual-licensed under MIT and [EUPL v1.1](https://joinup.ec.europa.eu/software/page/eupl/licence-eupl)
(any language version applies, English version is included in https://github.com/oskariorg/oskari-docs/blob/master/documents/LICENSE-EUPL.pdf).
You can choose between one of them if you use this work.

`SPDX-License-Identifier: MIT OR EUPL-1.1`

Copyright (c) 2014-present National Land Survey of Finland
