package wbidp.oskari.parser;

import java.util.Set;
import java.util.LinkedHashSet;

import fi.nls.oskari.domain.Role;
import fi.nls.oskari.domain.User;

/**
 * Class for CKAN Organization JSON object
 */
public class CKANOrganization extends Role {
    private String uuid;
    private Set<User> users = new LinkedHashSet<>();
    private String displayName;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        if(user != null) {
            users.add(user);
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
