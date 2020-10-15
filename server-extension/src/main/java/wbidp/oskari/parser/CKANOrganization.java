package wbidp.oskari.parser;

import java.util.ArrayList;
import java.util.List;

import fi.nls.oskari.domain.Role;
import fi.nls.oskari.domain.User;

/**
 * Class for CKAN Organization JSON object
 */
public class CKANOrganization extends Role {
    private String uuid;
    private List<User> users = new ArrayList<>();
    private String displayName;
    private String title;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<User> getUsers() {
        return users;
    }

    public boolean addUser(User user) {
        if(user != null) {
            return users.add(user);
        }
        return false;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
