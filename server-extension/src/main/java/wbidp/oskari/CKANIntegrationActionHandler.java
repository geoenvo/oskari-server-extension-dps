package wbidp.oskari;

import wbidp.oskari.db.SynchronizeDatabase;

import fi.nls.oskari.annotation.OskariActionRoute;
import fi.nls.oskari.control.*;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.ResponseHelper;

/**
 * CKAN Integration Rest action route
 * 
 * NOTE! This is a convenience class just for testing! 
 * Will be removed from the final version.
 */
@OskariActionRoute("CKANAction")
public class CKANIntegrationActionHandler extends RestActionHandler {

    private static final Logger LOG = LogFactory.getLogger(CKANIntegrationActionHandler.class);

    public void preProcess(ActionParameters params) throws ActionException {
        // common method called for all request methods
        LOG.info(params.getUser(), "Accessing route", getName());
    }

    @Override
    public void handleGet(ActionParameters params) throws ActionException {
        handlePost(params);
    }

    @Override
    public void handlePost(ActionParameters params) throws ActionException {
        if (params.getUser() != null && params.getUser().isAdmin()) {
            SynchronizeDatabase syncDb = new SynchronizeDatabase();
            syncDb.synchronizeUsersWithRolesFromCKAN();
            syncDb.synchronizeLayersFromCKAN();
        } else {
            ResponseHelper.writeResponse(params, params.getUser() + " cannot access CKAN synchronizer.");
        }
    }

    @Override
    public void handlePut(ActionParameters params) throws ActionException {
        throw new ActionParamsException(String.format("PUT-method for route %s not implemented.", getName()));
    }

    @Override
    public void handleDelete(ActionParameters params) throws ActionException {
        throw new ActionParamsException(String.format("DELETE-method for route %s not implemented.", getName()));
    }


}
