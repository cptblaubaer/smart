import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.workflow.WorkflowTransitionUtil
import com.atlassian.jira.util.JiraUtils
import com.atlassian.jira.workflow.WorkflowTransitionUtilImpl

def linksFromMassnahme = ComponentAccessor.getIssueLinkManager().getOutwardLinks(issue.id)
def sourceIssue = null
for (Object link in linksFromMassnahme) {
	if (link.getLinkTypeId() == 10200) {
		def issueType = link.getDestinationObject().getIssueTypeId();
		if (
			issueType.equals("10203") || //Stillstand Ereignis
			issueType.equals("10202") || //Text Ereignis
			issueType.equals("10201") || //Störung ohne Stillstand
			issueType.equals("10002") || //Einbau Ereignis
			issueType.equals("10200") || //Ausbau Ereignis
			issueType.equals("10300") || //Induktionsofen Neuzustellung Dauerfutter
			issueType.equals("10301") || //Induktionsofen Neuzustellung Verschleißfutter
			issueType.equals("10100") || //Beinaheunfall
			issueType.equals("10600") || //Geplante Instandhaltung mit Stillstand
                        issueType.equals("10900") || //Meldung Standardereignis
                        issueType.equals("10902") || //Induktionsofen Verschleißfutter Lebensende
                        issueType.equals("10901") || //Induktionsofen Dauerfutter Lebensende
                        issueType.equals("10302") || //Ausfallmeldung
			issueType.equals("10103") || //Blockbeurteilung
                        issueType.equals("10400") || //Unfall
                        issueType.equals("11000")    //Ursachenanalyse
		) {
			sourceIssue = link.getDestinationObject()
			break
		}
	}
}

if (sourceIssue == null) {
	return true;
}

def linksFromEreignis = ComponentAccessor.getIssueLinkManager().getInwardLinks(sourceIssue.id)
def isAnyMassnahmeOffen = false
for (Object link in linksFromEreignis) {
	if (link.getLinkTypeId() == 10200 && (link.getSourceObject().getIssueTypeId().equals("10204") || link.getSourceObject().getIssueTypeId().equals("11000"))) { //"Folgemaßnahme" vom Type "Aufgabe" oder "Ursachenanalyse
		if (link.getSourceObject().getStatus().getStatusCategory().getId() != 3) { //Status Kategorie "Geschlossen" (3)
			isAnyMassnahmeOffen = true
			break;
		}
	}
}

def closeActionId = 0;
def inProgressActionId = 0;
switch (sourceIssue.getIssueTypeId()) {
        case "10200": //Ausbau Ereignis
        case "10002": //Einbau Ereignis
        case "10300": //Induktionsofen Neuzustellung Dauerfutter
        case "10301": //Induktionsofen Neuzustellung Verschleißfutter
        case "10900": //Meldung Standardereignis
        case "10600": //Geplante Instandhaltung mit Stillstand
        case "10202": //Meldung Freitext
	case "10203": //Störung mit Stillstand
        case "10201": //Störung ohne Stillstand
	case "10902": //Induktionsofen Verschleißfutter Lebensende
	case "10901": //Induktionsofen Dauerfutter Lebensende
        	closeActionId = 21
        	inProgressActionId = 31
		break;
	case "10100": //Beinaheunfall
		closeActionId = 21
                inProgressActionId = 51
                break;
        case "10302": //Ausfallmeldung
        case "10103": //Blockbeurteilung
                closeActionId = 11
                inProgressActionId = 21
		break;
        case "10400": //Unfall
                closeActionId = 21
                inProgressActionId = 51
                break;
	case "11000": //Ursachenanalyse
                closeActionId = 21
                inProgressActionId = 61
		break;
	default:
		//Wenn kein passendes Issue, dann abbrechen.
        	return true;
}

def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser().getName()
WorkflowTransitionUtil workflowTransitionUtil = ( WorkflowTransitionUtil ) JiraUtils.loadComponent( WorkflowTransitionUtilImpl.class );
if (isAnyMassnahmeOffen && sourceIssue.getStatus().getStatusCategory().getId() == 3) { //Status Kategorie "Geschlossen" (3)
	//Status des Ereignisses in "In Arbeit" ändern
	workflowTransitionUtil.setIssue(sourceIssue);
	workflowTransitionUtil.setUsername(currentUser);
	workflowTransitionUtil.setAction(inProgressActionId);
	if (workflowTransitionUtil.validate().hasAnyErrors() == false) {
		try {
			workflowTransitionUtil.progress();
		} catch (Exception e) {
		}
	}
} else if (!isAnyMassnahmeOffen && sourceIssue.getStatus().getStatusCategory().getId() != 3) { //Status Kategorie "Geschlossen" (3)
	//Status des Ereignisses in "Bestätigen" ändern
	workflowTransitionUtil.setIssue(sourceIssue);
	workflowTransitionUtil.setUsername(currentUser);
	workflowTransitionUtil.setAction(closeActionId);
	if (workflowTransitionUtil.validate().hasAnyErrors() == false) {
		try {
			workflowTransitionUtil.progress();
		} catch (Exception e) {
		}
	}
}
