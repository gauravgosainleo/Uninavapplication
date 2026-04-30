package com.univishwas.app.auth;

public class User {
    public final String name;
    public final String email;
    public final String society;
    public final String houseNumber;
    public final String pendingHouseNumber;
    public final boolean emailVerified;

    public User(String name,
                String email,
                String society,
                String houseNumber,
                String pendingHouseNumber,
                boolean emailVerified) {
        this.name = name;
        this.email = email;
        this.society = society;
        this.houseNumber = houseNumber;
        this.pendingHouseNumber = pendingHouseNumber;
        this.emailVerified = emailVerified;
    }
}
