package dev.qwrky.taubot.APIManager;


import java.util.UUID;

enum AccountType {
    USER,
    GOVERNMENT,
    CORPORATION,
    CHARITY
}


public class Account {
    public UUID account_id;
    public Long owner_id;
    public String account_name;
    public AccountType account_type;
    public Integer balance;

    public Integer getBalance() {
        return balance;
    }
}
