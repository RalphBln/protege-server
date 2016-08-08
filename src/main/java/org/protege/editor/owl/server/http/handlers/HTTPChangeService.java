package org.protege.editor.owl.server.http.handlers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.protege.editor.owl.server.api.ChangeService;
import org.protege.editor.owl.server.api.CommitBundle;
import org.protege.editor.owl.server.api.exception.AuthorizationException;
import org.protege.editor.owl.server.api.exception.OutOfSyncException;
import org.protege.editor.owl.server.api.exception.ServerServiceException;
import org.protege.editor.owl.server.base.ProtegeServer;
import org.protege.editor.owl.server.change.ChangeManagementFilter;
import org.protege.editor.owl.server.change.DefaultChangeService;
import org.protege.editor.owl.server.conflict.ConflictDetectionFilter;
import org.protege.editor.owl.server.http.HTTPServer;
import org.protege.editor.owl.server.http.exception.ServerException;
import org.protege.editor.owl.server.policy.AccessControlFilter;
import org.protege.editor.owl.server.versioning.api.ChangeHistory;
import org.protege.editor.owl.server.versioning.api.DocumentRevision;
import org.protege.editor.owl.server.versioning.api.HistoryFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.protege.metaproject.api.ProjectId;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

public class HTTPChangeService extends BaseRoutingHandler {

	private static Logger logger = LoggerFactory.getLogger(HTTPChangeService.class);

	private ChangeService changeService;
	private ConflictDetectionFilter cf;
	private AccessControlFilter acf;

	public HTTPChangeService(ProtegeServer s) {
		cf = new ConflictDetectionFilter(new ChangeManagementFilter(s));
		acf = new AccessControlFilter(cf);
		changeService = new DefaultChangeService(acf.getChangePool());
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) {
		if (exchange.getRequestPath().equalsIgnoreCase(HTTPServer.COMMIT)) {
			try {
				ObjectInputStream ois = new ObjectInputStream(exchange.getInputStream());
				ProjectId pid = (ProjectId) ois.readObject();
				CommitBundle bundle = (CommitBundle) ois.readObject();
				submitCommitBundle(exchange, pid, bundle);
			}
			catch (IOException | ClassNotFoundException e) {
				internalServerErrorStatusCode(exchange, "Server failed to receive the sent data", e);
			}
			catch (ServerException e) {
				handleServerException(exchange, e);
			}
			finally {
				exchange.endExchange(); // end the request
			}
		}
		else if (exchange.getRequestPath().equalsIgnoreCase(HTTPServer.ALL_CHANGES)) {
			try {
				ObjectInputStream ois = new ObjectInputStream(exchange.getInputStream());
				HistoryFile file = (HistoryFile) ois.readObject();
				retrieveAllChanges(exchange, file);
			}
			catch (IOException | ClassNotFoundException e) {
				internalServerErrorStatusCode(exchange, "Server failed to receive the sent data", e);
			}
			catch (ServerException e) {
				handleServerException(exchange, e);
			}
			finally {
				exchange.endExchange(); // end the request
			}
		}
		else if (exchange.getRequestPath().equalsIgnoreCase(HTTPServer.LATEST_CHANGES)) {
			try {
				ObjectInputStream ois = new ObjectInputStream(exchange.getInputStream());
				HistoryFile file = (HistoryFile) ois.readObject();
				DocumentRevision start = (DocumentRevision) ois.readObject();
				retrieveLatestChanges(exchange, file, start);
			}
			catch (IOException | ClassNotFoundException e) {
				internalServerErrorStatusCode(exchange, "Server failed to receive the sent data", e);
			}
			catch (ServerException e) {
				handleServerException(exchange, e);
			}
			finally {
				exchange.endExchange(); // end the request
			}
		}
		else if (exchange.getRequestPath().equalsIgnoreCase(HTTPServer.HEAD)) {
			try {
				ObjectInputStream ois = new ObjectInputStream(exchange.getInputStream());
				HistoryFile file = (HistoryFile) ois.readObject();
				retrieveHeadRevision(exchange, file);
			}
			catch (IOException | ClassNotFoundException e) {
				internalServerErrorStatusCode(exchange, "Server failed to receive the sent data", e);
			}
			catch (ServerException e) {
				handleServerException(exchange, e);
			}
			finally {
				exchange.endExchange(); // end the request
			}
		}
	}

	private void submitCommitBundle(HttpServerExchange exchange, ProjectId pid,
			CommitBundle bundle) throws ServerException {
		try {
			ChangeHistory hist = acf.commit(getAuthToken(exchange), pid, bundle);
			ObjectOutputStream os = new ObjectOutputStream(exchange.getOutputStream());
			os.writeObject(hist);
		}
		catch (AuthorizationException e) {
			throw new ServerException(StatusCodes.UNAUTHORIZED, "Access denied", e);
		}
		catch (OutOfSyncException e) {
			throw new ServerException(StatusCodes.CONFLICT, "Commit failed, please update your local copy first", e);
		}
		catch (ServerServiceException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to receive the commit data", e);
		}
		catch (IOException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to transmit the returned data", e);
		}
	}

	private void retrieveAllChanges(HttpServerExchange exchange, HistoryFile file) throws ServerException {
		try {
			DocumentRevision headRevision = changeService.getHeadRevision(file);
			ChangeHistory history = changeService.getChanges(file, DocumentRevision.START_REVISION, headRevision);
			ObjectOutputStream os = new ObjectOutputStream(exchange.getOutputStream());
			os.writeObject(history);
		}
		catch (ServerServiceException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to get all changes", e);
		}
		catch (IOException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to transmit the returned data", e);
		}
	}

	private void retrieveLatestChanges(HttpServerExchange exchange, HistoryFile file,
			DocumentRevision start) throws ServerException {
		try {
			DocumentRevision headRevision = changeService.getHeadRevision(file);
			ChangeHistory history = changeService.getChanges(file, start, headRevision);
			ObjectOutputStream os = new ObjectOutputStream(exchange.getOutputStream());
			os.writeObject(history);
		}
		catch (ServerServiceException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to get the latest changes", e);
		}
		catch (IOException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to transmit the returned data", e);
		}
	}

	private void retrieveHeadRevision(HttpServerExchange exchange, HistoryFile file) throws ServerException {
		try {
			DocumentRevision headRevision = changeService.getHeadRevision(file);
			ObjectOutputStream os = new ObjectOutputStream(exchange.getOutputStream());
			os.writeObject(headRevision);
		}
		catch (ServerServiceException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to get the head revision", e);
		}
		catch (IOException e) {
			throw new ServerException(StatusCodes.INTERNAL_SERVER_ERROR, "Server failed to transmit the returned data", e);
		}
	}

	private void internalServerErrorStatusCode(HttpServerExchange exchange, String message, Exception cause) {
		logger.error(cause.getMessage(), cause);
		exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
		exchange.getResponseHeaders().add(new HttpString("Error-Message"), message);
	}

	private void handleServerException(HttpServerExchange exchange, ServerException e) {
		logger.error(e.getCause().getMessage(), e.getCause());
		exchange.setStatusCode(e.getErrorCode());
		exchange.getResponseHeaders().add(new HttpString("Error-Message"), e.getMessage());
	}
}