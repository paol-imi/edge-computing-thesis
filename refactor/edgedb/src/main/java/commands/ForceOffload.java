package commands;

import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import commands.annotations.RequiresHeaderAnnotation;
import commands.services.ForceOffloadService;
import daos.SessionsLocksDAO;
import static utils.Logger.*;

/**
 * force-offload API:
 * Header X-forced-session: sessionId of the session to offload
 */
@RequiresHeaderAnnotation.RequiresHeader(header = "X-forced-session")
public class ForceOffload implements ICommand {

    public void Handle(IRequest req, IResponse res) {
        String forcedSessionId = req.getHeader("X-forced-session");

        Logger.info("Header X-forced-session: " + forcedSessionId);

        /* --------- Checks before using the session --------- */
        if (!acquireLock(res, forcedSessionId))
            return;

        new ForceOffloadService().Handle(res, forcedSessionId);

        /* --------- Release session --------- */
        releaseLock(res, forcedSessionId);
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
