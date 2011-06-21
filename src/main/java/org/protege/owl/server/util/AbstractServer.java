package org.protege.owl.server.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.protege.owl.server.api.ConflictManager;
import org.protege.owl.server.api.Server;
import org.protege.owl.server.api.ServerOntologyInfo;
import org.protege.owl.server.connection.LocalClientConnection;
import org.protege.owl.server.exception.OntologyConflictException;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeVisitor;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;
import org.semanticweb.owlapi.model.SetOntologyID;

public abstract class AbstractServer implements Server {
    private OWLOntologyManager ontologyManager;
    private ConflictManager conflictManager;
    private Map<IRI, ServerOntologyInfo> ontologyInfoByIRI = new HashMap<IRI, ServerOntologyInfo>();
    private Map<String, ServerOntologyInfo> ontologyInfoByShortName = new HashMap<String, ServerOntologyInfo>();
    
    protected AbstractServer(OWLOntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }
    
    protected abstract boolean contains(IRI ontologyName, int revision, OWLAxiom axiom);
    protected abstract boolean contains(IRI ontologyName, int revision, OWLImportsDeclaration owlImport);
    protected abstract boolean contains(IRI ontologyName, int revision, OWLAnnotation annotation);
    protected abstract int getUpdatedRevision(OWLOntology ontology);
    
    public OWLOntologyManager getOntologyManager() {
        return ontologyManager;
    }
    
    public ConflictManager getConflictManager() {
        return conflictManager;
    }
    
    public void setConflictManager(ConflictManager conflictManager) {
        this.conflictManager = conflictManager;
    }
    
    @Override
    public Map<IRI, ServerOntologyInfo> getOntologyInfoByIRI() {
        return new HashMap<IRI, ServerOntologyInfo>(ontologyInfoByIRI);
    }

    @Override
    public Map<String, ServerOntologyInfo> getOntologyInfoByShortName() {
        return new HashMap<String, ServerOntologyInfo>(ontologyInfoByShortName);
    }
    
    protected void addOntologyInfo(ServerOntologyInfo info) {
        ontologyInfoByIRI.put(info.getOntologyName(), info);
        ontologyInfoByShortName.put(info.getShortName(), info);
    }
    
    protected void removeOntologyInfo(ServerOntologyInfo info) {
        ontologyInfoByIRI.remove(info.getOntologyName());
        ontologyInfoByShortName.remove(info.getShortName());
    }
    
    
    public synchronized void applyChanges(Map<IRI, Integer> versions, List<OWLOntologyChange> changes) throws OntologyConflictException {
        changes = reduceChangeList(versions, changes);
        if (conflictManager != null) {
            conflictManager.validateChanges(versions, changes);
        }
        ontologyManager.applyChanges(changes);
        for (OWLOntology ontology : ChangeUtilities.getChangedOntologies(changes)) {
            IRI ontologyName = ontology.getOntologyID().getOntologyIRI();
            ServerOntologyInfo original = getOntologyInfoByIRI().get(ontologyName);
            int newRevision = getUpdatedRevision(ontology);
            ServerOntologyInfo updated = new ServerOntologyInfo(ontologyName, original.getShortName(), original.getMarkedRevisions(), newRevision);
            addOntologyInfo(updated);
        }
    }
    
    public void save(OWLOntologyID id, int revision, File location) throws IOException, OWLOntologyStorageException {
        LocalClientConnection client = new LocalClientConnection(this);
        OWLOntology ontology;
        try {
            ontology = client.pull(id.getOntologyIRI(), revision);
        } catch (Exception e) {
            throw new OWLOntologyStorageException(e);
        }
        client.getOntologyManager().saveOntology(ontology, new RDFXMLOntologyFormat(), new StreamDocumentTarget(new FileOutputStream(location)));
    }
    
    public List<OWLOntologyChange> reduceChangeList(final Map<IRI, Integer> versions, List<OWLOntologyChange> changes) {
    	return ChangeUtilities.normalizeChangesToBase(changes, new OntologyContainerPredicates() {
			
			@Override
			public boolean contains(OWLOntology ontology, OWLAnnotation annotation) {
	            IRI ontologyName = ontology.getOntologyID().getOntologyIRI();
	            int version = versions.get(ontologyName);
	            return AbstractServer.this.contains(ontologyName, version, annotation);
			}
			
			@Override
			public boolean contains(OWLOntology ontology, OWLImportsDeclaration owlImport) {
	            IRI ontologyName = ontology.getOntologyID().getOntologyIRI();
	            int version = versions.get(ontologyName);
	            return AbstractServer.this.contains(ontologyName, version, owlImport);
			}
			
			@Override
			public boolean contains(OWLOntology ontology, OWLAxiom axiom) {
	            IRI ontologyName = ontology.getOntologyID().getOntologyIRI();
	            int version = versions.get(ontologyName);
	            return AbstractServer.this.contains(ontologyName, version, axiom);
			}
		});
    }

}
