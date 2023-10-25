package commands.services;

import com.openfaas.model.IResponse;
import commands.wrappers.WrapperOffloadSession;
import daos.SessionsDAO;
import model.SessionToken;
import utils.EdgeInfrastructureUtils;
import static utils.Logger.*;

public class ForceOffloadService {

    public void Handle(IResponse res, String forcedSessionId) {
        SessionToken sessionToOffload;

        /* --------- Checks before using the session --------- */
        if (!sessionExists(res, forcedSessionId))
            return;
        sessionToOffload = SessionsDAO.getSessionToken(forcedSessionId);

        /* --------- Offload --------- */
        offloadSession(res, sessionToOffload);
    }

    private void offloadSession(IResponse res, SessionToken sessionToOffload) {
        Logger.info("Session token about to be offloaded: " + sessionToOffload.getJson());

        // call parent node to offload the session
        String message = "Offloading:\n" +
                EdgeInfrastructureUtils.getParentLocationId(System.getenv("LOCATION_ID")) + "\n" +
                sessionToOffload.getJsonLocationsOnly();
        new WrapperOffloadSession()
                .gateway(EdgeInfrastructureUtils.getParentHost(System.getenv("LOCATION_ID")))
                .sessionToOffload(sessionToOffload)
                .call();

        // if we are not the proprietary of the session, we have to
        // set the currentLocation to proprietaryLocation so that the proprietary
        // will properly redirect to the actual currentLocation
        if (!sessionToOffload.proprietaryLocation.equals(System.getenv("LOCATION_ID"))) {
            sessionToOffload.currentLocation = sessionToOffload.proprietaryLocation;
            SessionsDAO.setSessionToken(sessionToOffload);
        }

        Logger.info(message);
        res.setStatusCode(200);
        res.setBody(message);
    }

    private boolean sessionExists(IResponse res, String session) {
        SessionToken sessionToken = SessionsDAO.getSessionToken(session);
        if (sessionToken == null) {
            Logger.info("Node is empty, can't force an offload");
            res.setStatusCode(400);
            res.setBody("Node is empty, can't force an offload");
            return false;
        }
        return true;
    }
}
