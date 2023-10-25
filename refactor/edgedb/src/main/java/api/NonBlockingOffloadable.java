package api;

import com.openfaas.model.AbstractHandler;
import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import com.openfaas.model.Response;
import commands.services.ForceOffloadService;
import daos.ConfigurationDAO;
import daos.SessionsDAO;
import daos.SessionsRequestsDAO;
import model.SessionToken;
import utils.EdgeInfrastructureUtils;
import static utils.Logger.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

public abstract class NonBlockingOffloadable extends AbstractHandler {

    public IResponse Handle(IRequest req) {
        IResponse res = new Response();
        Logger.info("\n\n\n--------BEGIN NON BLOCKING OFFLOADABLE--------");
        Logger.info("Queries:");
        for (var v : req.getQuery().keySet())
            Logger.info("\t" + v + ":\t" + req.getQuery().get(v));
        Logger.info("Headers:");
        for (var v : req.getHeaders().keySet())
            Logger.info("\t" + v + ":\t" + req.getHeader(v));
        try {
            String sessionId = checkSessionHeader(req, res);
            if (sessionId != null) {
                String requestId = checkRequestIdHeader(req, res);
                if (requestId != null) {
                    Logger.info("(NonBlockingOffloadable) About to locate session <" + sessionId + ">...");
                    SessionToken sessionToken = SessionsDAO.getSessionToken(sessionId);
                    if (sessionToken == null) {
                        Logger.info("(NonBlockingOffloadable) Session does not exists. Creating new session with sessionId <" + sessionId + ">");
                        // We are in the proprietary location, we create the session
                        res = handleNewSession(req, sessionId, requestId);
                    } else {
                        Logger.info("(NonBlockingOffloadable) Session exists. Detecting if locally or offloaded...");
                        if (!sessionToken.currentLocation.equals(System.getenv("LOCATION_ID"))) {
                            // CurrentLocation doesn't match with this location, we have to perform a redirect
                            Logger.info("(NonBlockingOffloadable) Session exists but it is offloaded. About to redirect the request...");
                            res = handleRemoteSession(req, sessionToken);
                        } else {
                            // Session exist and it is in this location
                            Logger.info("(NonBlockingOffloadable) Session exists and it is local. About to handle the request...");
                            res = handleLocalSession(req, sessionId, requestId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            String message = "500 Internal server error\n Stack trace: " + stackTrace;
            Logger.info(message);
            res.setBody(message);
            res.setStatusCode(500);
        }
        Logger.info("--------END NON BLOCKING OFFLOADABLE--------");
        return res;
    }

    public abstract IResponse HandleNonBlockingOffload(IRequest req);

    private String checkSessionHeader(IRequest req, IResponse res) {
        String sessionId = req.getHeader("X-session");
        Logger.info("(NonBlockingOffloadable) X-session: " + sessionId);
        if (sessionId == null) {
            Logger.info("(NonBlockingOffloadable) X-session is null, sending 300");
            res.setStatusCode(300);
            res.setBody("300 Header X-session is not present");
        }
        return sessionId;
    }

    private String checkRequestIdHeader(IRequest req, IResponse res) {
        String requestId = req.getHeader("X-session-request-id");
        Logger.info("(NonBlockingOffloadable) X-session-request-id: " + requestId);
        if (requestId == null) {
            Logger.info("(NonBlockingOffloadable) X-session-request-id is null, sending 300");
            res.setStatusCode(300);
            res.setBody("300 Header X-session-request-id is not present");
        } else {
            Pattern UUID_REGEX =
                    Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

            if (!UUID_REGEX.matcher(requestId).matches()) {
                Logger.info("(NonBlockingOffloadable) X-session-request-id <" + requestId + "> is not a UUID string, sending 300");
                res.setStatusCode(300);
                res.setBody("300 Header X-session-request-id <" + requestId + "> is not a UUID string");
                requestId = null;
            }
        }
        return requestId;
    }

    private boolean checkRequestIdUniqueness(String sessionId, String requestId, IResponse res) {
        if (SessionsRequestsDAO.existsSessionRequest(sessionId, requestId)) {
            Logger.info("(NonBlockingOffloadable) X-session-request-id was already processed, sending 208");
            res.setStatusCode(208);
            res.setBody("208 Header X-session-request-id was already processed");
            return true;
        }
        return false;
    }

    private IResponse handleNewSession(IRequest req, String sessionId, String requestId) {
        IResponse res = new Response();

        SessionToken sessionToken = new SessionToken();
        sessionToken.init(sessionId);
        Logger.info("(NonBlockingOffloadable) New session created: \n\t" + sessionToken.getJson());

        SessionsDAO.setSessionToken(sessionToken);
        Logger.info("(NonBlockingOffloadable) Session saved in Redis");

        if (ConfigurationDAO.getOffloading().equals("accept")) {
            res = handle(req, sessionId, requestId);
        } else {
            Logger.info("(NonBlockingOffloadable) Node is at full capacity, offloading the new session");
            new ForceOffloadService().Handle(res, sessionId);
            res = handleRemoteSession(req, sessionToken);
        }

        return res;
    }

    private IResponse handleRemoteSession(IRequest req, SessionToken sessionToken) {
        String redirectUrl =
                EdgeInfrastructureUtils.getGateway(sessionToken.currentLocation) +
                        "/function/" +
                        System.getenv("FUNCTION_NAME") + "?" +
                        req.getQueryRaw();

        Logger.info("(NonBlockingOffloadable) Redirecting session <" + sessionToken.getJson() + "> to: " + redirectUrl);

        Response res = new Response();
        res.setStatusCode(307);
        res.setBody("307 Session is remote. Location: " + redirectUrl);
        res.setHeader("Location", redirectUrl);
        return res;
    }

    private IResponse handleLocalSession(IRequest req, String sessionId, String requestId) {
        IResponse res;
        res = handle(req, sessionId, requestId);
        return res;
    }

    private IResponse handle(IRequest req, String sessionId, String requestId) {
        IResponse res = new Response();
        if (!checkRequestIdUniqueness(sessionId, requestId, res)) {
            EdgeDB.setCurrentSession(sessionId);
            res = HandleNonBlockingOffload(req);
            // The access time is updated regardless of a successful update of the data
            SessionsDAO.updateAccessTimestampToNow(sessionId);
        }
        return res;
    }
}
