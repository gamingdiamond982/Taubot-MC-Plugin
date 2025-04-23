package dev.qwrky.taubot;

import dev.qwrky.taubot.APIManager.APIManager;
import dev.qwrky.taubot.APIManager.Account;
import dev.qwrky.taubot.APIManager.Callback;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;
import java.util.UUID;
import java.util.logging.Level;


public final class Taubot extends JavaPlugin implements CommandExecutor {

    APIManager apiManager;

    private static Taubot instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        try {
            apiManager = new APIManager(UUID.fromString(config.getString("application_id")), config.getString("application_master_key"), config.getString("api_uri"));
        } catch (URISyntaxException | NullPointerException e) {
            getLogger().log(Level.SEVERE, "Something went wrong starting taubot");
            getServer().getPluginManager().disablePlugin(this);
        }
        getCommand("link").setExecutor(this);
        getCommand("balance").setExecutor(this);
        getCommand("pay").setExecutor(this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Taubot getPlugin() {
        return instance;
    }

    public static String frmt(Integer amount) {
        int tau = amount / 100;
        int cents = amount % 100;
        if (cents < 10) {
            return tau + ".0"+cents;
        }
        return tau + "." + cents;
    }

    public static Integer parse(String amount) {
        if (!amount.matches("^[0-9]*([.,][0-9]{1,2}0*)?$")) {
            return null;
        }
        String[] parts = amount.split("\\.");
        if (parts.length == 1) {
            return Integer.parseInt(amount)*100;
        }
        Integer value = Integer.parseInt(parts[0])*100;
        String cents = parts[1];
        if (cents.length() == 1) {
            cents += "0";
        }
        cents = cents.substring(0,2);
        value += Integer.parseInt(cents);
        return value;

    }

    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "link":
                apiManager.getAuthLink((Player) commandSender, new Callback<>() {
                    @Override
                    public void run() {
                        TextComponent message = new TextComponent( "Click me" );
                        message.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, result ) );
                        ((Player) commandSender).sendMessage( message );
                    }
                });
                return true;
            case "balance":
                apiManager.getUserAccount((Player) commandSender, new Callback<>() {
                    @Override
                    public void run() {
                        if (result == null) {
                            commandSender.sendMessage("You need to run /link before executing this command");
                        }
                        commandSender.sendMessage("The balance on your personal account is " + frmt(result.getBalance()));
                    }
                });

                return true;
            case "pay":
                if (args.length != 2){
                    return false;
                }

                Integer amount = parse(args[1]);
                if (amount == null) {
                    commandSender.sendMessage("Could not parse : " + args[1] + " as a valid transfer amount");
                    return false;
                }

                apiManager.getUserAccount((Player) commandSender, new Callback<>() {
                    @Override
                    public void run() {
                        Callback<Account> toAccountCallback = getToAccountCallback();
                        if (args[0].startsWith("@")) {
                            apiManager.getAccount(args[0].substring(1), toAccountCallback);
                        } else {
                            Player payee = (Player) getServer().getOfflinePlayer(args[0]); // This method has been deprecated for years
                            // it's deprecated cus some dumbfucks were using player names instead of uuids in persistent storage
                            // it's been deprecated for years so I feel safe using it and the only alternative appears to be iterating over all offline players
                            // and I don't want to do that
                            if (payee == null) {
                                commandSender.sendMessage("Could not find a player by the name: " + args[0]);
                                return;
                            }
                            apiManager.getUserAccount(payee, toAccountCallback);
                        }
                    }

                    private @NotNull Callback<Account> getToAccountCallback() {
                        Account from_acc = result;
                        return new Callback<>() {
                            @Override
                            public void run() {

                                if (result == null) {
                                    commandSender.sendMessage("Could not find any accounts under the name: " + args[0]);
                                }
                                apiManager.transfer((Player) commandSender, from_acc, result, amount, new Callback<Boolean>() {
                                        @Override
                                        public void run() {
                                            if (result) {
                                                commandSender.sendMessage("Successfully performed transaction");
                                            } else {
                                                commandSender.sendMessage("Something went wrong performing that transaction, have you linked your account with taubot?");
                                            }
                                        }
                                    }
                                );


                            }
                        };
                    }
                });




                return true;
        }
        return false;
    }
}
