import com.opensymphony.workflow.InvalidInputException
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.CustomFieldManager

def customFieldManager = ComponentAccessor.getCustomFieldManager()

switch (issue.getIssueType().getId()) {
	case "10203": //Störung mit Stillstand
		//Prüfen ob das Feld "Ende" gesetzt ist
		def endeCustomField = customFieldManager.getCustomFieldObject("customfield_10411"); //Ende
		def endeCustomFieldValue = issue.getCustomFieldValue(endeCustomField)
		if (endeCustomFieldValue == null) {
			throw new InvalidInputException("Das Ende muss gesetzt werden");
		}
		break;
	default:
		//Do nothing
		break;
}


