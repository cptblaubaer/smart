// Vorgangsanlage issuetype 10200 Ausbau

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.event.type.EventDispatchOption
Class objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade");  
def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass);
Class objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"); 
def objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass);
def issueManager = ComponentAccessor.getIssueManager()

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

def getAttributeByInsightObject(insightObject, attributName, objectTypeAttributeFacade, objectFacade) {
	def typeAttribute = objectTypeAttributeFacade.loadObjectTypeAttributeBean(insightObject.getObjectTypeId(), attributName)

	def attribute = null;
	try {
		attribute = objectFacade.loadObjectAttributeBean(insightObject.getId(), typeAttribute.getId())
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

//"ist belegt" am Attribut von "Einbauposition (Ausbau)" auf "false" setzen
def istBelegtAttribute = getAttributeByCustomFieldObject(10405, "ist belegt", objectTypeAttributeFacade, objectFacade)
def istBelegtFalseValue = createBooleanValue(istBelegtAttribute, false)
setAttributeValue(istBelegtAttribute, istBelegtFalseValue, objectFacade);


//"Equipment zum Einbau" anhand der "Einbauposition (Ausbau)" ermitteln (mittels Inbount über "ist eingebaut in")
def equipmentZumEinbauInsightObject = getInboundReferencedObject(getCustomFieldValue(10405).get(0), "ist eingebaut in", objectFacade)
if (equipmentZumEinbauInsightObject != null) {
	
	//"ist eingebaut" von "Equipment zum Einbau" auf "false" setzen
	def istEingebautAttribute = getAttributeByInsightObject(equipmentZumEinbauInsightObject, "ist eingebaut", objectTypeAttributeFacade, objectFacade)
	def istEingebautFalseValue = createBooleanValue(istEingebautAttribute, false)
	setAttributeValue(istEingebautAttribute, istEingebautFalseValue, objectFacade);
	
	
	//"Einbaustatus seit" von "Equipment zum Einbau" auf den Wert aus dem Custom Field "Vorgangs-Zeitstempel" setzen
	def einbaustatusSeitAttribut = getAttributeByInsightObject(equipmentZumEinbauInsightObject, "Einbaustatus seit", objectTypeAttributeFacade, objectFacade)
	def vorgangsZeitstempelValue = createDateValue(einbaustatusSeitAttribut, getCustomFieldValue(10403))
	setAttributeValue(einbaustatusSeitAttribut, vorgangsZeitstempelValue, objectFacade);
	
	
	//Summary setzen
	issue.setSummary("Ausbau \"" + equipmentZumEinbauInsightObject.getName() + "\" aus \"" + getCustomFieldValue(10405).get(0).getName() + "\"");
	
	
	//Bauteil(e) ergänzen um das Anlage-Objekt "Einbauposition (Ausbau)" und "Equipment zum Einbau" (Das Original-Object, nicht das Detail-Objekt!)
	def bauteilList = [] as ArrayList

	def beschreibtFromEquipmentZumEinbauAttribute = getAttributeByCustomFieldObject(10405, "Beschreibt", objectTypeAttributeFacade, objectFacade);
	if (beschreibtFromEquipmentZumEinbauAttribute != null && beschreibtFromEquipmentZumEinbauAttribute.getObjectAttributeValueBeans().size() > 0) {
	        def equipmentZumEinbauInsightObjectAnlage = objectFacade.loadObjectBean(beschreibtFromEquipmentZumEinbauAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
        	if (equipmentZumEinbauInsightObjectAnlage != null) {
                	bauteilList.add(equipmentZumEinbauInsightObjectAnlage)
        	}
	}

	def beschreibtFromIstEingebautInAttribute = objectFacade.loadObjectAttributeBean(equipmentZumEinbauInsightObject.getId(), "Beschreibt")
	if (beschreibtFromIstEingebautInAttribute != null && beschreibtFromIstEingebautInAttribute.getObjectAttributeValueBeans().size() > 0) {
		def istEingebautInInsightObjectAnlage = objectFacade.loadObjectBean(beschreibtFromIstEingebautInAttribute.getObjectAttributeValueBeans().get(0).getReferencedObjectBeanId());
                if (istEingebautInInsightObjectAnlage != null) {
                        bauteilList.add(istEingebautInInsightObjectAnlage)
                }
	}

	def bauteileCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10402);
	issue.setCustomFieldValue(bauteileCustomField, bauteilList)
	
	//"ist eingebaut in" von "Equipment zum Einbau" leeren
	def istEingebautInAttribute = getAttributeByInsightObject(equipmentZumEinbauInsightObject, "ist eingebaut in", objectTypeAttributeFacade, objectFacade)
	objectFacade.deleteObjectAttributeBean(istEingebautInAttribute.getId())
	
	//Issue speichern
	issue.store();
	def currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()   
	issueManager.updateIssue(currentUser, issue, EventDispatchOption.ISSUE_ASSIGNED, false)
}

return true;

