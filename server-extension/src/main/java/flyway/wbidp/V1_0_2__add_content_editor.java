package flyway.wbidp;

import fi.nls.oskari.util.FlywayHelper;

import java.sql.Connection;
import java.util.List;

public class V1_0_2__add_content_editor {
    private static final String BUNDLE_ID = "content-editor";

    public void migrate(Connection connection) throws Exception {

        final List<Long> views = FlywayHelper.getUserAndDefaultViewIds(connection);
        for(Long viewId : views){
            if (FlywayHelper.viewContainsBundle(connection, BUNDLE_ID, viewId)) {
                continue;
            }
            FlywayHelper.addBundleWithDefaults(connection, viewId, BUNDLE_ID);
        }
    }
}