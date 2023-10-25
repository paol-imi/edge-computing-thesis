package commands;

import com.openfaas.model.IRequest;
import com.openfaas.model.IResponse;
import commands.annotations.RequiresQueryAnnotation;
import daos.SessionsDataDAO;
import daos.SessionsRequestsDAO;
import model.sessiondata.SessionData;
import static utils.Logger.*;

@RequiresQueryAnnotation.RequiresQuery(query = "session")
@RequiresQueryAnnotation.RequiresQuery(query = "data-type")
public class MigrateSession implements ICommand {

    @Override
    public void Handle(IRequest req, IResponse res) {

        String sessionId = req.getQuery().get("session");
        String dataType = req.getQuery().get("data-type");

        Logger.info("About to migrate Session Id: " + sessionId);

        if (dataType.equals("sessionData")) {
            Logger.info("Migrating session data");

            SessionData data = SessionsDataDAO.getSessionData(sessionId);

            res.setBody(data.toJSON());
            res.setStatusCode(200);

            SessionsDataDAO.deleteSessionData(sessionId);
        } else if (dataType.equals("requestIds")) {
            Logger.info("Migrating session request ids");

            String data = SessionsRequestsDAO.getSessionRequests(sessionId).toString();
            data = data.substring(1, data.length() - 1);
            data = data.replaceAll(" ", "");

            res.setBody(data);
            res.setStatusCode(200);

            SessionsRequestsDAO.deleteSessionRequest(sessionId);
        } else {
            String message = "Data-type <" + dataType + "> not recognized";
            Logger.info(message);
            res.setBody(message);
            res.setStatusCode(400);
        }
    }
}
