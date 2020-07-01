package wbidp.oskari.parser;

import java.util.Set;
import java.util.LinkedHashSet;

import fi.nls.oskari.domain.User;

/**
 * Class for CKAN Organization JSON object
 */
public class CKANOrganization {
    private String name;
    private Set<User> users = new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        if(user != null) {
            users.add(user);
        }
    }
}
