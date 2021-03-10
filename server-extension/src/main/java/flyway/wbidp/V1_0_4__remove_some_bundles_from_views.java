package flyway.wbidp;

import fi.nls.oskari.util.FlywayHelper;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.util.List;

/**
 * Remove some unused bundles from views.
 * These might be added back later.
 */
public class V1_0_4__remove_some_bundles_from_views implements JdbcMigration {
        private static final String[] BUNDLE_IDS = {"search", "statsgrid", "heatmap", "timeseries", "metadatacataloque"};

        public void migrate(Connection connection) throws Exception {
            final List<Long> views = FlywayHelper.getUserAndDefaultViewIds(connection);
            for( int i = 0; i <= BUNDLE_IDS.length - 1; i++) {
                for (Long viewId : views) {
                    if (FlywayHelper.viewContainsBundle(connection, BUNDLE_IDS[i], viewId)) {
                        FlywayHelper.removeBundleFromView(connection, BUNDLE_IDS[i], viewId);
                    }
                }
            }
        }
    }
