package flyway.wbidp;

import fi.nls.oskari.util.FlywayHelper;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.util.List;

/**
 * Adds download-basket bundle to default and user views.
 */
public class V1_0_0__add_download_basket implements JdbcMigration {
    private static final String BUNDLE_ID = "download-basket";

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