package org.protege.owl.server.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.ImportChange;
import org.semanticweb.owlapi.model.OWLAxiomChange;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeVisitor;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;
import org.semanticweb.owlapi.model.SetOntologyID;

public class Utilities {

    /**
     * This routine removes all set ontology id changes.  Other than this, the result of this call is to 
     * return a minimal list of changes in the sense that 
     * <ol> 
     * <li> the returned list of changes has the same net effect as the original changes and
     * <li> the list of changes is minimal.
     * </ol>
     * That is to say that changes that will have no effect on the net result are removed.
     */
    public static List<OWLOntologyChange> removeRedundantChanges(List<OWLOntologyChange> changes) {
        List<OWLOntologyChange> result = new ArrayList<OWLOntologyChange>();
        for (int i = 0; i < changes.size(); i++) {
            OWLOntologyChange change1 = changes.get(i);
            if (!(change1 instanceof SetOntologyID)) {
                boolean overlap = false;
                for (int j = i + 1; j < changes.size(); j++) {
                    OWLOntologyChange change2 = changes.get(j);
                    if (overlappingChange(change1, change2)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    result.add(change1);
                }
            }
        }
        return result;
    }
    
    /**
     * This routine calculates a minimal set of changes x such that 
     * <center>
     *    firstChanges o secondChanges = secondChanges o x
     * </center>
     * where composition goes from left to right.
     * 
     * @param firstChanges 
     * @param secondChanges 
     * @return a minimal set of changes that when applied after secondChanges will result in the same result as if 
     *      we had first made the changes {@link firstChanges} and then made the changes {@link secondChanges}
     */
    public static List<OWLOntologyChange> swapOrderOfChangeLists(List<OWLOntologyChange> firstChanges, List<OWLOntologyChange> secondChanges) {
    	List<OWLOntologyChange> result = removeRedundantChanges(firstChanges);
    	List<OWLOntologyChange> toRemove = new ArrayList<OWLOntologyChange>();
    	for (OWLOntologyChange firstChange : result) {
			if (overlappingChange(firstChange, secondChanges)) {
				toRemove.add(firstChange);
			}
    	}
    	result.removeAll(toRemove);
    	return result;
    }

    public static boolean overlappingChange(OWLOntologyChange change1, OWLOntologyChange change2) {
        if (change1.getOntology().equals(change2.getOntology())) {
            OverlapVisitor visitor = new OverlapVisitor(change1);
            change2.accept(visitor);
            return visitor.isOverlapping();
        }
        return false;
    }
    
    public static boolean overlappingChange(OWLOntologyChange change1, List<OWLOntologyChange> changes) {
    	boolean overlapping = false;
    	for (OWLOntologyChange change2 : changes) {
    		if (overlappingChange(change1, change2)) {
    			overlapping = true;
    			break;
    		}
    	}
    	return overlapping;
    }

    protected static class OverlapVisitor implements OWLOntologyChangeVisitor {
        private OWLOntologyChange testChange;
        private boolean overlapping;
        
        public OverlapVisitor(OWLOntologyChange change) {
            testChange = change;
            overlapping = false;
        }
        
        public boolean isOverlapping() {
            return overlapping;
        }
    
        
        public void visit(AddAxiom change) {
            overlapping = testChange instanceof OWLAxiomChange 
                                && ((OWLAxiomChange) testChange).getAxiom().equals(change.getAxiom());
        }
    
        
        public void visit(RemoveAxiom change) {
            overlapping = testChange instanceof OWLAxiomChange 
                                && ((OWLAxiomChange) testChange).getAxiom().equals(change.getAxiom());
        }
    
        
        public void visit(SetOntologyID change) {
            overlapping = testChange instanceof SetOntologyID;
        }
    
        
        public void visit(AddImport change) {
            overlapping = testChange instanceof ImportChange 
                               && ((ImportChange) testChange).getImportDeclaration().equals(change.getImportDeclaration());
        }
    
        
        public void visit(RemoveImport change) {
            overlapping = testChange instanceof ImportChange 
                              && ((ImportChange) testChange).getImportDeclaration().equals(change.getImportDeclaration());
        }
    
        
        public void visit(AddOntologyAnnotation change) {
            if (testChange instanceof AddOntologyAnnotation) {
                overlapping = ((AddOntologyAnnotation) testChange).getAnnotation().equals(change.getAnnotation());
            }
            else if (testChange instanceof RemoveOntologyAnnotation) {
                overlapping = ((RemoveOntologyAnnotation) testChange).getAnnotation().equals(change.getAnnotation());
            }
        }
    
        
        public void visit(RemoveOntologyAnnotation change) {
            if (testChange instanceof AddOntologyAnnotation) {
                overlapping = ((AddOntologyAnnotation) testChange).getAnnotation().equals(change.getAnnotation());
            }
            else if (testChange instanceof RemoveOntologyAnnotation) {
                overlapping = ((RemoveOntologyAnnotation) testChange).getAnnotation().equals(change.getAnnotation());
            }
        }
        
    }
    
    public static Set<OWLOntology> getChangedOntologies(List<OWLOntologyChange> changes) {
        Set<OWLOntology> ontologies = new HashSet<OWLOntology>();
        for (OWLOntologyChange change : changes) {
            ontologies.add(change.getOntology());
        }
        return ontologies;
    }

}
