package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

import static ca.ubc.cs317.dict.net.DictStringParser.splitAtoms;
import static ca.ubc.cs317.dict.net.Status.readStatus;

public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private final Socket socketTCP;
    private final BufferedReader fromServer;
    private final PrintWriter toServer;


    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        try {
            socketTCP = new Socket(host, port);
            fromServer = new BufferedReader(new InputStreamReader(socketTCP.getInputStream())
            );
            toServer = new PrintWriter(socketTCP.getOutputStream(), true);
            Status initial = readStatus(fromServer);
            if (initial.getStatusCode() != 220) {
                throw new DictConnectionException();
            }
        } catch (Exception e) {
            throw new DictConnectionException();
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        toServer.println("QUIT");
        try {
            fromServer.readLine();
            socketTCP.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map linking database names to Database objects for all databases supported by the server, or an empty map
     * if no databases are available.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        toServer.println("SHOW DB");
        Status response = readStatus(fromServer);

        switch (response.getStatusCode()) {
            case 110:
                String next = "";
                try {
                    next = fromServer.readLine();
                } catch (IOException e) {
                }

                while (!next.equals(".")) {
                    try {
                        String[] in = splitAtoms(next);
                        Database db = new Database(in[0], in[1]);
                        databaseMap.put(db.getName(), db);
                        next = fromServer.readLine();
                    } catch (IOException e) {
                        //
                    }
                }

                Status serverResponse = readStatus(fromServer);
                if (serverResponse.getStatusCode() != 250) throw new DictConnectionException();
                break;
            case 554:
                break;
            default:
                throw new DictConnectionException();
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server, or an empty set if no strategies are supported.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        toServer.println("SHOW STRAT");
        Status response = readStatus(fromServer);

        switch (response.getStatusCode()) {
            case 555:
                break;
            case 111:
                String next ="";
                try {
                    next = fromServer.readLine();
                } catch (IOException e) {

                }
                while (!next.equals(".")) {
                    try {
                        String[] comps = splitAtoms(next);
                        set.add(new MatchingStrategy(comps[0], comps[1]));
                        next = fromServer.readLine();
                    } catch (IOException e) {

                    }
                }

                Status serverResponse = readStatus(fromServer);
                if (serverResponse.getStatusCode() != 250) throw new DictConnectionException();
                break;
            default:
                throw new DictConnectionException();
        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server, or an empty set if no matches were found.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     * value, or the database or strategy are invalid.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        String message = "MATCH " + database.getName() + " " + strategy.getName() + " " + "\"" + word + "\"";
        toServer.println(message);

        Status initialStatus = readStatus(fromServer);
        switch (initialStatus.getStatusCode()) {
            case 550:
                throw new DictConnectionException("Invalid database");
            case 551:
                throw new DictConnectionException("Invalid strategy");
            case 552:

                break;
            case 152:
                String definitionText = "";
                try {
                    definitionText = fromServer.readLine();
                } catch (IOException e) {

                }
                while (!definitionText.equals(".")) {
                    try {
                        String[] resp = splitAtoms(definitionText);
                        set.add(resp[1]);
                        definitionText = fromServer.readLine();
                    } catch (IOException e) {

                    }
                }
                Status serverResponse = readStatus(fromServer);
                if (serverResponse.getStatusCode() != 250) throw new DictConnectionException();
                break;
            default:
                throw new DictConnectionException("INVALID RESPONSE");
        }

        return set;
    }
    
    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were returned.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected
     * value, or the database is invalid.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        getDatabaseList();

        String message = "DEFINE " + database.getName() + " " + "\"" + word + "\"";
        toServer.println(message);
        Status initialStatus = readStatus(fromServer);

        switch (initialStatus.getStatusCode()) {
            case 550:
                throw new DictConnectionException("Invalid database");
            case 552:

                break;
            case 150:
                String[] firstResponse = splitAtoms(initialStatus.getDetails());
                int numberOfDefinitions = Integer.parseInt(firstResponse[0]);

                for (int i = 0; i < numberOfDefinitions; i++) {
                    Status status = readStatus(fromServer);
                    if (status.getStatusCode() != 151) throw new DictConnectionException();

                    Definition definition = new Definition(word, splitAtoms(status.getDetails())[1]);
                    String definitionText;
                    try {
                        definitionText = fromServer.readLine();
                    } catch (IOException e) {
                        throw new DictConnectionException();
                    }
                    while (!definitionText.equals(".")) {
                        try {
                            definition.appendDefinition(definitionText);
                            definitionText = fromServer.readLine();
                        } catch (IOException e) {
                            throw new DictConnectionException();
                        }
                    }
                    set.add(definition);
                }

                Status closing = readStatus(fromServer);
                if (closing.getStatusCode() != 250) throw new DictConnectionException();
                break;
            default:
                throw new DictConnectionException();
        }

        return set;
    }

}
