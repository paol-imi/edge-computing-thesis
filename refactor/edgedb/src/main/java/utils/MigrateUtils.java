package utils;

import com.google.gson.Gson;
import commands.wrappers.Response;
import commands.wrappers.WrapperMigrateSession;
import commands.wrappers.WrapperUpdateSession;
import daos.SessionsDAO;
import daos.SessionsDataDAO;
import daos.SessionsRequestsDAO;
import model.SessionToken;
import model.sessiondata.SessionData;

import static utils.Logger.*;

public class MigrateUtils {

    /**
     * Migrate the given session from the currentLocation of the session to this node
     *
     * @param sessionJson session token json of the session
     * @return the new session token json
     */
    public static SessionToken migrateSessionFromRemoteToLocal(String sessionJson) {
        // update session token with the local
        SessionToken sessionToken = SessionToken.Builder.buildFromJSON(sessionJson);
        String migrateFrom = sessionToken.currentLocation;
        sessionToken.currentLocation = System.getenv("LOCATION_ID");

        // save new json object in redis
        SessionsDAO.setSessionToken(sessionToken);

        // (1/3) prepare for migration of data from the location that has the session data, to this node
        String fromLocation = EdgeInfrastructureUtils.getGateway(migrateFrom);
        String sessionToMigrate = sessionToken.session;
        Logger.info("Migrating session from:\n\t" + fromLocation);

        // (2/3) migrate session data
        Response responseSessionData = new WrapperMigrateSession()
                .gateway(fromLocation)
                .sessionToMigrate(sessionToMigrate)
                .typeSessionData()
                .call();
        SessionData sessionData = new Gson().fromJson(responseSessionData.getBody(), SessionData.class);
        SessionsDataDAO.setSessionData(sessionToMigrate, sessionData);

        // (3/3) migrate request ids
        Response responseRequestIds = new WrapperMigrateSession()
                .gateway(fromLocation)
                .sessionToMigrate(sessionToMigrate)
                .typeRequestIds()
                .call();
        String[] requestIds = responseRequestIds.getBody().trim().split("\\s*,\\s*");
        SessionsRequestsDAO.addSessionRequests(sessionToMigrate, requestIds);

        // send new json object to proprietaryLocation
        Logger.info("Updating proprietary:\n\t" + sessionToken.proprietaryLocation + "\n\t" + sessionToken.getJson());
        new WrapperUpdateSession()
                .gateway(EdgeInfrastructureUtils.getGateway(sessionToken.proprietaryLocation))
                .sessionToUpdate(sessionToken)
                .call();

        return sessionToken;
    }
}
