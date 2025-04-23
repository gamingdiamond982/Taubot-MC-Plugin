package dev.qwrky.taubot.APIManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.qwrky.taubot.Taubot;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Ref;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

class Reference {
    public String ref_id;
    public Reference() {} // needed for jackson
    public Reference(UUID ref_id) {
        this.ref_id = ref_id.toString();
    }
}

class Key {
    public String key;
    public Key() {} // needed for jackson

    public Key(String key) {
        this.key = key;
    }
}

class Transaction {
    public UUID from_account;
    public UUID to_account;
    public Integer amount;

    public Transaction(UUID from, UUID to, Integer am) {
        from_account = from;
        to_account = to;
        amount = am;

    }
}


public class APIManager {
    /*
    A class used to abstract away API calls
     */

    private final URI APIUri;
    private final UUID appID;
    private final String masterKey;

    private ObjectMapper objectMapper;
    private HashMap<UUID, String> keyMap = new HashMap<>();

    private Logger logger;
    
    public APIManager(UUID appID, String masterKey, String apiUri) throws URISyntaxException {
        this.appID = appID;
        this.masterKey = masterKey;
        this.objectMapper = new ObjectMapper();
        logger = Taubot.getPlugin().getLogger();
        APIUri = new URI(apiUri);
    }



    private HttpRequest post(String resource, String token, String json) throws URISyntaxException {
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json);
        return HttpRequest.newBuilder()
                .uri(new URI(APIUri + resource))
                .POST(body)
                .header("Authorization", token)
                .build();
    }

    private HttpRequest get(String resource, String token) throws URISyntaxException {
        return HttpRequest.newBuilder()
                .uri(new URI(APIUri + resource))
                .header("Authorization", token)
                .build();
    }

    private void makeRequest(HttpRequest request, Callback<HttpResponse<String>> callback) throws IOException, InterruptedException {
        logger.log(Level.INFO,"Made request: " + request.uri());
        Thread newThread = new Thread(() -> {
            HttpClient client = HttpClient.newHttpClient();
            try {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                callback.setResult(resp);
            } catch (IOException | InterruptedException e) {
                callback.setException(e);
                callback.run();
            }
            callback.run();
        });
        newThread.start();
    }

    public void getAuthLink(Player player, Callback<String> callback) {
        try {
            makeRequest(
                        post(
                                "/oauth-references",
                                masterKey,
                                objectMapper.writeValueAsString(new Reference(player.getUniqueId()))
                        ), new Callback<>() {
                            @Override
                            public void run() {
                                if (result.statusCode() != 201) {
                                    callback.setResult(null);
                                    callback.run();
                                }
                                callback.setResult(APIUri + "/oauth/grant?ref="+player.getUniqueId()+"&aid="+appID);
                                callback.run();
                            }
                        }
            );
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }


    }

    private void getAuthToken(Player player, Callback<String> callback) {
        if (keyMap.containsKey(player.getUniqueId())) {
            callback.setResult(keyMap.get(player.getUniqueId()));
        }
        fetchAuthToken(player, callback);
    }

    private void fetchAuthToken(Player player, Callback<String> callback) {
        try {
            makeRequest(get("/retrieve-key/" + player.getUniqueId(), masterKey), new Callback<HttpResponse<String>>() {
                @Override
                public void run() {
                    if (result.statusCode() != 200) {
                        callback.setResult(null);
                        callback.run();
                    }
                    Key key;
                    try {
                        key = objectMapper.readValue(result.body(), Key.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    callback.setResult(key.key);
                    callback.run();
                }
            });
        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public void getUserAccount(Player player, Callback<Account> callback) {
        // gets the account of a linked player
         getAuthToken(player, new Callback<>() {
            @Override
            public void run() {

                if (result == null) {
                    callback.setResult(null);
                    callback.run();
                }
                try {
                    makeRequest(get( "/users/me", result), new Callback<>() {
                        @Override
                        public void run() {
                            Account account;
                            try {
                                account = objectMapper.readValue(result.body(), Account.class);
                            } catch (JsonProcessingException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                            callback.setResult(account);
                            callback.run();
                        }
                    });
                } catch (IOException | InterruptedException | URISyntaxException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void getAccount(String token, String name, Callback<Account> callback) {
        try {
            makeRequest(get("/accounts/by-name/" + encode(name), token), new Callback<>() {
                @Override
                public void run() {
                    Account account;
                    try {
                        account = objectMapper.readValue(result.body(), Account.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    callback.setResult(account);
                    callback.run();
                }
            });

        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    public void getAccount(Player authorizer, String name, Callback<Account> callback) {
        // will return account with balance info if the player has the necessary perms
        getAuthToken(authorizer, new Callback<>() {
            @Override
            public void run() {
                getAccount(result, name, callback);
            }
        });

    }

    public void getAccount(String name, Callback<Account> callback) {
        // will return accounts without balance information
        getAccount(masterKey, name, callback);
    }

    public void transfer(Player authorizer, Account from, Account to, Integer amount, Callback<Boolean> callback) {
        getAuthToken(authorizer, new Callback<>() {
            @Override
            public void run() {
                Transaction t = new Transaction(from.account_id, to.account_id, amount);
                try {
                    makeRequest(post("/transactions/", result, objectMapper.writeValueAsString(t)), new Callback<>() {
                        @Override
                        public void run() {
                            callback.setResult(result.statusCode() == 200);
                            callback.run();
                        }
                    });
                } catch (IOException | InterruptedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });


    }



}
