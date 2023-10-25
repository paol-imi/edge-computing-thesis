package commands;

import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import commands.annotations.RequiresQueryAnnotation;
import daos.ConfigurationDAO;
import daos.SessionsDAO;
import daos.SessionsDataDAO;
import model.SessionToken;
import static utils.Logger.*;

@RequiresQueryAnnotation.RequiresQuery(query = "type")
@RequiresQueryAnnotation.RequiresQuery(query = "value")
public class TestFunction implements ICommand {

    public void Handle(IRequest req, IResponse res) {
        String typeRequested = req.getQuery().get("type");
        String valueRequested = req.getQuery().get("value");

        Logger.info("About to test: " + typeRequested + " with " + valueRequested);

        switch (typeRequested) {
            case "configuration":
                testConfiguration(res);
                break;
            case "sessionMetadata":
                testSessionMetadata(valueRequested, res);
                break;
            case "sessionMetadataLocations":
                testSessionMetadataLocations(valueRequested, res);
                break;
            case "sessionData":
                testSessionData(valueRequested, res);
                break;
            case "session":
                testSession(valueRequested, res);
                break;
            default:
                error(typeRequested, res);
        }
    }

    private void testConfiguration(IResponse res) {
        String message =
                "Offloading status: " + ConfigurationDAO.getOffloading();

        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(200);
    }

    private void testSessionMetadata(String sessionId, IResponse res) {
        String message =
                "Session metadata <" + sessionId + ">: " + getSessionToken(sessionId) + "\n";

        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(200);
    }

    private void testSessionMetadataLocations(String sessionId, IResponse res) {
        String message =
                "Session metadata <" + sessionId + ">: " + getSessionTokenLocations(sessionId) + "\n";

        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(200);
    }

    private void testSessionData(String sessionId, IResponse res) {
        String message =
                "Session data <" + sessionId + ">: " + SessionsDataDAO.getSessionData(sessionId).toJSON() + "\n";

        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(200);
    }

    private void testSession(String sessionId, IResponse res) {
        String message =
                "Session metadata <" + sessionId + ">: " + getSessionTokenLocations(sessionId) + "\n" +
                        "Session data <" + sessionId + ">: " + SessionsDataDAO.getSessionData(sessionId).toJSON() + "\n";

        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(200);
    }

    private void error(String type, IResponse res) {
        String message = "Type <" + type + "> is not valid.\n";
        Logger.info(message);
        res.setBody(message);
        res.setStatusCode(400);
    }

    private String getSessionToken(String sessionId) {
        SessionToken token = SessionsDAO.getSessionToken(sessionId);
        if (token == null) {
            return "<session_not_present_in_this_node>";
        } else {
            return token.getJson();
        }
    }

    private String getSessionTokenLocations(String sessionId) {
        SessionToken token = SessionsDAO.getSessionToken(sessionId);
        if (token == null) {
            return "<session_not_present_in_this_node>";
        } else {
            return token.getJsonLocationsOnly();
        }
    }
}
