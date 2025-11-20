package br.ifmg.sd.models;

public class User {

    private String id;
    private String email;
    private String name;
    private String password;

    public User() {}

    public User(String id, String email, String name, String password) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return (
            "User{" +
            "id='" +
            id +
            '\'' +
            ", email='" +
            email +
            '\'' +
            ", name='" +
            name +
            '\'' +
            ", password='" +
            password +
            '\'' +
            '}'
        );
    }
}
