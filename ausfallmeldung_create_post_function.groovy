import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade");  
def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"); 
def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);def issueManager = ComponentAccessor.getIssueManager()

// Konstanten: Custom-Field-IDs
def int cfChargen = 10200

def createBooleanValue(attribute, trueOrFalse) {
	def boolValue = attribute.createObjectAttributeValueBean()
	boolValue.setBooleanValue(trueOrFalse)
	return boolValue
}

def createDateValue(attribute, date) {
	def dateValue = attribute.createObjectAttributeValueBean()
	dateValue.setDateValue(date)
	return dateValue
}

def createObjectValueReference(attribute, customFieldValues) {
	def objectReferenceValue = attribute.createObjectAttributeValueBean()
	objectReferenceValue.setReferencedObjectBeanId(customFieldValues.get(0).getId())
	return objectReferenceValue
}

def getAttributeByCustomFieldObject(customFieldId, attributName, objectTypeAttributeFacade, objectFacade) {
	CustomField customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(customFieldId);
	def insightObject = issue.getCustomFieldValue(customField).get(0);
	def typeAttribute = objectTypeAttributeFacade.loadObjectTypeAttributeBean(insightObject.getObjectTypeId(), attributName)
	def attribute = null;
	try {
		attribute = objectFacade.loadObjectAttributeBean(insightObject.getId(), typeAttribute.getId())
		if (attribute == null) {
			attribute = insightObject.createObjectAttributeBean(objectTypeAttributeFacade.loadObjectTypeAttributeBean(typeAttribute.getId()));
		}
	} catch (Exception ee) {
		attribute = insightObject.createObjectAttributeBean(objectTypeAttributeFacade.loadObjectTypeAttributeBean(typeAttribute.getId()));
	}

	return attribute
}

def getCustomFieldValue(customFieldId) {
	CustomField customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(customFieldId);
	return issue.getCustomFieldValue(customField)
}

def setAttributeValue(attribute, value, objectFacade) {
	def values = attribute.getObjectAttributeValueBeans();
	values.clear();
	values.add(value);
	attribute.setObjectAttributeValueBeans(values);
	try {
		objectFacade.storeObjectAttributeBean(attribute);
	} catch (Exception exception) {
		log.warn("Could not update object attribute due to validation exception:" + exception.getMessage());
	}
}

def getInboundReferencedObject(insightObject, referenceType, objectFacade) {
	def inboundReferencedObjects = objectFacade.findObjectInboundReferencedBeans(insightObject.getId(), 0, 9999, 0, true)
	for (def referencedObject : inboundReferencedObjects) {
		if (referencedObject.getObjectTypeAttributeBean().getName() == referenceType) {
			return referencedObject.getObjectBean()
		}
	}
	return null
}


def String Chargen = ""
// Zusammenfassung setzen
getCustomFieldValue(cfChargen).each {charge ->
  Chargen = Chargen + charge.getName() + ", "
}
Chargen = Chargen.substring(0, Chargen.length()-2)
issue.setSummary ('Ausfallmeldung ' + Chargen)


//Issue speichern
issue.store();
def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)



return true; 
