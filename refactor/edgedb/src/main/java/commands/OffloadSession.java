package commands;

import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import commands.annotations.RequiresHeaderAnnotation;
import commands.wrappers.WrapperOffloadSession;
import daos.ConfigurationDAO;
import daos.SessionsLocksDAO;
import model.SessionToken;
import utils.EdgeInfrastructureUtils;
import static utils.Logger.*;
import utils.MigrateUtils;

import java.util.Base64;

@RequiresHeaderAnnotation.RequiresHeader(header = "X-session-token")
public class OffloadSession implements ICommand {

    public void Handle(IRequest req, IResponse res) {
        String sessionJson = new String(Base64.getDecoder().decode(req.getHeader("X-session-token")));
        String offloading = ConfigurationDAO.getOffloading();
        if (offloading.equals("accept")) {
            // offload accepted
            Logger.info("Offloading accepted");

            SessionToken sessionToken = SessionToken.Builder.buildFromJSON(sessionJson);

            String sessionId = sessionToken.session;
            while (!acquireLock(res, sessionId)) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            SessionToken newSession = MigrateUtils.migrateSessionFromRemoteToLocal(sessionJson);

            releaseLock(res, sessionId);

            res.setStatusCode(200);
            res.setBody("Offloaded:\n\tOld session: " + sessionJson + "\n\tNew session: " + newSession.getJson());
        } else {
            // offload redirected to parent
            Logger.info("Offloading not accepted");
            Logger.info("Redirecting session to parent:\n\t" + EdgeInfrastructureUtils.getParentLocationId(System.getenv("LOCATION_ID")) + "\n\t" + sessionJson);

            // call parent node to offload the session
            SessionToken sessionToOffload = SessionToken.Builder.buildFromJSON(sessionJson);

            new WrapperOffloadSession()
                    .gateway(EdgeInfrastructureUtils.getParentHost(System.getenv("LOCATION_ID")))
                    .sessionToOffload(sessionToOffload)
                    .call();
        }
    }

    private boolean acquireLock(IResponse res, String session) {
        if (!SessionsLocksDAO.lockSession(session)) {
            Logger.info("Cannot acquire lock on session <" + session + ">");
            res.setStatusCode(400);
            res.setBody("Cannot acquire lock on session <" + session + ">");
            return false;
        }
        return true;
    }

    private boolean releaseLock(IResponse res, String session) {
        if (!SessionsLocksDAO.unlockSession(session)) {
            Logger.info("Cannot release lock on session <" + session + ">");
            res.setStatusCode(500);
            res.setBody("Cannot release lock on session <" + session + ">");
            return false;
        }
        return true;
    }
}
