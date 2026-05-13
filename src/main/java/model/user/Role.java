package model.user;


/**
 * Vai trò của người dùng trong hệ thống.
 * Một Account có thể có nhiều role cùng lúc (vừa BIDDER vừa SELLER).
 * Admin là một class riêng, không dùng enum này.
 */
public enum Role{
    BIDDER,
    SELLER,
}