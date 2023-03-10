package com.openfaas.function.api;

import com.openfaas.function.daos.SessionsDAO;
import com.openfaas.function.daos.SessionsLocksDAO;
import com.openfaas.function.daos.SessionsRequestsDAO;
import com.openfaas.function.model.SessionToken;
import com.openfaas.function.utils.EdgeInfrastructureUtils;
import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import com.openfaas.model.Response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

public abstract class Offloadable extends com.openfaas.model.AbstractHandler {

    public IResponse Handle(IRequest req) {
        IResponse res = new Response();
        System.out.println("\n\n\n--------BEGIN OFFLOADABLE--------");
        try {
            String sessionId = checkSessionHeader(req, res);
            String requestId = checkRequestIdHeader(req, res);
            if (sessionId != null && requestId != null) {
                if (acquireLock(sessionId, res)) {
                    System.out.println("(Offloadable) About to locate session <" + sessionId + ">...");
                    SessionToken sessionToken = SessionsDAO.getSessionToken(sessionId);
                    if (sessionToken == null) {
                        System.out.println("(Offloadable) Session does not exists. Creating new session with sessionId <" + sessionId + ">");
                        // We are in the proprietary location, we create the session
                        res = handleNewSession(req, sessionId, requestId);
                    } else {
                        System.out.println("(Offloadable) Session exists. Detecting if locally or offloaded...");
                        if (!sessionToken.currentLocation.equals(System.getenv("LOCATION_ID"))) {
                            // CurrentLocation doesn't match with this location, we have to perform a redirect
                            System.out.println("(Offloadable) Session exists but it is offloaded. About to redirect the request...");
                            res = handleRemoteSession(req, sessionToken);
                        } else {
                            // Session exist and it is in this location
                            System.out.println("(Offloadable) Session exists and it is local. About to handle the request...");
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
            System.out.println(message);
            res.setBody(message);
            res.setStatusCode(500);
        }
        System.out.println("--------END OFFLOADABLE--------");
        return res;
    }

    public abstract IResponse HandleOffload(IRequest req);

    private String checkSessionHeader (IRequest req, IResponse res) {
        String sessionId = req.getHeader("X-session");
        System.out.println("(Offloadable) X-session: " + sessionId);
        if (sessionId == null) {
            System.out.println("(Offloadable) X-session is null, sending 300");
            res.setStatusCode(300);
            res.setBody("300 Header X-session is not present");
        }
        return sessionId;
    }

    private String checkRequestIdHeader (IRequest req, IResponse res) {
        String requestId = req.getHeader("X-session-request-id");
        System.out.println("(Offloadable) X-session-request-id: " + requestId);
        if (requestId == null) {
            System.out.println("(Offloadable) X-session-request-id is null, sending 300");
            res.setStatusCode(300);
            res.setBody("300 Header X-session-request-id is not present");
        } else {
            Pattern UUID_REGEX =
                    Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

            if(!UUID_REGEX.matcher(requestId).matches()) {
                System.out.println("(Offloadable) X-session-request-id <" + requestId + "> is not a UUID string, sending 300");
                res.setStatusCode(300);
                res.setBody("300 Header X-session-request-id <" + requestId + "> is not a UUID string");
                requestId = null;
            }
        }
        return requestId;
    }

    private boolean checkRequestIdUniqueness (String sessionId, String requestId, IResponse res) {
        if (SessionsRequestsDAO.existsSessionRequest(sessionId, requestId)) {
            System.out.println("(Offloadable) X-session-request-id was already processed, sending 208");
            res.setStatusCode(208);
            res.setBody("208 Header X-session-request-id was already processed");
            return true;
        }
        return false;
    }

    private boolean acquireLock(String sessionId, IResponse res) {
        if (!SessionsLocksDAO.lockSession(sessionId)) {
            System.out.println("(Offloadable) Session <" + sessionId + "> not available. Can't acquire the session's lock");
            res.setStatusCode(503);
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Retry-After
            res.setHeader("Retry-After", "5");
            res.setBody("503 Session <" + sessionId + "> not available");
            return false;
        }
        return true;
    }

    private IResponse handleNewSession (IRequest req, String sessionId, String requestId) {
        IResponse res;

        SessionToken sessionToken = new SessionToken();
        sessionToken.init(sessionId);

        System.out.println("(Offloadable) New session created: \n\t" + sessionToken.getJson());

        SessionsDAO.setSessionToken(sessionToken);

        System.out.println("(Offloadable) Session saved in Redis");
        res = handle(req, sessionId, requestId);

        return res;
    }
    
    private IResponse handleRemoteSession (IRequest req, SessionToken sessionToken) {
        SessionsLocksDAO.unlockSession(sessionToken.session);

        String redirectUrl =
                EdgeInfrastructureUtils.getGateway(sessionToken.currentLocation) +
                        "/function/" +
                        System.getenv("FUNCTION_NAME") + "?" +
                        req.getQueryRaw();

        System.out.println("(Offloadable) Redirecting session <" + sessionToken.getJson() + "> to: " + redirectUrl);

        Response res = new Response();
        res.setStatusCode(307);
        res.setBody("307 Session is remote. Location: " + redirectUrl);
        res.setHeader("Location", redirectUrl);
        return res;
    }
    
    private IResponse handleLocalSession (IRequest req, String sessionId, String requestId) {
        IResponse res;
        res = handle(req, sessionId, requestId);
        return res;
    }

    private IResponse handle(IRequest req, String sessionId, String requestId) {
        IResponse res = new Response();
        if (!checkRequestIdUniqueness(sessionId, requestId, res)) {
            EdgeDB.setCurrentSession(sessionId);
            res = HandleOffload(req);
            // The access time is updated regardless of a successful update of the data
            SessionsDAO.updateAccessTimestampToNow(sessionId);

            // If the unlocking or the saving of data is unsuccessful, return failure
            if (!SessionsLocksDAO.unlockSessionAndUpdateData(sessionId, EdgeDB.getCache(), requestId)) {
                String message = "423 Locked: can't release lock and/or can't save data on redis";
                res = new Response();
                res.setStatusCode(423);
                res.setBody(message);
                System.out.println(message);
            }
        } else {
            SessionsLocksDAO.unlockSession(sessionId);
        }
        return res;
    }
}
