package com.heartsync.app.model;

import java.io.Serializable;

/**
 * 用户实体，对应数据库表 t_user。
 * 密码使用 SHA-256 加盐哈希存储，不保存明文。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    /** 学号，登录唯一标识 */
    private String account;
    /** 昵称 */
    private String nickname;
    /** 班级 */
    private String className;
    /** SHA-256 哈希后的密码 */
    private String passwordHash;
    /** 头像色值索引 */
    private int avatarColor;

    public User() {
    }

    public User(String account, String nickname, String className, String passwordHash) {
        this.account = account;
        this.nickname = nickname;
        this.className = className;
        this.passwordHash = passwordHash;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getAvatarColor() {
        return avatarColor;
    }

    public void setAvatarColor(int avatarColor) {
        this.avatarColor = avatarColor;
    }
}
