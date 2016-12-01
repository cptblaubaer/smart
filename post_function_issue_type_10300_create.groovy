// Vorgangsanlage issuetype 10300 Induktionsofen Dauerfutter Neuzustellung

import com.atlassian.jira.component.ComponentAccessor
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

def createBooleanValue(attribute, trueOrFalse) {
	def boolValue = attribute.createObjectAttributeValueBean()
	boolValue.setBooleanValue(trueOrFalse)
	return boolValue
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

def induktionsofenTiegeleinsatzInsightObject = getCustomFieldValue(11803).get(0)

//"Zustellung Dauerfutter seit" von "Induktionsofen Tiegeleinsatz" auf den Wert aus dem Custom Field "Vorgangs-Zeitstempel" setzen
def zustellungDauerfutterSeitAttribut = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Zustellung Dauerfutter seit", objectTypeAttributeFacade, objectFacade)
def vorgangsZeitstempelValue = createDateValue(zustellungDauerfutterSeitAttribut, getCustomFieldValue(10403))
setAttributeValue(zustellungDauerfutterSeitAttribut, vorgangsZeitstempelValue, objectFacade)

//"Dauerfutter Material" von "Induktionsofen Tiegeleinsatz" auf den Wert aus dem Custom Field "Induktionsofen Dauerfutter Material" setzen
def dauerfutterMaterialAttribut = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Dauerfutter Material", objectTypeAttributeFacade, objectFacade)
def dauerfutterMaterialValue = createObjectValueReference(dauerfutterMaterialAttribut, getCustomFieldValue(10525))
setAttributeValue(dauerfutterMaterialAttribut, dauerfutterMaterialValue, objectFacade)


//"Dauerfutter eingebaut" von "Induktionsofen Tiegeleinsatz" auf true setzen
def dauerfutterEingebautAttribute = getAttributeByInsightObject(induktionsofenTiegeleinsatzInsightObject, "Dauerfutter eingebaut", objectTypeAttributeFacade, objectFacade)
def dauerfutterEingebautTrueValue = createBooleanValue(dauerfutterEingebautAttribute, true)
setAttributeValue(dauerfutterEingebautAttribute, dauerfutterEingebautTrueValue, objectFacade);


//Summary setzen
issue.setSummary("Neuzustellung Dauerfutter \"" + induktionsofenTiegeleinsatzInsightObject.getName() + "\" mit \"" + getCustomFieldValue(10525).get(0).getName() + "\"");

//Bauteil(e) ergänzen um das Anlage-Objekt "Einbauposition (Ausbau)" und "Equipment zum Einbau" (Das Original-Object, nicht das Detail-Objekt!)
def bauteilList = [] as ArrayList

def beschreibtFromInduktionsofenTiegeleinsatzAttribute = objectFacade.loadObjectAttributeBean(induktionsofenTiegeleinsatzInsightObject.getId(), "Beschreibt")
if (beschreibtFromInduktionsofenTiegeleinsatzAttribute!= null && beschreibtFromInduktionsofenTiegeleinsatzAttribute.getObjectAttributeValueBeans().size() > 0) {
	def induktionsofenTiegeleinsatzInsightObjectAnlage = objectFacade.loadObjectBean(beschreibtFromInduktionsofenTiegeleinsatzAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
	if (induktionsofenTiegeleinsatzInsightObjectAnlage != null) {
		bauteilList.add(induktionsofenTiegeleinsatzInsightObjectAnlage)
	}
}

def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10402);
issue.setCustomFieldValue(bauteileCustomField, bauteilList)


//Issue speichern
issue.store();
def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)

return true;
