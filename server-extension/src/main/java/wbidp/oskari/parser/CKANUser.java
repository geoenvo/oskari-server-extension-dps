package wbidp.oskari.parser;

import fi.nls.oskari.domain.User;

/**
 * A customized version of Oskari User class.
 * 
 * This class has additional CKANPasswordHash field for storing passwords
 * in CKAN format (custom hash).
 */
public class CKANUser extends User  {
    private String CKANPasswordHash = "";

    public void setCKANPasswordHash(String CKANPasswordHash) {
        this.CKANPasswordHash = CKANPasswordHash;
    }

    public String getCKANPasswordHash() {
        return CKANPasswordHash;
    }
}