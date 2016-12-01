// Vorgangsanlage issuetype 10002 Einbau

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade");  
def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"); 
def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);def issueManager = ComponentAccessor.getIssueManager()

// Konstanten: Custom-Field-IDs
def int cfIDEquipmentZumEinbau = 11701
def int cfIDEinbauposition = 11703
def int cfIDBauteile = 10402
def int cfIDVorgangszeitstempel = 10403


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

//"eingebaut in" am Attribut von "Equipment zum Einbau" auf "true" setzen
def istEingebautAttribute = getAttributeByCustomFieldObject(cfIDEquipmentZumEinbau, "ist eingebaut", objectTypeAttributeFacade, objectFacade)
def istEingebautTrueValue = createBooleanValue(istEingebautAttribute, true)
setAttributeValue(istEingebautAttribute, istEingebautTrueValue, objectFacade)

//"Einbaustatus seit" am Attribut von "Equipment zum Einbau" auf den Wert aus dem Custom Field "Vorgangs-Zeitstempel" setzen
def einbaustatusSeitAttribut = getAttributeByCustomFieldObject(cfIDEquipmentZumEinbau, "Einbaustatus seit", objectTypeAttributeFacade, objectFacade)
def vorgangsZeitstempelValue = createDateValue(einbaustatusSeitAttribut, getCustomFieldValue(cfIDVorgangszeitstempel))
setAttributeValue(einbaustatusSeitAttribut, vorgangsZeitstempelValue, objectFacade);

//"ist eingebaut in" am Attribut von "Equipment zum Einbau" auf den Wert aus dem Custom Field "Einbauposition" setzen
def istEingebautInAttribut = getAttributeByCustomFieldObject(cfIDEquipmentZumEinbau, "ist eingebaut in", objectTypeAttributeFacade, objectFacade)
def einbaupositionValue = createObjectValueReference(istEingebautInAttribut, getCustomFieldValue(cfIDEinbauposition))
setAttributeValue(istEingebautInAttribut, einbaupositionValue, objectFacade);

//"ist belegt" am Attribut von "Einbauposition" auf "true" setzen
def istBelegtAttribute = getAttributeByCustomFieldObject(cfIDEinbauposition, "ist belegt", objectTypeAttributeFacade, objectFacade)
def isBelegtTrueValue = createBooleanValue(istEingebautAttribute, true)
setAttributeValue(istBelegtAttribute, isBelegtTrueValue, objectFacade);

//Bauteil(e) ergänzen um das Anlage-Objekt "Einbauposition" und "Equipment zum Einbau" (Das Original-Object, nicht das Detail-Objekt!)
def bauteilList = [] as ArrayList

def beschreibtFromEquipmentZumEinbauAttribute = getAttributeByCustomFieldObject(cfIDEquipmentZumEinbau, "Beschreibt", objectTypeAttributeFacade, objectFacade);
if (beschreibtFromEquipmentZumEinbauAttribute != null && beschreibtFromEquipmentZumEinbauAttribute.getObjectAttributeValueBeans().size() > 0) {
	def equipmentZumEinbauInsightObject = objectFacade.loadObjectBean(beschreibtFromEquipmentZumEinbauAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
	if (equipmentZumEinbauInsightObject != null) {
        	bauteilList.add(equipmentZumEinbauInsightObject)
	}
}

def beschreibtFromEinbaupositionAttribute = getAttributeByCustomFieldObject(cfIDEinbauposition, "Beschreibt", objectTypeAttributeFacade, objectFacade);
if (beschreibtFromEinbaupositionAttribute != null && beschreibtFromEinbaupositionAttribute.getObjectAttributeValueBeans().size() > 0) {
        def einbaupositionInsightObject = objectFacade.loadObjectBean(beschreibtFromEinbaupositionAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
        if (einbaupositionInsightObject != null) {
                bauteilList.add(einbaupositionInsightObject)
        }
}
def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(cfIDBauteile);
issue.setCustomFieldValue(bauteileCustomField, bauteilList)

// Zusammenfassung setzen
issue.setSummary ('Einbau "' + getCustomFieldValue(cfIDEquipmentZumEinbau).get(0).getName() + '" in "' + getCustomFieldValue(cfIDEinbauposition).get(0).getName() + '"')

//Issue speichern
issue.store();
def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)



return true; 

