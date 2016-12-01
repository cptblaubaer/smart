// Vorgangsanlage issuetype 10902 Induktionsofen Verschleiﬂfutter Lebensende

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade");  
def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"); 
def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);
def issueManager = ComponentAccessor.getIssueManager()


def createDateValue(attribute, date) {
	def dateValue = attribute.createObjectAttributeValueBean()
	dateValue.setDateValue(date)
	return dateValue
}

def createObjectValueReference(attribute, customFieldValues) {
	def objectReferenceValue = attribute.createObjectAttributeValueBean()
	objectReferenceValue.setReferencedObjectBeanId(customFieldValues.get(0).getId());
	return objectReferenceValue
}

def getAttributeByInsightObject(insightObject, attributName, objectTypeAttributeFacade, objectFacade) {
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
	if (value != null) {
		values.add(value);
	}
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

def EreignistypInsightObject = getCustomFieldValue(11400).get(0) // Ereignistyp

def AnlageInsightObject = getCustomFieldValue(11401).get(0) // Anlage


//Summary setzen
issue.setSummary('"' + EreignistypInsightObject.getName() + '" an "' + AnlageInsightObject.getName() + '"')

//Bauteil setzen
def bauteilList = [] as ArrayList
bauteilList.add(AnlageInsightObject)
def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10402);
issue.setCustomFieldValue(bauteileCustomField, bauteilList)



//Issue speichern
issue.store();
def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)


return true;
